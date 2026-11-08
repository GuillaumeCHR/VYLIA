package com.vylia.privacy.domain.scoring

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Règles (désérialisées depuis vylia_rules_v3.json, embarqué dans assets/)
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
    /**
     * Ensembles nommés « @… » (ex. `@HEALTH_INTIMATE`) : utilisables partout où une permission est attendue (poids,
     * catégories, groupes, badges) et développés par [expandSets] avant le calcul.
     */
    @SerialName("permission_sets") val permissionSets: Map<String, List<String>> = emptyMap(),
    /**
     * Capacités qui ne servent à rien sans un autre accès : comptées comme seulement déclarées tant qu'aucune des
     * permissions requises n'est accordée (ex. micro en arrière-plan sans accès au micro).
     */
    @SerialName("permission_requires") val permissionRequires: Map<String, List<String>> = emptyMap(),
    /** Capacités soumises à l'accord de l'utilisateur à chaque usage (capture d'écran) : jamais acquises d'avance. */
    @SerialName("consent_each_time") val consentEachTime: Set<String> = emptySet(),
    /** Permissions volontairement non comptées (ex. publicité, déjà sanctionnée par les traqueurs), affichées pour la transparence. */
    @SerialName("neutral_permissions") val neutralPermissions: List<String> = emptyList(),
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
    /** permission_set_context, permissions_absent : permissions ou ensembles visés. */
    val permissions: Set<String> = emptySet(),
    /** permission_set_context : contextes retenus (ALLOWED, TOLERATED, UNEXPECTED, FORBIDDEN). */
    val contexts: Set<String> = emptySet(),
    /** permissions_none : groupes de permission_rules ignorés (ex. le ciblage publicitaire). */
    @SerialName("ignore_groups") val ignoreGroups: Set<String> = emptySet(),
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
    /** Empreintes SHA-1 et SHA-256 des certificats de signature, en hexadécimal. */
    val signingCertDigests: Set<String> = emptySet(),
    /** WebAPK : catégorie de son site, d'après la liste des services web (un WebAPK n'a pas de catégorie Android). */
    val webCategory: String? = null,
    /** App préinstallée : certains logiciels espions imitent des noms de paquets système (voir [IocDatabase]). */
    val isSystemApp: Boolean = false,
)

data class Tracker(val name: String, val exodusCategories: Set<String>)

enum class Context { ALLOWED, TOLERATED, UNEXPECTED, FORBIDDEN }
enum class Confidence { FULL, PARTIAL }

data class PermissionFinding(
    val permission: String,
    val granted: Boolean,
    val context: Context,
    val penalty: Double,
    /** Part réellement retenue dans le score après les groupes (0 si une permission plus lourde du groupe compte). */
    val counted: Double = penalty,
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
    /** Score avant le plafond de sécurité : s'il dépasse [score], le plafond s'applique. */
    val uncappedScore: Int = score,
    val securityFindings: List<SecurityFinding> = emptyList(),
    /** Nom du logiciel espion connu auquel l'app correspond (Echap ou MVT), `null` sinon. */
    val knownStalkerware: String? = null,
    /** Vrai si ce logiciel espion relève de la surveillance ciblée (indicateurs MVT) plutôt que d'un stalkerware. */
    val knownSpywareTargeted: Boolean = false,
    /** Tous les badges attribués, dans l'ordre des définitions. */
    val badges: List<BadgeDef> = emptyList(),
    /** Badges à afficher sur la fiche (triés par gravité, limités à display.max_shown). */
    val displayedBadges: List<BadgeDef> = emptyList(),
) {
    /** Vrai si le plafond de sécurité a abaissé le score : la somme des pénalités ne l'explique plus. */
    val isCapped: Boolean get() = securityScoreCap != null && score < uncappedScore
}

