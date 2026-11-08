B = [
 ("PERM_FEATHER", ("Featherweight", "Poids plume"), ("No sensitive permission declared.", "Aucune permission sensible déclarée.")),
 ("PERM_ON_TARGET", ("Bullseye", "Dans le mille"), ("Every sensitive permission granted fits the app's purpose.", "Toutes les permissions sensibles accordées collent à la finalité de l'app.")),
 ("PERM_NOSY", ("Nosy", "Fouineuse"), ("At least one permission unexpected for this type of app is granted.", "Au moins une permission inattendue pour ce type d'app est accordée.")),
 ("PERM_FISHING", ("Fishing trip", "Pêche à la ligne"), ("Asks for permissions forbidden for this type of app, which you refused.", "Demande des permissions interdites pour ce type d'app, que vous avez refusées.")),
 ("PERM_OUTLAW", ("Off the road", "Sortie de route"), ("At least one permission forbidden for this type of app is granted.", "Au moins une permission interdite pour ce type d'app est accordée.")),
 ("PERM_GLUTTON", ("Glutton", "Goinfre"), ("So many permissions that the penalty cap is reached.", "Tellement de permissions que le plafond de pénalité est atteint.")),
 ("SEC_WANTED", ("Wanted", "Avis de recherche"), ("Recognised as known stalkerware by Echap's database: an app made to spy on someone in secret.", "Reconnue comme stalkerware connu par la base d'Echap : une app faite pour espionner quelqu'un en cachette.")),
 ("SEC_BIG_EARS", ("Big ears", "Grandes oreilles"), ("Can record the microphone or camera in the background.", "Peut enregistrer le micro ou la caméra en arrière-plan.")),
 ("SEC_SPY_PIANIST", ("Keystroke spy", "Pianiste espion"), ("Can read what you type and send it over the Internet.", "Peut lire ce que vous tapez et l'envoyer sur Internet.")),
 ("SEC_MOLOTOV", ("Molotov cocktail", "Cocktail Molotov"), ("A combination of capabilities typical of malware.", "Combinaison de capacités typique d'un logiciel malveillant.")),
 ("TRK_ORGANIC", ("Organic", "Bio"), ("Analysed: no trackers.", "Analysée : aucun traqueur.")),
 ("TRK_MECHANIC", ("Mechanic", "Mécano"), ("Only technical trackers (crash reports, consent).", "Uniquement des traqueurs techniques (rapports de plantage, consentement).")),
 ("TRK_SANDWICH", ("Eavesdropper", "Oreille indiscrète"), ("Embeds one or two targeting or profiling trackers.", "Embarque un ou deux traqueurs de ciblage ou de profilage.")),
 ("TRK_AD_NETWORK", ("Tracking network", "Réseau de pistage"), ("Embeds at least three targeting or profiling trackers.", "Embarque au moins trois traqueurs de ciblage ou de profilage.")),
 ("TRK_GPS_SNITCH", ("GPS snitch", "Mouchard GPS"), ("Embeds a tracker specialised in location.", "Embarque un traqueur spécialisé dans la localisation.")),
 ("TRK_FULL_HOUSE", ("Full house", "Carton plein"), ("Its trackers cover at least 4 of the 5 intrusive categories.", "Ses traqueurs couvrent au moins 4 des 5 catégories intrusives.")),
 ("TRK_HORNET_NEST", ("Hornet's nest", "Nid à mouchards"), ("Embeds 10 trackers or more.", "Embarque 10 traqueurs ou plus.")),
 ("TRK_BLACK_BOX", ("Black box", "Boîte noire"), ("Trackers not analysed yet.", "Traqueurs pas encore analysés.")),
 ("PERM_ROLLING", ("Rolling!", "Moteur, ça tourne !"), ("Can film, record sound or capture the screen in the background without its purpose justifying it.", "Peut filmer, enregistrer le son ou capturer l'écran en arrière-plan sans que sa finalité le justifie.")),
 ("PERM_NIGHT_OWL", ("Night owl", "Insomniaque"), ("At least two ways of running in the background (location, microphone, alarms, battery…) that its purpose does not justify.", "Au moins deux moyens de tourner en arrière-plan (position, micro, réveils, batterie…) que sa finalité ne justifie pas.")),
 ("PERM_DORMANT", ("Knows when to rest", "Sait se faire oublier"), ("Declares no way of capturing or running in the background: when you close it, it stops.", "Ne déclare aucun moyen de capter ou de tourner en arrière-plan : quand vous la fermez, elle s'arrête.")),
 ("COMBO_TOP_OF_CLASS", ("Top of the class", "Premier de la classe"), ("Positive on permissions and trackers, with no negative badge.", "Positive sur les permissions et les traqueurs, sans aucun badge négatif.")),
 ("COMBO_DOUBLE_TROUBLE", ("Double trouble", "Double peine"), ("A red badge on both the permissions or security side and the trackers side.", "Badge rouge à la fois côté permissions ou sécurité et côté traqueurs.")),
]

