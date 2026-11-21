package com.vylia.privacy.domain.scoring

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Contrôle d'un fichier de règles importé, avant tout usage. Le fichier vient de l'extérieur (Téléchargements, un
 * partage) : il est traité comme hostile tant qu'il n'a pas passé ces deux étapes.
 *
 * 1. [checkDocument], sur le texte brut, avant de le décoder en [Rules] : profondeur d'imbrication bornée (un JSON
 *    très imbriqué ferait déborder la pile du décodeur) et aucun caractère invisible ou de contrôle dans les textes
 *    et les clés (inversion bidirectionnelle U+202E, caractères de largeur nulle…), qui ferait afficher un libellé
 *    trompeur.
 * 2. [validate], sur les règles décodées :
 *    - bornes des nombres, tailles des listes et des textes ;
 *    - format des identifiants (permissions, paquets, catégories, badges), et catégories citées qui existent ;
 *    - ce que le moteur lit sans repli : une note qui commence à 0, les quatre multiplicateurs de contexte ;
 *    - un import ne peut pas faire oublier une app espion : pas d'exclusion d'app en plus des règles intégrées (VYLIA
 *      elle-même), et les motifs de sécurité intégrés restent en place, avec la même condition et un plafond au moins
 *      aussi strict. Il peut en ajouter, pas en affaiblir.
 *
 * Lève [IllegalArgumentException] avec la cause, affichée telle quelle.
 */
object RulesValidator {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_ENTRIES = 5_000
    private const val MAX_TEXT = 1_000
    private const val MAX_WEIGHT = 100
    private const val MAX_MULTIPLIER = 10.0
    private const val MAX_DEPTH = 32

    private val CONTEXTS = setOf("allowed", "tolerated", "unexpected", "forbidden")
    private val PERMISSION = Regex("^@?[A-Za-z][A-Za-z0-9_.]{0,199}$")
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val CATEGORY = Regex("^[A-Z][A-Z0-9_]{0,63}$")
    private val IDENTIFIER = Regex("^[A-Za-z][A-Za-z0-9_]{0,63}$")
    private val ICON = Regex("^[a-z][a-z0-9_]{0,63}$")
    private val GRADE = Regex("^[A-Z]{1,3}$")

    /** Étape 1, sur le texte brut ; à faire avant tout décodage. */
    fun checkDocument(content: String) {
        require(depthOf(content) <= MAX_DEPTH) { "structure trop imbriquée" }
        // Le décodage d'un JSON de profondeur bornée est sûr ; il sert ici à voir chaque texte et chaque clé.
        walk(Json.parseToJsonElement(content))
    }

