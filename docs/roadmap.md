# Roadmap & Spécifications UX — VYLIA : The Privacy Scorer

Ce document définit les orientations fonctionnelles, la structure UX et les exigences de développement pour les prochaines étapes de **VYLIA**.

---

## 1. Scoring & Badges Avancés (Granularité & Qualification)
* **Double système de notation :** Attribution d'une note chiffrée globale (0 à 100) combinée à des **badges de qualification** (positifs et négatifs) ciblés spécifiquement sur :
  * Les traqueurs détectés.
  * Les permissions demandées.
* **Philosophie non-anxiogène :** Éviter l'effet "antivirus alarmiste". Les badges mettent en valeur les bonnes pratiques autant qu'ils signalent les zones d'ombre.

---

## 2. Analyse Exhaustive des Permissions & Pédagogie
* **Alignement méthodologique :** Structuration de l'analyse des permissions pour s'harmoniser avec les standards d'**Exodus Privacy**.
* **Regroupement thématique :** Organisation claire des permissions par catégories cohérentes (ex: Localisation, Identité, Stockage, Réseau, Capteurs).
* **Section « Comprendre » enrichie :** Fiches pédagogiques associées à chaque permission pour expliquer clairement les risques réels associés en langage simple.

---

## 3. Refonte Visuelle (Icône & Splash Screen)
* **L'icône de l'application :** Représentation graphique centrée sur l'idée de **scan / audit**, abandonnant les codes agressifs de la cybersécurité classique.
* **Le Splash Screen :** Fini les symboles anxiogènes. Remplacement par une mascotte ou des illustrations de **petits espions stylisés et bienveillants** (dans un registre iconique ludique, moderne et rassurant).

---

## 4. Organisation de l'Écran « Scan » (3 Onglets)
L'écran principal de scan est structuré en trois onglets distincts pour une navigation fluide :
1. **Par Niveau de Risque :** Tri immédiat des applications selon leur score d'intrusivité (du pire au plus vertueux).
2. **Par Catégorie d'Apps :** Classement thématique (Réseaux sociaux, Productivité, Utilitaires, Jeux...).
3. **Par Badges Attribués :** Vue transversale mettant en avant les qualifications et trophées associés aux applications.

> 💡 **Interactivité UX (Deep Link Désinstallation) :** 
> Dans l'écran de Scan, chaque pavé d'application intègre son icône cliquable. Un clic sur l'icône déclenche un **deep link direct vers la fiche de l'application dans les paramètres système Android**, permettant sa désinstallation ou sa gestion immédiate sans passer par le Play Store.

---

## 5. Suivi Longitudinal complet (Onglet Analyse)
Un sous-onglet dédié (représenté par un petit icône de graphique) permet de suivre l'évolution de la vie privée sur l'appareil au fil du temps :
* **Historique des événements :** Journalisation automatique des **installations / désinstallations** d'applications.
* **Suivi des Octrois de Permissions :** Historique précis des modifications d'autorisations accordées ou révoquées aux applications.
* **Graphique temporel :** 
  * Évolution du score moyen de l'appareil dans le temps.
  * Calcul et affichage de l'écart-type de confidentialité.
  * Liste des applications qui influencent le plus la courbe de score.

---

## 6. Onglet « Agir » (Refonte UX en 3 Sous-onglets)
L'écran principal se concentre sur l'analyse globale suivant les différents axes, puis s'articule autour de trois modules pratiques :

### A. Réduire (Méthode & Réduction du Risque)
L'UX est orientée **action à fort impact** ("victoires faciles") en guidant l'utilisateur vers l'obtention de nouveaux badges positifs :
* **Réduire les octrois :** Inventaire des permissions sensibles accordées aux applications et incitation à les limiter au strict minimum.
* **Réduire le nombre d'applications :** Croisement entre la **dernière date d'utilisation** des apps et leur mauvais score. Proposer en priorité la suppression des applications inutilisées à fort risque pour booster immédiatement le score global et débloquer les badges associés.

### B. Alternatives
* Catalogue d'alternatives éthiques et open-source pour les applications les plus courantes, regroupées par catégories.
* Alternatives par catégorie du Play Store (menu), 2 à 3 apps chacune, choisies par `tools/alternatives/build_catalogue.py` depuis le classement AlternativeTo (filtre F-Droid) : gratuites d'abord, puis likes, une app par type, exclusions motivées, fiche Play Store sinon F-Droid. Worst of Fame et Hall of Fame retirés (2026-11-01).

