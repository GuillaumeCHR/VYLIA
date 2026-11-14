package com.vylia.privacy.data.automation

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import com.vylia.privacy.data.alert.StalkerwareAlerts
import com.vylia.privacy.data.preferences.SettingsRepository
import com.vylia.privacy.data.preferences.SettingsRepository.InstallCursor
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.usecase.FullAuditUseCase
import com.vylia.privacy.domain.usecase.scoreIfAllGranted

/**
 * Repère les apps installées ou mises à jour depuis le dernier contrôle, sans lister tout le téléphone : Android tient
 * un numéro de séquence des paquets modifiés ([PackageManager.getChangedPackages]). Une mise à jour n'est signalée que
 * si elle change des traqueurs ou des permissions notées, par rapport à la dernière analyse de l'app ([UpdateBaselines]).
 */
class InstallWatcher(
    private val context: Context,
    private val settings: SettingsRepository,
    private val fullAudit: FullAuditUseCase,
    private val notifier: InstallNotifier,
    private val baselines: UpdateBaselines,
    private val stalkerware: StalkerwareAlerts,
    private val scorer: () -> PrivacyScorer,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val packageManager = context.packageManager

    /** Point de départ : seules les installations qui suivent l'activation seront signalées. */
    fun start() {
        settings.installCursor = InstallCursor(bootCount(), sequenceSince(0)?.first ?: 0, clock())
    }

    /** Contrôle périodique : audite et notifie chaque nouvelle app utilisateur, et les mises à jour qui changent quelque chose. */
    suspend fun check() {
        val detected = InstallDetector.detect(
            cursor = settings.installCursor,
            bootCount = bootCount(),
            now = clock(),
            changedSince = ::sequenceSince,
            lookup = ::lookup,
        )
        // Une mise à jour n'est analysée que si son alerte est active : c'est une analyse DEX par app mise à jour.
        val updated = if (settings.updateAlerts.value) detected.updated else emptyList()
        if (detected.packages.isNotEmpty() || updated.isNotEmpty()) {
            val scorer = scorer()
            val audited = fullAudit((detected.packages + updated).toSet())
            val installed = audited.filter { it.app.packageName in detected.packages }
            // Le contrôle sert aux alertes d'installation et stalkerware, chacune selon son propre réglage.
            stalkerware.check(installed, full = false, notify = true)
            if (settings.installAlerts.value) installed.forEach { notifier.notify(it, scorer.scoreIfAllGranted(it).score) }
            // Les nouvelles apps y prennent leur état de départ : leur première mise à jour pourra être comparée.
            baselines.update(audited)
                .filter { (app, _) -> app.app.packageName in updated }
                .forEach { (app, change) -> notifier.notifyUpdate(app, change) }
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
        InstallDetector.Installed(info.firstInstallTime, system || packageName == context.packageName, info.lastUpdateTime)
    } catch (_: PackageManager.NameNotFoundException) {
        null // Installée puis aussitôt supprimée.
    }
}

/** Logique de [InstallWatcher], sans Android. */
object InstallDetector {
    /**
     * Paquet présent : date de première installation, s'il faut l'ignorer (app système ou VYLIA), date de dernière
     * mise à jour (égale à la première installation si elle n'a jamais été mise à jour).
     */
    data class Installed(val firstInstallTime: Long, val ignored: Boolean, val lastUpdateTime: Long = firstInstallTime)

    /** [packages] : apps installées depuis le dernier contrôle ; [updated] : apps déjà là, mises à jour depuis. */
    data class Result(val cursor: InstallCursor, val packages: List<String>, val updated: List<String> = emptyList())

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
        val installed = mutableListOf<String>()
        val updated = mutableListOf<String>()
        changed.second.distinct().forEach { pkg ->
            val app = lookup(pkg)?.takeUnless { it.ignored } ?: return@forEach
            when {
                app.firstInstallTime > cursor.checkedAt -> installed += pkg
                app.lastUpdateTime > cursor.checkedAt -> updated += pkg
            }
        }
        return Result(InstallCursor(bootCount, changed.first, now), installed, updated)
    }
}

/** Notifie le résultat d'audit d'une app tout juste installée, ou ce qu'une mise à jour a changé. */
interface InstallNotifier {
    /** @param scoreIfAllGranted son score si vous lui accordiez tout ce qu'elle déclare. */
    fun notify(app: AuditedApp, scoreIfAllGranted: Int)

    fun notifyUpdate(app: AuditedApp, change: UpdateChange)
}
