package com.vylia.privacy.domain.learn

/** Catégorie de traqueurs Exodus expliquée simplement. */
data class TrackerCategoryInfo(
    /** Clé Exodus ("Advertisement"…), ou [TrackerGlossary.UNCATEGORIZED]. */
    val category: String,
    val name: String,
    val riskLevel: String,
    val risk: String,
    /** Traqueurs répandus de cette catégorie, détectables par VYLIA (noms exacts de la base Exodus, avec signature). */
    val examples: List<String>,
)

/** Lexique des catégories de traqueurs, de la plus à la moins intrusive. */
object TrackerGlossary {

    const val UNCATEGORIZED = "Sans catégorie"

    val entries: List<TrackerCategoryInfo> = listOf(
        TrackerCategoryInfo(
            "Advertisement", "Publicité", "Risque élevé",
            "Récupère l'identifiant publicitaire de votre téléphone, vos centres d'intérêt et vos réactions aux " +
                "publicités, puis les met aux enchères auprès de nombreux annonceurs à chaque affichage. " +
                "Vos données circulent ainsi chez des dizaines d'entreprises que vous ne connaissez pas.",
            listOf("Google AdMob", "Facebook Ads", "AppLovin (MAX and SparkLabs)", "Unity3d Ads", "Amazon Advertisement", "Criteo", "Vungle"),
        ),
        TrackerCategoryInfo(
            "Profiling", "Profilage", "Risque élevé",
            "Assemble un portrait détaillé de vous : habitudes, achats, centres d'intérêt, parfois santé ou opinions " +
                "déduites de votre comportement. Ces profils sont croisés d'une app à l'autre et alimentent les courtiers en données.",
            listOf("Amplitude", "Segment", "Batch", "CleverTap", "Kochava", "Salesforce Marketing Cloud", "Quantcast"),
        ),
        TrackerCategoryInfo(
            "Location", "Localisation", "Risque élevé",
            "Enregistre vos positions, souvent en continu, y compris grâce aux balises Bluetooth des magasins. " +
                "Elles révèlent domicile, travail et lieux sensibles (santé, culte, rencontres) et sont presque impossibles à anonymiser.",
            listOf("Pilgrim by Foursquare", "Cuebiq", "Radar", "AltBeacon", "Huq Sourcekit", "Facebook Places", "Placed"),
        ),
        TrackerCategoryInfo(
            "Identification", "Identification", "Risque marqué",
            "Relie votre activité à une identité : compte Facebook ou autre, identifiant de l'appareil. " +
                "Il devient possible de vous reconnaître d'une app à l'autre et de rattacher votre usage à votre vrai nom.",
            listOf("Facebook Login", "IAB Open Measurement", "Snapchat Login Kit", "VKontakte SDK", "Jumio"),
        ),
        TrackerCategoryInfo(
            "Analytics", "Mesure d'audience", "Risque modéré",
            "Mesure ce que vous faites dans l'app (écrans vus, clics, achats, durée) et l'envoie à un tiers, souvent " +
                "Google ou Facebook, même si vous n'y avez pas de compte. Sert aussi à attribuer votre installation à une campagne publicitaire.",
            listOf("Google Firebase Analytics", "Google Analytics", "Facebook Analytics", "AppsFlyer", "Adjust", "mParticle", "Google Tag Manager"),
        ),
        TrackerCategoryInfo(
            "Crash reporting", "Rapports de plantage", "Risque faible",
            "Envoie un rapport technique quand l'app plante : modèle du téléphone, version d'Android, étapes avant l'erreur. " +
                "Utile à la qualité de l'app et peu intrusif, mais il décrit tout de même votre appareil.",
            listOf("Google CrashLytics", "Sentry", "Bugsnag", "Instabug"),
        ),
        TrackerCategoryInfo(
            UNCATEGORIZED, "Sans catégorie connue", "Risque indéterminé",
            "La base Exodus ne documente pas son usage. Par prudence, il est compté comme un traqueur de mesure d'audience.",
            emptyList(),
        ),
    )

    private val byCategory = entries.associateBy { it.category }

    fun find(category: String): TrackerCategoryInfo? = byCategory[category]

    /** Nom français d'une catégorie Exodus. */
    fun displayName(category: String): String = byCategory[category]?.name ?: category
}