    /** Étape 2, sur les règles décodées ; [bundled] = règles intégrées, référence de ce qui ne peut pas s'affaiblir. */
    fun validate(rules: Rules, bundled: Rules) {
        val s = rules.scoring
        require(s.base in 1..1_000) { "scoring.base hors bornes" }
        require(s.permissionCap in 0..s.base && s.trackerCap in 0..s.base) { "plafonds hors bornes" }
        require(s.unanalyzedPolicy.assumedTrackerPenalty in 0..s.base) { "pénalité des apps non analysées hors bornes" }
        require(s.unanalyzedPolicy.gradeSuffix.length <= 2) { "suffixe de note trop long" }
        require(s.declaredOnlyFactor.isFinite() && s.declaredOnlyFactor in 0.0..1.0) { "declared_only_factor hors bornes" }
        require(
            s.shield.blockedFactor.isFinite() && s.shield.partialFactor.isFinite() &&
                s.shield.blockedFactor in 0.0..s.shield.partialFactor && s.shield.partialFactor <= 1.0,
        ) { "facteurs du bouclier hors bornes" }
        require(s.contextMultipliers.keys == CONTEXTS) { "multiplicateurs de contexte attendus : ${CONTEXTS.joinToString()}" }
        require(s.contextMultipliers.values.all { it.isFinite() && it in 0.0..MAX_MULTIPLIER }) { "multiplicateurs hors bornes" }
        require(s.grades.isNotEmpty() && s.grades.size <= 26 && s.grades.all { it.min in 0..s.base && GRADE.matches(it.grade) }) {
            "notes invalides"
        }
        // Le moteur prend la première note dont le minimum est atteint : sans note à 0, un score bas n'en aurait aucune.
        require(s.grades.any { it.min == 0 }) { "aucune note ne commence à 0" }
        require(s.grades.map { it.min }.toSet().size == s.grades.size && s.grades.map { it.grade }.toSet().size == s.grades.size) {
            "notes en double"
        }
        require(rules.permissionWeights.values.all { it in 0..MAX_WEIGHT }) { "poids de permission hors bornes" }
        val t = rules.trackerRules
        require((t.categoryWeights.values + t.overrides.values + t.defaultWeight).all { it in 0..MAX_WEIGHT }) {
            "poids de traqueur hors bornes"
        }
        require(rules.security.severityEffects.values.all { e -> e.scoreCap == null || e.scoreCap in 0..s.base }) {
            "plafond de sécurité hors bornes"
        }
        require(rules.permissionRules.readExternalStorageMaxTargetSdk in 0..1_000) { "SDK cible hors bornes" }
        require(rules.badges.display.maxShown in 0..20) { "nombre de badges affichés hors bornes" }
        require("DEFAULT" in rules.categories) { "catégorie DEFAULT absente" }

        val extraExclusions = rules.excludedPackages - bundled.excludedPackages
        require(extraExclusions.isEmpty()) { "exclusion d'app refusée : ${extraExclusions.take(3).joinToString()}" }
        checkSecurity(rules.security, bundled.security)

        val sizes = listOf(
            rules.permissionWeights.size, rules.categories.size, rules.packageExceptions.size, rules.packageOverrides.size,
            rules.permissionRules.groups.size, rules.permissionSets.size, rules.permissionRequires.size,
            rules.consentEachTime.size, rules.neutralPermissions.size, rules.security.patterns.size,
            rules.security.signals.size, rules.badges.definitions.size, rules.categoryResolution.appInfoCategoryMap.size,
            t.categoryWeights.size, t.overrides.size,
        )
        require(sizes.all { it <= MAX_ENTRIES }) { "fichier trop volumineux" }

        checkIdentifiers(rules)

        val texts = buildList {
            add(rules.version)
            rules.categories.values.forEach { add(it.displayName); it.description?.let(::add) }
            rules.security.patterns.values.forEach { add(it.label); add(it.condition); it.description?.let(::add) }
            addAll(rules.security.signals.values)
            rules.badges.definitions.forEach { add(it.label); it.description?.let(::add) }
            addAll(t.categoryWeights.keys)
            addAll(t.overrides.keys)
        }
        require(texts.all { it.length <= MAX_TEXT }) { "texte trop long" }
    }

    /** Les motifs intégrés restent, inchangés, et leur plafond ne peut que se durcir. */
    private fun checkSecurity(imported: SecurityRules, bundled: SecurityRules) {
        fun cap(rules: SecurityRules, severity: String) = rules.severityEffects[severity]?.scoreCap
        bundled.patterns.forEach { (id, reference) ->
            val pattern = imported.patterns[id]
            require(pattern != null) { "motif de sécurité retiré : $id" }
            require(pattern.condition == reference.condition) { "condition du motif de sécurité modifiée : $id" }
            require(reference.exemptPackages.containsAll(pattern.exemptPackages)) { "exemption de sécurité ajoutée : $id" }
            require(reference.exemptCategories.containsAll(pattern.exemptCategories)) { "exemption de sécurité ajoutée : $id" }
            val before = cap(bundled, reference.severity)
            val after = cap(imported, pattern.severity)
            require(before == null || (after != null && after <= before)) { "plafond de sécurité affaibli : $id" }
        }
    }

