package com.vylia.privacy.data.apps

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.PowerManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.DetectedPermission
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.repository.AppRepository
import com.vylia.privacy.domain.scoring.PermissionRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Lit les applications installées via le [PackageManager] :
 * - **Apps utilisateur** : tous les paquets non système avec du code, y compris ceux sans icône de lanceur :
 *   une app qui se cache du tiroir d'apps est justement un signal de logiciel espion (`NO_LAUNCHER_ICON`).
 * - **Apps système** : tous les paquets `FLAG_SYSTEM`, y compris ceux sans icône de lanceur
 *   (services et bloatware constructeur), hormis les paquets purement techniques sans code
 *   (overlays de ressources, thèmes…).
 *
 * Permissions accordées = permissions runtime accordées (`REQUESTED_PERMISSION_GRANTED`), plus les
 * permissions spéciales effectivement actives : superposition, statistiques d'usage, notifications plein écran et
 * alarmes exactes (AppOps), exemption d'optimisation de la batterie, service d'accessibilité, écoute des
 * notifications, administrateur de l'appareil. Pour les accès spéciaux, l'indicateur du PackageManager ne dit pas
 * ce que l'utilisateur a choisi : seul l'état AppOps ou système fait foi.
 *
 * Tout le travail (requêtes IPC + chargement des icônes) est fait sur [ioDispatcher]
 * pour ne jamais bloquer le thread UI. Requiert `QUERY_ALL_PACKAGES` sur Android 11+.
 */
