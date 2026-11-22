package com.vylia.privacy.domain.act

import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.security.Untrusted
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Version allégée d'une app répandue (« Lite », même éditeur, même service), mieux notée qu'elle. Sa note vient de son
 * rapport Exodus, calculée comme dans l'annuaire du site : toutes ses permissions déclarées sont comptées, donc sur le
 * téléphone elle est souvent meilleure encore.
 */
@Serializable
data class LighterVariant(
    /** Paquets de l'app d'origine (versions régionales comprises). */
    val replaces: List<String>,
    @SerialName("package") val packageName: String,
    val name: String,
    val grade: String,
    val score: Int,
    val trackers: Int,
    @SerialName("exodus_report") val exodusReport: Long,
    @SerialName("exodus_date") val exodusDate: String,
    /** Ce qu'on perd en passant à la version allégée, par langue (« en », « fr ») ; l'anglais sert de repli. */
    val loss: Map<String, String>,
) {
    /** Page du rapport Exodus, en français ou en anglais. */
    fun reportUrl(language: String): String =
        "https://reports.exodus-privacy.eu.org/${if (language == "fr") "fr" else "en"}/reports/$exodusReport/"
}

/** Une app installée et sa version allégée, mieux notée et pas encore installée. */
data class LighterSuggestion(val app: AuditedApp, val variant: LighterVariant)

/**
 * Les versions allégées (`assets/lighter_variants.json`), lues comme une donnée externe : une entrée mal formée (paquet,
 * note, date, numéro de rapport) est écartée.
 */
class LighterVariants(val variants: List<LighterVariant>) {

    /**
     * Les apps installées qui ont une version allégée mieux notée qu'elles sur ce téléphone, et pas encore installée ;
     * les plus mal notées d'abord.
     */
    fun suggestions(apps: List<AuditedApp>): List<LighterSuggestion> {
        val installed = apps.mapTo(HashSet()) { it.app.packageName }
        return apps.mapNotNull { app ->
            variants.firstOrNull { app.app.packageName in it.replaces }
                ?.takeIf { it.packageName !in installed && it.score > app.result.score }
                ?.let { LighterSuggestion(app, it) }
        }.sortedBy { it.app.result.score }
    }

    companion object {
        val EMPTY = LighterVariants(emptyList())
        private const val MAX_VARIANTS = 500
        private val GRADE = Regex("[A-E]")
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class File(val variants: List<LighterVariant> = emptyList())

        fun parse(content: String): LighterVariants =
            LighterVariants(json.decodeFromString<File>(content.removePrefix("\uFEFF")).variants.take(MAX_VARIANTS).mapNotNull(::checked))

        private fun checked(v: LighterVariant): LighterVariant? = when {
            v.replaces.isEmpty() || !v.replaces.all(Untrusted::isPackage) || !Untrusted.isPackage(v.packageName) -> null
            v.packageName in v.replaces -> null
            !GRADE.matches(v.grade) || v.score !in 0..100 || v.trackers !in 0..500 || v.exodusReport <= 0 -> null
            runCatching { LocalDate.parse(v.exodusDate) }.isFailure -> null
            v.loss.isEmpty() -> null
            else -> v.copy(
                name = Untrusted.text(v.name, 60),
                loss = v.loss.mapValues { Untrusted.text(it.value, 300) },
                replaces = v.replaces.take(20),
            )
        }
    }
}
