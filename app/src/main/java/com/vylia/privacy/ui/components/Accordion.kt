package com.vylia.privacy.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vylia.privacy.R

/**
 * État déplié ou replié des accordéons, gardé le temps de la session (le processus de l'app) : il survit aux
 * changements d'écran et de rotation, pas à la fermeture de l'app. Tout est replié au départ.
 */
object AccordionSession {
    val expanded: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
}

/**
 * Accordéon : un en-tête toujours visible, cliquable sur toute sa surface (48 dp au moins), annoncé par TalkBack comme
 * un bouton avec son état (« replié », « déplié ») ; le contenu ne s'affiche que déplié. L'animation est coupée quand
 * l'utilisateur a réduit les animations d'Android.
 *
 * [header] reçoit la place de l'en-tête à côté de la flèche ; [actions] (bouton « Tout mettre à jour »…) restent
 * utilisables carte repliée, hors de la zone qui déplie.
 */
@Composable
fun Accordion(
    key: String,
    modifier: Modifier = Modifier,
    header: @Composable RowScope.() -> Unit,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expanded = AccordionSession.expanded[key] == true
    val animate = animationsEnabled()
    val state = stringResource(if (expanded) R.string.accordion_expanded else R.string.accordion_collapsed)
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { AccordionSession.expanded[key] = !expanded }
                .semantics { stateDescription = state }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            header()
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
            )
        }
        actions?.invoke(this)
        AnimatedVisibility(
            visible = expanded,
            enter = if (animate) expandVertically(tween(200)) else expandVertically(tween(0)),
            exit = if (animate) shrinkVertically(tween(200)) else shrinkVertically(tween(0)),
        ) {
            Column { content() }
        }
    }
}

/** Faux si l'utilisateur a désactivé les animations (échelle de durée à 0, réglage d'accessibilité ou développeur). */
@Composable
private fun animationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