    private fun checkIdentifiers(rules: Rules) {
        val permissions = buildList {
            addAll(rules.permissionWeights.keys)
            rules.categories.values.forEach { addAll(it.allowed); addAll(it.tolerated); addAll(it.forbidden) }
            rules.permissionRules.groups.forEach { addAll(it.members) }
            rules.permissionSets.forEach { (name, members) -> add(name); addAll(members) }
            rules.permissionRequires.forEach { (name, needed) -> add(name); addAll(needed) }
            addAll(rules.consentEachTime)
            addAll(rules.neutralPermissions)
            rules.packageExceptions.values.forEach { addAll(it.allowedExtra) }
        }
        permissions.firstOrNull { !PERMISSION.matches(it) }?.let { throw IllegalArgumentException("nom de permission invalide : ${it.take(40)}") }

        val packages = buildList {
            addAll(rules.excludedPackages)
            addAll(rules.packageExceptions.keys)
            addAll(rules.packageOverrides.keys)
            rules.security.patterns.values.forEach { addAll(it.exemptPackages) }
        }
        packages.firstOrNull { !PACKAGE.matches(it) }?.let { throw IllegalArgumentException("nom de paquet invalide : ${it.take(40)}") }

        rules.categories.keys.firstOrNull { !CATEGORY.matches(it) }?.let {
            throw IllegalArgumentException("nom de catégorie invalide : ${it.take(40)}")
        }
        val cited = rules.packageOverrides.values + rules.categoryResolution.appInfoCategoryMap.values +
            rules.security.patterns.values.flatMap { it.exemptCategories }
        cited.firstOrNull { it !in rules.categories }?.let { throw IllegalArgumentException("catégorie inconnue : ${it.take(40)}") }
        rules.categoryResolution.appInfoCategoryMap.keys.firstOrNull { !CATEGORY.matches(it) }?.let {
            throw IllegalArgumentException("catégorie Android invalide : ${it.take(40)}")
        }

        val ids = rules.badges.definitions.map { it.id } + rules.security.patterns.keys + rules.security.signals.keys +
            rules.permissionRules.groups.map { it.id }
        ids.firstOrNull { !IDENTIFIER.matches(it) }?.let { throw IllegalArgumentException("identifiant invalide : ${it.take(40)}") }
        rules.badges.definitions.firstOrNull { !ICON.matches(it.icon) }?.let {
            throw IllegalArgumentException("icône de badge invalide : ${it.id}")
        }
    }

    /** Profondeur maximale de `{` et `[` hors des chaînes, sans décoder le document. */
    fun depthOf(content: String): Int {
        var depth = 0
        var max = 0
        var inString = false
        var escaped = false
        for (c in content) {
            when {
                inString && escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                inString && c == '"' -> inString = false
                inString -> Unit
                c == '"' -> inString = true
                c == '{' || c == '[' -> { depth++; if (depth > max) max = depth }
                c == '}' || c == ']' -> depth--
            }
        }
        return max
    }

    private fun walk(element: JsonElement) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) -> checkText(key); walk(value) }
            is JsonArray -> element.forEach(::walk)
            is JsonPrimitive -> if (element.isString) checkText(element.content)
        }
    }

    /** Caractères invisibles ou de contrôle : seuls le retour à la ligne et la tabulation sont admis. */
    private fun checkText(text: String) {
        require(text.length <= MAX_TEXT) { "texte trop long" }
        text.firstOrNull(::isForbidden)?.let {
            throw IllegalArgumentException("caractère interdit (U+%04X)".format(it.code))
        }
    }

    fun isForbidden(c: Char): Boolean {
        val code = c.code
        return (code < 0x20 && c != '\n' && c != '\t') ||
            code in 0x7F..0x9F || // DEL et contrôles C1
            code in 0x200B..0x200F || // largeur nulle, marques gauche-droite
            code in 0x202A..0x202E || // enchâssements et inversions bidirectionnels
            code in 0x2060..0x2069 || // joint de mot, isolats bidirectionnels
            code == 0x061C || code == 0xFEFF
    }
}
