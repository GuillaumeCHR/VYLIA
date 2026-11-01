package com.vylia.privacy.domain.model

import android.graphics.drawable.Drawable

/** Application installée, telle que lue depuis le `PackageManager`. */
data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val isSystemApp: Boolean,
    val category: Int?,
    /** APK de base puis splits éventuels : ce sont eux qui contiennent les fichiers DEX. */
    val apkPaths: List<String>,
    /** Date de dernière installation/mise à jour, utilisée comme clé du cache d'analyse. */
    val lastUpdateTime: Long,
    /** Avec [lastUpdateTime], clé du cache persistant d'analyse des traqueurs. */
    val versionCode: Long = 0L,
    val targetSdk: Int = 0,
    /** Clé de `ApplicationInfo.category` ("CATEGORY_GAME", …), ou "CATEGORY_UNDEFINED". */
    val appInfoCategory: String = "CATEGORY_UNDEFINED",
    /** Noms courts (sans "android.permission."), entrée du moteur de score v2. */
    val declaredPermissions: Set<String> = emptySet(),
    /** Permissions runtime accordées + permissions spéciales actives (AppOps, accessibilité, notifications, admin). */
    val grantedPermissions: Set<String> = emptySet(),
    /** Signaux de sécurité (ids de `security.signals` des règles), collectés au scan. */
    val signals: Set<String> = emptySet(),
    /**
     * Permissions du manifeste, plus `BIND_ACCESSIBILITY_SERVICE` si l'app déclare un service
     * d'accessibilité (cette permission protège le service, elle n'est jamais "demandée").
     */
    val requestedPermissions: List<String>,
    /** Permission la plus intrusive de chaque famille détectée, de la plus à la moins pénalisante. */
    val permissions: List<DetectedPermission>,
)
