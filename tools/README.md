# Outils

## Signatures de traqueurs Exodus

```
python3 tools/update_exodus_trackers.py            # télécharge l'API Exodus (poste de dev uniquement)
python3 tools/update_exodus_trackers.py --source f # à partir d'un fichier déjà téléchargé
```

Produit `app/src/main/assets/exodus_trackers.json`, le snapshot embarqué dans l'app (aucun appel
réseau à l'exécution). Données Exodus Privacy sous ODbL 1.0 : garder l'attribution dans l'écran
Paramètres › Base de traqueurs et À propos.

### Révision du mapping des catégories (tous les 6 mois)

Environ un quart des traqueurs Exodus n'ont pas de catégorie ; `app/src/main/assets/exodus_category_overrides.json`
les complète (dans l'app aussi : la base Exodus téléchargée et le bouclier s'en servent, et l'app le met à jour
depuis ce dépôt avec les autres bases), car le score v2 pondère chaque traqueur par sa catégorie (Advertisement 12,
Profiling/Location 10, …, sans catégorie 5). À chaque exécution, le script signale :

- une révision échue (`last_reviewed` + `review_interval_days`, 180 jours) ;
- les nouveaux traqueurs sans catégorie, absents du mapping, à classer ;
- les entrées devenues inutiles (Exodus les catégorise désormais, ou les a retirées).

Pour réviser : corriger `categories` (ou déplacer un SDK sans finalité de pistage vers
`uncategorized`), mettre `last_reviewed` à la date du jour, relancer le script, puis
`./gradlew testDebugUnitTest` (les cas de `PrivacyScorerTest` reposent sur ces catégories).
Le changement de catégories invalide le cache d'analyse sur les appareils : tout est ré-analysé.

## Liste de blocage du Bouclier VYLIA

```
python3 tools/update_shield_blocklist.py          # télécharge Exodus, EasyPrivacy et EasyList
python3 tools/update_shield_blocklist.py --exodus api.json --easyprivacy easyprivacy.txt --easylist easylist.txt
```

Produit `shield/src/main/assets/shield_blocklist.json` : domaines de traçage Exodus (pub, analytics, profilage,
identification ; ODbL) et règles de domaine EasyPrivacy et EasyList (GPL v3), sans les domaines de plateformes listés dans
`NEVER_BLOCK`. Après mise à jour : `./gradlew testDebugUnitTest` (`BlocklistTest` contrôle la liste embarquée).
Détails : `docs/shield.md`.

## Stalkerwares connus (Echap)

```
python3 tools/update_stalkerware_iocs.py          # télécharge ioc.yaml d'Echap (branche master)
python3 tools/update_stalkerware_iocs.py --ioc ioc.yaml --commit <sha>
```

Produit `app/src/main/assets/stalkerware_iocs.json` depuis
[stalkerware-indicators](https://github.com/AssoEchap/stalkerware-indicators) (CC BY 4.0) : pour chaque stalkerware,
son nom, ses noms de paquet et les empreintes SHA-1 de ses certificats, avec le commit source. Nécessite PyYAML.
Chaque valeur est vérifiée ; une entrée invalide fait échouer la génération. Après mise à jour :
`./gradlew testDebugUnitTest` (`BadgeEngineTest` contrôle la base embarquée). L'écran À propos affiche la version.

## Apps préinstallées (UAD-ng)

```
python3 tools/update_uad_list.py                         # télécharge le dernier uad_lists.json (branche main)
python3 tools/update_uad_list.py --source uad_lists.json --commit <sha>
```

Copie `uad_lists.json` d'[Universal Android Debloater Next Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation)
(GPL v3) **tel quel** dans `app/src/main/assets/`, et note le commit source dans `uad_lists.source.json`. L'app lit
le fichier embarqué et celui qu'elle télécharge avec le même code (`UadList.parse`), qui écarte les entrées invalides ;
le script vérifie seulement qu'il s'agit bien de la liste (au moins 4 000 paquets). Après mise à jour :
`./gradlew testDebugUnitTest` (`PreinstalledTest` lit la liste embarquée).

### Traductions françaises des descriptions

```
python3 tools/translate_uad.py status            # traduites, manquantes, orphelines
python3 tools/translate_uad.py export <dossier>  # lots {clé: texte anglais} des descriptions sans traduction
python3 tools/translate_uad.py merge <dossier>   # fusionne les lots traduits {clé: texte français}
```

`app/src/main/assets/uad_descriptions_fr.json` (GPL v3, comme la liste) : une traduction par description anglaise,
indexée par l'empreinte de son texte (SHA-256, 16 caractères, comme `UadList.keyOf`). Après une mise à jour de la
liste, `export` ne propose que les descriptions nouvelles ou modifiées ; une description modifiée perd sa traduction
et l'app affiche l'anglais en attendant. `merge` vérifie que chaque traduction garde les adresses web et les noms de
paquets de l'original. Le fichier est publié dans ce dépôt et l'app le met à jour avec les autres bases.

## Logiciels espions de surveillance ciblée (MVT)

```
python3 tools/update_mvt_iocs.py                      # campagnes d'indicators.yaml, aux commits figés dans le script
python3 tools/update_shield_blocklist.py --spyware-only   # reporte leurs domaines dans le bouclier, sans réseau
```

Produit `app/src/main/assets/mvt_iocs.json` depuis [mvt-indicators](https://github.com/mvt-project/mvt-indicators)
(MIT ; fichiers d'Amnesty International repris avec attribution) : pour chaque campagne sauf celle des stalkerwares
(déjà couverte par Echap), ses noms de paquet, empreintes de certificat et domaines. Les paquets ne sont comparés qu'aux
apps installées par l'utilisateur (plusieurs imitent un nom système) ; les domaines forment le groupe « spyware » du
bouclier, toujours bloqué. Pour suivre MVT : mettre à jour `MVT_COMMIT` (et `COMMITS`), ajouter le nom court de toute
nouvelle campagne dans `SHORT_NAMES`, puis `./gradlew testDebugUnitTest` (`MvtDatabaseTest`, `BlocklistTest`).

## Textes traduits

```
python3 tools/strings/generate.py
```

Tous les textes de l'app sont décrits dans `tools/strings/` (une ligne = clé, anglais, français) : `ui_*.py` pour les
écrans, `glossary.py` et `glossary_extra.py` pour les lexiques (permissions, traqueurs, catégories, badges). Le script
régénère `values/strings.xml`, `values-fr/strings.xml`, les deux `strings_glossary.xml`, et les blocs Kotlin balisés
`// region generated:…` (PermissionGlossary, CategoryRationale, AppBadges). Ne pas modifier ces fichiers à la main :
changer la source, puis relancer le script. Il est idempotent : sans changement de source, il n'écrit rien.

## Grille de score

```
python3 tools/scoring/propose_v3.py
```

Génère `app/src/main/assets/vylia_rules_v3.json` et la page de relecture `docs/scoring/matrice_v3.html` à partir des
règles v2.1 archivées et des ajouts v3 décrits dans le script (poids, contextes par catégorie, groupes, badges).
Pour faire évoluer la grille : modifier le script, le relancer, puis comparer les scores avant / après sur un
téléphone avec la version de développement (`adb logcat -s VyliaScores`, une ligne par app).
