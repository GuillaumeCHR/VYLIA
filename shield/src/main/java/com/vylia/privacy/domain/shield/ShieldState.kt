package com.vylia.privacy.domain.shield

/** État du bouclier, tel que l'affiche l'onglet Réduire › Traçage pub. */
enum class ShieldStatus {
    OFF,
    STARTING,
    ON,

    /** Un autre VPN a pris la place du bouclier (Android n'en garde qu'un actif à la fois). */
    REVOKED,

    /** Démarrage impossible (autorisation VPN retirée, interface refusée par Android). */
    FAILED,
}

/** Requête bloquée récemment : gardée en mémoire seulement, jamais écrite sur le disque. */
data class BlockedRequest(val domain: String, val trackerName: String?, val at: Long)

data class ShieldState(
    val status: ShieldStatus = ShieldStatus.OFF,
    /** Requêtes bloquées depuis l'activation en cours. */
    val blockedSession: Long = 0,
    /** Requêtes bloquées depuis toujours (effacé par « Supprimer mes données »). */
    val blockedTotal: Long = 0,
    /** Du plus récent au plus ancien, au plus [MAX_RECENT]. */
    val recent: List<BlockedRequest> = emptyList(),
    /** Pause en cours : rien n'est bloqué jusqu'à cet instant, puis le bouclier reprend seul. */
    val pausedUntil: Long? = null,
    /** Filtrages actifs (au moins un). */
    val filters: Set<ShieldFilter> = ShieldFilter.entries.toSet(),
    /** Durée de la prochaine pause, en minutes : l'une des [PAUSE_STEPS]. */
    val pauseMinutes: Int = PAUSE_STEPS.first(),
) {
    fun isPaused(now: Long): Boolean = pausedUntil != null && now < pausedUntil

    fun onBlocked(request: BlockedRequest): ShieldState = copy(
        blockedSession = blockedSession + 1,
        blockedTotal = blockedTotal + 1,
        recent = (listOf(request) + recent).take(MAX_RECENT),
    )

    companion object {
        const val MAX_RECENT = 20

        /**
         * Durées de pause proposées par « − » et « + » : de quoi ouvrir un lien ou faire créditer une offre (5 min)
         * jusqu'à une longue session de jeu (2 h).
         */
        val PAUSE_STEPS = listOf(5, 10, 15, 30, 45, 60, 90, 120)

        fun shorterPause(minutes: Int): Int = PAUSE_STEPS.lastOrNull { it < minutes } ?: PAUSE_STEPS.first()

        fun longerPause(minutes: Int): Int = PAUSE_STEPS.firstOrNull { it > minutes } ?: PAUSE_STEPS.last()
    }
}
