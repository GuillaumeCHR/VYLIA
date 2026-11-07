package com.vylia.privacy.data.shield

import android.content.Context
import android.content.Intent

/**
 * Implémenté par l'`Application` qui embarque le module : donne au service VPN et à la tuile le contrôleur partagé
 * avec l'interface (même processus, même état).
 */
interface ShieldHost {
    val shieldController: ShieldController
}

/** L'écran d'accueil de l'app hôte, sans dépendre de sa classe d'activité. */
internal fun openAppIntent(context: Context): Intent =
    context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ?: Intent()
