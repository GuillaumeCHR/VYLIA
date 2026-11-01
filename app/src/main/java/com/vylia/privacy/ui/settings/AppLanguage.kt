package com.vylia.privacy.ui.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.vylia.privacy.R
import com.vylia.privacy.data.preferences.SettingsRepository
import java.util.Locale

/**
 * Langues de l'application. L'anglais est la langue par défaut : il est appliqué au premier lancement, quelle que
 * soit la langue du téléphone, puis le choix fait dans Paramètres (ou les réglages Android 13+) est conservé.
 */
enum class AppLanguage(
    val tag: String,
    val flag: String,
    @param:StringRes val label: Int,
) {
    English("en", "🇬🇧", R.string.settings_language_en),
    French("fr", "🇫🇷", R.string.settings_language_fr),
    ;

    fun select() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    companion object {
        /** Langue affichée : le choix explicite, sinon celle du téléphone si elle est proposée, sinon l'anglais. */
        fun current(): AppLanguage {
            val chosen = AppCompatDelegate.getApplicationLocales()
            val tag = if (chosen.isEmpty) Locale.getDefault().language else chosen[0]?.language
            return entries.firstOrNull { it.tag == tag } ?: English
        }

        /** Premier lancement sans choix de langue : l'app démarre en anglais. Une seule fois. */
        fun applyDefault(settings: SettingsRepository) {
            if (settings.languageInitialized) return
            if (AppCompatDelegate.getApplicationLocales().isEmpty) English.select()
            settings.setLanguageInitialized()
        }
    }
}
