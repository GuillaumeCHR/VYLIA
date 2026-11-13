package com.vylia.privacy.domain.alert

import com.vylia.privacy.domain.history.HistoryRecorder
import com.vylia.privacy.domain.history.ScoreSnapshot
import com.vylia.privacy.domain.usecase.historyPoint
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.usecase.scoreWithout
import com.vylia.privacy.domain.usecase.scoreWithoutTrackers

/**
 * Cause principale d'une baisse du score de l'appareil, entre le dernier point du Suivi ([previous]) et le scan complet
 * qui vient de finir ([current]).
 *
 * Méthode : le score de l'appareil est la moyenne des scores des apps utilisateur. Pour chaque changement constaté,
 * on recalcule cette moyenne comme si ce changement n'avait pas eu lieu, avec le vrai moteur de score sur l'app
 * concernée ; la différence avec la moyenne réelle est sa contribution à la baisse :
 *  - app installée : la moyenne sans elle ;
 *  - app désinstallée : la moyenne avec elle, à son score d'alors ;
 *  - permission nouvellement accordée : l'app notée sans cette permission ;
 *  - traqueurs ajoutés (mise à jour) : l'app notée sans eux ;
 *  - nouveau motif de sécurité : l'app sans ce motif (les autres gardent leur plafond).
 *  - traqueurs ou motifs apparus sur une app inchangée (même versionCode, mêmes permissions) alors qu'une base du
 *    score a été mise à jour : ce n'est pas l'app, c'est la base. Toutes ces apps sont notées ensemble sans eux, et la
 *    différence est la contribution de la mise à jour des bases ([DropCause.Type.DATABASES_UPDATED]).
 * Le changement qui contribue le plus l'emporte. Si les règles de score ont changé et qu'aucun changement d'app
 * n'explique au moins la moitié de la baisse, la cause est la mise à jour des règles ; sans changement d'app qui
 * fasse baisser la moyenne, la baisse est répartie sur plusieurs apps. De même pour le bouclier : arrêté depuis le
 * point précédent, il est la cause si aucun changement d'app n'explique au moins la moitié de la baisse.
 */
object DropCauseAnalyzer {

    private class Candidate(val cause: DropCause, val contribution: Double)

    fun analyze(
        previous: ScoreSnapshot?,
        current: List<AuditedApp>,
        rules: String,
        scorer: PrivacyScorer,
        databases: Map<String, String> = emptyMap(),
    ): DropCause {
        val apps = current.filter { AppFilter.USER.matches(it.app) }
        if (previous == null || apps.isEmpty()) return DropCause(DropCause.Type.SPREAD)
        val scores = apps.associate { it.app.packageName to it.score.value.toDouble() }
        val average = scores.values.average()

        fun averageWith(pkg: String, score: Double?): Double {
            val changed = scores.toMutableMap()
            if (score == null) changed.remove(pkg) else changed[pkg] = score
            return if (changed.isEmpty()) average else changed.values.average()
        }

        val basesChanged = HistoryRecorder.databasesChanged(previous, rules, databases)
        /** Score sans ce que les bases ont fait apparaître, pour chaque app inchangée concernée. */
        val withoutDatabases = HashMap<String, Double>()

        val candidates = buildList {
            apps.forEach { audited ->
                val pkg = audited.app.packageName
                val label = audited.app.label
                val score = audited.score.value
                val before = previous.apps[pkg]
                if (before == null) {
                    add(Candidate(DropCause(DropCause.Type.INSTALLED, pkg, label, score), averageWith(pkg, null) - average))
                    return@forEach
                }
                val granted = audited.result.permissionFindings.filter { it.granted }.map { it.permission }.toSet()
                (granted - before.granted).forEach { permission ->
                    val without = scorer.scoreWithout(audited, setOf(permission)).score.toDouble()
                    add(Candidate(DropCause(DropCause.Type.PERMISSION_GRANTED, pkg, label, score, permission), averageWith(pkg, without) - average))
                }
                val trackers = (audited.trackerAnalysis as? TrackerAnalysis.Analyzed)?.trackers?.map { it.name }?.toSet()
                val newTrackers = if (trackers != null && before.trackers != null) trackers - before.trackers else emptySet()
                val patterns = audited.result.securityFindings.map { it.id }.toSet()
                val newPatterns = if (before.security != null) patterns - before.security else emptySet()
                if (basesChanged && audited.historyPoint().sameAppAs(before) && (newTrackers.isNotEmpty() || newPatterns.isNotEmpty())) {
                    // App inchangée : ses nouveaux traqueurs ou motifs viennent des bases, pas d'elle.
                    var counterfactual = score.toDouble()
                    if (newTrackers.isNotEmpty()) counterfactual += scorer.scoreWithoutTrackers(audited, newTrackers).score - score
                    if (newPatterns.isNotEmpty()) counterfactual += scorer.scoreIgnoringPatterns(audited.result, newPatterns) - score
                    withoutDatabases[pkg] = counterfactual.coerceAtMost(100.0)
                    return@forEach
                }
                if (newTrackers.isNotEmpty()) {
                    val without = scorer.scoreWithoutTrackers(audited, newTrackers).score.toDouble()
                    add(
                        Candidate(
                            DropCause(DropCause.Type.TRACKER_ADDED, pkg, label, score, newTrackers.sorted().joinToString(", ")),
                            averageWith(pkg, without) - average,
                        )
                    )
                }
                newPatterns.forEach { id ->
                    val without = scorer.scoreIgnoringPatterns(audited.result, setOf(id)).toDouble()
                    add(Candidate(DropCause(DropCause.Type.SECURITY_PATTERN, pkg, label, score, id), averageWith(pkg, without) - average))
                }
            }
            if (withoutDatabases.isNotEmpty()) {
                val changed = scores.toMutableMap().apply { putAll(withoutDatabases) }
                add(Candidate(DropCause(DropCause.Type.DATABASES_UPDATED, detail = withoutDatabases.size.toString()), changed.values.average() - average))
            }
            (previous.apps.keys - scores.keys).forEach { pkg ->
                val before = previous.apps.getValue(pkg)
                add(Candidate(DropCause(DropCause.Type.REMOVED, pkg, before.label, before.score), averageWith(pkg, before.score.toDouble()) - average))
            }
        }

        val best = candidates.maxByOrNull { it.contribution }?.takeIf { it.contribution > 0.0 }
        val drop = previous.average - average
        val rulesChanged = previous.rules.isNotEmpty() && rules.isNotEmpty() && previous.rules != rules
        val shieldOff = HistoryRecorder.shieldTurnedOff(previous, databases)
        val cause = when {
            shieldOff && (best == null || best.contribution < drop / 2) -> DropCause(DropCause.Type.SHIELD_OFF)
            best?.cause?.type == DropCause.Type.DATABASES_UPDATED -> best.cause
            rulesChanged && (best == null || best.contribution < drop / 2) -> DropCause(DropCause.Type.RULES_UPDATED)
            best != null -> best.cause
            else -> DropCause(DropCause.Type.SPREAD)
        }
        val sensitive = cause.type == DropCause.Type.SECURITY_PATTERN ||
            apps.any { it.app.packageName == cause.packageName && it.result.securityFindings.isNotEmpty() }
        return if (sensitive) cause.copy(sensitive = true) else cause
    }
}