/**
 * Logiciels espions connus, chargés séparément des règles (un import de règles ne peut pas les modifier) : les
 * stalkerwares de la base d'Echap et les logiciels espions de surveillance ciblée des indicateurs MVT. Une app y
 * correspond par son nom de paquet ou par l'empreinte (SHA-1 ou SHA-256) d'un de ses certificats de signature, qui la
 * trahit même renommée. [userAppPackages] : paquets reconnus seulement sur une app installée par l'utilisateur, car
 * ils imitent des noms système (`com.android.core`) qu'une vraie app préinstallée pourrait porter.
 */
class IocDatabase(
    packages: Map<String, String>,
    certificates: Map<String, String>,
    userAppPackages: Map<String, String> = emptyMap(),
    /** Noms des logiciels espions de surveillance ciblée (MVT), pour adapter les conseils. */
    val targeted: Set<String> = emptySet(),
) {
    private val packages = packages.toMap()
    private val certs = certificates.mapKeys { normalizeHash(it.key) }
    private val userAppPackages = userAppPackages.toMap()

    /** Nom du logiciel espion reconnu, `null` sinon. */
    fun match(scan: AppScan): String? =
        packages[scan.packageName]
            ?: (if (scan.isSystemApp) null else userAppPackages[scan.packageName])
            ?: scan.signingCertDigests.firstNotNullOfOrNull { certs[normalizeHash(it)] }

    val size: Int get() = (packages.values + certs.values + userAppPackages.values).toSet().size

    /** Stalkerwares (Echap) et campagnes de surveillance ciblée (MVT) reconnaissables, comptés à part. */
    val stalkerwareSize: Int get() = (packages.values + certs.values + userAppPackages.values).toSet().count { it !in targeted }
    val targetedSize: Int get() = (packages.values + certs.values + userAppPackages.values).toSet().count { it in targeted }

    /** Les deux bases ensemble ; à indicateur égal, celle-ci l'emporte. */
    operator fun plus(other: IocDatabase) = IocDatabase(
        other.packages + packages,
        other.certs + certs,
        (other.userAppPackages + userAppPackages) - packages.keys - other.packages.keys,
        other.targeted + targeted,
    )

    /** Contenu complet (paquets, empreintes), pour comparer deux bases dans les tests. */
    fun entries(): Pair<Map<String, String>, Map<String, String>> = packages to certs

    companion object {
        val EMPTY = IocDatabase(emptyMap(), emptyMap())
        fun normalizeHash(h: String) = h.replace(":", "").replace(" ", "").lowercase()
    }
}

// ---------------------------------------------------------------------------
// Moteur
// ---------------------------------------------------------------------------

