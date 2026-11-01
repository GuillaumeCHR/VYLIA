package com.vylia.privacy.domain.learn

import androidx.annotation.StringRes
import com.vylia.privacy.R

/**
 * Nom traduit d'une catégorie d'app et pourquoi, en une phrase, elle justifie ou tolère certaines permissions
 * (voir `allowed` / `tolerated` dans vylia_rules_v3.json). Une catégorie ajoutée par import n'y figure pas :
 * on affiche alors le `display_name` et la `description` des règles.
 */
object CategoryRationale {

    private class Texts(@param:StringRes val name: Int, @param:StringRes val rationale: Int)

    private val texts = mapOf(
        "DEFAULT" to Texts(R.string.category_default, R.string.category_default_rationale),
        "NAVIGATION" to Texts(R.string.category_navigation, R.string.category_navigation_rationale),
        "TRANSPORT" to Texts(R.string.category_transport, R.string.category_transport_rationale),
        "VEHICLE" to Texts(R.string.category_vehicle, R.string.category_vehicle_rationale),
        "WEATHER" to Texts(R.string.category_weather, R.string.category_weather_rationale),
        "UTILITY" to Texts(R.string.category_utility, R.string.category_utility_rationale),
        "MESSAGING" to Texts(R.string.category_messaging, R.string.category_messaging_rationale),
        "EMAIL" to Texts(R.string.category_email, R.string.category_email_rationale),
        "SOCIAL" to Texts(R.string.category_social, R.string.category_social_rationale),
        "MEDIA_PLAYER" to Texts(R.string.category_media_player, R.string.category_media_player_rationale),
        "NEWS_READING" to Texts(R.string.category_news_reading, R.string.category_news_reading_rationale),
        "ECOMMERCE" to Texts(R.string.category_ecommerce, R.string.category_ecommerce_rationale),
        "FINANCE_ACCOUNTS" to Texts(R.string.category_finance_accounts, R.string.category_finance_accounts_rationale),
        "HEALTH_ADMIN" to Texts(R.string.category_health_admin, R.string.category_health_admin_rationale),
        "WEARABLE_COMPANION" to Texts(R.string.category_wearable_companion, R.string.category_wearable_companion_rationale),
        "FITNESS" to Texts(R.string.category_fitness, R.string.category_fitness_rationale),
        "SECURITY_AUTH" to Texts(R.string.category_security_auth, R.string.category_security_auth_rationale),
        "BROWSER" to Texts(R.string.category_browser, R.string.category_browser_rationale),
        "AI_ASSISTANT" to Texts(R.string.category_ai_assistant, R.string.category_ai_assistant_rationale),
        "PRODUCTIVITY_CLOUD" to Texts(R.string.category_productivity_cloud, R.string.category_productivity_cloud_rationale),
        "PHOTO_VIDEO" to Texts(R.string.category_photo_video, R.string.category_photo_video_rationale),
        "TRAVEL_LEISURE" to Texts(R.string.category_travel_leisure, R.string.category_travel_leisure_rationale),
        "GAME" to Texts(R.string.category_game, R.string.category_game_rationale),
        "ACCESSIBILITY" to Texts(R.string.category_accessibility, R.string.category_accessibility_rationale),
        "HEALTH_TRACKING" to Texts(R.string.category_health_tracking, R.string.category_health_tracking_rationale),
        "DATING" to Texts(R.string.category_dating, R.string.category_dating_rationale),
        "KEYBOARD" to Texts(R.string.category_keyboard, R.string.category_keyboard_rationale),
        "EDUCATION_FAMILY" to Texts(R.string.category_education_family, R.string.category_education_family_rationale),
    )

    @StringRes
    fun nameOf(categoryId: String): Int? = texts[categoryId]?.name

    @StringRes
    fun of(categoryId: String): Int? = texts[categoryId]?.rationale
}
