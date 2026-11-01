package com.vylia.privacy.domain.scoring

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Règles (désérialisées depuis vylia_rules_v2_1.json, embarqué dans assets/)
// À charger avec Json { ignoreUnknownKeys = true } : les clés purement
// documentaires du JSON (description, condition_syntax…) ne sont pas mappées.
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
    val security: SecurityRules = SecurityRules(),
    val badges: BadgeRules = BadgeRules(),
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

// --- v2.1 : sécurité -------------------------------------------------------

@Serializable
data class SecurityRules(
    /** Signaux connus (id → description). Sert aussi à valider les conditions. */
    val signals: Map<String, String> = emptyMap(),
    @SerialName("severity_effects") val severityEffects: Map<String, SeverityEffect> = emptyMap(),
    val patterns: Map<String, SecurityPattern> = emptyMap(),
)

@Serializable
data class SeverityEffect(@SerialName("score_cap") val scoreCap: Int? = null)

@Serializable
data class SecurityPattern(
    val label: String,
    val severity: String,
    val description: String? = null,
    /** Expression booléenne, voir SecurityPatternDetector. */
    @SerialName("when") val condition: String,
    @SerialName("exempt_packages") val exemptPackages: Set<String> = emptySet(),
    @SerialName("exempt_categories") val exemptCategories: Set<String> = emptySet(),
)

// --- v2.1 : badges ----------------------------------------------------------

@Serializable
data class BadgeRules(
    val display: BadgeDisplay = BadgeDisplay(),
    @SerialName("technical_trackers") val technicalTrackers: TechnicalTrackers = TechnicalTrackers(),
    val definitions: List<BadgeDef> = emptyList(),
)

@Serializable
data class BadgeDisplay(
    @SerialName("max_shown") val maxShown: Int = 3,
    @SerialName("severity_order") val severityOrder: List<String> =
        listOf("danger", "warning", "pro", "success", "neutral"),
    @SerialName("axis_order") val axisOrder: List<String> =
        listOf("security", "combo", "permissions", "trackers"),
)

@Serializable
data class TechnicalTrackers(
    val categories: Set<String> = emptySet(),
    val names: Set<String> = emptySet(),
)

@Serializable
data class BadgeDef(
    val id: String,
    val label: String,
    val icon: String,
    /** permissions | security | trackers | combo */
    val axis: String,
    /** positive | negative | neutral */
    val polarity: String,
    /** success | warning | danger | pro | neutral */
    val severity: String,
    val description: String? = null,
    val rule: BadgeRule,
    val supersedes: Set<String> = emptySet(),
)

/** Paramètres à plat : seuls ceux utiles au `type` sont renseignés. */
@Serializable
data class BadgeRule(
    val type: String,
    val context: String? = null,
    val granted: Boolean? = null,
    val min: Int? = null,
    val max: Int? = null,
    val categories: Set<String> = emptySet(),
    val patterns: Set<String> = emptySet(),
    val severity: String? = null,
    @SerialName("axis_groups") val axisGroups: List<List<String>> = emptyList(),
)

// ---------------------------------------------------------------------------
// Entrées / sorties
// ---------------------------------------------------------------------------

