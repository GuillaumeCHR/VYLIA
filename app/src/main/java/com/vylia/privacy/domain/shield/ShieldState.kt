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
) {
    fun onBlocked(request: BlockedRequest): ShieldState = copy(
        blockedSession = blockedSession + 1,
        blockedTotal = blockedTotal + 1,
        recent = (listOf(request) + recent).take(MAX_RECENT),
    )

    companion object {
        const val MAX_RECENT = 20
    }
}
