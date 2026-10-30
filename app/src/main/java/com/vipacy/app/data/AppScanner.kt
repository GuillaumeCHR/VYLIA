package com.vipacy.app.data

import android.Manifest
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import com.vipacy.app.domain.model.AppFilter
import com.vipacy.app.domain.model.DetectedPermission
import com.vipacy.app.domain.model.InstalledApp
import com.vipacy.app.domain.repository.AppRepository
import com.vipacy.app.domain.scoring.PermissionRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Lit les applications installées via le [PackageManager] :
 * - **Apps utilisateur** : paquets non système ayant une activité `ACTION_MAIN` / `CATEGORY_LAUNCHER`.
 * - **Apps système** : tous les paquets `FLAG_SYSTEM`, y compris ceux sans icône de lanceur
 *   (services et bloatware constructeur), hormis les paquets purement techniques sans code
 *   (overlays de ressources, thèmes…).
 *
 * Tout le travail (requêtes IPC + chargement des icônes) est fait sur [ioDispatcher]
 * pour ne jamais bloquer le thread UI. Requiert `QUERY_ALL_PACKAGES` sur Android 11+.
 */
class AppScanner(
    private val packageManager: PackageManager,
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppRepository {

    override suspend fun getInstalledApps(filter: AppFilter): List<InstalledApp> =
        withContext(ioDispatcher) {
            val launcherPackages = queryLauncherActivities().mapTo(HashSet()) { it.activityInfo.packageName }
            val enabledAccessibilityPackages = enabledAccessibilityPackages()
            getInstalledPackages().mapNotNull { packageInfo ->
                ensureActive()
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                val isSystemApp = appInfo.isSystemApp()
                val isListed = if (isSystemApp) appInfo.hasCode() else packageInfo.packageName in launcherPackages
                // Filtrage avant loadApp() : on ne charge pas d'icône pour un paquet écarté.
                if (isListed && filter.matches(isSystemApp)) {
                    loadApp(packageInfo, appInfo, isSystemApp, enabledAccessibilityPackages)
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

    private fun loadApp(
        packageInfo: PackageInfo,
        appInfo: ApplicationInfo,
        isSystemApp: Boolean,
        enabledAccessibilityPackages: Set<String>,
    ): InstalledApp {
        val requested = packageInfo.requestedPermissions.orEmpty()
        val flags = packageInfo.requestedPermissionsFlags ?: IntArray(0)
        val granted = requested.filterIndexedTo(HashSet()) { index, _ ->
            flags.getOrElse(index) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
        }

        val declaresAccessibilityService = packageInfo.services.orEmpty()
            .any { it.permission == Manifest.permission.BIND_ACCESSIBILITY_SERVICE }
        if (declaresAccessibilityService && packageInfo.packageName in enabledAccessibilityPackages) {
            granted += Manifest.permission.BIND_ACCESSIBILITY_SERVICE
        }

        val permissions = buildList {
            addAll(requested)
            if (declaresAccessibilityService) add(Manifest.permission.BIND_ACCESSIBILITY_SERVICE)
        }.distinct()

        return InstalledApp(
            label = appInfo.loadLabel(packageManager).toString(),
            packageName = packageInfo.packageName,
            icon = appInfo.loadIcon(packageManager),
            isSystemApp = isSystemApp,
            category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appInfo.category else null,
            apkPaths = listOf(appInfo.sourceDir) + appInfo.splitSourceDirs.orEmpty(),
            lastUpdateTime = packageInfo.lastUpdateTime,
            requestedPermissions = permissions,
            permissions = PermissionRegistry.strongestByFamily(permissions).map { strongest ->
                val familyGranted = permissions.any { permission ->
                    permission in granted && PermissionRegistry.risks[permission]?.family == strongest.family
                }
                DetectedPermission(strongest, isGranted = familyGranted)
            },
        )
    }

    /** Une app système mise à jour via le Play Store reste une app système. */
    private fun ApplicationInfo.isSystemApp(): Boolean =
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    /** Les overlays et paquets de ressources n'ont pas de code : rien à auditer. */
    private fun ApplicationInfo.hasCode(): Boolean = flags and ApplicationInfo.FLAG_HAS_CODE != 0
}
