package com.vylia.privacy.data

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager

/**
 * Collecte les signaux de sécurité (ids de `security.signals` dans vylia_rules_v3.json) qui alimentent
 * `AppScan.signals`. Tout passe par des API publiques, sans root ; `QUERY_ALL_PACKAGES` est nécessaire,
 * sans quoi Android 11+ masque les services et claviers des autres apps.
 *
 * Usage : un [snapshot] par scan complet, puis [signals] pour chaque paquet.
 */
class SecuritySignalCollector(context: Context) {

    private val pm: PackageManager = context.packageManager
    private val a11y = context.getSystemService(AccessibilityManager::class.java)
    private val imm = context.getSystemService(InputMethodManager::class.java)

    /** État global, coûteux à lire : à prendre une seule fois par scan. */
    class Snapshot(
        val enabledA11yByPackage: Map<String, List<AccessibilityServiceInfo>>,
        val enabledThirdPartyImes: Set<String>,
    )

    fun snapshot(): Snapshot {
        val a11yServices = a11y?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
            .groupBy { it.resolveInfo.serviceInfo.packageName }
        val imes = imm?.enabledInputMethodList.orEmpty()
            .filter { (it.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .mapTo(HashSet()) { it.packageName }
        return Snapshot(a11yServices, imes)
    }

    /**
     * Signaux d'un paquet. [pi] doit avoir été lu avec `GET_PERMISSIONS` et `GET_SERVICES` ;
     * [hasLauncherIcon] vient de la requête groupée des activités de lancement.
     */
    fun signals(pi: PackageInfo, hasLauncherIcon: Boolean, snap: Snapshot): Set<String> = buildSet {
        val pkg = pi.packageName
        val requested = pi.requestedPermissions.orEmpty().toSet()
        if ("android.permission.INTERNET" in requested) add("INTERNET")
        if ("android.permission.RECEIVE_BOOT_COMPLETED" in requested) add("BOOT_COMPLETED")

        // Accessibilité : seuls les services ACTIVÉS comptent. Limite : on lit la configuration courante,
        // un service peut élargir ses eventTypes plus tard via setServiceInfo().
        snap.enabledA11yByPackage[pkg].orEmpty().forEach { info ->
            if (info.eventTypes and TEXT_EVENTS != 0) add("A11Y_TEXT_EVENTS")
            if (info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0) {
                add("A11Y_WINDOW_CONTENT")
            }
        }

        if (pkg in snap.enabledThirdPartyImes) add("IME_ENABLED")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val fgsTypes = pi.services.orEmpty().fold(0) { acc, s -> acc or s.foregroundServiceType }
            if (fgsTypes and ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE != 0) add("FGS_MICROPHONE")
            if (fgsTypes and ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA != 0) add("FGS_CAMERA")
        }

        if (!hasLauncherIcon && pm.getLeanbackLaunchIntentForPackage(pkg) == null) add("NO_LAUNCHER_ICON")

        if (installerOf(pkg) !in KNOWN_STORES) add("SIDELOADED")
    }

    private fun installerOf(pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        private const val TEXT_EVENTS =
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED

        /** Installeurs considérés comme des stores. À compléter selon les besoins. */
        val KNOWN_STORES = setOf(
            "com.android.vending", // Google Play
            "org.fdroid.fdroid", // F-Droid
            "org.fdroid.basic", // F-Droid Basic
            "app.accrescent.client", // Accrescent
            "com.aurora.store", // Aurora Store (miroir Play)
        )
    }
}