class PrivacyScorer(
    rawRules: Rules,
    private val iocs: IocDatabase = IocDatabase.EMPTY,
) {
    /** Règles avec les ensembles « @… » développés en permissions. */
    private val rules = rawRules.expandSets()
    private val securityDetector = SecurityPatternDetector(rules)
    private val badgeEngine = BadgeEngine(rules)

    fun isExcluded(pkg: String) = pkg in rules.excludedPackages

    fun resolveCategory(scan: AppScan): String =
        rules.packageOverrides[scan.packageName]
            ?: scan.webCategory?.takeIf { it in rules.categories }
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

    private val neutralPermissions = rules.neutralPermissions.toSet()

    /**
     * Score qu'aurait l'app sans les motifs de sécurité [ignored] : les autres motifs gardent leur plafond. Sert à
     * mesurer ce qu'un nouveau motif coûte au score de l'appareil (cause d'une baisse).
     */
    fun scoreIgnoringPatterns(result: ScoreResult, ignored: Set<String>): Int {
        val cap = result.securityFindings
            .filter { it.id !in ignored }
            .mapNotNull { rules.security.severityEffects[it.severity]?.scoreCap }
            .minOrNull()
        return if (cap != null) min(result.uncappedScore, cap) else result.uncappedScore
    }

    fun score(scan: AppScan): ScoreResult {
        val categoryId = resolveCategory(scan)

        // 1. Permissions : poids intrinsèque × contexte × facteur d'octroi. Les permissions neutralisées (publicité,
        // notifications) ne comptent jamais, même si une grille leur donnait un poids.
        val findings = scan.declaredPermissions
            .filter { it in rules.permissionWeights && it !in neutralPermissions }
            .filterNot {
                it == "READ_EXTERNAL_STORAGE" &&
                    scan.targetSdk > rules.permissionRules.readExternalStorageMaxTargetSdk
            }
            .map { perm ->
                val ctx = contextOf(perm, scan, categoryId)
                val granted = isEffectivelyGranted(perm, scan.grantedPermissions)
                val grantFactor = if (granted) 1.0 else rules.scoring.declaredOnlyFactor
                val mult = rules.scoring.contextMultipliers.getValue(ctx.name.lowercase())
                PermissionFinding(perm, granted, ctx, rules.permissionWeights.getValue(perm) * mult * grantFactor)
            }

        val counted = countedPenalties(findings)
        val permissionFindings = findings.map { it.copy(counted = counted.getValue(it.permission)) }
        val permissionPenaltyRaw = counted.values.sum()
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
        val knownStalkerware = iocs.match(scan)
        val signals = if (knownStalkerware != null) scan.signals + SIGNAL_IOC_MATCH else scan.signals
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
                permissionFindings = permissionFindings,
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
            uncappedScore = raw,
            knownStalkerware = knownStalkerware,
            knownSpywareTargeted = knownStalkerware != null && knownStalkerware in iocs.targeted,
            permissionFindings = permissionFindings.sortedByDescending { it.penalty },
            trackerFindings = trackerFindings.sortedByDescending { it.penalty },
            securityFindings = securityFindings,
            badges = badges,
            displayedBadges = badgeEngine.forDisplay(badges),
        )
    }

    /** Accordée, et réellement utilisable : l'accès dont elle dépend est accordé, sans accord à chaque usage. */
    private fun isEffectivelyGranted(perm: String, granted: Set<String>): Boolean =
        perm in granted && perm !in rules.consentEachTime &&
            rules.permissionRequires[perm].let { required -> required.isNullOrEmpty() || required.any { it in granted } }

    /**
     * Part retenue de chaque pénalité : dans un groupe, seule la plus lourde compte entièrement (« max ») ou les autres
     * comptent à moitié (« max_plus_half ») ; ex. position précise et approximative ne comptent qu'une fois.
     */
    private fun countedPenalties(findings: List<PermissionFinding>): Map<String, Double> {
        val byPerm = findings.associate { it.permission to it.penalty }
        val counted = HashMap(byPerm)
        for (group in rules.permissionRules.groups) {
            val present = group.members.filter { it in byPerm }.sortedByDescending { byPerm.getValue(it) }
            present.drop(1).forEach { perm ->
                counted[perm] = when (group.mode) {
                    "max" -> 0.0
                    "max_plus_half" -> byPerm.getValue(perm) * 0.5
                    else -> byPerm.getValue(perm)
                }
            }
        }
        return counted
    }

    companion object {
        const val SIGNAL_IOC_MATCH = "IOC_MATCH"
    }
}

/** Développe les ensembles « @… » partout où ils servent de permission : poids, catégories, groupes, badges. */
fun Rules.expandSets(): Rules {
    if (permissionSets.isEmpty()) return this
    fun expand(perms: Collection<String>): List<String> = perms.flatMap { permissionSets[it] ?: listOf(it) }
    return copy(
        // Un poids explicite sur une permission l'emporte sur celui de son ensemble.
        permissionWeights = permissionWeights.entries
            .sortedBy { !it.key.startsWith("@") }
            .flatMap { (perm, weight) -> (permissionSets[perm] ?: listOf(perm)).map { it to weight } }
            .toMap(),
        permissionRules = permissionRules.copy(groups = permissionRules.groups.map { it.copy(members = expand(it.members)) }),
        categories = categories.mapValues { (_, c) ->
            c.copy(allowed = expand(c.allowed).toSet(), tolerated = expand(c.tolerated).toSet(), forbidden = expand(c.forbidden).toSet())
        },
        badges = badges.copy(
            definitions = badges.definitions.map { it.copy(rule = it.rule.copy(permissions = expand(it.rule.permissions).toSet())) },
        ),
        permissionSets = emptyMap(),
    )
}
