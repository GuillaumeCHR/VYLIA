package com.vylia.privacy.domain.web

import com.vylia.privacy.domain.security.Untrusted
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * Un service qui existe en site web installable comme WebAPK : son adresse, ses domaines, sa catégorie dans la grille
 * de score et les apps natives qu'il remplace. [example] : proposé en exemple dans Gérer › WebAPK.
 */
@Serializable
data class WebService(
    val id: String,
    val name: String,
    val url: String,
    val hosts: List<String>,
    val category: String,
    val packages: List<String> = emptyList(),
    val example: Boolean = false,
)

/**
 * La liste des services web (`assets/web_services.json`), lue comme une donnée externe : une entrée dont l'adresse
 * n'est pas en https, dont un domaine ou un paquet est mal formé, est écartée.
 */
class WebServices(
    val services: List<WebService>,
    val noWeb: Set<String> = emptySet(),
    /** Catégories de la grille où une app détectée (hors liste) peut être proposée ; la liste, elle, l'est toujours. */
    val webCategories: Set<String> = emptySet(),
) {

    /** Le service d'un site, d'après l'adresse de départ d'un WebAPK ; un sous-domaine compte (www.lemonde.fr). */
    fun forUrl(url: String?): WebService? {
        val host = hostOf(url) ?: return null
        return services.firstOrNull { service -> service.hosts.any { host == it || host.endsWith(".$it") } }
    }

    /** Le service qui remplace une app native. */
    fun forPackage(packageName: String): WebService? = services.firstOrNull { packageName in it.packages }

    companion object {
        val EMPTY = WebServices(emptyList())

        /** Méta-donnée qui porte l'adresse de départ d'un WebAPK (Chrome et navigateurs Chromium qui en produisent). */
        const val WEBAPK_START_URL = "org.chromium.webapk.shell_apk.startUrl"
        private const val MAX_SERVICES = 500
        private val HOST = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")
        private val ID = Regex("[a-z0-9_-]{1,40}")
        private val CATEGORY = Regex("[A-Z][A-Z0-9_]{0,63}")

        @Serializable
        private data class File(
            val services: List<WebService> = emptyList(),
            /** Apps sans site utilisable sur téléphone (messageries) : jamais proposées. */
            @SerialName("no_web") val noWeb: List<String> = emptyList(),
            @SerialName("web_categories") val webCategories: List<String> = emptyList(),
        )

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(content: String): WebServices {
            val file = json.decodeFromString<File>(content)
            return WebServices(
                file.services.take(MAX_SERVICES).mapNotNull(::checked),
                file.noWeb.take(MAX_SERVICES).filter(Untrusted::isPackage).toSet(),
                file.webCategories.take(MAX_SERVICES).filter { CATEGORY.matches(it) }.toSet(),
            )
        }

        /** Domaine d'une adresse https, en minuscules ; `null` pour toute autre adresse. */
        fun hostOf(url: String?): String? = try {
            URI(url ?: return null).takeIf { it.scheme == "https" }?.host?.lowercase()?.takeIf { HOST.matches(it) }
        } catch (_: Exception) {
            null
        }

        private fun checked(s: WebService): WebService? = when {
            !ID.matches(s.id) || !CATEGORY.matches(s.category) -> null
            hostOf(s.url) == null -> null
            s.hosts.isEmpty() || !s.hosts.all { HOST.matches(it) } -> null
            !s.packages.all(Untrusted::isPackage) -> null
            else -> s.copy(name = Untrusted.text(s.name, 60))
        }
    }
}
