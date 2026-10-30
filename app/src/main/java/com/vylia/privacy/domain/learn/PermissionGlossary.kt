package com.vylia.privacy.domain.learn

/** Comment Android accorde une permission : détermine si vous avez eu votre mot à dire. */
enum class GrantMode(val label: String, val explanation: String) {
    RUNTIME(
        "Fenêtre de demande",
        "Android vous la demande dans une fenêtre la première fois que l'app en a besoin. " +
            "Vous pouvez la retirer à tout moment : Paramètres > Applications > l'app > Autorisations.",
    ),
    BACKGROUND(
        "Réglage « Toujours autoriser »",
        "Elle n'est jamais accordée d'un simple clic : il faut choisir « Toujours autoriser » dans les " +
            "autorisations de l'app. Repassez-la sur « Seulement si l'app est en cours d'utilisation » si vous doutez.",
    ),
    WITH_MEDIA(
        "Liée à l'accès aux photos",
        "Pas de fenêtre distincte : elle accompagne l'accès à vos photos et vidéos. " +
            "Retirer l'accès aux photos de l'app la retire aussi.",
    ),
    SPECIAL(
        "Accès spécial",
        "C'est un accès puissant qu'il faut activer soi-même dans un écran dédié des paramètres " +
            "(Paramètres > Applications > Accès spéciaux). Une app qui vous y envoie doit avoir une très bonne raison.",
    ),
    INSTALL(
        "Accordée d'office",
        "Elle est accordée automatiquement à l'installation, sans aucune fenêtre de demande, " +
            "et ne peut pas être retirée sans désinstaller l'app.",
    ),
}

enum class PermissionTheme(val title: String) {
    LOCATION("Localisation"),
    MESSAGES_CALLS("Messages et appels"),
    CONTACTS("Contacts et comptes"),
    MIC_CAMERA("Micro et caméra"),
    CALENDAR("Agenda"),
    HEALTH("Santé et activité physique"),
    FILES("Photos, vidéos et fichiers"),
    NEARBY("Appareils à proximité"),
    APPS("Vos applications"),
    CONTROL("Contrôle du téléphone"),
}

/** Explication grand public d'une permission notée par les règles de score. */
data class PermissionInfo(
    /** Nom court, sans "android.permission.", tel qu'utilisé par les règles. */
    val permission: String,
    val title: String,
    val theme: PermissionTheme,
    val allows: String,
    val whySensitive: String,
    val grant: GrantMode,
)

/** Lexique des permissions sensibles : une entrée pour chaque permission notée dans vylia_rules_v2.json. */
object PermissionGlossary {

