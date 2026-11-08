package com.vylia.privacy.domain.scoring

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Apps de référence partagées par les tests de :scoring, de :directory-cli et de l'app : mêmes entrées, même note
 * attendue. Les permissions sont au format des rapports Exodus (noms complets), toutes considérées comme accordées,
 * comme dans l'annuaire.
 */
object ReferenceApps {

    @Serializable
    data class Expected(val score: Int, val grade: String, val badges: List<String>)

    @Serializable
    data class RefTracker(val name: String, val categories: List<String>)

    @Serializable
    data class RefApp(
        @kotlinx.serialization.SerialName("package") val packageName: String,
        @kotlinx.serialization.SerialName("target_sdk") val targetSdk: Int,
        val permissions: List<String>,
        val trackers: List<RefTracker>,
        val expected: Expected? = null,
    )

    @Serializable
    private data class FileContent(val apps: List<RefApp>)

    private val json = Json { ignoreUnknownKeys = true }

    val apps: List<RefApp> by lazy {
        val text = ReferenceApps::class.java.getResource("/reference_apps.json")!!.readText()
        json.decodeFromString<FileContent>(text).apps
    }

    /** Règles en vigueur de l'app, lues depuis la racine du dépôt (ou le module, selon le répertoire de travail). */
    val rules: Rules by lazy {
        val file = listOf("app/src/main/assets/vylia_rules_v3.json", "../app/src/main/assets/vylia_rules_v3.json", "src/main/assets/vylia_rules_v3.json")
            .map(::File).first { it.exists() }
        RulesLoader.parse(file.readText())
    }

    /** Nom court d'une permission Android, comme dans l'app ; les permissions propres à une app gardent leur nom. */
    fun shortName(permission: String): String = permission.removePrefix("android.permission.")

    /** Conversion directe, sans passer par l'outil de l'annuaire : la référence côté moteur. */
    fun scan(app: RefApp): AppScan {
        val permissions = app.permissions.map(::shortName).toSet()
        return AppScan(
            packageName = app.packageName,
            targetSdk = app.targetSdk,
            appInfoCategory = "CATEGORY_UNDEFINED",
            declaredPermissions = permissions,
            grantedPermissions = permissions,
            trackers = app.trackers.map { Tracker(it.name, it.categories.toSet()) },
        )
    }

    fun summary(result: ScoreResult): Expected = Expected(result.score, result.grade, result.badges.map { it.id })
}
