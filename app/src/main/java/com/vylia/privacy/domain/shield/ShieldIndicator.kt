package com.vylia.privacy.domain.shield

/** Les quatre états du contrôle du bouclier, identiques sur tous les écrans et dans la tuile des réglages rapides. */
enum class ShieldIndicatorState { ACTIVE, INACTIVE, STARTING, BYPASSED }

/** Pourquoi le bouclier est contourné. */
enum class ShieldBypass {
    /** Un DNS privé est réglé sur un service (nom d'hôte) : Android l'interroge directement, sans passer par le tunnel. */
    PRIVATE_DNS,

    /** Un autre VPN a pris la place du bouclier (Android n'en garde qu'un actif à la fois). */
    OTHER_VPN,
}

/** Effet d'un appui sur le contrôle du bouclier. */
enum class ShieldTapEffect {
    DEACTIVATE,

    /** Active, après l'accord VPN d'Android s'il n'est pas encore donné. */
    ACTIVATE,

    /** Affiche le motif du contournement et le geste qui y répond. */
    SHOW_BYPASS,
    NONE,
}

/**
 * État affiché du bouclier. Toujours dérivé de l'état réel du service ([ShieldStatus]) et de ce qu'Android applique
 * au réseau, jamais d'une préférence enregistrée.
 */
data class ShieldIndicator(val state: ShieldIndicatorState, val bypass: ShieldBypass? = null) {

    val tapEffect: ShieldTapEffect
        get() = when (state) {
            ShieldIndicatorState.ACTIVE -> ShieldTapEffect.DEACTIVATE
            ShieldIndicatorState.INACTIVE -> ShieldTapEffect.ACTIVATE
            ShieldIndicatorState.STARTING -> ShieldTapEffect.NONE
            ShieldIndicatorState.BYPASSED -> ShieldTapEffect.SHOW_BYPASS
        }

    companion object {
        val INACTIVE = ShieldIndicator(ShieldIndicatorState.INACTIVE)

        /**
         * @param privateDnsHostname un DNS privé est réglé sur un service par son nom d'hôte (le mode automatique
         *   retombe sur le DNS du tunnel et ne contourne pas le bouclier).
         * @param otherVpn un VPN d'une autre app est actif.
         *
         * Une pause en cours laisse l'état à [ShieldIndicatorState.ACTIVE] : le service tourne et la reprise est
         * automatique. Un échec de démarrage ([ShieldStatus.FAILED]) s'affiche comme inactif.
         */
        fun derive(status: ShieldStatus, privateDnsHostname: Boolean, otherVpn: Boolean): ShieldIndicator = when (status) {
            ShieldStatus.STARTING -> ShieldIndicator(ShieldIndicatorState.STARTING)
            ShieldStatus.ON ->
                if (privateDnsHostname) ShieldIndicator(ShieldIndicatorState.BYPASSED, ShieldBypass.PRIVATE_DNS)
                else ShieldIndicator(ShieldIndicatorState.ACTIVE)
            // Évincé par un autre VPN, toujours présent : le bouclier est contourné, pas simplement éteint.
            ShieldStatus.REVOKED ->
                if (otherVpn) ShieldIndicator(ShieldIndicatorState.BYPASSED, ShieldBypass.OTHER_VPN) else INACTIVE
            ShieldStatus.OFF, ShieldStatus.FAILED -> INACTIVE
        }
    }
}
