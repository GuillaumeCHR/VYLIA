# Aide en cas de surveillance (stalkerware, logiciel espion) : écran « Que faire ? », avertissement avant la
# désinstallation, notifications discrètes. Ton calme et factuel ; aucun numéro ni organisme national : les deux
# exemples internationaux viennent de app/src/main/assets/support_resources.json.
S = [
 ("safety_title", "What to do?", "Que faire ?"),
 ("safety_open", "What to do?", "Que faire ?"),
 ("safety_close", "Close", "Fermer"),
 ("safety_emergency", "In case of immediate danger, call your country's emergency number.", "En cas de danger immédiat, appelez le numéro d'urgence de votre pays."),
 ("safety_principles_title", "Before doing anything", "Avant toute action"),
 ("safety_principle_wait", "Do not uninstall in a hurry. Removing the app can alert the person who installed it, they may react dangerously, and evidence can be lost. If you fear for your safety, ask for help first: support services can help you plan before removing it.",
  "Ne désinstallez pas dans la précipitation. Supprimer l'app peut prévenir la personne qui l'a installée, elle peut réagir de façon dangereuse, et des preuves peuvent se perdre. Si vous craignez pour votre sécurité, demandez d'abord de l'aide : les structures d'accompagnement peuvent vous aider à préparer la suite avant de la supprimer."),
 ("safety_principle_traces", "Keep a record of what you saw: the app's name, the date, a screenshot of its VYLIA page. If you can, keep it off this device.",
  "Gardez une trace de ce que vous avez constaté : le nom de l'app, la date, une capture d'écran de sa fiche VYLIA. Si possible, conservez-la ailleurs que sur cet appareil."),
 ("safety_principle_device", "To look for help, use another device that you have no reason to suspect.",
  "Pour chercher de l'aide, utilisez de préférence un autre appareil, que vous n'avez pas de raison de soupçonner."),
 ("safety_principle_certainty", "VYLIA describes what an app can do, not what it does. Only an app listed in a surveillance software database is identified as such.",
  "VYLIA décrit ce qu'une app peut faire, pas ce qu'elle fait. Seule une app référencée dans une base de logiciels de surveillance est identifiée comme telle."),
 ("safety_question", "Who could be behind this surveillance?", "Qui pourrait être à l'origine de cette surveillance ?"),
 ("safety_question_hint", "Your answer only changes the advice shown. It is not saved.", "Votre réponse change seulement les conseils affichés. Elle n'est pas enregistrée."),
 ("safety_choice_partner", "Someone close to me, a partner or an ex-partner", "Un proche, un conjoint ou un ex-conjoint"),
 ("safety_choice_criminal", "Sophisticated spyware, unknown or criminal origin", "Un logiciel espion sophistiqué, auteur inconnu ou criminel"),
 ("safety_choice_state", "The authorities of my country (activist, journalist, lawyer, rights defender…)", "Les autorités de mon pays (militant·e, journaliste, avocat·e, défenseur·e des droits…)"),
 ("safety_choice_unknown", "I don't know", "Je ne sais pas"),
 # Parcours 1 : un proche
 ("safety_partner_title", "If it may be someone close to you", "Si c'est peut-être un proche"),
 ("safety_partner_text", "This is the most common case with stalkerware. Contact a local organisation that supports victims of domestic violence, in particular women's support organisations: they also deal with the digital side of violence, and can help you prepare to be safe before removing the app.",
  "C'est le cas le plus fréquent avec les stalkerwares. Rapprochez-vous d'une association locale d'aide aux victimes de violences conjugales, notamment les associations d'aide aux femmes : elles accompagnent aussi la dimension numérique des violences, et peuvent vous aider à préparer votre mise en sécurité avant de supprimer l'app."),
 # Parcours 2 : logiciel espion sophistiqué, auteur inconnu ou criminel
 ("safety_criminal_title", "If it may be sophisticated spyware or a criminal", "Si c'est peut-être un logiciel espion sophistiqué ou un criminel"),
 ("safety_criminal_text", "Ask your local police or judicial authorities about filing a complaint, and cybersecurity experts about having the device examined.",
  "Renseignez-vous auprès de la police ou des autorités judiciaires locales pour un dépôt de plainte, et auprès d'experts en cybersécurité pour une analyse de l'appareil."),
 ("safety_criminal_tips", "Without any guarantee, these steps can help: update the system, restart the device regularly, turn on the system's enhanced protection modes if they exist. Do not reset the device before it has been examined: traces would be lost.",
  "Sans garantie d'efficacité, ces gestes peuvent aider : mettre à jour le système, redémarrer l'appareil régulièrement, activer les modes de protection renforcée du système s'ils existent. Ne réinitialisez pas l'appareil avant une analyse : les traces seraient perdues."),
 # Parcours 3 : les autorités du pays
 ("safety_state_title", "If it may be the authorities of your country", "Si ce sont peut-être les autorités de votre pays"),
 ("safety_state_text", "Contact organisations that defend human rights or protect journalists: many offer digital security help and device analysis, often for free and confidentially.",
  "Rapprochez-vous d'organisations de défense des droits humains ou de protection des journalistes : beaucoup proposent une assistance en sécurité numérique et des analyses d'appareils, souvent gratuitement et en toute confidentialité."),
 # Ressources
 ("safety_examples", "For example:", "Par exemple :"),
 ("safety_resource_site", "Website", "Site"),
 ("safety_resource_site_named", "Open the website of %1$s", "Ouvrir le site de %1$s"),
 ("safety_resource_call", "Call %1$s", "Appeler le %1$s"),
 ("safety_resources_note", "These are examples. VYLIA is not affiliated with these organisations; links open in your browser.",
  "Ce sont des exemples. VYLIA n'est pas liée à ces organisations ; les liens s'ouvrent dans votre navigateur."),
 # Limite
 ("safety_limit_title", "What VYLIA cannot see", "Ce que VYLIA ne peut pas voir"),
 ("safety_limit_text", "Spyware such as Pegasus does not install like a visible app: VYLIA cannot detect it. A good score does not prove that a device is safe.",
  "Les logiciels espions comme Pegasus ne s'installent pas comme une app visible : VYLIA ne peut pas les détecter. Un score correct ne prouve pas qu'un appareil est sain."),
 # Aide
 ("help_surveillance_title", "If you think you are being watched", "Si vous pensez être surveillé·e"),
 ("help_surveillance_text", "In case of immediate danger, call your country's emergency number. Do not uninstall anything in a hurry: ask for help first, from another device if you can. VYLIA cannot detect Pegasus-type spyware: a good score does not prove a device is safe.",
  "En cas de danger immédiat, appelez le numéro d'urgence de votre pays. Ne désinstallez rien dans la précipitation : demandez d'abord de l'aide, si possible depuis un autre appareil. VYLIA ne peut pas détecter les logiciels espions de type Pegasus : un score correct ne prouve pas qu'un appareil est sain."),
 # Avertissement avant la fiche système (où se trouve Désinstaller)
 ("guard_title", "Before uninstalling", "Avant de désinstaller"),
 ("guard_text", "This app has surveillance capabilities. Removing it can alert the person who installed it, they may react dangerously, and evidence can be lost. If you fear for your safety, ask for help first.",
  "Cette app a des capacités de surveillance. La supprimer peut prévenir la personne qui l'a installée, elle peut réagir de façon dangereuse, et des preuves peuvent se perdre. Si vous craignez pour votre sécurité, demandez d'abord de l'aide."),
 ("guard_help", "See what to do", "Voir que faire"),
 ("guard_continue", "Continue", "Continuer"),
 # Notifications discrètes : ni nom d'app, ni terme explicite
 ("notif_security_title", "VYLIA", "VYLIA"),
 ("notif_security_text", "VYLIA has found something to check.", "VYLIA a détecté un point à vérifier."),
 ("notif_channel_security", "Points to check", "Points à vérifier"),
 ("notif_channel_security_description", "Discreet notifications: they never name the app concerned.", "Notifications discrètes : elles ne nomment jamais l'app concernée."),
 ("notif_install_public", "VYLIA has analysed a new app.", "VYLIA a analysé une nouvelle app."),
 ("notif_threshold_sensitive", "Device score: %1$d, below your threshold of %2$d. Something to check: open VYLIA.", "Score de l'appareil : %1$d, sous votre seuil de %2$d. Un point à vérifier : ouvrez VYLIA."),
]

P = []
