package com.vylia.privacy.domain.scoring

/**
 * Attribue les badges définis dans `badges.definitions`.
 *
 * Deux passes :
 *  1. badges « de base » (permissions, sécurité, traqueurs), puis résolution des `supersedes`
 *     (Hors-la-loi remplace Pêche à la ligne, Régie publicitaire remplace Homme-sandwich…) ;
 *  2. badges « combo » (types axes_*), évalués sur le résultat de la passe 1.
 *
 * Un type de règle inconnu est ignoré (le badge n'est jamais attribué), ce qui permet
 * d'ajouter un badge dans le JSON avant que l'app sache l'évaluer.
 */
class BadgeEngine(private val rules: Rules) {

    data class Input(
        /** null = traqueurs non analysés. */
        val trackers: List<Tracker>?,
        val permissionFindings: List<PermissionFinding>,
        val permissionPenaltyRaw: Double,
        val securityFindings: List<SecurityFinding>,
    )

    private val defs = rules.badges.definitions
    private val technical = rules.badges.technicalTrackers

    init {
        val ids = defs.map { it.id }
        val dupes = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        require(dupes.isEmpty()) { "Badges en double : $dupes" }
        defs.forEach { d ->
            val unknown = d.supersedes - ids.toSet()
            require(unknown.isEmpty()) { "Badge ${d.id} : supersedes inconnus $unknown" }
            if (d.rule.type == "security_pattern") {
                val missing = d.rule.patterns - rules.security.patterns.keys
                require(missing.isEmpty()) { "Badge ${d.id} : patterns inconnus $missing" }
            }
            if (d.rule.type == "permission_context") {
                require(d.rule.context != null && Context.entries.any { it.name == d.rule.context }) {
                    "Badge ${d.id} : context invalide « ${d.rule.context} »"
                }
            }
        }
    }

    fun award(input: Input): List<BadgeDef> {
        val (combos, base) = defs.partition { it.rule.type.startsWith("axes_") }
        val pass1 = resolveSupersedes(base.filter { evalBase(it.rule, input) })
        val pass2 = combos.filter { evalCombo(it.rule, pass1) }
        val awarded = resolveSupersedes(pass1 + pass2).map { it.id }.toSet()
        return defs.filter { it.id in awarded } // ordre stable = ordre du JSON
    }

    /**
     * Badges à montrer sur la fiche : par gravité, puis par axe (la sécurité avant le reste),
     * puis dans l'ordre du JSON.
     */
    fun forDisplay(badges: List<BadgeDef>): List<BadgeDef> {
        val d = rules.badges.display
        fun rank(list: List<String>, v: String) = list.indexOf(v).let { if (it < 0) list.size else it }
        return badges
            .sortedWith(compareBy({ rank(d.severityOrder, it.severity) }, { rank(d.axisOrder, it.axis) }))
            .take(d.maxShown)
    }

    private fun resolveSupersedes(badges: List<BadgeDef>): List<BadgeDef> {
        val superseded = badges.flatMap { it.supersedes }.toSet()
        return badges.filter { it.id !in superseded }
    }

    private fun evalBase(r: BadgeRule, i: Input): Boolean {
        val f = i.permissionFindings
        val t = i.trackers
        return when (r.type) {
            "permissions_none" -> f.isEmpty()

            "permissions_in_purpose" -> f.isNotEmpty() && f.all {
                it.context == Context.ALLOWED || (it.context == Context.TOLERATED && !it.granted)
            }

            "permission_context" -> inRange(
                f.count { it.context.name == r.context && (r.granted == null || it.granted == r.granted) },
                r,
            )

            "permission_penalty_at_cap" -> i.permissionPenaltyRaw >= rules.scoring.permissionCap

            "security_pattern" -> i.securityFindings.any { it.id in r.patterns }

            "trackers_none" -> t != null && t.isEmpty()

            "trackers_technical_only" -> t != null && t.isNotEmpty() && t.all(::isTechnical)

            "trackers_in_categories" -> t != null &&
                inRange(t.count { it.exodusCategories.any { c -> c in r.categories } }, r)

            "tracker_category_coverage" -> t != null &&
                inRange(t.flatMap { it.exodusCategories }.toSet().count { it in r.categories }, r)

            "trackers_count" -> t != null && inRange(t.size, r)

            "trackers_unanalyzed" -> t == null

            else -> false
        }
    }

    private fun evalCombo(r: BadgeRule, present: List<BadgeDef>): Boolean = when (r.type) {
        "axes_all_positive" ->
            present.none { it.polarity == "negative" } &&
                r.axisGroups.isNotEmpty() &&
                r.axisGroups.all { g -> present.any { it.axis in g && it.polarity == "positive" } }

        "axes_all_severity" ->
            r.axisGroups.isNotEmpty() &&
                r.axisGroups.all { g -> present.any { it.axis in g && it.severity == r.severity } }

        else -> false
    }

    private fun isTechnical(t: Tracker) =
        t.name in technical.names ||
            (t.exodusCategories.isNotEmpty() && t.exodusCategories.all { it in technical.categories })

    private fun inRange(n: Int, r: BadgeRule) = n >= (r.min ?: 1) && (r.max == null || n <= r.max)
}
