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

/**
 * Requête bloquée récemment : gardée en mémoire seulement, jamais écrite sur le disque. [spyware] : serveur de logiciel
 * espion, pour lequel aucune exception n'est proposée. [count] : blocages de ce domaine regroupés sur cette ligne.
 */
data class BlockedRequest(
    val domain: String,
    val trackerName: String?,
    val at: Long,
    val spyware: Boolean = false,
    val count: Int = 1,
)

data class ShieldState(
    val status: ShieldStatus = ShieldStatus.OFF,
    /** Requêtes bloquées depuis l'activation en cours. */
    val blockedSession: Long = 0,
    /** Requêtes bloquées depuis toujours (effacé par « Supprimer mes données »). */
    val blockedTotal: Long = 0,
    /** Requêtes bloquées le jour [blockedDay] (jour local, `LocalDate.toEpochDay`), pour le widget. */
    val blockedToday: Long = 0,
    val blockedDay: Long = -1,
    /** Un domaine par ligne, du plus récemment bloqué au plus ancien, au plus [MAX_RECENT]. */
    val recent: List<BlockedRequest> = emptyList(),
    /** Pause en cours : rien n'est bloqué jusqu'à cet instant, puis le bouclier reprend seul. */
    val pausedUntil: Long? = null,
    /** Filtrages actifs (au moins un). */
    val filters: Set<ShieldFilter> = ShieldFilter.entries.toSet(),
    /** Durée de la prochaine pause, en minutes : l'une des [PAUSE_STEPS]. */
    val pauseMinutes: Int = PAUSE_STEPS.first(),
    /** Domaines que l'utilisateur ne veut plus bloquer. */
    val exceptions: ShieldExceptions = ShieldExceptions.EMPTY,
) {
    fun isPaused(now: Long): Boolean = pausedUntil != null && now < pausedUntil

    /** Requêtes bloquées le jour [day] : zéro si le dernier blocage date d'un autre jour. */
    fun blockedOn(day: Long): Long = if (day == blockedDay) blockedToday else 0

    /**
     * [day] : jour local de la requête ; le compteur du jour repart de zéro à minuit. Un domaine déjà dans [recent] n'y
     * est pas répété : sa ligne remonte en tête avec un blocage de plus.
     */
    fun onBlocked(request: BlockedRequest, day: Long): ShieldState {
        val previous = recent.firstOrNull { it.domain == request.domain }
        val line = if (previous == null) request else request.copy(count = previous.count + 1)
        return copy(
            blockedSession = blockedSession + 1,
            blockedTotal = blockedTotal + 1,
            blockedToday = blockedOn(day) + 1,
            blockedDay = day,
            recent = (listOf(line) + recent.filterNot { it.domain == request.domain }).take(MAX_RECENT),
        )
    }

    /**
     * Les lignes de [recent] des domaines [order], dans cet ordre : l'écran fige l'ordre des lignes qu'il montre, pour
     * qu'un nouveau blocage ne décale pas un bouton sous le doigt. Une ligne sortie de [recent] (exception) disparaît.
     */
    fun recentIn(order: List<String>): List<BlockedRequest> {
        val byDomain = recent.associateBy { it.domain }
        return order.mapNotNull(byDomain::get)
    }

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
