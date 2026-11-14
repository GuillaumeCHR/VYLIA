package com.vylia.privacy.domain.act

import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.PrivacyScore

/** Une action du ménage guidé, avec ce qu'elle change sur ce téléphone. */
sealed interface CleanupAction {
    /** Identifiant stable, pour retrouver l'étape d'un scan à l'autre. */
    val key: String

    /** Retirer à une app toutes ses autorisations injustifiées (une seule carte, comme dans Gérer › Autorisations). */
    data class Revoke(val revocations: AppRevocations) : CleanupAction {
        override val key: String get() = "revoke:${revocations.app.app.packageName}"
        val revoked: Set<String> get() = revocations.suggestions.flatMapTo(HashSet()) { it.revoked }
    }

    /** Supprimer une app inutilisée ou intrusive. */
    data class Remove(val removal: RemovalSuggestion) : CleanupAction {
        override val key: String get() = "remove:${removal.app.app.packageName}"
    }

    /** Activer le Bouclier VYLIA. */
    data object Shield : CleanupAction {
        override val key: String get() = "shield"
    }
}

/**
 * Une étape et son effet : [appBefore] → [appAfter] pour l'app concernée (rien pour le bouclier, qui touche toutes les
 * apps), et le score moyen du téléphone avant et après, en valeur exacte : avec une centaine d'apps, une étape peut
 * rapporter moins d'un point à la moyenne arrondie.
 */
data class CleanupStep(
    val action: CleanupAction,
    val appBefore: Int?,
    val appAfter: Int?,
    val deviceBefore: Double,
    val deviceAfter: Double,
) {
    val deviceGain: Double get() = deviceAfter - deviceBefore
}

/**
 * Ménage guidé en 5 minutes : les [MAX_STEPS] actions qui font le plus monter le score moyen des apps utilisateur de
 * ce téléphone, choisies une à une. Chaque étape est évaluée après les précédentes, pour ne pas compter deux fois la
 * même app. Les gains des autorisations viennent du vrai moteur de score ([ReductionAdvisor]) ; après le bouclier, ils
 * sont reportés tels quels sur le score avec bouclier, ce qui reste une estimation (le bouclier ne touche que les
 * traqueurs, les autorisations que les permissions).
 */
object GuidedCleanup {

    const val MAX_STEPS = 3

    /**
     * @param apps apps utilisateur du scan, scores tels qu'affichés (bouclier compris s'il filtre).
     * @param shieldedScores score de chaque app avec le bouclier, ou `null` si le bouclier est déjà actif ou absent.
     */
    fun plan(
        apps: List<AuditedApp>,
        revocations: List<AppRevocations>,
        removals: List<RemovalSuggestion>,
        shieldedScores: Map<String, Int>?,
    ): List<CleanupStep> {
        if (apps.isEmpty()) return emptyList()
        val scores = apps.associateTo(LinkedHashMap()) { it.app.packageName to it.score.value }
        val candidates = buildList<CleanupAction> {
            revocations.filter { it.gain > 0 }.forEach { add(CleanupAction.Revoke(it)) }
            removals.forEach { add(CleanupAction.Remove(it)) }
            if (!shieldedScores.isNullOrEmpty()) add(CleanupAction.Shield)
        }.toMutableList()
        val steps = mutableListOf<CleanupStep>()
        // Une app ne revient pas deux fois : la retirer puis la supprimer ferait une étape pour rien.
        val touched = HashSet<String>()
        while (steps.size < MAX_STEPS) {
            val before = average(scores) ?: break
            val best = candidates
                .filter { packageOf(it) !in touched }
                .mapNotNull { action -> evaluate(action, scores, shieldedScores, apps)?.let { action to it } }
                .filter { (_, after) -> average(after)?.let { it > before + EPSILON } == true }
                .maxByOrNull { (_, after) -> average(after)!! }
                ?: break
            val (action, after) = best
            val pkg = packageOf(action)
            steps += CleanupStep(
                action = action,
                appBefore = pkg?.let { scores[it] },
                appAfter = pkg?.let { after[it] },
                deviceBefore = before,
                deviceAfter = average(after)!!,
            )
            scores.clear()
            scores.putAll(after)
            candidates.remove(action)
            pkg?.let(touched::add)
        }
        return steps
    }

    /** Score du téléphone affiché pour une moyenne exacte (même arrondi que partout ailleurs). */
    fun rounded(average: Double): Int = PrivacyScore.ofAverage(average).value

    private fun packageOf(action: CleanupAction): String? = when (action) {
        is CleanupAction.Revoke -> action.revocations.app.app.packageName
        is CleanupAction.Remove -> action.removal.app.app.packageName
        CleanupAction.Shield -> null
    }

    /** Scores des apps après [action], ou `null` si elle ne s'applique plus (app absente). */
    private fun evaluate(
        action: CleanupAction,
        scores: Map<String, Int>,
        shieldedScores: Map<String, Int>?,
        apps: List<AuditedApp>,
    ): Map<String, Int>? = when (action) {
        is CleanupAction.Revoke -> {
            val pkg = action.revocations.app.app.packageName
            scores[pkg]?.let { current -> scores + (pkg to (current + action.revocations.gain).coerceIn(0, PrivacyScore.MAX)) }
        }
        is CleanupAction.Remove -> {
            val pkg = action.removal.app.app.packageName
            if (pkg in scores && scores.size > 1) scores - pkg else null
        }
        CleanupAction.Shield -> shieldedScores?.let { shielded ->
            // Le gain du bouclier sur chaque app s'ajoute à ce que les étapes précédentes lui ont déjà fait gagner.
            val original = apps.associate { it.app.packageName to it.score.value }
            scores.mapValues { (pkg, score) ->
                val gain = (shielded[pkg] ?: original[pkg] ?: score) - (original[pkg] ?: score)
                (score + gain.coerceAtLeast(0)).coerceIn(0, PrivacyScore.MAX)
            }
        }
    }

    private fun average(scores: Map<String, Int>): Double? = if (scores.isEmpty()) null else scores.values.average()

    private const val EPSILON = 1e-9
}
