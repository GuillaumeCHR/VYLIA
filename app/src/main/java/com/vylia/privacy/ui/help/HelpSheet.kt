package com.vylia.privacy.ui.help

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vylia.privacy.ui.understand.HelpContent

/**
 * L'Aide, ouverte par le « ? » de la barre du haut : la même partout (mode d'emploi des
 * écrans, calcul du score, badges, lexiques des traqueurs et des permissions), pour ne jamais chercher une explication
 * qui ne serait visible que depuis un autre écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(onDismiss: () -> Unit) {
    // Déplié d'emblée : les lexiques sont longs et défilent dans la feuille.
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        HelpContent(modifier = Modifier.fillMaxHeight())
    }
}