    val entries: List<PermissionInfo> = listOf(
        // --- Localisation ---
        PermissionInfo(
            "ACCESS_BACKGROUND_LOCATION", "Position en arrière-plan", PermissionTheme.LOCATION,
            "Connaître votre position même quand l'app est fermée ou que vous ne l'utilisez pas.",
            "C'est un suivi 24 h/24 : domicile, travail, lieux de soin, de culte, de rencontre, horaires… " +
                "Quelques jours de positions suffisent à vous identifier et à reconstituer votre vie. " +
                "Ces données sont très recherchées par les courtiers en données.",
            GrantMode.BACKGROUND,
        ),
        PermissionInfo(
            "ACCESS_FINE_LOCATION", "Position précise (GPS)", PermissionTheme.LOCATION,
            "Connaître votre position à quelques mètres près pendant que vous utilisez l'app.",
            "Indispensable pour un GPS ou une app de course, mais révèle où vous êtes exactement. " +
                "Pour une app qui n'a besoin que de la ville (météo, boutique), la position approximative suffit.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "ACCESS_COARSE_LOCATION", "Position approximative", PermissionTheme.LOCATION,
            "Connaître votre position à l'échelle du quartier ou de la ville.",
            "Moins intrusive que le GPS, elle indique tout de même vos déplacements réguliers " +
                "et sert souvent à cibler la publicité locale.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "ACCESS_MEDIA_LOCATION", "Lieux de prise des photos", PermissionTheme.LOCATION,
            "Lire les coordonnées GPS enregistrées dans vos photos et vidéos.",
            "Vos photos racontent où vous étiez et quand : domicile, vacances, lieux fréquentés. " +
                "Une app peut ainsi retracer vos déplacements passés sans jamais demander votre position.",
            GrantMode.WITH_MEDIA,
        ),

        // --- Messages et appels ---
        PermissionInfo(
            "READ_SMS", "Lecture des SMS", PermissionTheme.MESSAGES_CALLS,
            "Lire tous vos SMS, anciens comme nouveaux.",
            "Vos conversations privées, mais aussi les codes de validation bancaires et de connexion " +
                "reçus par SMS : de quoi prendre le contrôle de comptes. Google en réserve l'usage aux apps de SMS par défaut.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "RECEIVE_SMS", "Réception des SMS", PermissionTheme.MESSAGES_CALLS,
            "Être prévenue et lire chaque SMS au moment où il arrive.",
            "Permet d'intercepter les codes à usage unique dès leur réception, technique classique des logiciels malveillants bancaires.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "SEND_SMS", "Envoi de SMS", PermissionTheme.MESSAGES_CALLS,
            "Envoyer des SMS en votre nom, sans passer par votre app de messagerie.",
            "Risque de SMS surtaxés facturés sur votre forfait, ou de messages envoyés à vos contacts à votre insu.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "RECEIVE_MMS", "Réception des MMS", PermissionTheme.MESSAGES_CALLS,
            "Recevoir et lire les MMS (messages avec photo ou vidéo).",
            "Donne accès au contenu de messages privés, photos comprises.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "READ_CALL_LOG", "Journal d'appels", PermissionTheme.MESSAGES_CALLS,
            "Lire la liste de vos appels : numéros, dates, durées.",
            "Qui vous appelez, quand et combien de temps dessine votre cercle intime, médical ou professionnel. " +
                "Google en réserve l'usage aux apps de téléphone par défaut.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "WRITE_CALL_LOG", "Modification du journal d'appels", PermissionTheme.MESSAGES_CALLS,
            "Ajouter, modifier ou effacer des entrées de votre historique d'appels.",
            "Une app pourrait effacer la trace d'appels qu'elle a passés ou falsifier votre historique.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "PROCESS_OUTGOING_CALLS", "Appels sortants", PermissionTheme.MESSAGES_CALLS,
            "Voir le numéro que vous composez et rediriger ou annuler l'appel (permission ancienne, retirée depuis Android 10).",
            "Révèle tous les numéros que vous appelez ; sa présence trahit souvent une app ancienne ou peu maintenue.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "CALL_PHONE", "Passer des appels", PermissionTheme.MESSAGES_CALLS,
            "Appeler un numéro directement, sans que vous confirmiez dans l'app Téléphone.",
            "Utile pour un bouton « appeler le service client », mais permet aussi des appels vers des numéros surtaxés.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "ANSWER_PHONE_CALLS", "Répondre aux appels", PermissionTheme.MESSAGES_CALLS,
            "Décrocher ou raccrocher un appel entrant à votre place.",
            "Légitime pour une montre ou une voiture connectée ; ailleurs, une app n'a aucune raison de gérer vos appels.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "READ_PHONE_STATE", "État et identité du téléphone", PermissionTheme.MESSAGES_CALLS,
            "Savoir si vous êtes en communication, connaître votre opérateur, votre réseau et des informations sur la carte SIM.",
            "Le nom rassurant cache des informations utiles pour vous reconnaître d'une app à l'autre. " +
                "Elle est très prisée des SDK publicitaires pour constituer une empreinte de l'appareil.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "READ_PHONE_NUMBERS", "Votre numéro de téléphone", PermissionTheme.MESSAGES_CALLS,
            "Lire le numéro de téléphone de vos cartes SIM.",
            "Votre numéro est un identifiant stable et personnel, qui permet de vous retrouver ailleurs " +
                "(messageries, fuites de données, démarchage).",
            GrantMode.RUNTIME,
        ),

        // --- Contacts et comptes ---
        PermissionInfo(
            "READ_CONTACTS", "Carnet d'adresses", PermissionTheme.CONTACTS,
            "Lire tous vos contacts : noms, numéros, e-mails, adresses, notes.",
            "Ce ne sont pas seulement vos données mais celles de vos proches, qui n'ont rien accepté. " +
                "Les carnets d'adresses sont souvent envoyés aux serveurs pour « trouver des amis » ou alimenter des profils.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "WRITE_CONTACTS", "Modification des contacts", PermissionTheme.CONTACTS,
            "Ajouter, modifier ou supprimer des contacts.",
            "Une app peut ajouter ses propres fiches (numéro de démarchage, support) ou altérer les vôtres.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "GET_ACCOUNTS", "Comptes du téléphone", PermissionTheme.CONTACTS,
            "Voir les comptes enregistrés sur le téléphone (Google, e-mail, réseaux sociaux).",
            "Depuis Android 8, l'app ne voit plus que ses propres comptes ou ceux que vous lui confiez, " +
                "mais sa demande reste un signal d'intérêt pour votre identité en ligne.",
            GrantMode.RUNTIME,
        ),

        // --- Micro et caméra ---
        PermissionInfo(
            "RECORD_AUDIO", "Microphone", PermissionTheme.MIC_CAMERA,
            "Enregistrer le son via le micro pendant que vous utilisez l'app.",
            "Conversations, voix, bruits de fond : le micro capte bien plus que votre commande vocale. " +
                "Android affiche un point vert quand il est utilisé ; méfiez-vous s'il apparaît sans raison.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "CAMERA", "Appareil photo", PermissionTheme.MIC_CAMERA,
            "Prendre des photos et des vidéos, ou scanner un code, pendant que vous utilisez l'app.",
            "Normal pour scanner un QR code ou un document. Moins pour une app qui n'a rien à photographier. " +
                "Un point vert signale son utilisation.",
            GrantMode.RUNTIME,
        ),

        // --- Agenda ---
        PermissionInfo(
            "READ_CALENDAR", "Lecture de l'agenda", PermissionTheme.CALENDAR,
            "Lire tous vos rendez-vous et ceux des agendas partagés.",
            "Vos rendez-vous médicaux, professionnels ou personnels, avec lieux et participants, " +
                "décrivent précisément votre vie et celle des autres invités.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "WRITE_CALENDAR", "Modification de l'agenda", PermissionTheme.CALENDAR,
            "Créer, modifier ou supprimer des événements.",
            "Pratique pour ajouter une réservation, mais une app peut aussi effacer ou modifier vos rendez-vous.",
            GrantMode.RUNTIME,
        ),

        // --- Santé et activité ---
        PermissionInfo(
            "BODY_SENSORS", "Capteurs corporels", PermissionTheme.HEALTH,
            "Lire les capteurs de santé : rythme cardiaque, oxygénation du sang, etc.",
            "Les données de santé sont parmi les plus sensibles qui soient (RGPD, article 9) : " +
                "elles peuvent révéler maladies, stress ou grossesse.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "BODY_SENSORS_BACKGROUND", "Capteurs corporels en arrière-plan", PermissionTheme.HEALTH,
            "Lire vos capteurs de santé même quand l'app n'est pas ouverte.",
            "Une surveillance de santé permanente, rarement nécessaire hors d'une app de montre ou de suivi médical.",
            GrantMode.BACKGROUND,
        ),
        PermissionInfo(
            "ACTIVITY_RECOGNITION", "Activité physique", PermissionTheme.HEALTH,
            "Détecter si vous marchez, courez, êtes en vélo ou en voiture, et compter vos pas.",
            "Révèle vos habitudes, vos trajets et votre mode de vie, même sans accès à la position.",
            GrantMode.RUNTIME,
        ),

        // --- Fichiers ---
        PermissionInfo(
            "READ_EXTERNAL_STORAGE", "Fichiers et médias (ancien accès)", PermissionTheme.FILES,
            "Lire les photos, vidéos, musiques et documents du stockage partagé.",
            "Ouvre l'accès à toute votre galerie et vos téléchargements. Depuis Android 13, elle est remplacée " +
                "par des accès plus fins : VYLIA ne la compte que pour les apps conçues pour des versions plus anciennes.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "MANAGE_EXTERNAL_STORAGE", "Accès à tous les fichiers", PermissionTheme.FILES,
            "Lire, modifier et supprimer tous les fichiers du stockage partagé, sans restriction.",
            "Documents d'identité, relevés, photos, sauvegardes : tout devient lisible. " +
                "Réservé en principe aux gestionnaires de fichiers et antivirus.",
            GrantMode.SPECIAL,
        ),
        PermissionInfo(
            "READ_MEDIA_IMAGES", "Photos", PermissionTheme.FILES,
            "Lire les photos de votre galerie.",
            "Visages, documents photographiés, captures d'écran, lieux : une galerie en dit beaucoup. " +
                "Android 14 permet de ne partager que certaines photos ; préférez cette option.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "READ_MEDIA_VIDEO", "Vidéos", PermissionTheme.FILES,
            "Lire les vidéos de votre galerie.",
            "Même enjeu que pour les photos : vos vidéos montrent des personnes et des lieux.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "READ_MEDIA_AUDIO", "Fichiers audio", PermissionTheme.FILES,
            "Lire votre musique, vos podcasts et vos enregistrements.",
            "Peu sensible pour la musique, davantage pour des mémos vocaux ou des enregistrements d'appels.",
            GrantMode.RUNTIME,
        ),

        // --- Appareils à proximité ---
        PermissionInfo(
            "BLUETOOTH_SCAN", "Recherche d'appareils Bluetooth", PermissionTheme.NEARBY,
            "Détecter les appareils Bluetooth autour de vous (écouteurs, montres, balises).",
            "Les balises Bluetooth des magasins et lieux publics permettent de savoir où vous êtes, " +
                "même sans accès à la position.",
            GrantMode.RUNTIME,
        ),
        PermissionInfo(
            "NEARBY_WIFI_DEVICES", "Appareils Wi-Fi à proximité", PermissionTheme.NEARBY,
            "Détecter et se connecter aux appareils Wi-Fi proches (imprimante, enceinte, caméra).",
            "La liste des réseaux et appareils voisins peut servir à déduire votre position.",
            GrantMode.RUNTIME,
        ),

        // --- Vos applications ---
        PermissionInfo(
            "QUERY_ALL_PACKAGES", "Liste de vos applications", PermissionTheme.APPS,
            "Voir la liste complète des applications installées sur votre téléphone.",
            "Votre liste d'apps en dit beaucoup sur vous : une app de santé, une banque, une app de rencontre, " +
                "religieuse ou politique révèlent des informations intimes. La combinaison des apps installées " +
                "sert aussi d'empreinte quasi unique pour vous pister.",
            GrantMode.INSTALL,
        ),
        PermissionInfo(
            "PACKAGE_USAGE_STATS", "Statistiques d'utilisation", PermissionTheme.APPS,
            "Savoir quelles apps vous utilisez, quand et combien de temps.",
            "Vos horaires de sommeil, vos habitudes, vos usages de nuit ou au travail se lisent dans ces statistiques.",
            GrantMode.SPECIAL,
        ),
        PermissionInfo(
            "REQUEST_INSTALL_PACKAGES", "Installation d'autres applications", PermissionTheme.APPS,
            "Proposer l'installation d'applications téléchargées en dehors du Play Store.",
            "Une porte d'entrée pour des apps non vérifiées par Google, voire malveillantes. " +
                "Légitime pour un navigateur ou un magasin alternatif (F-Droid), suspecte ailleurs.",
            GrantMode.SPECIAL,
        ),

        // --- Contrôle du téléphone ---
        PermissionInfo(
            "BIND_ACCESSIBILITY_SERVICE", "Service d'accessibilité", PermissionTheme.CONTROL,
            "Lire tout ce qui s'affiche à l'écran et agir à votre place : toucher, saisir du texte, naviguer.",
            "C'est l'accès le plus puissant d'Android, conçu pour aider les personnes handicapées. " +
                "Il permet de lire mots de passe et messages, et c'est l'outil favori des logiciels espions. " +
                "À n'accorder qu'à une app d'accessibilité de confiance.",
            GrantMode.SPECIAL,
        ),
        PermissionInfo(
            "BIND_NOTIFICATION_LISTENER_SERVICE", "Lecture des notifications", PermissionTheme.CONTROL,
            "Lire toutes vos notifications, de toutes les apps, et pouvoir les fermer.",
            "Aperçus de messages, e-mails, codes de validation, alertes bancaires : tout transite par les notifications.",
            GrantMode.SPECIAL,
        ),
        PermissionInfo(
            "BIND_DEVICE_ADMIN", "Administrateur de l'appareil", PermissionTheme.CONTROL,
            "Verrouiller le téléphone, imposer un code, effacer toutes les données, empêcher la désinstallation.",
            "Légitime pour un téléphone professionnel géré par votre employeur ou un antivol. " +
                "Ailleurs, c'est un contrôle excessif, parfois utilisé pour rendre une app impossible à supprimer.",
            GrantMode.SPECIAL,
        ),
        PermissionInfo(
            "SYSTEM_ALERT_WINDOW", "Affichage par-dessus les autres apps", PermissionTheme.CONTROL,
            "Afficher des fenêtres au-dessus des autres applications (bulles de discussion, lecteur flottant).",
            "Une fausse fenêtre peut recouvrir une vraie app pour vous faire saisir un mot de passe " +
                "ou toucher un bouton à votre insu.",
            GrantMode.SPECIAL,
        ),
    )

    private val byPermission = entries.associateBy { it.permission }

    fun find(permission: String): PermissionInfo? = byPermission[permission.removePrefix("android.permission.")]
}
