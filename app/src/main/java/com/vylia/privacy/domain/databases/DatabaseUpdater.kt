package com.vylia.privacy.domain.databases

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest

/**
 * Mise à jour des bases, une par une ou toutes ensemble, sans Android.
 *
 * - Trois téléchargements à la fois au plus ([CONCURRENCY]) : les bases viennent de quelques hébergeurs (Exodus,
 *   easylist.to, GitHub), aucun n'est sollicité par plus d'un ou deux fichiers à la fois, et le pic de mémoire et de
 *   radio reste modeste (la plus grosse liste fait 10 Mo décompressée).
 * - Requêtes conditionnelles (`If-None-Match`, `If-Modified-Since`) : une base inchangée n'est pas retéléchargée.
 * - Un échec n'interrompt pas les autres bases ; la base en échec garde sa version en service.
 * - Une seule mise à jour à la fois.
 */
class DatabaseUpdater(
    val sources: () -> List<DatabaseSource>,
    private val http: Http,
    private val metaStore: DatabaseMetaStore,
    private val tempDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Progression d'une mise à jour en cours ; `null` hors mise à jour. */
    data class Progress(val done: Int, val total: Int, val running: Set<String>)

    private val _meta = MutableStateFlow(metaStore.load())
    val meta: StateFlow<Map<String, DatabaseMeta>> = _meta.asStateFlow()

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val mutex = Mutex()
    private val metaLock = Any()

    /** Met à jour toutes les bases actives. */
    suspend fun updateAll(): UpdateSummary = update(sources().filter { it.isEnabled() })

    /** Met à jour une seule base (bouton de sa ligne). */
    suspend fun updateOne(id: String): UpdateSummary = update(sources().filter { it.id == id && it.isEnabled() })

    suspend fun update(selected: List<DatabaseSource>): UpdateSummary = mutex.withLock {
        val updated = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        val failed = LinkedHashMap<String, String>()
        _progress.value = Progress(0, selected.size, emptySet())
        try {
            val semaphore = Semaphore(CONCURRENCY)
            coroutineScope {
                selected.map { source ->
                    async {
                        semaphore.withPermit {
                            _progress.update { it?.copy(running = it.running + source.id) }
                            val result = runOne(source)
                            synchronized(updated) {
                                when (result) {
                                    is OneResult.Updated -> updated += source.id
                                    OneResult.Unchanged -> unchanged += source.id
                                    is OneResult.Failed -> failed[source.id] = result.reason
                                }
                            }
                            _progress.update { it?.copy(done = it.done + 1, running = it.running - source.id) }
                        }
                    }
                }.awaitAll()
            }
        } finally {
            _progress.value = null
        }
        UpdateSummary(updated, unchanged, failed, scoreChanged = selected.any { it.affectsScore && it.id in updated })
    }

    private sealed interface OneResult {
        data object Updated : OneResult
        data object Unchanged : OneResult
        data class Failed(val reason: String) : OneResult
    }

    private suspend fun runOne(source: DatabaseSource): OneResult {
        val previous = _meta.value[source.id] ?: DatabaseMeta()
        val fetch = RecordingFetch(previous.validators)
        return try {
            val outcome = source.update(fetch)
            val now = clock()
            editMeta(source.id) { m ->
                val validators = m.validators + fetch.recorded
                when (outcome) {
                    Outcome.Unchanged -> m.copy(
                        validators = validators, checkedAt = now, lastError = null, consecutiveFailures = 0, updateAvailable = false,
                        entries = source.entries() ?: m.entries,
                    )
                    is Outcome.Installed -> m.copy(
                        validators = validators, updatedAt = now, checkedAt = now, entries = outcome.entries,
                        version = versionOf(validators, now), lastError = null, consecutiveFailures = 0, updateAvailable = false,
                        transferred = fetch.transferred,
                    )
                }
            }
            if (outcome is Outcome.Installed) OneResult.Updated else OneResult.Unchanged
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = e.message?.take(120) ?: e.javaClass.simpleName
            editMeta(source.id) { it.copy(lastError = reason, consecutiveFailures = it.consecutiveFailures + 1) }
            OneResult.Failed(reason)
        } finally {
            fetch.cleanup()
        }
    }

    /**
     * Contrôle sans téléchargement (réseau facturé, option « Uniquement en Wi-Fi ») : une requête HEAD conditionnelle
     * par base, pour pouvoir annoncer « mise à jour disponible ». Les échecs sont ignorés : ce n'est qu'un indice.
     */
    suspend fun checkAll() = mutex.withLock {
        sources().filter { it.isEnabled() }.forEach { source ->
            val previous = _meta.value[source.id]
            val validators = previous?.validators?.get(source.checkUrl)
            val available = try {
                http.request(source.checkUrl, validators, 0, dest = null) is Http.Response.Ok
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@forEach
            }
            editMeta(source.id) { it.copy(updateAvailable = available) }
        }
    }

    /** Base retirée par l'utilisateur (base proposée) : son état est oublié. */
    fun forget(id: String) = editMeta(id) { DatabaseMeta() }

    private fun editMeta(id: String, change: (DatabaseMeta) -> DatabaseMeta) {
        synchronized(metaLock) {
            val next = _meta.value + (id to change(_meta.value[id] ?: DatabaseMeta()))
            _meta.value = next
            metaStore.save(next)
        }
    }

    /** Version : empreinte des en-têtes de la source s'il y en a, sinon la date d'installation. */
    private fun versionOf(validators: Map<String, Validators>, now: Long): String {
        if (validators.isEmpty() || validators.values.all { it.isEmpty }) return now.toString()
        val text = validators.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value.etag} ${it.value.lastModified}" }
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
    }

    /** [Fetch] qui retient les nouveaux en-têtes, enregistrés seulement si la base est bien mise en service. */
    private inner class RecordingFetch(private val known: Map<String, Validators>) : Fetch {
        val recorded = HashMap<String, Validators>()
        var transferred = 0L
        private val files = mutableListOf<File>()

        override suspend fun get(url: String, maxBytes: Long, conditional: Boolean): File? {
            val file = File.createTempFile("vylia-db", ".tmp", tempDir).also { synchronized(files) { files += it } }
            return when (val response = http.request(url, known[url]?.takeIf { conditional }, maxBytes, file)) {
                Http.Response.NotModified -> null
                is Http.Response.Ok -> {
                    recorded[url] = response.validators
                    transferred += response.transferred
                    file
                }
            }
        }

        fun cleanup() = synchronized(files) { files.forEach(File::delete) }
    }

    companion object {
        const val CONCURRENCY = 3
    }
}