class AppScanner(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppRepository {

    private val packageManager = context.packageManager
    private val contentResolver = context.contentResolver
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val securitySignals = SecuritySignalCollector(context)

    override suspend fun getInstalledApps(filter: AppFilter, only: Set<String>?): List<InstalledApp> =
        withContext(ioDispatcher) {
            val launcherPackages = queryLauncherActivities().mapTo(HashSet()) { it.activityInfo.packageName }
            val specialAccess = SpecialAccess(
                accessibility = enabledAccessibilityPackages(),
                notificationListeners = NotificationManagerCompat.getEnabledListenerPackages(context),
                deviceAdmins = activeDeviceAdminPackages(),
            )
            val signalSnapshot = securitySignals.snapshot()
            getInstalledPackages().mapNotNull { packageInfo ->
                ensureActive()
                if (only != null && packageInfo.packageName !in only) return@mapNotNull null
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                val isSystemApp = appInfo.isSystemApp()
                // Filtrage avant loadApp() : on ne charge pas d'icône pour un paquet écarté.
                if (appInfo.hasCode() && filter.matches(isSystemApp)) {
                    val signals = securitySignals.signals(
                        packageInfo,
                        hasLauncherIcon = packageInfo.packageName in launcherPackages,
                        snap = signalSnapshot,
                    )
                    loadApp(packageInfo, appInfo, isSystemApp, specialAccess, signals)
                } else {
                    null
                }
            }
        }

    private fun queryLauncherActivities(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    /** `GET_SERVICES` sert à repérer les services d'accessibilité déclarés. */
    private fun getInstalledPackages(): List<PackageInfo> {
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }
    }

    /** Paquets dont un service d'accessibilité est activé par l'utilisateur (lisible sans permission). */
    private fun enabledAccessibilityPackages(): Set<String> =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .mapNotNullTo(HashSet()) { ComponentName.unflattenFromString(it)?.packageName }

    /** Paquets disposant d'un accès spécial activé par l'utilisateur, lus une seule fois par scan. */
    private class SpecialAccess(
        val accessibility: Set<String>,
        val notificationListeners: Set<String>,
        val deviceAdmins: Set<String>,
    )

    private fun loadApp(
        packageInfo: PackageInfo,
        appInfo: ApplicationInfo,
        isSystemApp: Boolean,
        specialAccess: SpecialAccess,
        signals: Set<String>,
    ): InstalledApp {
        val packageName = packageInfo.packageName
        val requested = packageInfo.requestedPermissions.orEmpty()
        val flags = packageInfo.requestedPermissionsFlags ?: IntArray(0)
        val granted = requested.filterIndexedTo(HashSet()) { index, _ ->
            flags.getOrElse(index) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
        }

        // Ces permissions protègent un service de l'app : elles ne sont jamais "demandées", seulement déclarées.
        val services = packageInfo.services.orEmpty()
        val declaresAccessibilityService = services.any { it.permission == Manifest.permission.BIND_ACCESSIBILITY_SERVICE }
        val declaresNotificationListener =
            services.any { it.permission == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE }
        // Récepteur d'admin non listé (GET_RECEIVERS alourdirait trop la requête groupée) : seul l'admin actif est vu.
        val isDeviceAdmin = packageName in specialAccess.deviceAdmins

        if (declaresAccessibilityService && packageName in specialAccess.accessibility) {
            granted += Manifest.permission.BIND_ACCESSIBILITY_SERVICE
        }
        if (declaresNotificationListener && packageName in specialAccess.notificationListeners) {
            granted += Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE
        }
        if (isDeviceAdmin) granted += Manifest.permission.BIND_DEVICE_ADMIN
        applyAppOp(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, Manifest.permission.SYSTEM_ALERT_WINDOW, appInfo, requested, granted)
        applyAppOp(AppOpsManager.OPSTR_GET_USAGE_STATS, Manifest.permission.PACKAGE_USAGE_STATS, appInfo, requested, granted)
        // Android 14+ refuse le plein écran par défaut hors appels et alarmes, alors que l'indicateur reste « accordé ».
        applyAppOp(OPSTR_USE_FULL_SCREEN_INTENT, USE_FULL_SCREEN_INTENT, appInfo, requested, granted)
        applyAppOp(OPSTR_SCHEDULE_EXACT_ALARM, SCHEDULE_EXACT_ALARM, appInfo, requested, granted)
        // Permission « normale » qui ne fait qu'ouvrir la fenêtre de demande : accordée seulement si l'app est exemptée.
        if (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in requested) {
            val exempted = runCatching { powerManager.isIgnoringBatteryOptimizations(packageName) }.getOrNull()
            when (exempted) {
                true -> granted += REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                false -> granted -= REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                null -> Unit
            }
        }
        // Permissions d'installation : déclarée = accordée.
        INSTALL_TIME_PERMISSIONS.filterTo(granted) { it in requested }

        val permissions = buildList {
            addAll(requested)
            if (declaresAccessibilityService) add(Manifest.permission.BIND_ACCESSIBILITY_SERVICE)
            if (declaresNotificationListener) add(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
            if (isDeviceAdmin) add(Manifest.permission.BIND_DEVICE_ADMIN)
        }.distinct()

        return InstalledApp(
            label = appInfo.loadLabel(packageManager).toString(),
            packageName = packageInfo.packageName,
            icon = appInfo.loadIcon(packageManager),
            isSystemApp = isSystemApp,
            category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appInfo.category else null,
            apkPaths = listOf(appInfo.sourceDir) + appInfo.splitSourceDirs.orEmpty(),
            lastUpdateTime = packageInfo.lastUpdateTime,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            targetSdk = appInfo.targetSdkVersion,
            appInfoCategory = categoryKey(appInfo),
            declaredPermissions = permissions.mapTo(HashSet(), ::shortName),
            grantedPermissions = granted.mapTo(HashSet(), ::shortName),
            signals = signals,
            requestedPermissions = permissions,
            permissions = PermissionRegistry.strongestByFamily(permissions).map { strongest ->
                val familyGranted = permissions.any { permission ->
                    permission in granted && PermissionRegistry.risks[permission]?.family == strongest.family
                }
                DetectedPermission(strongest, isGranted = familyGranted)
            },
        )
    }

    /**
     * Permission spéciale gérée par AppOps : `MODE_ALLOWED` = accordée par l'utilisateur ; `MODE_DEFAULT`
     * renvoie à l'état de la permission elle-même. Ne s'applique qu'aux apps qui la déclarent.
     */
    private fun applyAppOp(
        op: String,
        permission: String,
        appInfo: ApplicationInfo,
        requested: Array<out String>,
        granted: MutableSet<String>,
    ) {
        if (permission !in requested) return
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(op, appInfo.uid, appInfo.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(op, appInfo.uid, appInfo.packageName)
            }
        } catch (_: SecurityException) {
            return
        } catch (_: IllegalArgumentException) {
            return // opération inconnue de cette version d'Android
        }
        when (mode) {
            AppOpsManager.MODE_ALLOWED -> granted += permission
            AppOpsManager.MODE_DEFAULT -> Unit
            else -> granted -= permission
        }
    }

    private fun activeDeviceAdminPackages(): Set<String> =
        context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins.orEmpty()
            .mapTo(HashSet()) { it.packageName }

    /** Une app système mise à jour via le Play Store reste une app système. */
    private fun ApplicationInfo.isSystemApp(): Boolean =
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    /** Les overlays et paquets de ressources n'ont pas de code : rien à auditer. */
    private fun ApplicationInfo.hasCode(): Boolean = flags and ApplicationInfo.FLAG_HAS_CODE != 0

    private companion object {
        // Constantes en clair : publiques seulement à partir d'Android 14, ou masquées par le SDK.
        private const val USE_FULL_SCREEN_INTENT = "android.permission.USE_FULL_SCREEN_INTENT"
        private const val OPSTR_USE_FULL_SCREEN_INTENT = "android:use_full_screen_intent"
        private const val SCHEDULE_EXACT_ALARM = "android.permission.SCHEDULE_EXACT_ALARM"
        private const val OPSTR_SCHEDULE_EXACT_ALARM = "android:schedule_exact_alarm"
        private const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"

        val INSTALL_TIME_PERMISSIONS = listOf(Manifest.permission.QUERY_ALL_PACKAGES, Manifest.permission.REQUEST_INSTALL_PACKAGES)

        fun shortName(permission: String) = permission.removePrefix("android.permission.")

        /** `ApplicationInfo.category` → clé attendue par les règles ("CATEGORY_GAME", …). */
        fun categoryKey(appInfo: ApplicationInfo): String {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "CATEGORY_UNDEFINED"
            return when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> "CATEGORY_GAME"
                ApplicationInfo.CATEGORY_AUDIO -> "CATEGORY_AUDIO"
                ApplicationInfo.CATEGORY_VIDEO -> "CATEGORY_VIDEO"
                ApplicationInfo.CATEGORY_IMAGE -> "CATEGORY_IMAGE"
                ApplicationInfo.CATEGORY_SOCIAL -> "CATEGORY_SOCIAL"
                ApplicationInfo.CATEGORY_NEWS -> "CATEGORY_NEWS"
                ApplicationInfo.CATEGORY_MAPS -> "CATEGORY_MAPS"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "CATEGORY_PRODUCTIVITY"
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> "CATEGORY_ACCESSIBILITY"
                else -> "CATEGORY_UNDEFINED"
            }
        }
    }
}
