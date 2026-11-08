package com.vylia.privacy.domain.databases

import kotlinx.serialization.Serializable
import java.io.File

/** Sous-sections de la carte « Bases de données ». */
enum class DatabaseGroup { SCORE, SHIELD, ALTERNATIVES, ENRICHMENT }

/**
 * Une base que VYLIA sait télécharger depuis sa source officielle. L'implémentation valide le contenu reçu et le met en
 * service de façon atomique ; en cas d'échec, l'ancienne version reste en service.
 */
interface DatabaseSource {
    val id: String
    val group: DatabaseGroup
    /** Nom propre de la base (« EasyPrivacy », « Exodus Privacy »), affiché tel quel. */
    val name: String
    /** Qui la publie et sous quelle licence (« AdGuard · GPL v3 »), affiché sous le nom. */
    val attribution: String
    /** Adresse interrogée par un simple contrôle (requête HEAD conditionnelle), sans téléchargement. */
    val checkUrl: String
    /** La mise à jour change-t-elle les scores ? Sert à proposer un nouveau scan et à reconnaître les baisses dues aux bases. */
    val affectsScore: Boolean
    /**
     * Base proposée : elle n'est téléchargée que si l'utilisateur l'ajoute. [downloadBytes] : taille du téléchargement
     * (compressé), [rawBytes] : taille une fois décompressée, affichées avant de l'ajouter.
     */
    val proposed: Boolean get() = false
    val downloadBytes: Long get() = 0
    val rawBytes: Long get() = 0

    /** Base active : toutes, sauf une base proposée que l'utilisateur n'a pas ajoutée. */
    fun isEnabled(): Boolean = true

    /** Nombre d'entrées de la version en service (traqueurs, domaines, règles…), `null` s'il n'a pas de sens. */
    fun entries(): Int?

    /** Télécharge avec [fetch] et met en service ; [Outcome.Unchanged] si la source n'a pas changé. */
    suspend fun update(fetch: Fetch): Outcome
}

sealed interface Outcome {
    data object Unchanged : Outcome
    data class Installed(val entries: Int?) : Outcome
}

/** Téléchargement pour une source, avec les en-têtes de la version en service. */
interface Fetch {
    /**
     * Le fichier téléchargé (décompressé, au plus [maxBytes] octets), ou `null` si [conditional] et que la source
     * répond qu'il n'a pas changé (304). Le fichier est temporaire : à lire avant de rendre la main.
     */
    suspend fun get(url: String, maxBytes: Long, conditional: Boolean = true): File?
}

/** En-têtes qui permettent à la source de répondre « inchangé » (`ETag`, `Last-Modified`). */
@Serializable
data class Validators(val etag: String? = null, val lastModified: String? = null) {
    val isEmpty: Boolean get() = etag == null && lastModified == null
}

/** Accès réseau, réduit à ce dont les bases ont besoin (remplacé par un faux dans les tests). */
interface Http {
    sealed interface Response {
        data object NotModified : Response
        data class Ok(val validators: Validators, val transferred: Long) : Response
    }

    /** GET : le corps est écrit dans [dest]. HEAD si [dest] est `null`. Lève une exception en cas d'échec. */
    suspend fun request(url: String, validators: Validators?, maxBytes: Long, dest: File?): Response
}

/** Ce qui est mémorisé pour chaque base, d'une session à l'autre. */
@Serializable
data class DatabaseMeta(
    /** En-têtes par adresse téléchargée (une base peut en avoir plusieurs, comme MVT). */
    val validators: Map<String, Validators> = emptyMap(),
    /** Date de la dernière mise en service d'une version téléchargée. */
    val updatedAt: Long? = null,
    /** Date du dernier contrôle réussi (mise à jour ou constat « inchangé »). */
    val checkedAt: Long? = null,
    val entries: Int? = null,
    /** Version en service : change à chaque nouvelle version installée. `null` : version embarquée. */
    val version: String? = null,
    val lastError: String? = null,
    val consecutiveFailures: Int = 0,
    /** Un contrôle a constaté une version plus récente, pas encore téléchargée. */
    val updateAvailable: Boolean = false,
    /** Taille du dernier téléchargement, compressé. */
    val transferred: Long? = null,
)

interface DatabaseMetaStore {
    fun load(): Map<String, DatabaseMeta>
    fun save(meta: Map<String, DatabaseMeta>)
}

/** État d'une base pendant et après une mise à jour. */
enum class DatabaseStatus { UP_TO_DATE, UPDATING, FAILED, UPDATE_AVAILABLE, EMBEDDED, NOT_ADDED }

/** Bilan d'une mise à jour groupée, traduit par l'écran. */
data class UpdateSummary(
    val updated: List<String>,
    val unchanged: List<String>,
    /** id → cause de l'échec. */
    val failed: Map<String, String>,
    /** Une base qui touche au score a changé : un nouveau scan est à proposer. */
    val scoreChanged: Boolean = false,
)
