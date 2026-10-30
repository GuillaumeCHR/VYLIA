package com.vylia.privacy.di

import android.content.Context
import android.os.Environment
import com.vylia.privacy.data.AppScanner
import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.trackers.AssetTrackerSignatureRepository
import com.vylia.privacy.data.trackers.DexTrackerAnalyzer
import com.vylia.privacy.data.trackers.TrackerUpdater
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.scoring.RuleMatrix
import com.vylia.privacy.domain.usecase.AnalyzeTrackersUseCase
import com.vylia.privacy.domain.usecase.AuditAppsUseCase
import java.io.File
import java.io.InputStreamReader

/** Injection de dépendances manuelle : suffisante pour la V1, sans framework supplémentaire. */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val trackerUpdater = TrackerUpdater(context, settingsRepository)

    private val ruleMatrix: RuleMatrix = try {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val externalFile = File(downloadDir, "privacy_rules_matrix_advanced.json")
        val altExternalFile = File(downloadDir, "privacy_rules_matrix.json")
        
        val jsonString = if (externalFile.exists() && externalFile.canRead()) {
            externalFile.readText()
        } else if (altExternalFile.exists() && altExternalFile.canRead()) {
            altExternalFile.readText()
        } else {
            context.assets.open("privacy_rules_matrix_advanced.json").bufferedReader().use { it.readText() }
        }
        RuleMatrix.fromJson(jsonString)
    } catch (e: Exception) {
        RuleMatrix(emptyList()) // Fallback empty rules
    }

    private val scorer = PrivacyScorer(ruleMatrix)
    private val appScanner = AppScanner(context.packageManager, context.contentResolver)
    
    val trackerSignatureRepository = AssetTrackerSignatureRepository(context)
    private val trackerAnalyzer = DexTrackerAnalyzer(trackerSignatureRepository)

    val auditAppsUseCase = AuditAppsUseCase(appScanner, scorer)
    val analyzeTrackersUseCase = AnalyzeTrackersUseCase(trackerAnalyzer, scorer)
}