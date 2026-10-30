package com.vylia.privacy.domain.learn

/**
 * Pourquoi, en une phrase, une catégorie d'app justifie ou tolère certaines permissions
 * (voir `allowed` / `tolerated` dans vylia_rules_v2.json).
 */
object CategoryRationale {

    private val rationales = mapOf(
        "DEFAULT" to "Catégorie inconnue : aucune permission n'est présumée nécessaire, chacune est jugée inattendue.",
        "NAVIGATION" to "Le guidage repose sur votre position. Micro (commandes vocales), contacts (adresses) et Bluetooth (voiture) sont tolérés.",
        "TRANSPORT" to "Horaires et itinéraires autour de vous. La caméra (scanner un billet) et l'agenda (ajouter un trajet) sont tolérés.",
        "VEHICLE" to "Localiser la voiture et s'y connecter en Bluetooth. Contacts, agenda et micro sont tolérés pour les fonctions embarquées.",
        "WEATHER" to "La ville suffit pour la météo locale. La position précise ou en arrière-plan n'est que tolérée, pour les alertes.",
        "UTILITY" to "Lampe, calculatrice ou boussole n'ont besoin de rien de sensible. La caméra est tolérée pour la lampe, même si Android s'en passe depuis la version 6.",
        "MESSAGING" to "Écrire à vos contacts, envoyer photos, vidéos et messages vocaux, passer des appels.",
        "EMAIL" to "Retrouver les destinataires et joindre des fichiers ou des photos. Agenda et micro sont tolérés.",
        "SOCIAL" to "Publier photos, vidéos et sons. Retrouver des amis et localiser une publication ne sont que tolérés.",
        "MEDIA_PLAYER" to "Lire votre musique et vos vidéos. Bluetooth (enceintes) et micro (reconnaissance musicale) sont tolérés.",
        "NEWS_READING" to "Lire des articles ne demande aucune permission sensible. La ville est tolérée pour l'info locale.",
        "ECOMMERCE" to "Scanner un produit et photographier une annonce. La position, pour trouver un magasin ou une annonce proche, est tolérée.",
        "FINANCE_ACCOUNTS" to "Scanner un chèque ou une pièce d'identité. Lire vos SMS est inutile : Android transmet les codes de validation sans y donner accès.",
        "HEALTH_ADMIN" to "Envoyer une ordonnance ou un justificatif en photo. Agenda et position sont tolérés pour les rendez-vous.",
        "WEARABLE_COMPANION" to "Relier et synchroniser la montre : Bluetooth, capteurs, activité, position. SMS et appels sont tolérés pour les afficher au poignet.",
        "FITNESS" to "Suivre vos parcours et vos efforts : position, activité, capteurs, Bluetooth pour une ceinture cardio.",
        "SECURITY_AUTH" to "Scanner un QR code pour ajouter un compte. L'accessibilité est tolérée pour le remplissage automatique des mots de passe.",
        "BROWSER" to "Un navigateur ne demande rien pour lui-même : il relaie les demandes des sites web (caméra, micro, position), d'où une simple tolérance.",
        "AI_ASSISTANT" to "Parler à l'assistant et lui montrer des images. Agenda, contacts et position ne sont que tolérés.",
        "PRODUCTIVITY_CLOUD" to "Ouvrir, numériser et synchroniser vos fichiers et photos.",
        "PHOTO_VIDEO" to "Prendre et gérer vos photos et vidéos, se connecter à une caméra en Bluetooth ou Wi-Fi.",
        "TRAVEL_LEISURE" to "Trouver des lieux et des événements autour de vous. Agenda et photos sont tolérés pour les réservations.",
        "GAME" to "Un jeu n'a besoin d'aucune permission sensible. Micro et Bluetooth sont tolérés pour le chat vocal et les manettes.",
        "ACCESSIBILITY" to "Lire l'écran et agir à votre place, c'est précisément leur rôle : accessibilité et micro sont justifiés.",
        "EDUCATION_FAMILY" to "Envoyer des devoirs ou des documents en photo. L'agenda est toléré pour l'emploi du temps.",
    )

    /** Justification de la catégorie, ou la description des règles pour une catégorie ajoutée par import. */
    fun of(categoryId: String, rulesDescription: String?): String? = rationales[categoryId] ?: rulesDescription
}
