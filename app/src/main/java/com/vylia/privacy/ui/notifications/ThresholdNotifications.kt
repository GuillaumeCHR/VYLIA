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
import com.vylia.privacy.data.alert.ThresholdNotifier
import com.vylia.privacy.domain.alert.AlertTarget
import com.vylia.privacy.domain.alert.DropCause
import com.vylia.privacy.domain.alert.ThresholdCrossing
import com.vylia.privacy.domain.alert.target
import com.vylia.privacy.ui.components.dropCauseText

/**
 * « Score de l'appareil sous votre seuil », sur un canal à part que l'on peut couper seul. Un appui ouvre l'endroit
 * où agir sur la cause : Gérer (l'app en tête), sa fiche, ou le Suivi. Écran verrouillé : seulement le score et le seuil, jamais le nom d'une app.
 */
class ThresholdNotifications(private val context: Context) : ThresholdNotifier {

    override fun notify(crossing: ThresholdCrossing) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel()
        val res = context.resources
        val title = res.getString(R.string.notif_threshold_title)
        // Cause liée à une app à capacités de surveillance : ni son nom ni le motif, seulement un point à vérifier.
        val text = if (crossing.cause.sensitive) res.getString(R.string.notif_threshold_sensitive, crossing.score, crossing.threshold)
        else res.getString(R.string.notif_threshold_text, crossing.score, crossing.threshold, res.dropCauseText(crossing.cause))
        val open = PendingIntent.getActivity(
            context,
            REQUEST,
            destination(crossing.cause).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(res.getString(R.string.notif_threshold_public, crossing.score, crossing.threshold))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        try {
            manager.notify(ID, notification)
        } catch (_: SecurityException) {
            // Autorisation de notifier retirée entre-temps.
        }
    }

    /** Gérer › Apps ou Autorisations (l'app en tête), sa fiche, ou Analyser › Suivi : voir [DropCause.target]. */
    private fun destination(cause: DropCause): Intent {
        val intent = Intent(context, MainActivity::class.java)
        return when (cause.target) {
            AlertTarget.MANAGE_APPS -> intent.putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_MANAGE)
                .putExtra(MainActivity.EXTRA_OPEN_SECTION, MainActivity.SECTION_APPS)
                .putExtra(MainActivity.EXTRA_PIN_PACKAGE, cause.pinnedPackage)
            AlertTarget.MANAGE_PERMISSIONS -> intent.putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_MANAGE)
                .putExtra(MainActivity.EXTRA_OPEN_SECTION, MainActivity.SECTION_PERMISSIONS)
                .putExtra(MainActivity.EXTRA_PIN_PACKAGE, cause.pinnedPackage)
            AlertTarget.APP_DETAIL -> intent.putExtra(MainActivity.EXTRA_OPEN_PACKAGE, cause.packageName)
            AlertTarget.TRACKING -> intent.putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TRACKING)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_threshold), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.notif_channel_threshold_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL = "threshold"
        const val ID = 2
        const val REQUEST = 2
    }
}
