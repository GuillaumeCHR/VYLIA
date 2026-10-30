package com.vylia.privacy.data.trackers

import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerSignature

/**
 * Recherche simultanée de toutes les signatures Exodus dans les noms de classes, construite une
 * seule fois par base de signatures.
 *
 * Les `code_signature` Exodus sont des regex appliquées par recherche (`re.search`) aux noms de
 * classes, mais en pratique ce sont des littéraux (`com.facebook.ads.`) où `.` vaut un point. Ces
 * alternatives littérales passent par un automate d'Aho-Corasick : chaque nom de classe est lu une
 * seule fois, quel que soit le nombre de signatures. Les rares alternatives réellement régulières
 * (aucune dans le snapshot actuel) sont testées une à une avec leur regex compilée.
 *
 * Travailler sur les noms de classes complets, et non sur les seuls paquets, permet de détecter
 * les signatures qui visent une classe précise (`com.adobe.mobile.Analytics`).
 */
class TrackerMatcher(signatures: List<TrackerSignature>) {

    private val trackers: List<Tracker> = signatures.map { it.tracker }
    private val trackerIndexById: Map<Int, Int> = trackers.withIndex().associate { (index, tracker) -> tracker.id to index }

    // Automate : transitions par nœud, lien d'échec, traqueurs reconnus en arrivant sur le nœud.
    private val transitions = mutableListOf(HashMap<Char, Int>())
    private val failure: IntArray
    private val outputs: Array<IntArray>
    private val regexAlternatives = mutableListOf<Pair<Regex, Int>>()

    init {
        val nodeOutputs = mutableListOf(mutableSetOf<Int>())
        signatures.forEachIndexed { trackerIndex, signature ->
            for (alternative in splitTopLevel(signature.codeSignature.pattern)) {
                if (alternative.isEmpty()) continue
                if (alternative.any { it in REGEX_METACHARACTERS }) {
                    regexAlternatives.add(Regex(alternative) to trackerIndex)
                    continue
                }
                var node = 0
                for (char in alternative) {
                    node = transitions[node].getOrPut(char) {
                        transitions.add(HashMap())
                        nodeOutputs.add(mutableSetOf())
                        transitions.lastIndex
                    }
                }
                nodeOutputs[node].add(trackerIndex)
            }
        }

        // Liens d'échec en largeur ; chaque nœud hérite des sorties de son lien d'échec.
        failure = IntArray(transitions.size)
        val queue = ArrayDeque<Int>()
        transitions[0].values.forEach(queue::addLast)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for ((char, child) in transitions[node]) {
                var fallback = failure[node]
                while (fallback != 0 && char !in transitions[fallback]) fallback = failure[fallback]
                failure[child] = transitions[fallback][char]?.takeIf { it != child } ?: 0
                nodeOutputs[child].addAll(nodeOutputs[failure[child]])
                queue.addLast(child)
            }
        }
        outputs = Array(nodeOutputs.size) { nodeOutputs[it].toIntArray() }
    }

    /** Version de la base : invalide le cache persistant quand les signatures changent. */
    val version: String = signatures.joinToString("\n") { "${it.tracker.id}:${it.codeSignature.pattern}:${it.tracker.categories}" }
        .hashCode().toString(16)

    fun trackersById(ids: List<Int>): List<Tracker> = ids.mapNotNull { id -> trackerIndexById[id]?.let(trackers::get) }

    fun newScan() = Scan()

    /** Accumule les traqueurs trouvés dans les noms de classes d'une app (tous ses DEX). */
    inner class Scan {
        private val found = BooleanArray(trackers.size)

        fun accept(className: String) {
            // Classes internes : `Foo$Bar` → `Foo.Bar`, comme le `.` des regex Exodus les accepte.
            var node = 0
            for (raw in className) {
                val char = if (raw == '$') '.' else raw
                while (node != 0 && char !in transitions[node]) node = failure[node]
                node = transitions[node][char] ?: 0
                for (trackerIndex in outputs[node]) found[trackerIndex] = true
            }
            for ((regex, trackerIndex) in regexAlternatives) {
                if (!found[trackerIndex] && regex.containsMatchIn(className)) found[trackerIndex] = true
            }
        }

        fun result(): List<Tracker> = trackers.filterIndexed { index, _ -> found[index] }
    }

    private companion object {
        /** Tout sauf `.` : les signatures qui en contiennent sont de vraies regex. */
        const val REGEX_METACHARACTERS = "\\[](){}*+?^$"

        /** Alternatives de premier niveau : un `|` dans un groupe, une classe ou échappé n'en sépare pas. */
        fun splitTopLevel(pattern: String): List<String> {
            val parts = mutableListOf<String>()
            var depth = 0
            var inClass = false
            var start = 0
            var i = 0
            while (i < pattern.length) {
                when (pattern[i]) {
                    '\\' -> i++
                    '[' -> inClass = true
                    ']' -> inClass = false
                    '(' -> if (!inClass) depth++
                    ')' -> if (!inClass) depth--
                    '|' -> if (!inClass && depth == 0) {
                        parts += pattern.substring(start, i)
                        start = i + 1
                    }
                }
                i++
            }
            parts += pattern.substring(start)
            return parts
        }
    }
}