/** Noms courts sans préfixe "android.permission.". */
data class AppScan(
    val packageName: String,
    val targetSdk: Int,
    /** Clé de ApplicationInfo.category ("CATEGORY_GAME", ...), ou "CATEGORY_UNDEFINED". */
    val appInfoCategory: String,
    /**
     * Permissions demandées (uses-permission) + permissions BIND_* qui protègent
     * un service de l'app (accessibilité, notifications, device admin).
     */
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
    /** Signaux de sécurité collectés par SecuritySignalCollector (ids de security.signals). */
    val signals: Set<String> = emptySet(),
    /** Empreintes SHA-256 des certificats de signature, hex minuscule sans séparateur. */
    val signingCertsSha256: Set<String> = emptySet(),
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

data class SecurityFinding(
    val id: String,
    val label: String,
    val severity: String,
    val description: String?,
)

data class ScoreResult(
    val score: Int,
    val grade: String,
    val confidence: Confidence,
    val categoryId: String,
    val permissionPenalty: Double,
    val trackerPenalty: Int,
    val permissionFindings: List<PermissionFinding>,
    val trackerFindings: List<TrackerFinding>,
    /** Avant plafonnement par permission_cap (sert au badge Goinfre). */
    val permissionPenaltyRaw: Double = permissionPenalty,
    /** Plafond imposé par un pattern de sécurité, null si aucun. */
    val securityScoreCap: Int? = null,
    val securityFindings: List<SecurityFinding> = emptyList(),
    /** Tous les badges attribués, dans l'ordre des définitions. */
    val badges: List<BadgeDef> = emptyList(),
    /** Badges à afficher sur la fiche (triés par gravité, limités à display.max_shown). */
    val displayedBadges: List<BadgeDef> = emptyList(),
)

/** Base d'indicateurs (ex. stalkerware-indicators d'Echap), chargée séparément des règles. */
class IocDatabase(private val packages: Set<String>, certificatesSha256: Set<String>) {
    private val certs = certificatesSha256.map(::normalizeHash).toSet()

    fun matches(scan: AppScan): Boolean =
        scan.packageName in packages || scan.signingCertsSha256.any { normalizeHash(it) in certs }

    companion object {
        val EMPTY = IocDatabase(emptySet(), emptySet())
        fun normalizeHash(h: String) = h.replace(":", "").replace(" ", "").lowercase()
    }
}

// ---------------------------------------------------------------------------
// Moteur
// ---------------------------------------------------------------------------

class PrivacyScorer(
    private val rules: Rules,
    private val iocs: IocDatabase = IocDatabase.EMPTY,
) {
    private val securityDetector = SecurityPatternDetector(rules)
    private val badgeEngine = BadgeEngine(rules)

    fun isExcluded(pkg: String) = pkg in rules.excludedPackages

    fun resolveCategory(scan: AppScan): String =
        rules.packageOverrides[scan.packageName]
            ?: rules.categoryResolution.appInfoCategoryMap[scan.appInfoCategory]
            ?: "DEFAULT"

    /** Contexte d'une permission pour une app donnée (partagé avec le détecteur de sécurité). */
    fun contextOf(perm: String, scan: AppScan, categoryId: String = resolveCategory(scan)): Context {
        val category = rules.categories[categoryId] ?: rules.categories.getValue("DEFAULT")
        val extraAllowed = rules.packageExceptions[scan.packageName]?.allowedExtra.orEmpty()
        return when (perm) {
            in category.allowed, in extraAllowed -> Context.ALLOWED
            in category.tolerated -> Context.TOLERATED
            in category.forbidden -> Context.FORBIDDEN
            else -> Context.UNEXPECTED
        }
    }

    fun score(scan: AppScan): ScoreResult {
        val categoryId = resolveCategory(scan)

        // 1. Permissions : poids intrinsèque × contexte × facteur d'octroi
        val findings = scan.declaredPermissions
            .filter { it in rules.permissionWeights }
            .filterNot {
                it == "READ_EXTERNAL_STORAGE" &&
                    scan.targetSdk > rules.permissionRules.readExternalStorageMaxTargetSdk
            }
            .map { perm ->
                val ctx = contextOf(perm, scan, categoryId)
                val granted = perm in scan.grantedPermissions
                val grantFactor = if (granted) 1.0 else rules.scoring.declaredOnlyFactor
                val mult = rules.scoring.contextMultipliers.getValue(ctx.name.lowercase())
                PermissionFinding(perm, granted, ctx, rules.permissionWeights.getValue(perm) * mult * grantFactor)
            }

        val permissionPenaltyRaw = aggregateWithGroups(findings)
        val permissionPenalty = min(rules.scoring.permissionCap.toDouble(), permissionPenaltyRaw)

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

        // 3. Sécurité : patterns de capacités + IOC, qui plafonnent le score
        val signals = if (iocs.matches(scan)) scan.signals + SIGNAL_IOC_MATCH else scan.signals
        val securityFindings = securityDetector.detect(
            scan = scan.copy(signals = signals),
            categoryId = categoryId,
            contextOf = { perm -> contextOf(perm, scan, categoryId) },
        )
        val securityScoreCap = securityFindings
            .mapNotNull { rules.security.severityEffects[it.severity]?.scoreCap }
            .minOrNull()

        // 4. Score final
        val raw = max(0, (rules.scoring.base - permissionPenalty - trackerPenalty).roundToInt())
        val score = if (securityScoreCap != null) min(raw, securityScoreCap) else raw
        val letter = rules.scoring.grades.sortedByDescending { it.min }.first { score >= it.min }.grade
        val grade = if (confidence == Confidence.PARTIAL) letter + policy.gradeSuffix else letter

        // 5. Badges
        val badges = badgeEngine.award(
            BadgeEngine.Input(
                trackers = scan.trackers,
                permissionFindings = findings,
                permissionPenaltyRaw = permissionPenaltyRaw,
                securityFindings = securityFindings,
            )
        )

        return ScoreResult(
            score = score,
            grade = grade,
            confidence = confidence,
            categoryId = categoryId,
            permissionPenalty = permissionPenalty,
            permissionPenaltyRaw = permissionPenaltyRaw,
            trackerPenalty = trackerPenalty,
            securityScoreCap = securityScoreCap,
            permissionFindings = findings.sortedByDescending { it.penalty },
            trackerFindings = trackerFindings.sortedByDescending { it.penalty },
            securityFindings = securityFindings,
            badges = badges,
            displayedBadges = badgeEngine.forDisplay(badges),
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

    companion object {
        const val SIGNAL_IOC_MATCH = "IOC_MATCH"
    }
}
