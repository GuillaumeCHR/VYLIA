package com.vipacy.app.domain.model

/** Filtre "Apps Utilisateur" / "Apps Système". */
enum class AppFilter {
    USER,
    SYSTEM,
    ALL,
    ;

    fun matches(app: InstalledApp): Boolean = matches(app.isSystemApp)

    fun matches(isSystemApp: Boolean): Boolean = when (this) {
        USER -> !isSystemApp
        SYSTEM -> isSystemApp
        ALL -> true
    }
}
