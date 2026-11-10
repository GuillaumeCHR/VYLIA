#!/usr/bin/env python3
"""Tableau Markdown des apps les plus téléchargées sur Google Play, à partir du directory.json produit par
`directory-cli generate` (même moteur de score que l'app) et de directory/top-google-play.apps.json.

Usage : top_table.py <apps.json> <directory.json> <sortie.md> [--compare <apps.json> <directory.json>]
        [--trending <apps.json> <directory.json>] [--oem <apps.json> <directory.json>]

Une app de la liste absente du directory.json n'a pas encore été lue sur Exodus : elle est comptée comme « à lire ».
"""
import json
import statistics
import sys
from collections import Counter
from pathlib import Path

TIERS = {"10B": "10 Md+", "5B": "5 Md+", "1B": "1 Md+", "500M": "500 M+"}
CATEGORIES = {
    "MESSAGING": "Messagerie", "SOCIAL": "Réseau social", "GAME": "Jeu", "MEDIA_PLAYER": "Vidéo, musique",
    "PRODUCTIVITY_CLOUD": "Bureautique, stockage", "AI_ASSISTANT": "Assistant IA", "EDUCATION_FAMILY": "Éducation",
    "NEWS_READING": "Actualités", "TRANSPORT": "Transport", "PHOTO_VIDEO": "Photo, montage", "ECOMMERCE": "Achats",
    "TRAVEL_LEISURE": "Voyage", "UTILITY": "Utilitaire", "FINANCE_ACCOUNTS": "Paiement", "NAVIGATION": "Navigation",
    "BROWSER": "Navigateur", "SECURITY_AUTH": "Sécurité", "HEALTH_ADMIN": "Santé, démarches", "DEFAULT": "Autre", "KEYBOARD": "Clavier", "EMAIL": "E-mail",
}


OLD_BEFORE = ""


def row(rank, app, entry):
    name = app["name"]
    tier = TIERS[app["tier"]] if "tier" in app else " · ".join(
        f"{c.upper()} {app['rank_' + c]}" for c in ("fr", "us") if "rank_" + c in app) or "hors classement"
    if "maker" in app:
        tier = app["maker"]
    category = CATEGORIES.get(app["category"], app["category"])
    if entry["status"] != "SCORED":
        return f"| {rank} | {name} | {tier} | {category} | – | – | – | – | pas de rapport Exodus |"
    trackers = entry.get("trackers", [])
    names = ", ".join(t["name"] for t in trackers[:3]) + (f" (+{len(trackers) - 3})" if len(trackers) > 3 else "")
    counted = sum(1 for p in entry.get("permissions", []) if p["counted"] > 0)
    ex = entry["exodus"]
    old = OLD_BEFORE and ex["updated"] < OLD_BEFORE
    report = f"[{ex['version_name'] or 'rapport'}, {ex['updated']}]({ex['url']}){' (ancien)' if old else ''}"
    return (f"| {rank} | {name} | {tier} | {category} | **{entry['grade']}** | {entry['score']} | {len(trackers)}"
            f"{' : ' + names if names else ''} | {counted} | {report} |")


def table(apps, by_package, column="Téléchargements"):
    head = (f"| # | App | {column} | Catégorie | Note | Score /100 | Traqueurs | Permissions pénalisées | Rapport Exodus |\n"
            "|---:|---|---|---|:---:|---:|---|---:|---|")
    ordered = sorted(apps, key=lambda a: (by_package[a["package"]]["status"] != "SCORED",
                                          -(by_package[a["package"]].get("score") or 0), a["name"].lower()))
    return head + "\n" + "\n".join(row(i, a, by_package[a["package"]]) for i, a in enumerate(ordered, 1))


def summary(apps, by_package):
    scored = [by_package[a["package"]] for a in apps if by_package[a["package"]]["status"] == "SCORED"]
    grades = Counter(e["grade"] for e in scored)
    trackers = [len(e.get("trackers", [])) for e in scored]
    if not scored:
        return f"Aucune app notée sur {len(apps)}."
    without = sum(1 for t in trackers if t == 0)
    return (f"{len(scored)} apps notées sur {len(apps)}. Notes : "
            + ", ".join(f"{g} : {grades.get(g, 0)}" for g in "ABCDE")
            + f". Traqueurs par app : médiane {statistics.median(trackers):g}, maximum {max(trackers)} ; "
            f"{without} app{'s' if without > 1 else ''} sans traqueur connu.")


def compare_section(compare, by_package):
    if not compare:
        return ""
    return f"""
## Pour comparer

Les alternatives que l'app VYLIA propose (écran Gérer, « Alternatives »), quand elles sont sur Google Play, notées avec
la même méthode : un point de repère pour lire les notes ci-dessus. La plupart sont des logiciels libres.

{summary(compare, by_package)}

{table(compare, by_package, "Classement")}
"""


