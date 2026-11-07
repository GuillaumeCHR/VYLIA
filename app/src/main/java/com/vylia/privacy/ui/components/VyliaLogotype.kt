package com.vylia.privacy.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vylia.privacy.R
import com.vylia.privacy.ui.theme.Slate300

/**
 * Logotype « VYLIA » : le même composant sert de titre à l'intro et à l'en-tête de l'écran Scan, pour que
 * l'intro le fasse glisser exactement à sa place (seule la taille change, l'espacement est proportionnel).
 */
@Composable
fun VyliaLogotype(modifier: Modifier = Modifier, fontSize: TextUnit = HeaderLogotypeSize) {
    Text(
        text = stringResource(R.string.app_name),
        modifier = modifier,
        fontSize = fontSize,
        fontFamily = Unbounded,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.06.em,
        color = Slate300,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * Taille du logotype dans la barre d'en-tête. 20 sp : à 360 dp de large, la pastille du bouclier garde son libellé le
 * plus long (« Contourné ») à côté des trois boutons de 48 dp.
 */
val HeaderLogotypeSize = 20.sp

/** Unbounded (SIL Open Font License 1.1), réservée au nom VYLIA : large, géométrique, reconnaissable. */
val Unbounded = FontFamily(
    Font(R.font.unbounded, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)
