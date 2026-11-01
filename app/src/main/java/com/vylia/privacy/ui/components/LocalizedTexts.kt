package com.vylia.privacy.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.vylia.privacy.domain.learn.CategoryRationale
import com.vylia.privacy.domain.learn.TrackerGlossary
import com.vylia.privacy.domain.scoring.Rules
import java.util.Locale

/** Langue d'affichage en cours (celle choisie dans l'app, appliquée par AppCompat à la configuration). */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH

/**
 * Nom traduit d'une catégorie d'app ; pour une catégorie ajoutée par import de règles, son `display_name`,
 * à défaut son identifiant.
 */
fun Resources.categoryName(categoryId: String, rules: Rules?): String =
    CategoryRationale.nameOf(categoryId)?.let(::getString)
        ?: rules?.categories?.get(categoryId)?.displayName
        ?: categoryId

/** Nom traduit d'une catégorie de traqueurs Exodus, ou la clé Exodus si elle est inconnue du lexique. */
fun Resources.trackerCategoryName(category: String): String =
    TrackerGlossary.displayName(category)?.let(::getString) ?: category

/** Nombre sans décimale inutile, au format de la langue (0.4 / 0,4 ; 12.8 ; 0). `null` : « ? ». */
fun Double?.formatted(locale: Locale, decimals: Int = 2): String {
    if (this == null) return "?"
    val text = String.format(locale, "%.${decimals}f", this)
    val separator = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return if (separator in text) text.trimEnd('0').trimEnd(separator) else text
}
