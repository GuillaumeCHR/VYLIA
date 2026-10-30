# Outils

## Signatures de traqueurs Exodus

```
python3 tools/update_exodus_trackers.py            # télécharge l'API Exodus (poste de dev uniquement)
python3 tools/update_exodus_trackers.py --source f # à partir d'un fichier déjà téléchargé
```

Produit `app/src/main/assets/exodus_trackers.json`, le snapshot embarqué dans l'app (aucun appel
réseau à l'exécution). Données Exodus Privacy sous ODbL 1.0 : garder l'attribution dans l'écran
Paramètres > Crédits.

### Révision du mapping des catégories (tous les 6 mois)

Environ un quart des traqueurs Exodus n'ont pas de catégorie ; `exodus_category_overrides.json`
les complète, car le score v2 pondère chaque traqueur par sa catégorie (Advertisement 12,
Profiling/Location 10, …, sans catégorie 5). À chaque exécution, le script signale :

- une révision échue (`last_reviewed` + `review_interval_days`, 180 jours) ;
- les nouveaux traqueurs sans catégorie, absents du mapping, à classer ;
- les entrées devenues inutiles (Exodus les catégorise désormais, ou les a retirées).

Pour réviser : corriger `categories` (ou déplacer un SDK sans finalité de pistage vers
`uncategorized`), mettre `last_reviewed` à la date du jour, relancer le script, puis
`./gradlew testDebugUnitTest` (les cas de `PrivacyScorerTest` reposent sur ces catégories).
Le changement de catégories invalide le cache d'analyse sur les appareils : tout est ré-analysé.
