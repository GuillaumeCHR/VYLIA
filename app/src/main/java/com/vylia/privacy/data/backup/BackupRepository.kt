package com.vylia.privacy.data.backup

import com.vylia.privacy.BuildConfig
import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.rules.RuleMatrixRepository
import com.vylia.privacy.data.trackers.TrackerScanCache
import com.vylia.privacy.domain.backup.BackupFile
import com.vylia.privacy.domain.backup.BackupMerge
import com.vylia.privacy.domain.backup.BackupSanitizer
import com.vylia.privacy.domain.backup.BackupSettings
import com.vylia.privacy.domain.backup.RestoreError
import com.vylia.privacy.domain.backup.RestoreException
import com.vylia.privacy.domain.backup.RestoreReport
import com.vylia.privacy.domain.history.HistoryRecorder
import com.vylia.privacy.domain.rewards.BadgesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Paramètres › Gestion des données : exporter, importer (sans rien écraser) et supprimer les données de VYLIA. */
class BackupRepository(
    private val settings: SettingsRepository,
    private val history: HistoryRecorder,
    private val rewards: BadgesManager,
    private val rules: RuleMatrixRepository,
    private val trackerCache: TrackerScanCache,
    private val language: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun export(out: OutputStream) {
        val file = BackupFile(
            exportedAt = clock(),
            app = "VYLIA ${BuildConfig.VERSION_NAME}",
            history = history.history.value,
            rewards = rewards.state.value,
            settings = BackupSettings(
                advancedMode = settings.advancedMode.value,
                installAlerts = settings.installAlerts.value,
                backgroundScan = settings.backgroundScan.value.name,
                language = language(),
            ),
            importedRules = withContext(Dispatchers.IO) { rules.importedContent() },
        )
        withContext(Dispatchers.IO) {
            out.write(json.encodeToString(file).toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    /** Ajoute le contenu d'une sauvegarde. Lève [RestoreException] si le fichier est refusé, sans rien avoir écrit. */
    suspend fun restore(input: InputStream): RestoreReport {
        val raw = withContext(Dispatchers.IO) { parse(read(input)) }
        val (file, rejected) = BackupSanitizer.clean(raw)
        val now = clock()
        val h = history.merge { current -> BackupMerge.history(current, file.history, now).let { it.value to it } }
        val r = rewards.merge { current -> BackupMerge.rewards(current, file.rewards, now).let { it.value to it } }
        return RestoreReport(
            snapshotsAdded = h.added,
            eventsAdded = h.addedEvents,
            rewardsAdded = r.added,
            alreadyThere = h.alreadyThere + r.alreadyThere,
            invalid = rejected + h.invalid + r.invalid,
            // Une sauvegarde ne change jamais la façon dont les apps sont jugées : les règles s'importent à part.
            rulesIgnored = file.importedRules != null,
        )
    }

    /** Efface le Suivi, les récompenses et le cache d'analyse (qui contient la liste de vos apps). Les réglages restent. */
    suspend fun deleteAll() {
        history.clear()
        rewards.clear()
        trackerCache.clear()
        settings.clearScanTimestamps()
    }

    private fun read(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        try {
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > BackupFile.MAX_BYTES) throw RestoreException(RestoreError.TOO_LARGE)
            }
        } catch (_: IOException) {
            throw RestoreException(RestoreError.UNREADABLE)
        }
        return out.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

        /** Lecture d'une sauvegarde : refusée si ce n'en est pas une, ou si elle vient d'une version plus récente. */
        fun parse(text: String): BackupFile {
            val file = try {
                json.decodeFromString<BackupFile>(text)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            return when {
                file == null || file.format != BackupFile.FORMAT -> throw RestoreException(RestoreError.NOT_A_BACKUP)
                file.version > BackupFile.VERSION -> throw RestoreException(RestoreError.NEWER_VERSION)
                else -> file
            }
        }
    }
}
