package com.vylia.privacy.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vylia.privacy.MainActivity
import com.vylia.privacy.R
import com.vylia.privacy.data.automation.InstallNotifier
import com.vylia.privacy.data.automation.UpdateChange
import com.vylia.privacy.domain.learn.PermissionGlossary
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.ui.components.ManageAppGuard
import com.vylia.privacy.ui.components.badgeLabel

/**
 * Notification « nouvelle app » (score et badges d'alerte) et « mise à jour » (traqueurs et permissions ajoutés ou
 * retirés) ; un appui ouvre la fiche de l'app dans VYLIA.
 */
class InstallNotifications(private val context: Context) : InstallNotifier {

    override fun notify(app: AuditedApp, scoreIfAllGranted: Int) {
        // Capacités de surveillance : ni nom d'app ni badge en clair, la notification discrète la remplace.
        if (ManageAppGuard.needsWarning(app.result)) {
            SecurityNotice.post(context, app.app.packageName)
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel()
        val res = context.resources
        // Rien n'a encore été demandé : les badges qui supposent un refus de votre part seraient trompeurs.
        val warnings = app.result.badges.filter { it.polarity == "negative" && it.rule.granted != false }.map { res.badgeLabel(it) }
        val text = listOfNotNull(
            if (scoreIfAllGranted < app.score.value) res.getString(R.string.notif_install_if_all_granted, scoreIfAllGranted) else null,
            if (warnings.isEmpty()) res.getString(R.string.notif_install_no_warning)
            else res.getString(R.string.notif_install_warnings, warnings.joinToString(" · ")),
        ).joinToString("\n")
        post(manager, app, CHANNEL, ID, res.getString(R.string.notif_install_title, app.app.label, app.score.value), text, R.string.notif_install_public)
    }

    override fun notifyUpdate(app: AuditedApp, change: UpdateChange) {
        if (ManageAppGuard.needsWarning(app.result)) {
            SecurityNotice.post(context, app.app.packageName)
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createUpdateChannel()
        val res = context.resources
        val permissions = { keys: Set<String> -> keys.map { key -> PermissionGlossary.titleOf(key)?.let(res::getString) ?: key.removePrefix("@").substringAfterLast('.') }.sorted().joinToString(" · ") }
        // Le pire d'abord : ce qui s'ajoute, puis ce qui part.
        val text = listOfNotNull(
            change.trackersAdded.takeIf { it.isNotEmpty() }?.let { res.getQuantityString(R.plurals.notif_update_trackers_added, it.size, it.size, it.sorted().joinToString(", ")) },
            change.permissionsAdded.takeIf { it.isNotEmpty() }?.let { res.getString(R.string.notif_update_permissions_added, permissions(it)) },
            change.trackersRemoved.takeIf { it.isNotEmpty() }?.let { res.getQuantityString(R.plurals.notif_update_trackers_removed, it.size, it.size, it.sorted().joinToString(", ")) },
            change.permissionsRemoved.takeIf { it.isNotEmpty() }?.let { res.getString(R.string.notif_update_permissions_removed, permissions(it)) },
        ).joinToString("\n")
        val title = change.scoreBefore?.takeIf { it != change.scoreAfter }
            ?.let { res.getString(R.string.notif_update_title, app.app.label, it, change.scoreAfter) }
            ?: res.getString(R.string.notif_update_title_score, app.app.label, change.scoreAfter)
        post(manager, app, UPDATE_CHANNEL, UPDATE_ID, title, text, R.string.notif_update_public)
    }

    private fun post(manager: NotificationManagerCompat, app: AuditedApp, channel: String, id: Int, title: String, text: String, publicText: Int) {
        val res = context.resources
        val open = PendingIntent.getActivity(
            context,
            app.app.packageName.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PACKAGE, app.app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(res.getString(R.string.app_name))
                    .setContentText(res.getString(publicText))
                    .build(),
            )
            .build()
        try {
            manager.notify(app.app.packageName, id, notification)
        } catch (_: SecurityException) {
            // Autorisation de notifier retirée entre-temps.
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_installs), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.notif_channel_installs_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Canal à part : on peut couper les mises à jour sans couper les nouvelles apps. Moins pressant qu'une installation. */
    private fun createUpdateChannel() {
        val channel = NotificationChannel(UPDATE_CHANNEL, context.getString(R.string.notif_channel_updates), NotificationManager.IMPORTANCE_LOW)
            .apply { description = context.getString(R.string.notif_channel_updates_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL = "installs"
        const val ID = 1
        const val UPDATE_CHANNEL = "updates"
        /** Distinct de [ID] : une mise à jour ne remplace pas la notification d'installation de la même app. */
        const val UPDATE_ID = 4
    }
}
