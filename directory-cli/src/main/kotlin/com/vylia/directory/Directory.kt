package com.vylia.directory

import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.scoring.Rules
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.math.roundToLong

/** `directory.json` : base dérivée des données Exodus, publiée sous ODbL 1.0. */
@Serializable
data class Directory(
    val format: Int = 1,
    val license: String = LICENSE,
    val attribution: String = ATTRIBUTION,
    val generated: String,
    @SerialName("rules_version") val rulesVersion: String,
    val apps: List<DirectoryApp>,
) {
    companion object {
        const val LICENSE = "ODbL-1.0"
        const val ATTRIBUTION = "Contient des informations d'Exodus Privacy (https://exodus-privacy.eu.org), disponibles sous Open Database License (ODbL 1.0)."
    }
}

@Serializable
data class DirectoryApp(
    @SerialName("package") val packageName: String,
    val name: String,
    val category: String,
    val countries: List<String>,
    /** SCORED, ou NOT_ANALYZED (aucun rapport Exodus). */
    val status: String,
    val score: Int? = null,
    val grade: String? = null,
    val penalties: Penalties? = null,
    val permissions: List<PermissionLine> = emptyList(),
    val trackers: List<TrackerLine> = emptyList(),
    val badges: List<BadgeLine> = emptyList(),
    /** L'API ne donne pas le SDK cible : toujours faux pour l'instant, la fiche le signale. */
    @SerialName("target_sdk_known") val targetSdkKnown: Boolean = false,
    val exodus: ExodusRef? = null,
)

@Serializable
data class Penalties(val permissions: Double, val trackers: Int)

@Serializable
data class PermissionLine(val name: String, val context: String, val penalty: Double, val counted: Double)

@Serializable
data class TrackerLine(val id: Int, val name: String, val categories: List<String>, val penalty: Int)

@Serializable
data class BadgeLine(val id: String, val label: String, val icon: String, val polarity: String)

@Serializable
data class ExodusRef(
    val report: Long,
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: String,
    val updated: String,
    val url: String,
)

/** Calcul des notes et écriture déterministe : mêmes entrées, mêmes octets. */
object DirectoryBuilder {

    val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }

    fun build(
        apps: List<AppEntry>,
        reports: Map<String, ExodusReport?>,
        trackers: Map<Int, ExodusTracker>,
        rules: Rules,
        rulesText: String,
        date: String,
    ): Directory {
        val scorer = PrivacyScorer(rules)
        val byName = trackers.values.associateBy { it.name }
        val entries = apps.sortedBy { it.packageName }.map { app ->
            val report = reports[app.packageName]
                ?: return@map DirectoryApp(app.packageName, app.name, app.category, app.countries.sorted(), status = "NOT_ANALYZED")
            val result = scorer.score(ReportConverter.toScan(app.packageName, report, trackers))
            DirectoryApp(
                packageName = app.packageName,
                name = app.name,
                category = app.category,
                countries = app.countries.sorted(),
                status = "SCORED",
                score = result.score,
                grade = result.grade,
                penalties = Penalties(round1(result.permissionPenalty), result.trackerPenalty),
                permissions = result.permissionFindings
                    .map { PermissionLine(it.permission, it.context.name, round1(it.penalty), round1(it.counted)) }
                    .sortedWith(compareByDescending<PermissionLine> { it.counted }.thenBy { it.name }),
                trackers = result.trackerFindings
                    .map { f -> TrackerLine(byName[f.name]?.id ?: 0, f.name, byName[f.name]?.categories?.sorted().orEmpty(), f.penalty) }
                    .sortedWith(compareByDescending<TrackerLine> { it.penalty }.thenBy { it.name }),
                badges = result.badges.map { BadgeLine(it.id, it.label, it.icon, it.polarity) },
                exodus = ExodusRef(
                    report.report, report.versionName, report.versionCode, report.updated,
                    "https://reports.exodus-privacy.eu.org/fr/reports/${report.report}/",
                ),
            )
        }
        return Directory(generated = date, rulesVersion = rulesVersion(rules, rulesText), apps = entries)
    }

    fun encode(directory: Directory): String = json.encodeToString(Directory.serializer(), directory) + "\n"

    fun decode(text: String): Directory = Json { ignoreUnknownKeys = true }.decodeFromString(Directory.serializer(), text)

    /** Version de la grille et empreinte du fichier : deux grilles différentes ne se confondent pas. */
    private fun rulesVersion(rules: Rules, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(4).joinToString("") { "%02x".format(it) }
        return "${rules.version} · $digest"
    }

    /** Arrondi au dixième, indépendant de la langue du système. */
    fun round1(value: Double): Double = (value * 10).roundToLong() / 10.0
}

/**
 * Garde-fou de publication : si plus de [MAX_LOST] des apps notées la semaine précédente n'ont plus de note (panne de
 * l'API, jeton expiré…), on ne publie pas, et l'ancienne version reste en ligne.
 */
object PublishGuard {
    const val MAX_LOST = 0.10

    data class Verdict(val ok: Boolean, val previouslyScored: Int, val lost: Int)

    fun check(previous: Directory?, current: Directory): Verdict {
        if (previous == null) return Verdict(true, 0, 0)
        val scoredBefore = previous.apps.filter { it.status == "SCORED" }.map { it.packageName }.toSet()
        if (scoredBefore.isEmpty()) return Verdict(true, 0, 0)
        val scoredNow = current.apps.filter { it.status == "SCORED" }.map { it.packageName }.toSet()
        val lost = (scoredBefore - scoredNow).size
        return Verdict(lost.toDouble() / scoredBefore.size <= MAX_LOST, scoredBefore.size, lost)
    }
}
