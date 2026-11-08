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
 * Détection d'un logiciel de surveillance : la personne qui l'a installé peut avoir le téléphone en main, déverrouillé
 * ou non. La notification reste donc neutre partout (« VYLIA a détecté un point à vérifier »), sans le nom de l'app ni
 * un terme explicite, et son canal s'appelle « Points à vérifier ». Un appui ouvre la fiche de l'app, qui dit quoi
 * faire avant de la désinstaller.
 */
class StalkerwareNotifications(private val context: Context) : StalkerwareNotifier {

    override fun notify(app: AuditedApp) {
        if (app.result.knownStalkerware == null) return
        SecurityNotice.post(context, app.app.packageName)
    }

}

/** Notification discrète d'un point de sécurité à vérifier, partagée par l'alerte et la notification d'installation. */
object SecurityNotice {
    const val CHANNEL = "stalkerware"
    private const val ID = 3

    fun post(context: Context, packageName: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel(context)
        val res = context.resources
        val title = res.getString(R.string.notif_security_title)
        val text = res.getString(R.string.notif_security_text)
        val open = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        try {
            // Une seule notification à la fois : son étiquette ne révèle pas le paquet.
            manager.notify(ID, notification)
        } catch (_: SecurityException) {
            // Autorisation de notifier retirée entre-temps.
        }
    }

    /** Le canal existant est renommé : son ancien nom (« Logiciel espion détecté ») était explicite. */
    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_security), NotificationManager.IMPORTANCE_HIGH)
            .apply {
                description = context.getString(R.string.notif_channel_security_description)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
