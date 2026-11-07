package com.vylia.privacy.ui.help

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vylia.privacy.R
import com.vylia.privacy.ui.understand.HelpContent
import com.vylia.privacy.ui.understand.UnderstandTab

/**
 * Aide d'un écran, ouverte par le « ? » de la barre du haut : une introduction propre à l'écran, puis les lexiques
 * qui servent à le lire. La fiche d'une app explique son score elle-même et n'en a pas besoin.
 */
enum class HelpTopic(@param:StringRes val title: Int, @param:StringRes val intro: Int, internal val tabs: List<UnderstandTab>) {
    SCAN(R.string.help_scan_title, R.string.help_scan_intro, listOf(UnderstandTab.SCORE, UnderstandTab.BADGES)),
    ANALYZE(
        R.string.help_analyze_title, R.string.help_analyze_intro,
        listOf(UnderstandTab.TRACKERS, UnderstandTab.PERMISSIONS, UnderstandTab.SCORE),
    ),
    REDUCE(R.string.help_reduce_title, R.string.help_reduce_intro, listOf(UnderstandTab.PERMISSIONS, UnderstandTab.SCORE)),
    REPLACE(
        R.string.help_replace_title, R.string.help_replace_intro,
        listOf(UnderstandTab.PERMISSIONS, UnderstandTab.TRACKERS),
    ),
    CELEBRATE(R.string.help_celebrate_title, R.string.help_celebrate_intro, listOf(UnderstandTab.BADGES)),
    /** Paramètres › Règles de score : la grille appliquée, expliquée. */
    SCORING(R.string.help_scoring_title, R.string.help_scoring_intro, listOf(UnderstandTab.SCORE)),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(topic: HelpTopic, onDismiss: () -> Unit) {
    // Déplié d'emblée : les lexiques sont longs et défilent dans la feuille.
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        HelpContent(
            title = stringResource(topic.title),
            intro = stringResource(topic.intro),
            tabs = topic.tabs,
            modifier = Modifier.fillMaxHeight(),
        )
    }
}