def pending_note(apps, by_package):
    pending = sum(1 for a in apps if a["package"] not in by_package)
    return f"\n\nLecture en cours : {pending} apps de la liste restent à lire sur Exodus." if pending else ""


def known(apps, by_package):
    return [a for a in apps if a["package"] in by_package]


def trending_section(trending, by_package):
    if not trending:
        return ""
    ready = known(trending, by_package)
    return f"""
## En ce moment sur Google Play

Les 200 premières apps gratuites du classement du jour de Google Play, en France et aux États-Unis, d'après
AppInsider ([France](https://appinsider.io/android/charts/fr/all/free),
[États-Unis](https://appinsider.io/android/charts/us/all/free), classement du 9 novembre 2026). Ce classement suit
les téléchargements récents, pas le total : on y trouve les apps du moment. Sont écartées les apps de Google, des
fabricants et des opérateurs (notées dans la section suivante) et celles déjà notées plus haut. Paquet trouvé par la
recherche Google Play, éditeur vérifié sur la fiche. Quand la grille de l'app ne connaissait pas l'app, sa catégorie a
été déduite de sa catégorie Google Play et ajoutée à la grille de l'app : le téléphone la note avec la même catégorie.
La colonne « Classement » donne la place en France (FR) et aux États-Unis (US).

{summary(ready, by_package)}{pending_note(trending, by_package)}

{table(ready, by_package, "Classement")}
"""


MAKERS_ORDER = ("Google", "Samsung", "Xiaomi", "Huawei", "Motorola", "vivo", "Oppo", "Transsion", "Apple")


