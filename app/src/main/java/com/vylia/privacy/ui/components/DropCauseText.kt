package com.vylia.privacy.ui.components

import android.content.res.Resources
import com.vylia.privacy.R
import com.vylia.privacy.domain.alert.DropCause
import com.vylia.privacy.domain.learn.PermissionGlossary

/** La cause d'une baisse du score de l'appareil, en une phrase (notification de seuil, marqueurs du Suivi). */
fun Resources.dropCauseText(cause: DropCause): String {
    val label = cause.label.orEmpty()
    val score = cause.appScore ?: 0
    return when (cause.type) {
        DropCause.Type.INSTALLED -> getString(R.string.cause_installed, label, score)
        DropCause.Type.REMOVED -> getString(R.string.cause_removed, label, score)
        DropCause.Type.PERMISSION_GRANTED -> getString(
            R.string.cause_permission,
            label,
            cause.detail?.let { permission -> PermissionGlossary.titleOf(permission)?.let(::getString) ?: permission }.orEmpty(),
            score,
        )
        DropCause.Type.TRACKER_ADDED -> getString(R.string.cause_trackers, label, cause.detail.orEmpty(), score)
        DropCause.Type.SECURITY_PATTERN -> getString(R.string.cause_security, label, securityLabel(cause.detail.orEmpty()), score)
        DropCause.Type.RULES_UPDATED -> getString(R.string.cause_rules)
        DropCause.Type.SHIELD_OFF -> getString(R.string.cause_shield_off)
        DropCause.Type.DATABASES_UPDATED -> getString(R.string.cause_databases, cause.detail.orEmpty())
        DropCause.Type.SPREAD -> getString(R.string.cause_spread)
    }
}
