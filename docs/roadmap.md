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
* Mise en avant directe du **Worst of Fame** (les pires élèves à remplacer) et du **Hall of Fame** (les meilleures alternatives à adopter).

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
   - **V1 — Guidage DNS privé** : tutoriel pas à pas et bouton d'accès rapide aux paramètres système pour activer un
     DNS chiffré anti-traqueurs (ex. AdGuard, NextDNS), sans code complexe ni impact sur la batterie.
   - **V2 — Bouclier VYLIA (VPN local)** : interception DNS 100 % locale via l'API `VpnService` d'Android, qui filtre en
     temps réel les requêtes de traçage sortantes sans envoyer aucune donnée vers l'extérieur, activable par un simple
     interrupteur. Attention : demande l'accès Internet et un service au premier plan, à refléter dans l'écran
     « Vie privée » et dans la grille (VYLIA est exclue de son propre score).
6. ~~§8~~ (fait en 0.6.0). §8 Paramètres › Gestion des données : remplacer l'écran « Export et données » par Exporter / Importer / Supprimer
   mes données, comme dans Molkky Board, en gardant l'export CSV des résultats du scan (voir §8).
7. ~~**Bug Analyse en mode avancé :**~~ (corrigé en 0.6.4) deux scores de confidentialité s'affichent (apps utilisateur et apps système).
   N'en garder qu'un seul, qui fusionne les résultats des apps installées par l'utilisateur et des apps système.
8. ~~§9~~ (fait en 0.7.0). §9 À propos › Crédits avec une touche d'humour, comme dans Molkky Board (voir §9).

### Versions

Une version par lot livré (versionName et versionCode dans `app/build.gradle.kts`, tag `vX.Y.0`) :
0.5.0 = §7 Automatisations, 0.6.0 = §8 Gestion des données, 0.6.1 = sécurité des données externes, 0.6.2 = Comprendre › Permissions (loupe, apps concernées), 0.6.3 = recherche dans Badges et Traqueurs, 0.6.4 = un seul score dans Analyse en mode Avancé, 0.7.0 = Crédits et VYLIA notée comme les autres (première release publiée), 0.7.1 = accès rapide par catégorie dans Scan ; la suite continue à 0.8.0.
