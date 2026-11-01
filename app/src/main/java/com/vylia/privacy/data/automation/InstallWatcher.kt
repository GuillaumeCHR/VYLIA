package com.vylia.privacy.data.automation

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.preferences.SettingsRepository.InstallCursor
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.usecase.FullAuditUseCase
import com.vylia.privacy.domain.usecase.scoreIfAllGranted

/**
 * Repère les apps installées depuis le dernier contrôle, sans lister tout le téléphone : Android tient un numéro de
 * séquence des paquets modifiés ([PackageManager.getChangedPackages]). Les mises à jour sont écartées (date de
 * première installation antérieure au dernier contrôle).
 */
class InstallWatcher(
    private val context: Context,
    private val settings: SettingsRepository,
    private val fullAudit: FullAuditUseCase,
    private val notifier: InstallNotifier,
    private val scorer: () -> PrivacyScorer,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val packageManager = context.packageManager

    /** Point de départ : seules les installations qui suivent l'activation seront signalées. */
    fun start() {
        settings.installCursor = InstallCursor(bootCount(), sequenceSince(0)?.first ?: 0, clock())
    }

    /** Contrôle périodique : audite et notifie chaque nouvelle app utilisateur. */
    suspend fun check() {
        val detected = InstallDetector.detect(
            cursor = settings.installCursor,
            bootCount = bootCount(),
            now = clock(),
            changedSince = ::sequenceSince,
            lookup = ::lookup,
        )
        if (detected.packages.isNotEmpty()) {
            val scorer = scorer()
            fullAudit(detected.packages.toSet()).forEach { notifier.notify(it, scorer.scoreIfAllGranted(it).score) }
        }
        settings.installCursor = detected.cursor
    }

    private fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun sequenceSince(sequence: Int): Pair<Int, List<String>>? =
        packageManager.getChangedPackages(sequence)?.let { it.sequenceNumber to it.packageNames }

    private fun lookup(packageName: String): InstallDetector.Installed? = try {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, 0)
        val appInfo = info.applicationInfo
        val system = appInfo != null && appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        InstallDetector.Installed(info.firstInstallTime, system || packageName == context.packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null // Installée puis aussitôt supprimée.
    }
}

/** Logique de [InstallWatcher], sans Android. */
object InstallDetector {
    /** Paquet présent : date de première installation, et s'il faut l'ignorer (app système ou VYLIA). */
    data class Installed(val firstInstallTime: Long, val ignored: Boolean)

    data class Result(val cursor: InstallCursor, val packages: List<String>)

    /**
     * @param changedSince paquets modifiés depuis un numéro de séquence, avec le nouveau numéro ; `null` si rien n'a
     * changé.
     */
    fun detect(
        cursor: InstallCursor?,
        bootCount: Int,
        now: Long,
        changedSince: (Int) -> Pair<Int, List<String>>?,
        lookup: (String) -> Installed?,
    ): Result {
        if (cursor == null) return Result(InstallCursor(bootCount, changedSince(0)?.first ?: 0, now), emptyList())
        // Après un redémarrage, la séquence repart de zéro : on relit tout depuis le démarrage.
        val since = if (cursor.bootCount == bootCount) cursor.sequence else 0
        val changed = changedSince(since) ?: return Result(InstallCursor(bootCount, since, now), emptyList())
        val installed = changed.second.distinct().filter { pkg ->
            val app = lookup(pkg) ?: return@filter false
            !app.ignored && app.firstInstallTime > cursor.checkedAt
        }
        return Result(InstallCursor(bootCount, changed.first, now), installed)
    }
}

/** Notifie le résultat d'audit d'une app tout juste installée. */
fun interface InstallNotifier {
    /** @param scoreIfAllGranted son score si vous lui accordiez tout ce qu'elle déclare. */
    fun notify(app: AuditedApp, scoreIfAllGranted: Int)
}
