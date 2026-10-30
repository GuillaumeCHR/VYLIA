package com.vylia.privacy.domain.scoring

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Règles (désérialisées depuis vylia_rules_v2.json, embarqué dans assets/)
// ---------------------------------------------------------------------------

@Serializable
data class Rules(
    val version: String,
    val scoring: Scoring,
    @SerialName("permission_weights") val permissionWeights: Map<String, Int>,
    @SerialName("permission_rules") val permissionRules: PermissionRules,
    @SerialName("tracker_rules") val trackerRules: TrackerRules,
    @SerialName("category_resolution") val categoryResolution: CategoryResolution,
    val categories: Map<String, Category>,
    @SerialName("excluded_packages") val excludedPackages: Set<String> = emptySet(),
    @SerialName("package_exceptions") val packageExceptions: Map<String, PackageException> = emptyMap(),
    @SerialName("package_overrides") val packageOverrides: Map<String, String> = emptyMap(),
)

@Serializable
data class Scoring(
    val base: Int,
    @SerialName("permission_cap") val permissionCap: Int,
    @SerialName("tracker_cap") val trackerCap: Int,
    @SerialName("context_multipliers") val contextMultipliers: Map<String, Double>,
    @SerialName("declared_only_factor") val declaredOnlyFactor: Double,
    @SerialName("unanalyzed_policy") val unanalyzedPolicy: UnanalyzedPolicy,
    val grades: List<Grade>,
)

@Serializable
data class UnanalyzedPolicy(
    @SerialName("assumed_tracker_penalty") val assumedTrackerPenalty: Int,
    val confidence: String,
    @SerialName("grade_suffix") val gradeSuffix: String,
)

@Serializable data class Grade(val min: Int, val grade: String)

@Serializable
data class PermissionRules(
    @SerialName("ignore_unlisted") val ignoreUnlisted: Boolean = true,
    @SerialName("read_external_storage_max_target_sdk") val readExternalStorageMaxTargetSdk: Int = 32,
    val groups: List<PermissionGroup> = emptyList(),
)

@Serializable data class PermissionGroup(val id: String, val mode: String, val members: List<String>)

@Serializable
data class TrackerRules(
    @SerialName("category_weights") val categoryWeights: Map<String, Int>,
    @SerialName("default_weight") val defaultWeight: Int,
    val overrides: Map<String, Int> = emptyMap(),
)

@Serializable
data class CategoryResolution(
    @SerialName("app_info_category_map") val appInfoCategoryMap: Map<String, String>,
)

@Serializable
data class Category(
    @SerialName("display_name") val displayName: String,
    val description: String? = null,
    val allowed: Set<String> = emptySet(),
    val tolerated: Set<String> = emptySet(),
    val forbidden: Set<String> = emptySet(),
)

@Serializable
data class PackageException(@SerialName("allowed_extra") val allowedExtra: Set<String> = emptySet())

// ---------------------------------------------------------------------------
// Entrées / sorties
// ---------------------------------------------------------------------------

/** Noms courts sans préfixe "android.permission.". */
data class AppScan(
    val packageName: String,
    val targetSdk: Int,
    /** Clé de ApplicationInfo.category ("CATEGORY_GAME", ...), ou "CATEGORY_UNDEFINED". */
    val appInfoCategory: String,
    val declaredPermissions: Set<String>,
    /**
     * Permissions effectivement accordées :
     * - runtime : PackageInfo.requestedPermissionsFlags & REQUESTED_PERMISSION_GRANTED
     * - spéciales : AppOpsManager (SYSTEM_ALERT_WINDOW, PACKAGE_USAGE_STATS),
     *   Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, NotificationManagerCompat.getEnabledListenerPackages,
     *   DevicePolicyManager.getActiveAdmins
     * - install-time (QUERY_ALL_PACKAGES, REQUEST_INSTALL_PACKAGES) : déclarée = accordée
     */
    val grantedPermissions: Set<String>,
    /** null = traqueurs non analysés. Liste vide = analysée, aucun traqueur. */
    val trackers: List<Tracker>?,
)

data class Tracker(val name: String, val exodusCategories: Set<String>)

enum class Context { ALLOWED, TOLERATED, UNEXPECTED, FORBIDDEN }
enum class Confidence { FULL, PARTIAL }

data class PermissionFinding(
    val permission: String,
    val granted: Boolean,
    val context: Context,
    val penalty: Double,
)

data class TrackerFinding(val name: String, val penalty: Int)