### C. Récompenses & Partage (Gamification refactorisée)
* **Hall of Fame / Trophées :** Centralisation de tous les badges de gamification obtenus grâce aux actions de nettoyage (ex: *"Désintoxication"*, *"Chasseur de traqueurs"*).
* **Partage & Sensibilisation :** Fonctionnalité permettant d'exporter/partager visuellement ses accomplissements, ses badges et son score de sobriété numérique pour sensibiliser son entourage.

---

## 7. Paramètres & Automatisations
* **Notification à l'installation :** Alerte en temps réel dans la barre de notifications lorsqu'une nouvelle application est installée, affichant un résumé de son score et de ses badges.
* **Scans de fond programmés :** Option d'audit périodique (hebdomadaire ou mensuel) pour alimenter le graphique du suivi longitudinal (fonctionnalité désactivable à tout moment).

---

## 8. Paramètres › Gestion des données (sur le modèle de Molkky Board)
* **Exporter mes données :** une sauvegarde complète en un fichier (Suivi : courbe et historique, récompenses et
  points, réglages, règles de score importées), versionnée, qui sert aussi d'export RGPD.
* **Importer une sauvegarde :** restauration qui **ajoute sans jamais écraser** (points de courbe et événements déjà
  présents ou invalides écartés), avec un bilan affiché ; fichier refusé sans rien écrire s'il n'est pas une
  sauvegarde VYLIA, s'il vient d'une version plus récente, s'il est trop gros ou illisible.
* **Supprimer mes données :** effacement de tout ce que VYLIA a stocké (Suivi, récompenses, cache d'analyse), après
  confirmation ; les réglages sont conservés.
* **Conservé à part :** l'export spécifique des résultats du scan (rapport d'audit CSV).
* **Sécurité des données externes :** sauvegardes, règles importées et base Exodus téléchargée sont traitées comme
  hostiles (bornes, validation, aucun écrasement, règles jamais appliquées depuis une sauvegarde), et l'export CSV
  neutralise les formules. Détail : `docs/security/donnees-externes.md`.

---

