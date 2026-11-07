package com.vylia.privacy.data.apps

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build

/** Niveau de protection Android d'une permission, présenté comme sur Exodus Privacy. */
enum class Protection {
    /** Demandée à l'utilisateur dans une fenêtre (« dangerous »). */
    DANGEROUS,

    /** Accès spécial activé dans un écran dédié des paramètres (AppOps). */
    SPECIAL,

    /** Réservée au système ou aux apps signées par le constructeur (« signature »). */
    SYSTEM,

    /** Accordée d'office à l'installation (« normal »). */
    NORMAL,

    /** Permission propre à une app, absente du téléphone : Android n'en connaît rien. */
    UNDEFINED,
}

/** Ce qu'Android dit lui-même d'une permission : protection, libellé et description (traduits par le système). */
data class AndroidPermission(val protection: Protection, val label: String?, val description: String?)

/**
 * Lit les métadonnées des permissions dans le `PackageManager`, dans la langue de [context] (celle choisie dans
 * l'app, pas forcément celle du téléphone). Résultats mémorisés : une permission n'est lue qu'une fois.
 */
class PermissionCatalog(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val cache = HashMap<String, AndroidPermission>()
    private val localizedResources = HashMap<String, Resources?>()

    @Synchronized
    fun describe(permission: String): AndroidPermission = cache.getOrPut(permission) { load(permission) }

    private fun load(permission: String): AndroidPermission {
        val info = try {
            pm.getPermissionInfo(permission, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return AndroidPermission(Protection.UNDEFINED, null, null)
        }
        val res = localizedResources.getOrPut(info.packageName) { localized(info.packageName) }
        return AndroidPermission(
            protection = protectionOf(info),
            label = text(res, info.labelRes) ?: info.nonLocalizedLabel?.toString(),
            description = text(res, info.descriptionRes) ?: info.nonLocalizedDescription?.toString(),
        )
    }

    private fun text(res: Resources?, id: Int): String? =
        if (res == null || id == 0) null else runCatching { res.getText(id).toString() }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Ressources du paquet qui définit la permission (souvent « android »), dans la langue de l'app. */
    private fun localized(packageName: String): Resources? = runCatching {
        val base = pm.getResourcesForApplication(packageName)
        val config = Configuration(base.configuration).apply { setLocales(context.resources.configuration.locales) }
        @Suppress("DEPRECATION")
        Resources(base.assets, base.displayMetrics, config)
    }.getOrNull()

    private fun protectionOf(info: PermissionInfo): Protection {
        if (info.name in NORMAL_FOR_VYLIA) return Protection.NORMAL
        if (info.name in SPECIAL_FOR_VYLIA) return Protection.SPECIAL
        val (base, flags) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.protection to info.protectionFlags
        } else {
            @Suppress("DEPRECATION")
            (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) to (info.protectionLevel and PermissionInfo.PROTECTION_MASK_FLAGS)
        }
        return when {
            base == PermissionInfo.PROTECTION_DANGEROUS -> Protection.DANGEROUS
            flags and PermissionInfo.PROTECTION_FLAG_APPOP != 0 -> Protection.SPECIAL
            base == PermissionInfo.PROTECTION_NORMAL -> Protection.NORMAL
            else -> Protection.SYSTEM
        }
    }

    companion object {
        /**
         * « dangerous » pour Android, qui les demande dans une fenêtre, mais sans accès à aucune donnée : présentées
         * comme normales, ni sensibles ni comptées (voir neutral_permissions dans la grille).
         */
        val NORMAL_FOR_VYLIA = setOf(
            "android.permission.POST_NOTIFICATIONS",
            // Marquée « appop » pour la politique de Google Play : rien à accorder par l'utilisateur.
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        )

        /**
         * « normal » pour Android, mais l'effet dépend d'un choix de l'utilisateur dans un écran dédié : présentées
         * comme un accès spécial, avec leur état réel (lu par AppScanner).
         */
        val SPECIAL_FOR_VYLIA = setOf("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
    }
}
