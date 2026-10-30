package com.vipacy.app.domain.model

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
    /**
     * Permissions du manifeste, plus `BIND_ACCESSIBILITY_SERVICE` si l'app déclare un service
     * d'accessibilité (cette permission protège le service, elle n'est jamais "demandée").
     */
    val requestedPermissions: List<String>,
    /** Permission la plus intrusive de chaque famille détectée, de la plus à la moins pénalisante. */
    val permissions: List<DetectedPermission>,
)
