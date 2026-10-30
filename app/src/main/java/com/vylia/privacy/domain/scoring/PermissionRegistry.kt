package com.vylia.privacy.domain.scoring

import android.Manifest

enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Famille de permissions donnant accès aux mêmes données : une famille ne pénalise qu'une fois,
 * au niveau de son membre le plus intrusif détecté.
 */
enum class PermissionFamily {
    BACKGROUND_LOCATION, ACCESSIBILITY, CAMERA, MICROPHONE, SMS, LOCATION, CONTACTS, CALENDAR, STORAGE
}

data class PermissionRisk(
    val permission: String,
    val family: PermissionFamily,
    val title: String,
    val description: String,
    val basePenalty: Int,
    val riskLevel: RiskLevel
)

object PermissionRegistry {
    private val registry = listOf(
        // --- CRITICAL (-25 à -30 points) ---
        PermissionRisk(
            permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            family = PermissionFamily.BACKGROUND_LOCATION,
            title = "Géolocalisation en arrière-plan",
            description = "Suit vos déplacements 24h/24 même quand l'application est fermée.",
            basePenalty = 30,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            family = PermissionFamily.ACCESSIBILITY,
            title = "Service d'Accessibilité",
            description = "Peut lire tout votre écran et simuler des clics à votre place.",
            basePenalty = 30,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.CAMERA,
            family = PermissionFamily.CAMERA,
            title = "Appareil photo",
            description = "Accès à l'appareil photo pouvant capturer des images ou vidéos à tout moment.",
            basePenalty = 25,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.RECORD_AUDIO,
            family = PermissionFamily.MICROPHONE,
            title = "Microphone",
            description = "Accès au microphone permettant l'enregistrement et la captation sonore.",
            basePenalty = 25,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.READ_SMS,
            family = PermissionFamily.SMS,
            title = "Lecture des SMS",
            description = "Accès à vos messages privés et codes de validation bancaires.",
            basePenalty = 25,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.RECEIVE_SMS,
            family = PermissionFamily.SMS,
            title = "Réception des SMS",
            description = "Intercepte vos SMS entrants, y compris les codes de validation.",
            basePenalty = 25,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.SEND_SMS,
            family = PermissionFamily.SMS,
            title = "Envoi de SMS",
            description = "Peut envoyer des SMS en votre nom, potentiellement surtaxés.",
            basePenalty = 25,
            riskLevel = RiskLevel.CRITICAL
        ),

        // --- HIGH (-15 à -20 points) ---
        PermissionRisk(
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            family = PermissionFamily.LOCATION,
            title = "Géolocalisation précise",
            description = "Accès GPS précis à quelques mètres près.",
            basePenalty = 20,
            riskLevel = RiskLevel.HIGH
        ),
        PermissionRisk(
            permission = Manifest.permission.READ_CONTACTS,
            family = PermissionFamily.CONTACTS,
            title = "Carnet d'adresses",
            description = "Lecture de la liste de vos contacts et de leurs coordonnées.",
            basePenalty = 20,
            riskLevel = RiskLevel.HIGH
        ),
        PermissionRisk(
            permission = Manifest.permission.WRITE_CONTACTS,
            family = PermissionFamily.CONTACTS,
            title = "Modification des contacts",
            description = "Peut ajouter, modifier ou supprimer vos contacts.",
            basePenalty = 20,
            riskLevel = RiskLevel.HIGH
        ),
        PermissionRisk(
            permission = Manifest.permission.GET_ACCOUNTS,
            family = PermissionFamily.CONTACTS,
            title = "Comptes de l'appareil",
            description = "Liste les comptes (e-mail, réseaux sociaux…) enregistrés sur le téléphone.",
            basePenalty = 20,
            riskLevel = RiskLevel.HIGH
        ),
        PermissionRisk(
            permission = Manifest.permission.READ_CALENDAR,
            family = PermissionFamily.CALENDAR,
            title = "Agenda / Calendrier",
            description = "Accès à vos rendez-vous personnels et professionnels.",
            basePenalty = 15,
            riskLevel = RiskLevel.HIGH
        ),
        PermissionRisk(
            permission = Manifest.permission.WRITE_CALENDAR,
            family = PermissionFamily.CALENDAR,
            title = "Modification de l'agenda",
            description = "Peut créer, modifier ou supprimer vos événements.",
            basePenalty = 15,
            riskLevel = RiskLevel.HIGH
        ),

        // --- MEDIUM (-10 points) ---
        PermissionRisk(
            permission = Manifest.permission.ACCESS_COARSE_LOCATION,
            family = PermissionFamily.LOCATION,
            title = "Géolocalisation approximative",
            description = "Position à l'échelle du quartier ou de la ville.",
            basePenalty = 10,
            riskLevel = RiskLevel.MEDIUM
        ),
        storage(Manifest.permission.READ_EXTERNAL_STORAGE, "Lecture du stockage", "Accès en lecture à vos fichiers, photos et documents."),
        storage(Manifest.permission.WRITE_EXTERNAL_STORAGE, "Écriture du stockage", "Peut modifier ou supprimer vos fichiers partagés."),
        storage(Manifest.permission.MANAGE_EXTERNAL_STORAGE, "Accès à tous les fichiers", "Accès complet à l'ensemble du stockage partagé."),
        storage(Manifest.permission.READ_MEDIA_IMAGES, "Photos", "Accès à vos photos."),
        storage(Manifest.permission.READ_MEDIA_VIDEO, "Vidéos", "Accès à vos vidéos."),
        storage(Manifest.permission.READ_MEDIA_AUDIO, "Fichiers audio", "Accès à votre musique et à vos enregistrements."),
        storage(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, "Photos sélectionnées", "Accès aux photos et vidéos que vous choisissez."),
    )

    val risks: Map<String, PermissionRisk> = registry.associateBy { it.permission }

    fun strongestByFamily(permissions: Collection<String>): List<PermissionRisk> {
        val declared = permissions.toSet()
        return registry
            .filter { it.permission in declared }
            .groupBy { it.family }
            .values
            .map { family -> family.maxBy { it.basePenalty } }
            .sortedByDescending { it.basePenalty }
    }

    private fun storage(permission: String, title: String, description: String) = PermissionRisk(
        permission = permission,
        family = PermissionFamily.STORAGE,
        title = title,
        description = description,
        basePenalty = 10,
        riskLevel = RiskLevel.MEDIUM
    )
}
