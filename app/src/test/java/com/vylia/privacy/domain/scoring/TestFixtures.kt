package com.vylia.privacy.domain.scoring

import com.vylia.privacy.data.rules.RuleMatrixRepository
import com.vylia.privacy.data.trackers.ExodusTrackerParser
import java.io.File

/** Fichiers réellement embarqués dans l'app (les tests JVM s'exécutent depuis le module `app`). */
object TestFixtures {
    val rules: Rules by lazy { RuleMatrixRepository.parse(File("src/main/assets/vylia_rules_v2_1.json").readText()) }

    /** Catégories Exodus par nom de traqueur, depuis le snapshot embarqué. */
    val exodusCategories: Map<String, Set<String>> by lazy {
        ExodusTrackerParser().parse(File("src/main/assets/exodus_trackers.json").readText())
            .associate { it.tracker.name to it.tracker.categories.toSet() }
    }

    fun trackers(vararg names: String): List<Tracker> = names.map { name ->
        Tracker(name, exodusCategories[name] ?: error("Traqueur absent du snapshot Exodus : $name"))
    }
}
