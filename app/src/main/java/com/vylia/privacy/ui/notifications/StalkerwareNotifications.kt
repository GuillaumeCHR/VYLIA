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
import com.vylia.privacy.data.alert.StalkerwareNotifier
import com.vylia.privacy.domain.model.AuditedApp

/**
 * « Stalkerware connu détecté » : un appui ouvre la fiche de l'app, qui dit quoi faire avant de la désinstaller.
 * La personne qui a installé l'app peut avoir le téléphone en main : écran verrouillé, une version neutre, sans le
 * mot stalkerware ni le nom de l'app.
 */
class StalkerwareNotifications(private val context: Context) : StalkerwareNotifier {

    override fun notify(app: AuditedApp) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel()
        val res = context.resources
        val name = app.result.knownStalkerware ?: return
        val text = res.getString(R.string.notif_stalkerware_text, app.app.label, name)
        val open = PendingIntent.getActivity(
            context,
            app.app.packageName.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PACKAGE, app.app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.app_name))
            .setContentText(res.getString(R.string.notif_stalkerware_public))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.notif_stalkerware_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        try {
            manager.notify(app.app.packageName, ID, notification)
        } catch (_: SecurityException) {
            // Autorisation de notifier retirée entre-temps.
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_stalkerware), NotificationManager.IMPORTANCE_HIGH)
            .apply {
                description = context.getString(R.string.notif_channel_stalkerware_description)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL = "stalkerware"
        const val ID = 3
    }
}
