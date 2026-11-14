package com.vylia.privacy.data.automation

import android.util.Log
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.TrackerAnalysis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Dernier état connu d'une app, pour dire ce qu'une mise à jour a changé : sa version, son score, ses traqueurs et les
 * permissions déclarées qui lui coûtent des points. [databases] : versions des bases et des règles avec lesquelles elle
 * a été analysée ; [shield] : le bouclier filtrait-il (il change le score).
 */
@Serializable
data class AppBaseline(
    val version: Long,
    val score: Int,
    /** `null` : traqueurs pas encore analysés, rien à comparer. */
    val trackers: Set<String>?,
    val permissions: Set<String>,
    val databases: String,
    val shield: Boolean,
)

/**
 * Ce qu'une mise à jour change, par rapport à la dernière analyse de l'app. Les traqueurs ne sont comparés qu'avec les
 * mêmes bases (un traqueur apparu avec une mise à jour des signatures n'est pas imputé à l'app), le score qu'avec les
 * mêmes bases et le même état du bouclier : sinon ils valent vide et `null`.
 */
data class UpdateChange(
    val trackersAdded: Set<String> = emptySet(),
    val trackersRemoved: Set<String> = emptySet(),
    val permissionsAdded: Set<String> = emptySet(),
    val permissionsRemoved: Set<String> = emptySet(),
    val scoreBefore: Int? = null,
    val scoreAfter: Int,
) {
    val isEmpty: Boolean get() = trackersAdded.isEmpty() && trackersRemoved.isEmpty() && permissionsAdded.isEmpty() && permissionsRemoved.isEmpty()

    companion object {
        /** `null` : pas de nouvelle version, ou rien de ce que VYLIA note n'a changé. */
        fun between(before: AppBaseline, after: AppBaseline): UpdateChange? {
            if (after.version == before.version) return null
            val sameBases = before.databases == after.databases
            val compareTrackers = sameBases && before.trackers != null && after.trackers != null
            val change = UpdateChange(
                trackersAdded = if (compareTrackers) after.trackers!! - before.trackers!! else emptySet(),
                trackersRemoved = if (compareTrackers) before.trackers!! - after.trackers!! else emptySet(),
                permissionsAdded = after.permissions - before.permissions,
                permissionsRemoved = before.permissions - after.permissions,
                scoreBefore = before.score.takeIf { sameBases && before.shield == after.shield },
                scoreAfter = after.score,
            )
            return change.takeUnless { it.isEmpty }
        }
    }
}

/** Fichier des derniers états connus. */
interface BaselineStore {
    fun load(): Map<String, AppBaseline>
    fun save(baselines: Map<String, AppBaseline>)
}

/** Stocké en JSON dans les fichiers privés de l'app (exclus des sauvegardes Android) : il contient la liste de vos apps. */
class FileBaselineStore(private val file: File) : BaselineStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): Map<String, AppBaseline> = try {
        if (file.exists()) json.decodeFromString<Map<String, AppBaseline>>(file.readText()) else emptyMap()
    } catch (e: Exception) {
        Log.w(TAG, "États des apps illisibles, repart de zéro", e)
        emptyMap()
    }

    override fun save(baselines: Map<String, AppBaseline>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(baselines))
        if (!tmp.renameTo(file)) Log.w(TAG, "Écriture des états des apps impossible")
    }

    private companion object {
        const val TAG = "FileBaselineStore"
    }
}

/**
 * Derniers états connus des apps utilisateur : remplacés à chaque scan complet, complétés à chaque installation ou mise
 * à jour repérée. Une app sans état connu (jamais analysée) ne donne lieu à aucune alerte de mise à jour.
 */
class UpdateBaselines(
    private val store: BaselineStore,
    /** Versions des bases et empreinte des règles en vigueur. */
    private val databases: () -> Map<String, String>,
    private val shield: () -> Boolean,
) {
    private var cache: Map<String, AppBaseline>? = null

    /** Scan complet : l'état de toutes les apps, celles qui ne sont plus là disparaissent. */
    @Synchronized
    fun replaceAll(apps: List<AuditedApp>) = write(apps.mapNotNull { baseline(it) }.toMap())

    /** Installations et mises à jour : compare chaque app à son état connu, retient le nouvel état. */
    @Synchronized
    fun update(apps: List<AuditedApp>): List<Pair<AuditedApp, UpdateChange>> {
        val known = read()
        val changes = mutableListOf<Pair<AuditedApp, UpdateChange>>()
        val updated = known.toMutableMap()
        apps.forEach { app ->
            val (pkg, now) = baseline(app) ?: return@forEach
            known[pkg]?.let { UpdateChange.between(it, now) }?.let { changes += app to it }
            updated[pkg] = now
        }
        write(updated)
        return changes
    }

    @Synchronized
    fun clear() = write(emptyMap())

    private fun read(): Map<String, AppBaseline> = cache ?: store.load().also { cache = it }

    private fun write(baselines: Map<String, AppBaseline>) {
        cache = baselines
        store.save(baselines)
    }

    private fun baseline(app: AuditedApp): Pair<String, AppBaseline>? {
        val version = app.app.versionCode.takeIf { it > 0 } ?: return null
        return app.app.packageName to AppBaseline(
            version = version,
            score = app.score.value,
            trackers = (app.trackerAnalysis as? TrackerAnalysis.Analyzed)?.trackers?.mapTo(HashSet()) { it.name },
            permissions = app.result.permissionFindings.filter { it.penalty > 0 }.mapTo(HashSet()) { it.permission },
            databases = fingerprint(databases()),
            shield = shield(),
        )
    }

    private fun fingerprint(versions: Map<String, String>): String =
        versions.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }
}