SEC = [
 ("KNOWN_STALKERWARE", ("Known spyware", "Logiciel espion connu"),
  ("Its package name or signing certificate is listed in Echap's stalkerware database or in MVT's targeted spyware indicators.", "Son nom de paquet ou son certificat de signature figure dans la base des stalkerwares d'Echap ou dans les indicateurs MVT des logiciels espions de surveillance ciblée.")),
 ("KEYLOGGER_CAPABLE", ("Keylogger-like capability", "Capacité de type keylogger"),
  ("Can read what you type (accessibility service or third-party keyboard) and has Internet access.", "Peut lire ce que vous tapez (service d'accessibilité ou clavier tiers) et dispose d'un accès Internet.")),
 ("COVERT_RECORDING", ("Background recording", "Enregistrement en arrière-plan"),
  ("Can record the microphone or camera in the background, starts at boot and has Internet access, without the app's purpose justifying it.", "Peut enregistrer le micro ou la caméra en arrière-plan, démarre au boot et dispose d'un accès Internet, sans que la finalité de l'app le justifie.")),
 ("STALKERWARE_PROFILE", ("Surveillance software profile", "Profil de logiciel de surveillance"),
  ("Hidden icon and at least two highly sensitive accesses granted: the typical profile of surveillance software.", "Icône cachée et au moins deux accès très sensibles accordés : profil typique d'un logiciel de surveillance.")),
 ("OVERLAY_TROJAN", ("Screen reading + overlay", "Combinaison lecture d'écran + superposition"),
  ("Can read the screen (accessibility) and draw over other apps: a combination used by banking trojans.", "Peut lire l'écran (accessibilité) et dessiner par-dessus les autres apps : combinaison utilisée par les chevaux de Troie bancaires.")),
]

S = []
for id_, label, desc in B:
    S.append((f"badgedef_{id_.lower()}", label[0], label[1]))
    S.append((f"badgedef_{id_.lower()}_description", desc[0], desc[1]))
for id_, label, desc in SEC:
    S.append((f"security_{id_.lower()}", label[0], label[1]))
    S.append((f"security_{id_.lower()}_description", desc[0], desc[1]))
S += [
 ("badges_title", "Badges", "Badges"),
 ("badges_hint", "What this app does well, and what deserves attention.", "Ce que cette app fait bien, et ce qui mérite votre attention."),
 ("badges_positive", "Good practices", "Bonnes pratiques"),
 ("badges_negative", "Points of attention", "Points d'attention"),
 ("badges_neutral", "To be confirmed", "À confirmer"),
 ("security_title", "Security", "Sécurité"),
 ("security_hint", "Capabilities typical of spyware: they describe what the app CAN do, not what it does. VYLIA does not see other apps' network traffic.",
  "Capacités typiques des logiciels espions : elles décrivent ce que l'app PEUT faire, pas ce qu'elle fait. VYLIA ne voit pas le trafic réseau des autres apps."),
 ("security_cap", "Score capped at %1$d because of this.", "Score plafonné à %1$d pour cette raison."),
 ("security_matched_stalkerware", "Recognised as %1$s, a known stalkerware.", "Reconnue comme %1$s, un stalkerware connu."),
 ("security_matched_stalkerware_safety", "This app is listed as surveillance software. Before uninstalling it, think about your safety: see what to do.", "Cette app est référencée comme logiciel de surveillance. Avant de la désinstaller, pensez à votre sécurité : voyez que faire."),
 ("security_matched_targeted", "Recognised as %1$s, a targeted surveillance spyware (MVT indicators).", "Reconnue comme %1$s, un logiciel espion de surveillance ciblée (indicateurs MVT)."),
 ("security_matched_targeted_safety", "This kind of spyware is used for targeted surveillance. Don't uninstall it right away: it is evidence. See what to do.", "Ce type de logiciel espion sert à une surveillance ciblée. Ne la désinstallez pas tout de suite : c'est une preuve. Voyez que faire."),
 ("app_icon_manage", "Manage %1$s in Android settings", "Gérer %1$s dans les paramètres d'Android"),
]

P = []
