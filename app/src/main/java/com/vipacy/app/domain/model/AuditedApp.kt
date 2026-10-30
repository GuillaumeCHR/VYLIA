package com.vipacy.app.domain.model

/** Application accompagnée de son score de confidentialité calculé. */
data class AuditedApp(
    val app: InstalledApp,
    val score: PrivacyScore,
    val trackerAnalysis: TrackerAnalysis = TrackerAnalysis.NotAnalyzed,
)