## 9. À propos › Crédits, avec une touche d'humour (sur le modèle de Molkky Board)
* **Écran Crédits** façon générique de film, ouvert depuis À propos : un faux terminal qui « initialise » l'app
  (« Recherche de traqueurs… 0 trouvé », clin d'œil au métier de VYLIA), puis la version et le build.
* **Rôles décalés :** Production Yogi Studio (« effectif total : une personne et un smartphone en méditation »),
  Développement, design, tests et service après-vente, Pair programming (Claude, via Claude Code), Moteur de score
  (« une fonction pure, qui n'a jamais rien envoyé sur Internet »), Données (Exodus Privacy), Police (Unbounded).
* **Compléments d'À propos** repris de Molkky Board : liste des bibliothèques open source et leurs licences,
  politique de confidentialité et conditions d'utilisation (pages du site), « Envoyer un retour » dans les
  versions de test.

---

## Avancement (mis à jour le 2026-11-01)

| § | Chantier | État |
|---|---|---|
| 1 | Scoring et badges de qualification | Fait : badges v2.1 puis v3 (capture, arrière-plan), patterns de sécurité |
| 2 | Analyse exhaustive des permissions | Fait : toutes les permissions par thème, niveau de protection Android, fiches |
| 3 | Icône et splash screen | Fait : icône loupe et coche, splash avec petits espions bienveillants qui s'éclipsent |
| 4 | Écran Scan en 3 onglets | Fait : par risque, par catégorie, par badge ; icône → fiche Android |
| 5 | Suivi longitudinal | Fait : onglet Analyse › Suivi (courbe moyenne ± écart-type, apps qui pèsent le plus, historique) |
| 6 | Agir en 3 sous-onglets | Fait : Réduire (gain exact, apps inutilisées), Alternatives (catalogue), Récompenses et partage |
| 7 | Notifications et scans programmés | Fait, à valider sur téléphone : Paramètres › Automatisations (alerte d'installation toutes les ~15 min, scan de fond hebdomadaire ou mensuel), JobScheduler sans bibliothèque |
| 8 | Gestion des données (export, import, suppression) | Fait (0.6.0) : Paramètres › Gestion des données, sauvegarde JSON versionnée, import sans écraser avec bilan, suppression confirmée ; export CSV du scan conservé |
| 9 | À propos › Crédits avec humour | Fait (0.7.0) : générique façon Molkky Board (faux terminal, rôles, statistiques de production) ; VYLIA se note elle-même, QUERY_ALL_PACKAGES comprise |
|   | Grille de score v3 | Faite et visible dans Paramètres › Règles de score › Voir la grille |

### Prochaines actions

1. ~~Comprendre : sous-onglet Badges et relecture des textes pour la grille v3~~ (fait le 2026-11-01).
2. ~~§6 Agir~~ (fait le 2026-11-01).
3. ~~§3 Refonte visuelle~~ (fait le 2026-11-01) : nouvelle icône centrée sur l'idée de scan / audit (sans codes agressifs de cybersécurité),
   et splash screen redéfini avec une mascotte ou de petits espions stylisés et bienveillants, dans un registre
   ludique, moderne et rassurant (à la place des bugs rouges expulsés).
4. ~~§5 Suivi~~ (fait le 2026-11-01). ~~§7 Automatisations~~ (fait le 2026-11-01) : notification à l'installation,
   scans de fond programmés qui alimentent le Suivi. L'effacement du Suivi rejoint « Supprimer mes données » (§8).
5. §6 Agir › Réduire, deux nouveaux modules (non développés) :
   - ~~**V1 — Guidage DNS privé**~~ (fait en 0.8.0 : Réduire › DNS privé, à côté de Autorisations et Suppressions) : tutoriel pas à pas et bouton d'accès rapide aux paramètres système pour activer un
     DNS chiffré anti-traqueurs (ex. AdGuard, NextDNS), sans code complexe ni impact sur la batterie.
   - **V2 — Bouclier VYLIA (VPN local)** (en cours sur `feature/shield-local-vpn`, architecture et suite dans
     `docs/shield.md`) : interception DNS 100 % locale via l'API `VpnService` d'Android, qui filtre en
     temps réel les requêtes de traçage sortantes sans envoyer aucune donnée vers l'extérieur, activable par un simple
     interrupteur. Attention : demande l'accès Internet et un service au premier plan, à refléter dans l'écran
     « Vie privée » et dans la grille (VYLIA est exclue de son propre score).
     Depuis le 2026-11-14 : traqueurs seulement, pas de publicité (Google Play), une seule version de l'app ; filtrage
     des régies à revoir en option désactivée d'office une fois l'app acceptée sur Play (voir `docs/shield.md`).
6. ~~§8~~ (fait en 0.6.0). §8 Paramètres › Gestion des données : remplacer l'écran « Export et données » par Exporter / Importer / Supprimer
   mes données, comme dans Molkky Board, en gardant l'export CSV des résultats du scan (voir §8).
7. ~~**Bug Analyse en mode avancé :**~~ (corrigé en 0.6.4) deux scores de confidentialité s'affichent (apps utilisateur et apps système).
   N'en garder qu'un seul, qui fusionne les résultats des apps installées par l'utilisateur et des apps système.
8. ~~§9~~ (fait en 0.7.0). §9 À propos › Crédits avec une touche d'humour, comme dans Molkky Board (voir §9).
9. **Catégories des apps d'après Google Play** (à étudier après la publication sur Play, idée du 2026-11-08) : les
   catégories et les tags Play (Banque mobile, Transports publics, Météo, Moniteur d'activité…) sont bien plus précis
   que `ApplicationInfo.category` (8 valeurs), mais illisibles depuis le téléphone, et les demander à Google pendant
   le scan enverrait la liste des apps installées. Piste : un script de poste de dev, sur le modèle de
   `tools/alternatives/fetch_play_ratings.py`, qui lit la catégorie et les tags Play des apps populaires (meilleures
   apps de chaque catégorie en France), les traduit vers les 28 catégories de VYLIA et enrichit la liste des apps
   connues embarquée (`package_overrides`, 106 apps aujourd'hui). Précautions : les tags sont choisis par l'éditeur,
   donc les `package_overrides` vérifiés à la main restent prioritaires et aucun tag ne donne seul une catégorie qui
   justifie des accès très sensibles ; le grattage des pages Play casse si Google change leur HTML. Ne pas changer la
   grille pendant le test fermé.
10. **Conseil « version allégée » et rapports anciens** (idée du 2026-11-09, issue du classement des apps les plus
   téléchargées, `directory/top-google-play.md` et page Classement du site) : une même app existe souvent sous
   plusieurs paquets (« Lite », « Go », « Business », version régionale), avec des notes très différentes : TikTok
   D (30, 7 traqueurs) contre TikTok Lite B (78, 1 traqueur), Facebook E (0, 8 traqueurs) contre Facebook Lite
   C (50, 0 traqueur). Dans Agir › Réduire ou Alternatives, quand une app installée a une variante connue mieux notée,
   proposer la variante (fiche Play, gain de score estimé, ce qu'on perd en fonctions). Liste des variantes embarquée
   et mise à jour avec les bases (comme `alternatives.json`), vérifiée à la main ; notes des variantes calculées sur
   le téléphone si elles sont installées, sinon d'après le rapport Exodus. Signaler aussi, sur la fiche d'une app,
   quand son dernier rapport Exodus a plus d'un an (cas de TikTok, rapport de 2024) : sa note de traqueurs porte
   alors sur une version dépassée.
11. **Menu « Apps préinstallées » dans le mode Avancé** (idée du 2026-11-14) : les apps système pèsent dans le score
   mais Gérer › Apps ne peut rien proposer pour elles, faute de désinstallation possible.
   - **Inventaire par origine** : constructeur (Samsung, Xiaomi…), opérateur, Google, partenaires (Facebook, Netflix
     préinstallés). Point de départ : `directory/oem-google-play.json`.
   - **Trois niveaux par app** : sans risque à désactiver, à vos risques (une fonction casse), indispensable (ne jamais
     toucher). Indispensable : un mauvais paquet désactivé peut empêcher le téléphone de démarrer. Liste de départ :
     celle d'Universal Android Debloater (UAD-ng, GPL v3, compatible), reprise avec attribution et vérifiée à la main
     pour les grandes marques.
   - **Actions en paliers, sans root** : (1) lien vers la fiche Android (bouton « Désactiver » quand le système le
     permet, révocation des permissions) ; (2) guide ADB qui génère les commandes à copier
     (`pm disable-user --user 0 …`), réversibles ; (3) plus tard, **Shizuku** en option « si vous l'avez déjà » :
     boutons Désactiver / Réactiver depuis VYLIA, limités à ces seules actions. Shizuku reste une option pour les
     connaisseurs (débogage sans fil, à relancer après chaque redémarrage) et pas un passage obligé.
   - **Score** : un seul score (décision 0.6.4) ; afficher seulement la part du « socle constructeur ».
12. **Bouclier et apps préinstallées** (idée du 2026-11-14), deux pistes :
   - **A. Télémétrie des constructeurs** : listes de domaines d'analytics par marque (Xiaomi, Samsung, Huawei…),
     activées depuis le menu Préinstallées pour la marque du téléphone. Ce sont des traqueurs et pas des publicités :
     compatible avec la règle Play « traqueurs seulement ». Liste prudente : certains domaines servent aussi aux mises
     à jour système.
   - **B. Journal par app** : sur Android 10 et plus, `ConnectivityManager.getConnectionOwnerUid` permet au
     `VpnService` de retrouver l'app à l'origine d'une connexion, et donc d'afficher le comportement observé
     (« 12 tentatives vers des traqueurs cette semaine ») en plus du code analysé. À tester sur téléphone d'abord :
     l'attribution n'est pas garantie pour les requêtes passées par le résolveur DNS du système.
   - Ordre : A d'abord, B si le test est concluant.
13. **Base communautaire des scores** (idée du 2026-11-14, après l'acceptation sur Play) : faire grandir l'annuaire
   du site avec les analyses faites sur les téléphones, surtout pour les apps préinstallées qu'Exodus n'analyse pas
   (elles ne sont pas sur le Play Store).
   - **Étape 0, sans collecte** : « Demander une analyse à Exodus » pour une app sans rapport, qui ouvre leur
     formulaire.
   - **Vie privée** (la promesse « aucune donnée collectée » reste vraie par défaut) : contribution app par app depuis
     la fiche, jamais d'envoi en masse (une liste d'apps peut révéler santé, religion, rencontres : RGPD art. 9) ;
     aperçu exact de ce qui part puis confirmation ; aucun identifiant d'appareil ni de compte ; permissions déclarées
     seulement, jamais celles accordées.
   - **Contenu envoyé** : paquet, `versionCode`, empreinte du certificat de signature, traqueurs détectés, permissions
     déclarées, versions des règles et de VYLIA. Pas le score : le serveur le recalcule avec `:scoring`, comme
     l'annuaire.
   - **Contre l'empoisonnement** : une fiche n'est publiée qu'après plusieurs envois concordants (même version, même
     certificat), et elle est comparée au rapport Exodus quand il existe.
   - **Infrastructure** : le site est statique, il faut un petit service de réception (par exemple un Worker
     Cloudflare avec une base D1, sans journaux d'IP). À mettre à jour : fiche « Sécurité des données » de Play,
     politique de confidentialité du site, licence ODbL de la base.
14. **Alerte à la mise à jour** (idée du 2026-11-14) : les apps changent surtout en se mettant à jour, pas à
   l'installation. Quand une app installée change de version, comparer avec l'analyse de la version précédente et
   prévenir si elle ajoute des traqueurs ou des permissions : « Cette mise à jour de X ajoute 2 traqueurs et l'accès
   aux contacts : 72 → 58 ». S'appuie sur l'alerte d'installation (§7, Paramètres › Automatisations, désactivée par
   défaut) et sur l'historique du Suivi, qui reçoit un nouvel événement « mise à jour ». Une mise à jour qui améliore la
   note se signale aussi (philosophie non anxiogène). Comme pour les bases, un traqueur qui n'apparaît qu'avec une mise
   à jour des signatures Exodus n'est pas imputé à la mise à jour de l'app.
15. **Comparer avec la fiche « Sécurité des données » de Play** (idée du 2026-11-14, pour l'annuaire du site) :
   repérer les apps qui déclarent « aucune donnée partagée avec des tiers » alors qu'elles embarquent des traqueurs.
   Un script de poste de dev, sur le modèle de l'idée n° 9, lit la fiche Play des apps de l'annuaire ; rien n'est
   demandé à Google depuis le téléphone. Précautions : la fiche est déclarative et Google a ses propres définitions
   (un SDK peut être « prestataire » et pas « partage ») ; présenter un écart constaté et sourcé, daté, jamais une
   accusation ; le grattage casse si Google change le HTML de ses pages.
16. **Ménage guidé en 5 minutes** (idée du 2026-11-14) : un parcours qui enchaîne les 3 actions qui rapportent le plus
   sur ce téléphone (par exemple une permission inutile, une app inutilisée à mauvais score, le bouclier), une à la
   fois, avec le gain de score de chacune, puis un badge à la fin. Les gains viennent des calculs déjà faits dans
   Gérer (gain exact par permission, apps inutilisées) ; chaque étape peut être passée. Entrée depuis Scanner ou
   Célébrer, et proposé de nouveau quand le score baisse.
17. **Widget d'écran d'accueil** (idée du 2026-11-14) : le score du téléphone et sa note (A à E), l'état du bouclier
   (actif, en pause, arrêté) avec un bouton pour l'activer ou le mettre en pause, et le nombre de requêtes bloquées
   aujourd'hui. Un toucher ailleurs ouvre VYLIA. Deux tailles : petite (score seul), moyenne (score et bouclier).
   - **Vie privée** : l'écran d'accueil est vu par l'entourage, donc aucun nom d'app, comme l'image partagée.
   - **Technique** : `AppWidgetProvider` et `RemoteViews`, sans bibliothèque (dans l'esprit du JobScheduler des
     automatisations), ou Jetpack Glance si la mise en page Compose le justifie. Mis à jour à la fin d'un scan et quand
     le bouclier change d'état, sans tâche périodique de plus ; le compteur se rafraîchit au plus toutes les quelques
     minutes pour ne pas coûter de batterie.
   - **Bouclier** : même logique que la tuile des réglages rapides. Si Android n'a pas encore donné l'accord VPN
     (`VpnService.prepare`), le bouton ouvre VYLIA pour le demander.
   - Thème clair et sombre, et couleurs dynamiques (Material You) quand le téléphone les propose.
18. **Ordre proposé (2026-11-14)** : menu Préinstallées v1 (inventaire, niveaux, fiche Android, guide ADB) ; alerte à
   la mise à jour ; ménage guidé ; widget ; bouclier, piste A ; points déjà notés en n° 10 (rapport Exodus ancien, version
   Lite) ; comparaison « Sécurité des données » sur le site ; base communautaire après Play, en commençant par
   l'étape 0 ; journal par app (piste B) après le test ; Shizuku en dernier.

### Versions

Une version par lot livré (versionName et versionCode dans `app/build.gradle.kts`, tag `vX.Y.0`) :
0.5.0 = §7 Automatisations, 0.6.0 = §8 Gestion des données, 0.6.1 = sécurité des données externes, 0.6.2 = Comprendre › Permissions (loupe, apps concernées), 0.6.3 = recherche dans Badges et Traqueurs, 0.6.4 = un seul score dans Analyse en mode Avancé, 0.7.0 = Crédits et VYLIA notée comme les autres (première release publiée), 0.7.1 = accès rapide par catégorie dans Scan, 0.7.2 = apps de finance reconnues, 0.8.0 = guide DNS privé, 0.8.1 = Réduire › Traçage pub, 0.8.2 = NextDNS recommandé ; la suite continue à 0.9.0.
