package com.vylia.directory

import com.vylia.privacy.domain.scoring.AppScan
import com.vylia.privacy.domain.scoring.Tracker
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Conversion d'un rapport Exodus en [AppScan], avec la convention de l'annuaire : hors de l'appareil, on ne sait pas ce
 * que l'utilisateur a accordé, donc **toutes les permissions déclarées sont considérées comme accordées** (note
 * d'exposition maximale). L'API ne donne pas le SDK cible : [UNKNOWN_TARGET_SDK] fait compter `READ_EXTERNAL_STORAGE`,
 * et la fiche le signale. Les motifs de sécurité demandent l'APK : ils ne sont pas évalués (`signals` vide).
 */
object ReportConverter {

    /** Faute de SDK cible connu : une valeur inférieure à 33, pour que `READ_EXTERNAL_STORAGE` compte. */
    const val UNKNOWN_TARGET_SDK = 32

    /**
     * Le rapport le plus récent d'une réponse `/api/search/{handle}/details` (tableau de rapports), ou `null` si l'app
     * n'a jamais été analysée. Seuls les rapports de Google Play sont retenus.
     */
    fun latestReport(details: String): ExodusReport? {
        val array = lenientJson.parseToJsonElement(details) as? JsonArray ?: return null
        return array.mapNotNull { it as? JsonObject }
            .filter { it["source"]?.jsonPrimitive?.contentOrNull in setOf(null, "google") }
            .mapNotNull(::report)
            .maxWithOrNull(compareBy<ExodusReport>({ it.updated }, { it.report }))
    }

    private fun report(o: JsonObject): ExodusReport? {
        val id = o["report"]?.jsonPrimitive?.longOrNull ?: return null
        return ExodusReport(
            report = id,
            versionName = o["version_name"]?.jsonPrimitive?.contentOrNull.orEmpty().take(60),
            versionCode = o["version_code"]?.jsonPrimitive?.contentOrNull.orEmpty().take(30),
            updated = o["updated"]?.jsonPrimitive?.contentOrNull.orEmpty().take(10),
            trackerIds = o["trackers"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.sorted().orEmpty(),
            permissions = o["permissions"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.sorted().orEmpty(),
        )
    }

    /** Nom court, comme dans l'app ; une permission propre à une app garde son nom complet (et n'est pas notée). */
    fun shortName(permission: String): String = permission.removePrefix("android.permission.")

    fun toScan(packageName: String, report: ExodusReport, trackers: Map<Int, ExodusTracker>): AppScan {
        val permissions = report.permissions.map(::shortName).toSet()
        return AppScan(
            packageName = packageName,
            targetSdk = UNKNOWN_TARGET_SDK,
            // Pas de catégorie Android hors de l'appareil : la catégorie vient de package_overrides.
            appInfoCategory = "CATEGORY_UNDEFINED",
            declaredPermissions = permissions,
            grantedPermissions = permissions,
            trackers = report.trackerIds.mapNotNull(trackers::get).map { Tracker(it.name, it.categories.toSet()) },
            signals = emptySet(),
        )
    }

    /** `/api/trackers` → traqueurs, catégories manquantes complétées comme dans l'app. */
    fun trackers(apiTrackers: String, overrides: Map<String, List<String>>): Map<Int, ExodusTracker> {
        val root = lenientJson.parseToJsonElement(apiTrackers).jsonObject["trackers"]?.jsonObject ?: return emptyMap()
        return root.entries.mapNotNull { (key, value) ->
            val t = value as? JsonObject ?: return@mapNotNull null
            val id = t["id"]?.jsonPrimitive?.intOrNull ?: key.toIntOrNull() ?: return@mapNotNull null
            val name = t["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val categories = t["categories"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            ExodusTracker(id, name, categories.ifEmpty { overrides[name].orEmpty() })
        }.associateBy { it.id }
    }

    /** `exodus_category_overrides.json` : appliqué seulement s'il est validé, comme dans l'app. */
    fun overrides(text: String): Map<String, List<String>> {
        val root = lenientJson.parseToJsonElement(text).jsonObject
        if (root["validated"]?.jsonPrimitive?.contentOrNull != "true") return emptyMap()
        return root["categories"]?.jsonObject?.mapValues { (_, v) -> v.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.orEmpty()
    }
}
