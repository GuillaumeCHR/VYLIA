package com.vylia.privacy.domain.learn

/**
 * Range n'importe quelle permission dans un thème, à la manière d'Exodus Privacy qui liste toutes les permissions
 * d'une app : d'abord la fiche du lexique, sinon des mots-clés du nom (ordre significatif : le plus spécifique
 * d'abord), sinon « Autres ». Les permissions de badges et raccourcis des lanceurs (Samsung, Huawei, HTC…) sont
 * repérées à part pour être regroupées en une seule ligne.
 */
object PermissionClassifier {

    fun theme(permission: String): PermissionTheme {
        PermissionGlossary.find(permission)?.let { return it.theme }
        if (isLauncherBadge(permission)) return PermissionTheme.OTHER
        val name = permission.uppercase()
        return KEYWORDS.firstOrNull { (_, words) -> words.any { it in name } }?.first ?: PermissionTheme.OTHER
    }

    /** Badges de notification et raccourcis propres aux lanceurs : nombreux, sans enjeu pour la vie privée. */
    fun isLauncherBadge(permission: String): Boolean {
        val name = permission.lowercase()
        return !name.startsWith("android.permission.") &&
            (".launcher" in name || "launcher3" in name || "badge" in name || "badger" in name || "unread" in name)
    }

    private val KEYWORDS: List<Pair<PermissionTheme, List<String>>> = listOf(
        PermissionTheme.ADS to listOf("ADSERVICES", "AD_SERVICES", "AD_ID", "INSTALL_REFERRER", "PRIVACYPASS"),
        PermissionTheme.HEALTH to listOf("HEALTH.", "SENSOR", "ACTIVITY_RECOGNITION"),
        PermissionTheme.LOCATION to listOf("LOCATION"),
        PermissionTheme.SCREEN to listOf("SCREEN", "PROJECTION", "CLIPBOARD", "OVERLAY", "CAPTURE_VIDEO_OUTPUT"),
        PermissionTheme.SYSTEM to listOf(
            "FOREGROUND_SERVICE", "BOOT", "ALARM", "WAKE_LOCK", "NOTIFICATION", "JOBS", "BATTERY", "C2DM",
            "USER_PRESENT", "FULL_SCREEN", "VIBRATE", "DEVICE_POWER", "MESSAGING", "STICKY",
        ),
        PermissionTheme.MESSAGES_CALLS to listOf(
            "SMS", "MMS", "CALL", "PHONE", "VOICEMAIL", "SIP", "TELECOM", "SUBSCRIPTIONS", "APN", "CARRIER",
        ),
        PermissionTheme.CONTACTS to listOf(
            "CONTACTS", "ACCOUNT", "CREDENTIAL", "PROFILE", "BIOMETRIC", "FINGERPRINT", "AUTHENTICATE", "SOCIAL_STREAM",
            "USER_DICTIONARY", "SYNC", "IDENTITY",
        ),
        PermissionTheme.MIC_CAMERA to listOf("AUDIO", "CAMERA", "RECORD", "FLASHLIGHT", "VIDEO"),
        PermissionTheme.CALENDAR to listOf("CALENDAR"),
        PermissionTheme.FILES to listOf("STORAGE", "MEDIA", "DOCUMENTS", "FILESYSTEM", "DOWNLOAD"),
        PermissionTheme.NEARBY to listOf("BLUETOOTH", "NFC", "NEARBY", "UWB", "COMPANION"),
        PermissionTheme.NETWORK to listOf("INTERNET", "NETWORK", "WIFI"),
        PermissionTheme.APPS to listOf("PACKAGE", "INSTALL", "SHORTCUT", "TASKS", "USAGE", "BILLING", "LICENSE"),
        PermissionTheme.CONTROL to listOf(
            "SETTINGS", "KEYGUARD", "LOGS", "INTERACT_ACROSS", "DEVICE_ADMIN", "MDM", "ENTERPRISE", "STATUS_BAR",
            "KILL_BACKGROUND", "WALLPAPER", "COMPONENT_ENABLED", "INSTRUMENTATION", "SYSTEM_DIALOGS",
        ),
    )
}
