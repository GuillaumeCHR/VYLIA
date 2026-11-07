package com.vylia.privacy.data.web

import android.content.Context
import android.content.res.Resources
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.telephony.TelephonyManager
import com.vylia.privacy.domain.web.SiteChooser

/**
 * Le site de chaque app, d'après les domaines web qu'elle déclare à Android (ses liens d'app) : lu sur l'appareil,
 * sans réseau. Android 12 et plus seulement (DomainVerificationManager) ; avant, aucune app n'a de site trouvé ici.
 */
class AppSites(private val context: Context) {

    // Le pays des réglages d'Android : la langue choisie pour VYLIA peut n'en porter aucun (« fr »).
    private val country: String = Resources.getSystem().configuration.locales[0].country.ifEmpty {
        context.getSystemService(TelephonyManager::class.java)?.networkCountryIso.orEmpty()
    }

    /** Adresse du site de l'app, ou `null` (pas de domaine déclaré, ou aucun qui convienne, ou Android trop ancien). */
    fun siteOf(packageName: String, label: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return null
        val state = try {
            manager.getDomainVerificationUserState(packageName)
        } catch (_: Exception) {
            null
        } ?: return null
        val hosts = state.hostToStateMap.map { (host, s) -> SiteChooser.Host(host, s == DomainVerificationUserState.DOMAIN_STATE_VERIFIED) }
        return SiteChooser.choose(packageName, label, hosts, country)
    }
}
