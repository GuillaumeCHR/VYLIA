package com.vylia.privacy.domain.model

import com.vylia.privacy.domain.scoring.ScoreResult

/** Application accompagnée de son score de confidentialité calculé. */
data class AuditedApp(
    val app: InstalledApp,
    val score: PrivacyScore,
    val trackerAnalysis: TrackerAnalysis = TrackerAnalysis.NotAnalyzed,
    /** Détail du calcul (règle v2) : constats permissions / traqueurs, note, confiance. */
    val result: ScoreResult,
)
