package com.vipacy.app.domain.model

/** Traqueur référencé dans la base Exodus Privacy. */
data class Tracker(
    val id: Int,
    val name: String,
    val website: String?,
    val categories: List<String>,
)

/**
 * Signature de code d'un traqueur : expression régulière (format Exodus) recherchée dans
 * les noms de paquets Java/Kotlin présents dans les fichiers DEX d'une application.
 */
data class TrackerSignature(
    val tracker: Tracker,
    val codeSignature: Regex,
)

/** État de l'analyse DEX d'une application. */
sealed interface TrackerAnalysis {
    /** Analyse pas encore faite, ou base de signatures indisponible. */
    data object NotAnalyzed : TrackerAnalysis
    data class Analyzed(val trackers: List<Tracker>) : TrackerAnalysis
    /** APK illisible ou DEX corrompu. */
    data object Failed : TrackerAnalysis
}
