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
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.ui.components.badgeLabel

/** Notification « nouvelle app » : score et badges d'alerte ; un appui ouvre sa fiche dans VYLIA. */
class InstallNotifications(private val context: Context) : InstallNotifier {

    override fun notify(app: AuditedApp, scoreIfAllGranted: Int) {
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
        val open = PendingIntent.getActivity(
            context,
            app.app.packageName.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PACKAGE, app.app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.notif_install_title, app.app.label, app.score.value))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            manager.notify(app.app.packageName, ID, notification)
        } catch (_: SecurityException) {
            // Autorisation de notifier retirée entre-temps.
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_installs), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.notif_channel_installs_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL = "installs"
        const val ID = 1
    }
}