data class ScoreResult(
    val score: Int,
    val grade: String,
    val confidence: Confidence,
    val categoryId: String,
    val permissionPenalty: Double,
    val trackerPenalty: Int,
    val permissionFindings: List<PermissionFinding>,
    val trackerFindings: List<TrackerFinding>,
)

// ---------------------------------------------------------------------------
// Moteur
// ---------------------------------------------------------------------------

class PrivacyScorer(private val rules: Rules) {

    fun isExcluded(pkg: String) = pkg in rules.excludedPackages

    fun categoryDisplayName(categoryId: String): String = rules.categories[categoryId]?.displayName ?: categoryId

    fun resolveCategory(scan: AppScan): String =
        rules.packageOverrides[scan.packageName]
            ?: rules.categoryResolution.appInfoCategoryMap[scan.appInfoCategory]
            ?: "DEFAULT"

    fun score(scan: AppScan): ScoreResult {
        val categoryId = resolveCategory(scan)
        val category = rules.categories[categoryId] ?: rules.categories.getValue("DEFAULT")
        val extraAllowed = rules.packageExceptions[scan.packageName]?.allowedExtra.orEmpty()

        // 1. Permissions : poids intrinsèque × contexte × facteur d'octroi
        val findings = scan.declaredPermissions
            .filter { it in rules.permissionWeights }
            .filterNot {
                it == "READ_EXTERNAL_STORAGE" &&
                    scan.targetSdk > rules.permissionRules.readExternalStorageMaxTargetSdk
            }
            .map { perm ->
                val ctx = when (perm) {
                    in category.allowed, in extraAllowed -> Context.ALLOWED
                    in category.tolerated -> Context.TOLERATED
                    in category.forbidden -> Context.FORBIDDEN
                    else -> Context.UNEXPECTED
                }
                val granted = perm in scan.grantedPermissions
                val grantFactor = if (granted) 1.0 else rules.scoring.declaredOnlyFactor
                val mult = rules.scoring.contextMultipliers.getValue(ctx.name.lowercase())
                PermissionFinding(perm, granted, ctx, rules.permissionWeights.getValue(perm) * mult * grantFactor)
            }

        val permissionPenalty = min(
            rules.scoring.permissionCap.toDouble(),
            aggregateWithGroups(findings),
        )

        // 2. Traqueurs : poids = max des catégories Exodus du traqueur
        val trackerFindings = scan.trackers?.map { t ->
            val w = rules.trackerRules.overrides[t.name]
                ?: t.exodusCategories.mapNotNull { rules.trackerRules.categoryWeights[it] }.maxOrNull()
                ?: rules.trackerRules.defaultWeight
            TrackerFinding(t.name, w)
        }.orEmpty()

        val policy = rules.scoring.unanalyzedPolicy
        val confidence = if (scan.trackers == null) Confidence.PARTIAL else Confidence.FULL
        val trackerPenalty = if (scan.trackers == null) {
            policy.assumedTrackerPenalty
        } else {
            min(rules.scoring.trackerCap, trackerFindings.sumOf { it.penalty })
        }

        // 3. Score final
        val score = max(0, (rules.scoring.base - permissionPenalty - trackerPenalty).roundToInt())
        val letter = rules.scoring.grades.sortedByDescending { it.min }.first { score >= it.min }.grade
        val grade = if (confidence == Confidence.PARTIAL) letter + policy.gradeSuffix else letter

        return ScoreResult(
            score = score,
            grade = grade,
            confidence = confidence,
            categoryId = categoryId,
            permissionPenalty = permissionPenalty,
            trackerPenalty = trackerPenalty,
            permissionFindings = findings.sortedByDescending { it.penalty },
            trackerFindings = trackerFindings.sortedByDescending { it.penalty },
        )
    }

    /** Dans un groupe, la localisation fine+approx. ne compte qu'une fois ; les SMS cumulent à moitié. */
    private fun aggregateWithGroups(findings: List<PermissionFinding>): Double {
        val byPerm = findings.associate { it.permission to it.penalty }
        val grouped = mutableSetOf<String>()
        var total = 0.0
        for (group in rules.permissionRules.groups) {
            val values = group.members.mapNotNull { byPerm[it] }.sortedDescending()
            grouped += group.members
            if (values.isEmpty()) continue
            total += when (group.mode) {
                "max" -> values.first()
                "max_plus_half" -> values.first() + 0.5 * values.drop(1).sum()
                else -> values.sum()
            }
        }
        total += byPerm.filterKeys { it !in grouped }.values.sum()
        return total
    }
}
