package com.vylia.privacy.domain.scoring

/**
 * Contrôle d'un fichier de règles importé, avant tout usage : bornes des nombres, tailles des listes et des textes.
 * Un import ne peut pas non plus retirer une app de l'audit : seules les exclusions des règles intégrées (VYLIA
 * elle-même) sont admises, sinon une app espion pourrait se faire oublier.
 *
 * Lève [IllegalArgumentException] avec la cause, affichée telle quelle.
 */
object RulesValidator {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_ENTRIES = 5_000
    private const val MAX_TEXT = 1_000
    private const val MAX_WEIGHT = 100
    private const val MAX_MULTIPLIER = 10.0

    fun validate(rules: Rules, allowedExclusions: Set<String>) {
        val s = rules.scoring
        require(s.base in 1..1_000) { "scoring.base hors bornes" }
        require(s.permissionCap in 0..s.base && s.trackerCap in 0..s.base) { "plafonds hors bornes" }
        require(s.unanalyzedPolicy.assumedTrackerPenalty in 0..s.base) { "pénalité des apps non analysées hors bornes" }
        require(s.declaredOnlyFactor.isFinite() && s.declaredOnlyFactor in 0.0..1.0) { "declared_only_factor hors bornes" }
        require(s.contextMultipliers.values.all { it.isFinite() && it in 0.0..MAX_MULTIPLIER }) { "multiplicateurs hors bornes" }
        require(s.grades.isNotEmpty() && s.grades.size <= 26 && s.grades.all { it.min in 0..s.base && it.grade.length <= 3 }) {
            "notes invalides"
        }
        require(rules.permissionWeights.values.all { it in 0..MAX_WEIGHT }) { "poids de permission hors bornes" }
        val t = rules.trackerRules
        require((t.categoryWeights.values + t.overrides.values + t.defaultWeight).all { it in 0..MAX_WEIGHT }) {
            "poids de traqueur hors bornes"
        }
        require(rules.security.severityEffects.values.all { e -> e.scoreCap == null || e.scoreCap in 0..s.base }) {
            "plafond de sécurité hors bornes"
        }
        require("DEFAULT" in rules.categories) { "catégorie DEFAULT absente" }

        val extraExclusions = rules.excludedPackages - allowedExclusions
        require(extraExclusions.isEmpty()) { "exclusion d'app refusée : ${extraExclusions.take(3).joinToString()}" }

        val sizes = listOf(
            rules.permissionWeights.size, rules.categories.size, rules.packageExceptions.size, rules.packageOverrides.size,
            rules.permissionRules.groups.size, rules.permissionSets.size, rules.permissionRequires.size,
            rules.consentEachTime.size, rules.neutralPermissions.size, rules.security.patterns.size,
            rules.security.signals.size, rules.badges.definitions.size, rules.categoryResolution.appInfoCategoryMap.size,
            t.categoryWeights.size, t.overrides.size,
        )
        require(sizes.all { it <= MAX_ENTRIES }) { "fichier trop volumineux" }

        val texts = buildList {
            add(rules.version)
            rules.categories.values.forEach { add(it.displayName); it.description?.let(::add) }
            rules.security.patterns.values.forEach { add(it.label); add(it.condition); it.description?.let(::add) }
            addAll(rules.security.signals.values)
            rules.badges.definitions.forEach { add(it.label) }
        }
        require(texts.all { it.length <= MAX_TEXT }) { "texte trop long" }
    }
}
