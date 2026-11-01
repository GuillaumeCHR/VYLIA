package com.vylia.privacy.domain.learn

import androidx.annotation.StringRes
import com.vylia.privacy.R

/** Catégorie de traqueurs Exodus expliquée simplement. */
data class TrackerCategoryInfo(
    /** Clé Exodus ("Advertisement"…), ou [TrackerGlossary.UNCATEGORIZED]. */
    val category: String,
    @param:StringRes val name: Int,
    @param:StringRes val riskLevel: Int,
    @param:StringRes val risk: Int,
    /** Traqueurs répandus de cette catégorie, détectables par VYLIA (noms exacts de la base Exodus, avec signature). */
    val examples: List<String>,
)

/** Lexique des catégories de traqueurs, de la plus à la moins intrusive. */
object TrackerGlossary {

    /** Clé interne des traqueurs sans catégorie Exodus (jamais affichée telle quelle). */
    const val UNCATEGORIZED = "Sans catégorie"

    val entries: List<TrackerCategoryInfo> = listOf(
        TrackerCategoryInfo(
            "Advertisement", R.string.tracker_cat_advertisement, R.string.risk_level_high, R.string.tracker_cat_advertisement_risk,
            listOf("Google AdMob", "Facebook Ads", "AppLovin (MAX and SparkLabs)", "Unity3d Ads", "Amazon Advertisement", "Criteo", "Vungle"),
        ),
        TrackerCategoryInfo(
            "Profiling", R.string.tracker_cat_profiling, R.string.risk_level_high, R.string.tracker_cat_profiling_risk,
            listOf("Amplitude", "Segment", "Batch", "CleverTap", "Kochava", "Salesforce Marketing Cloud", "Quantcast"),
        ),
        TrackerCategoryInfo(
            "Location", R.string.tracker_cat_location, R.string.risk_level_high, R.string.tracker_cat_location_risk,
            listOf("Pilgrim by Foursquare", "Cuebiq", "Radar", "AltBeacon", "Huq Sourcekit", "Facebook Places", "Placed"),
        ),
        TrackerCategoryInfo(
            "Identification", R.string.tracker_cat_identification, R.string.risk_level_significant, R.string.tracker_cat_identification_risk,
            listOf("Facebook Login", "IAB Open Measurement", "Snapchat Login Kit", "VKontakte SDK", "Jumio"),
        ),
        TrackerCategoryInfo(
            "Analytics", R.string.tracker_cat_analytics, R.string.risk_level_moderate, R.string.tracker_cat_analytics_risk,
            listOf("Google Firebase Analytics", "Google Analytics", "Facebook Analytics", "AppsFlyer", "Adjust", "mParticle", "Google Tag Manager"),
        ),
        TrackerCategoryInfo(
            "Crash reporting", R.string.tracker_cat_crash, R.string.risk_level_low, R.string.tracker_cat_crash_risk,
            listOf("Google CrashLytics", "Sentry", "Bugsnag", "Instabug"),
        ),
        TrackerCategoryInfo(
            UNCATEGORIZED, R.string.tracker_cat_uncategorized, R.string.risk_level_unknown, R.string.tracker_cat_uncategorized_risk,
            emptyList(),
        ),
    )

    private val byCategory = entries.associateBy { it.category }

    fun find(category: String): TrackerCategoryInfo? = byCategory[category]

    /** Nom traduit d'une catégorie Exodus ; `null` pour une catégorie inconnue (afficher alors la clé Exodus). */
    @StringRes
    fun displayName(category: String): Int? = byCategory[category]?.name
}