def maker_summary(oem, by_package):
    rows = ["| Constructeur | Apps | Notées | Score médian | A | B | C | D | E |", "|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
    makers = sorted({a["maker"] for a in oem}, key=lambda m: MAKERS_ORDER.index(m) if m in MAKERS_ORDER else 99)
    for m in makers:
        apps = [a for a in oem if a["maker"] == m]
        scored = [by_package[a["package"]] for a in apps if by_package[a["package"]]["status"] == "SCORED"]
        grades = Counter(e["grade"] for e in scored)
        med = f"{statistics.median(e['score'] for e in scored):g}" if scored else "–"
        rows.append(f"| {m} | {len(apps)} | {len(scored)} | {med} | " + " | ".join(str(grades.get(g, 0)) for g in "ABCDE") + " |")
    return "\n".join(rows)


def oem_section(oem, by_package):
    if not oem:
        return ""
    ready = known(oem, by_package)
    return f"""
## Google et les constructeurs

Les apps de Google et des fabricants de téléphones (Samsung, Xiaomi, Huawei, Motorola, vivo, Oppo, Transsion) et
celles d'Apple sur Android, écartées des sections précédentes. Beaucoup sont préinstallées et impossibles à
désinstaller ; leur rapport Exodus porte sur la version publiée sur Google Play, quand elle existe. Les applications
système qui ne sont pas sur Google Play n'ont pas de rapport.

{maker_summary(ready, by_package)}

{summary(ready, by_package)}{pending_note(oem, by_package)}

{table(ready, by_package, "Constructeur")}
"""


def load(pair):
    if not pair:
        return [], []
    return json.loads(Path(pair[0]).read_text())["apps"], json.loads(Path(pair[1]).read_text())["apps"]


def main(apps_file, directory_file, out_file, compare=None, trending=None, oem=None):
    meta = json.loads(Path(apps_file).read_text())
    directory = json.loads(Path(directory_file).read_text())
    compare, compare_entries = load(compare)
    trending, trending_entries = load(trending)
    oem, oem_entries = load(oem)
    by_package = {a["package"]: a for a in directory["apps"] + compare_entries + trending_entries + oem_entries}
    global OLD_BEFORE
    year, rest = directory["generated"].split("-", 1)
    OLD_BEFORE = f"{int(year) - 1}-{rest}"
    main_apps = [a for a in meta["apps"] if not a.get("preinstalled_some")]
    pre_apps = [a for a in meta["apps"] if a.get("preinstalled_some")]
    reports = sorted(e["exodus"]["updated"] for e in by_package.values() if e.get("exodus"))
    text = f"""# Les apps les plus téléchargées sur Google Play, notées par VYLIA

Tableau ponctuel du {directory['generated']}. Grille VYLIA {directory['rules_version']}, le même moteur de score que
l'application.

## Quelles apps

La liste vient de la page Wikipedia
[List of most-downloaded Google Play applications]({meta['source']}) (révision lue le {directory['generated']}),
paliers de 10 milliards à 500 millions de téléchargements, apps gratuites. Sont écartées :

- les apps que Wikipedia marque comme préinstallées : leur compteur additionne les téléphones vendus, pas des choix ;
- toutes les apps de Google et des fabricants (Samsung, Xiaomi, Huawei, Motorola, vivo, Oppo, Transsion), des
  opérateurs, les services système et les apps arrêtées ; celles de Google et des fabricants sont notées à part, plus
  bas.

Les apps de développeurs indépendants préinstallées seulement sur certains téléphones (Facebook, Instagram, TikTok,
Netflix, Microsoft Office…) sont notées à part, plus bas.

Classement par score décroissant ; la colonne « Téléchargements » donne le palier Wikipedia.

## Résultats

{summary(main_apps, by_package)}

{table(main_apps, by_package)}

## Apps préinstallées sur certains téléphones

{summary(pre_apps, by_package)}

{table(pre_apps, by_package)}
{compare_section(compare, by_package)}{trending_section(trending, by_package)}{oem_section(oem, by_package)}
## Comment lire ce tableau

- **Note de A à E et score sur 100** : calculés avec la grille VYLIA, comme dans l'app. Les permissions sont jugées
  selon ce que l'app fait (la localisation ne coûte rien à une app de navigation, beaucoup à un jeu), les traqueurs
  selon leur type.
- **Exposition maximale** : hors du téléphone, on ne sait pas ce que vous avez accordé. Toutes les permissions déclarées
  sont comptées comme accordées. Sur votre téléphone, l'app peut obtenir une meilleure note si vous refusez des accès.
- **SDK cible inconnu** : les rapports ne le donnent pas ; l'ancien accès au stockage est compté.
- **Traqueurs** : signatures trouvées dans le code par l'analyse statique d'Exodus Privacy. Leur présence ne prouve
  pas leur activité, et une app peut contenir des traqueurs qu'Exodus ne connaît pas encore.
- **Ce que la note ne mesure pas** : ce que l'éditeur fait des données sur ses serveurs, et les motifs de sécurité,
  qui demandent l'APK.
- **(ancien)** : le dernier rapport Exodus a plus d'un an ; l'app a pu changer depuis.
- Rapports Exodus du {reports[0]} au {reports[-1]} (dernier rapport de chaque app au moment de la lecture).

## Données et licence

Contient des informations d'[Exodus Privacy](https://exodus-privacy.eu.org), disponibles sous
[Open Database License (ODbL 1.0)](https://opendatacommons.org/licenses/odbl/1-0/), lues sur les pages publiques des
rapports. Les données complètes de ce tableau (permissions, traqueurs, badges) sont dans
[`top-google-play.json`](top-google-play.json), [`compare-google-play.json`](compare-google-play.json),
[`trending-google-play.json`](trending-google-play.json) et [`oem-google-play.json`](oem-google-play.json), publiés
sous ODbL 1.0. VYLIA est une application indépendante, ni affiliée ni approuvée par Exodus Privacy.

Reproduire, pour chaque liste `directory/<liste>-google-play.apps.json` (`top`, `compare`, `trending`, `oem`) :

1. `python3 tools/directory/exodus_pages.py directory/<liste>-google-play.apps.json directory/cache/top-google-play`,
   puis la même commande suivie de `--resolve` (vrais numéros de rapport) ;
2. `python3 tools/directory/rules_for.py app/src/main/assets/vylia_rules_v3.json directory/cache/rules-classement.json
   directory/compare-google-play.apps.json directory/trending-google-play.apps.json directory/oem-google-play.apps.json`
   (copie de la grille avec les catégories des apps qu'elle ne connaît pas encore ; elles ont été ajoutées à la
   grille de l'app le 10 novembre 2026, pour que le téléphone et le classement utilisent la même catégorie) ;
3. `directory-cli generate --date 2026-11-09 --offline --apps directory/<liste>-google-play.apps.json --cache
   directory/cache/top-google-play --rules directory/cache/rules-classement.json --out directory/<liste>-google-play.json` ;
4. `python3 tools/directory/top_table.py directory/top-google-play.apps.json directory/top-google-play.json
   directory/top-google-play.md --compare … --trending … --oem …` (chaque option suivie de la liste et de son
   directory.json).
"""
    Path(out_file).write_text(text)
    print(f"{out_file} écrit")


if __name__ == "__main__":
    args = sys.argv[1:]
    extra = {}
    while len(args) > 3 and args[-3] in ("--compare", "--trending", "--oem"):
        extra[args[-3][2:]] = args[-2:]
        args = args[:-3]
    if len(args) != 3:
        raise SystemExit(__doc__)
    main(*args, **extra)
