# Annuaire public VYLIA

Les notes VYLIA d'environ 100 apps populaires en Europe, calculées chaque semaine hors de l'appareil avec le même
moteur que l'app (module `:scoring`), à partir des rapports publics d'[Exodus Privacy](https://exodus-privacy.eu.org/).
Site : <https://guillaumechr.github.io/VYLIA/>.

## Fonctionnement

1. `directory/apps.json` : la liste, maintenue à la main (paquet, nom, catégorie VYLIA, pays). Chaque app y figure aussi
   dans `package_overrides` de `app/src/main/assets/vylia_rules_v3.json`, pour que l'app et l'annuaire utilisent la même
   catégorie (`validate` le vérifie).
2. `./gradlew :directory-cli:installDist` puis `directory-cli generate --date AAAA-MM-JJ` : pour chaque app, le dernier
   rapport Exodus (`/api/search/{paquet}/details`) et la liste des traqueurs (`/api/trackers`), convertis en `AppScan` :
   - **toutes les permissions déclarées sont considérées comme accordées** : note d'exposition maximale ;
   - SDK cible inconnu (l'API ne le donne pas) : l'accès historique au stockage est compté ;
   - motifs de sécurité non évalués (ils demandent l'APK) ;
   - pas de rapport : « pas encore analysée ».
3. `validate`, puis `guard --previous <semaine précédente>` : la publication est bloquée si plus de 10 % des apps notées
   ont perdu leur note (panne de l'API, jeton expiré…). L'ancienne version reste alors en ligne.
4. `site` : site statique dans `directory/out/site` (HTML, CSS, un petit script local ; aucune ressource externe).

La GitHub Action [`.github/workflows/directory.yml`](../.github/workflows/directory.yml) enchaîne ces étapes chaque lundi,
publie sur GitHub Pages et garde chaque `directory.json` sur la branche `directory-data` (courbe des fiches).

Essai local, sans réseau : `directory-cli generate --date 2026-11-09 --offline --cache <dossier>` avec un cache de
réponses Exodus (`<paquet>.json`, `_trackers.json`).

## API Exodus

- Clé obligatoire, demandée à api@exodus-privacy.eu.org en décrivant le projet. Exodus ne fournit pas de clé « for end
  users products production usage » : l'accord pour cet annuaire doit être explicite avant la première publication.
- Secret GitHub `EXODUS_API_TOKEN`, jamais dans le dépôt ni dans les journaux ; User-Agent `vylia-annuaire/1.0`.
- Une requête toutes les 2 s (limite : 30 par seconde), `/api/trackers` une fois par exécution (limite : 3 par minute).
- Seuls appels réseau de la génération.

## Licence

Les résultats de l'API Exodus sont sous [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) (contenus sous
DbCL 1.0). `directory.json` est une base dérivée : il est publié sous ODbL 1.0, avec attribution à Exodus Privacy, et
téléchargeable depuis le site. Le code de l'outil et du site est sous GPL v3.

## Revue trimestrielle de la liste

Sources de la liste initiale (2026-11-09) : top 20 des téléchargements dans l'UE en 2025 (AppFigures, Euronews,
9 février 2026) ; classements App Store 2025 de France (iPhonesoft), d'Allemagne (iphone-ticker.de), d'Espagne
(teknofilo, Mobile World Live ; Similarweb T3 2025 pour Android), d'Italie (iPhoneItalia) et de Pologne (tabletowo,
imagazine) ; Framna Mobile App Trends 2025 pour les Pays-Bas. Les classements Android complets par pays ne sont pas
publics : une partie de la liste est une proposition (apps très répandues), à confirmer ou remplacer à chaque revue.
Ce n'est pas un classement officiel. Paquets vérifiés sur Google Play le 2026-11-09.
