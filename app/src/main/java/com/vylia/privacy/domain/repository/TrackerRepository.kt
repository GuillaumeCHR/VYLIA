package com.vylia.privacy.domain.repository

import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerSignature

/** Base locale de signatures de traqueurs (fichier Exodus embarqué dans les assets). */
interface TrackerSignatureRepository {
    suspend fun getSignatures(): List<TrackerSignature>
}

/** Recherche des traqueurs dans le code compilé (DEX) d'une application. */
interface TrackerAnalyzer {
    /** `false` si la base de signatures est vide : on ne peut alors rien affirmer. */
    suspend fun isAvailable(): Boolean

    suspend fun findTrackers(app: InstalledApp): List<Tracker>
}
