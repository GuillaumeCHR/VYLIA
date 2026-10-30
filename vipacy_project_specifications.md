# 🛡️ VIPACY — Spécifications Globales du Projet (v1.5)

## 1. Vision & Identité

* **Nom de l'application :** **VIPACY** (Anagramme de *Privacy*)

* **Package ID :** `com.vipacy.app`

* **Philosophie :** 100 % local, *privacy-first*, open-source, sans télémétrie distante ni API tierce.

* **Design System :** Material Design 3 natif (Dynamic Color) couplé à une structure épurée et à des touches de transparence (*frosted glass*) pour inspirer la confiance et la sérénité.

## 2. Navigation & Ergonomie (Bottom Bar à 5 entrées)

L'interface s'inspire du modèle ergonomique de type Yuka pour offrir une expérience claire, visuelle et orientée action :

1. **`Scan` (Équipement & Historique) :**

   * Point d'entrée principal pour lancer ou rafraîchir l'analyse globale de l'appareil.

   * **Journal de bord / Frise chronologique :** Suivi longitudinal du *Privacy Score* global au fil de l'eau.

   * Détection des variations : met en relief l'impact direct de l'ajout ou de la suppression d'une application sur le score de risque de l'appareil.

2. **`Analyse` (Résultats transversaux) :**

   * **Par zones de score de risque :** Séparation en blocs distincts (Zone Rouge / Orange / Verte) avec affichage du score global de l'appareil sur 100.

   * **Par adéquation Permissions / Finalité (Context-Aware Audit) :** Évaluation de la légitimité des autorisations en fonction de la catégorie déclarée de l'application (ex. : une application de lampe torche ou de calculatrice demandant la géolocalisation ou l'accès aux contacts sera immédiatement signalée comme "Anomalie / Surexposition de données").

   * **Par permissions spécifiques :** Vue transversale (ex. : lister toutes les applications accédant au micro, à la caméra ou au GPS en arrière-plan).

   * **Par traqueurs :** Cartographie des SDKs de traçage et d'analytique détectés.

3. **`Reco` (Recommandations & Alternatives / "Swaps") :**

   * **Worst of Fame ⚠️ :** Podium des applications les plus intrusives pénalisant le plus lourdement le score.

   * **Actions correctives immédiates :** Conseils ciblés pour regagner des points (révocation de permissions sensibles, nettoyage d'applications inactives).

   * **Alternatives vertueuses ("Swaps") :** Proposition d'applications de remplacement éthiques, respectueuses de la vie privée ou open-source (type F-Droid).

4. **`Apprendre` (Pédagogie) :**

   * Explication détaillée de l'algorithme de calcul du **Privacy Score**.

   * Lexique illustré des risques associés à chaque permission critique, aux anomalies de finalité et aux types de traqueurs.

5. **`Paramètres` :**

   * Choix de la langue, section "À propos" et crédits (mention de la base Exodus Privacy).

   * **Gestion des notifications proactives :** Activation ou désactivation des alertes locales en cas de détection d'une nouvelle application impactant négativement la confidentialité.

   * **Gestion des données locales :** Export des rapports d'audit et suppression définitive de l'historique local.

## 3. Moteur de Score (`PrivacyScorer.kt`) & Analyse Technique

### A. Barème de pénalités par permissions :

* **Critiques (-25 à -30 pts) :**

  * Géolocalisation en arrière-plan (`ACCESS_BACKGROUND_LOCATION`) : **-30 pts**

  * Service d'accessibilité (`BIND_ACCESSIBILITY_SERVICE`) : **-30 pts**

  * Famille SMS (`READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`) : **-25 pts**

  * Caméra (`CAMERA`) : **-25 pts**

  * Microphone (`RECORD_AUDIO`) : **-25 pts**

* **Élevées (-15 à -20 pts) :**

  * GPS précis (`ACCESS_FINE_LOCATION`) : **-20 pts**

  * Famille Contacts (`READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS`) : **-20 pts**

  * Famille Agenda (`READ_CALENDAR`, `WRITE_CALENDAR`) : **-15 pts**

* **Modérées / Faibles (-10 pts) :**

  * Stockage partagé / Fichiers médias (`READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_*`) : **-10 pts**

  * GPS approximatif (`ACCESS_COARSE_LOCATION`) : **-10 pts**

### B. Analyse contextuelle (Légitimité vs Finalité de l'app) :

Le moteur croise la catégorie de l'application (récupérée via `ApplicationInfo.category` d'Android ou le manifeste) avec les autorisations demandées :

* **Pénalité de sur-permission / Incohérence de finalité : -15 à -25 pts supplémentaires** si une application réclame un accès sans lien logique avec sa fonction principale (ex. : utilitaire/outil demandant la géolocalisation ou les contacts ; jeu hors-ligne demandant l'accès au micro).

### C. Paliers de pénalités pour les traqueurs (Exodus) :

* 0 traqueur = **0 pt**

* 1 à 3 traqueurs = **-10 pts**

* 4 à 10 traqueurs = **-20 pts**

* 11 à 30 traqueurs = **-35 pts**

* > 30 traqueurs = **-50 pts**

*Note technique : Le score final de chaque application et le score global de l'appareil sont strictement bornés entre 0 et 100 via `.coerceIn(0, 100)`. L'analyse des traqueurs repose sur l'embarquement local de la base de signatures Exodus Privacy (`assets/search.json`) pour analyser les fichiers DEX des applications sans aucune connexion réseau.*