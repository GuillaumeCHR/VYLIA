#!/usr/bin/env python3
"""Catégories des apps populaires en France, d'après leur catégorie Google Play.

Usage (depuis la racine du dépôt, poste de dev uniquement) :
  python3 tools/categories/play_categories.py fetch     # classements AppInsider France -> tools/categories/play_charts.json
  python3 tools/categories/play_categories.py check 20  # compare 20 apps tirées au hasard à leur fiche Google Play
  python3 tools/categories/play_categories.py build     # écrit "play_categories" dans app/src/main/assets/vylia_rules_v3.json

Pourquoi : ApplicationInfo.category, lue sur le téléphone, n'a que 8 valeurs et la plupart des apps ne la renseignent
pas. La catégorie Google Play est plus précise, mais le téléphone ne peut pas la demander à Google sans lui envoyer la
liste des apps installées. Ce script la relève donc sur le poste de dev, pour les 100 apps gratuites les plus
téléchargées en France de chaque catégorie Play (classements publics d'AppInsider, tirés de Google Play), et la
traduit vers les catégories de VYLIA.

Précautions :
- package_overrides (choisi à la main) et excluded_packages restent prioritaires : une app qui y figure est ignorée ;
- la catégorie Play est choisie par l'éditeur. Une catégorie Play ne donne donc jamais une catégorie VYLIA qui autorise
  un accès très sensible (SENSITIVE, vérifié par build) : l'app garde alors la catégorie qu'Android lui donne, ou la
  catégorie par défaut. Les catégories Play trop larges (Outils, Communication, Style de vie…) ne sont pas traduites ;
- une app classée dans deux catégories Play qui ne mènent pas à la même catégorie VYLIA est écartée (les classements
  Famille, Wear OS et Cadrans, qui s'ajoutent à la catégorie de la fiche, sont ignorés).
Une requête à la fois, avec une pause, comme une visite ordinaire des pages.
"""
import json
import random
import re
import sys
import time
import urllib.request
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RULES = ROOT / "app/src/main/assets/vylia_rules_v3.json"
CHARTS = ROOT / "tools/categories/play_charts.json"
CHART_URL = "https://appinsider.io/android/charts/fr/{slug}/free"
PLAY_URL = "https://play.google.com/store/apps/details?id={package}&hl=fr&gl=FR"
UA = {"User-Agent": "VYLIA-dev-tools"}
PACKAGE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
LD_JSON = re.compile(r'<script type="application/ld\+json">(.*?)</script>', re.S)
APP_URL = re.compile(r"^https://appinsider\.io/android/app/([A-Za-z0-9_.]+)$")
PLAY_CATEGORY = re.compile(r'"applicationCategory":"([A-Z_]+)"')

# Classement AppInsider -> (catégorie Google Play, catégorie VYLIA ou None si non traduite, raison).
GAMES = ["action", "adventure", "arcade", "board", "card", "casino", "casual", "educational", "music", "puzzle",
         "racing", "role-playing", "simulation", "sports", "strategy", "trivia", "word"]
MAPPING = {
    "art-design": ("ART_AND_DESIGN", None, "dessin, coloriage, fonds d'écran : rien de commun"),
    "auto-vehicles": ("AUTO_AND_VEHICLES", "TRANSPORT", "VEHICLE autorise la position en service de premier plan"),
    "beauty": ("BEAUTY", None, "salons, maquillage virtuel, boutiques : trop varié"),
    "books-reference": ("BOOKS_AND_REFERENCE", "NEWS_READING", ""),
    "business": ("BUSINESS", "PRODUCTIVITY_CLOUD", ""),
    "comics": ("COMICS", "NEWS_READING", ""),
    "communication": ("COMMUNICATION", None, "messageries, e-mail, navigateurs et VPN : MESSAGING et EMAIL autorisent les contacts"),
    "dating": ("DATING", "DATING", ""),
    "education": ("EDUCATION", "EDUCATION_FAMILY", ""),
    "entertainment": ("ENTERTAINMENT", "MEDIA_PLAYER", ""),
    "events": ("EVENTS", "TRAVEL_LEISURE", ""),
    "family": ("FAMILY", None, "apps et jeux pour enfants mêlés"),
    "finance": ("FINANCE", "FINANCE_ACCOUNTS", ""),
    "food-drink": ("FOOD_AND_DRINK", "ECOMMERCE", ""),
    "games": ("GAME", "GAME", ""),
    "health-fitness": ("HEALTH_AND_FITNESS", None, "FITNESS et HEALTH_TRACKING autorisent les données de santé"),
    "house-home": ("HOUSE_AND_HOME", None, "maison connectée (Bluetooth, Wi-Fi) et annonces immobilières mêlées"),
    "libraries-demo": ("LIBRARIES_AND_DEMO", None, "rien de commun"),
    "lifestyle": ("LIFESTYLE", None, "trop varié"),
    "maps-navigation": ("MAPS_AND_NAVIGATION", "TRANSPORT", "NAVIGATION autorise la position en service de premier plan"),
    "medical": ("MEDICAL", "HEALTH_ADMIN", ""),
    "music-audio": ("MUSIC_AND_AUDIO", "MEDIA_PLAYER", ""),
    "news-magazines": ("NEWS_AND_MAGAZINES", "NEWS_READING", ""),
    "parenting": ("PARENTING", "EDUCATION_FAMILY", ""),
    "personalization": ("PERSONALIZATION", None, "lanceurs, claviers, fonds d'écran mêlés"),
    "photography": ("PHOTOGRAPHY", "PHOTO_VIDEO", ""),
    "productivity": ("PRODUCTIVITY", "PRODUCTIVITY_CLOUD", ""),
    "shopping": ("SHOPPING", "ECOMMERCE", ""),
    "social": ("SOCIAL", None, "SOCIAL autorise le micro ; Android donne déjà CATEGORY_SOCIAL"),
    "sports": ("SPORTS", None, "résultats, paris (position exigée par la loi) et suivi d'activité mêlés"),
    "tools": ("TOOLS", None, "trop varié"),
    "travel-local": ("TRAVEL_AND_LOCAL", "TRAVEL_LEISURE", ""),
    "video-players-editors": ("VIDEO_PLAYERS", "MEDIA_PLAYER", ""),
    "watch-faces": ("WATCH_FACE", None, "apps pour montre"),
    "wear-os": ("WEAR_OS", None, "apps qui ont aussi une version pour montre, quelle que soit leur catégorie"),
    "weather": ("WEATHER", "WEATHER", ""),
    **{f"games-{g}": (f"GAME_{g.upper().replace('-', '_')}", "GAME", "") for g in GAMES},
}

# Classements qui ne correspondent pas à une catégorie Play de la fiche (une app y figure en plus de sa catégorie) :
# ignorés, pour ne pas compter comme une catégorie contradictoire.
SECONDARY = {"family", "wear-os", "watch-faces"}

# Accès qu'une catégorie choisie par l'éditeur ne doit jamais rendre « autorisés ». GET_ACCOUNTS n'en fait pas partie :
# depuis Android 8, elle ne montre à une app que ses propres comptes.
SENSITIVE = {
    "ACCESS_BACKGROUND_LOCATION", "FOREGROUND_SERVICE_LOCATION", "FOREGROUND_SERVICE_MICROPHONE", "FOREGROUND_SERVICE_CAMERA",
    "RECORD_AUDIO", "READ_CONTACTS", "WRITE_CONTACTS", "READ_SMS", "RECEIVE_SMS", "SEND_SMS",
    "READ_CALL_LOG", "WRITE_CALL_LOG", "READ_PHONE_NUMBERS", "BODY_SENSORS", "BODY_SENSORS_BACKGROUND", "ACTIVITY_RECOGNITION",
    "BIND_ACCESSIBILITY_SERVICE", "BIND_NOTIFICATION_LISTENER_SERVICE", "BIND_DEVICE_ADMIN", "MANAGE_EXTERNAL_STORAGE",
    "@HEALTH_ACTIVITY", "@HEALTH_BACKGROUND", "@HEALTH_INTIMATE", "@HEALTH_VITALS",
}


def get(url: str) -> str:
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return r.read(5_000_000).decode("utf-8", "replace")


def chart(slug: str) -> list[str]:
    for block in LD_JSON.findall(get(CHART_URL.format(slug=slug))):
        data = json.loads(block)
        if isinstance(data, dict) and data.get("@type") == "ItemList":
            items = sorted(data["itemListElement"], key=lambda i: i["position"])
            found = [m.group(1) for i in items if (m := APP_URL.match(i.get("url", "")))]
            return [p for p in found if PACKAGE.match(p)]
    raise ValueError(f"{slug} : pas de liste dans la page")


def fetch():
    charts = {}
    for slug in MAPPING:
        try:
            charts[slug] = chart(slug)
        except Exception as e:  # une page manquante ne bloque pas les autres
            print(f"{slug} : échec ({e})")
        print(f"{slug} : {len(charts.get(slug, []))} apps")
        time.sleep(3)
    CHARTS.write_text(json.dumps({"source": CHART_URL.format(slug="…"), "date": date.today().isoformat(),
                                  "charts": charts}, indent=1) + "\n", encoding="utf-8")
    print(f"{CHARTS.relative_to(ROOT)} : {sum(map(len, charts.values()))} entrées, {len(charts)} classements")


def check(count: int):
    """Contrôle par sondage : la catégorie de la fiche Google Play est-elle celle du classement ?"""
    charts = json.loads(CHARTS.read_text(encoding="utf-8"))["charts"]
    pairs = [(slug, p) for slug, packages in charts.items() for p in packages if slug not in SECONDARY | {"games"}]
    ok = 0
    for slug, package in random.sample(pairs, min(count, len(pairs))):
        try:
            m = PLAY_CATEGORY.search(get(PLAY_URL.format(package=package)))
        except Exception:
            m = None
        play = m.group(1) if m else "?"
        same = play == MAPPING[slug][0]
        ok += same
        print(f"{'ok  ' if same else 'DIFF'} {package} : classement {MAPPING[slug][0]}, fiche {play}")
        time.sleep(2)
    print(f"{ok}/{count} identiques")


def build():
    rules = json.loads(RULES.read_text(encoding="utf-8"))
    charts = json.loads(CHARTS.read_text(encoding="utf-8"))["charts"]
    for slug, (_, target, _) in MAPPING.items():
        if target is None:
            continue
        category = rules["categories"][target]
        risky = SENSITIVE & set(category.get("allowed", []))
        assert not risky, f"{slug} -> {target} autoriserait {sorted(risky)}"
    known = set(rules["package_overrides"]) | set(rules["excluded_packages"])
    seen: dict[str, set[str]] = {}
    for slug, packages in charts.items():
        if slug in SECONDARY:
            continue
        target = MAPPING.get(slug, (None, None, None))[1]
        for p in packages:
            if p not in known:
                seen.setdefault(p, set()).add(target)
    result, conflicts, untranslated = {}, 0, 0
    for p, targets in seen.items():
        mapped = targets - {None}
        if len(mapped) > 1:
            conflicts += 1
        elif mapped and None not in targets:
            result[p] = mapped.pop()
        else:
            untranslated += 1
    rules["play_categories"] = dict(sorted(result.items()))
    order = rules["category_resolution"]["order"]
    if "play_categories" not in order:
        order.insert(order.index("app_info_category"), "play_categories")
    RULES.write_text(json.dumps(rules, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    by_target: dict[str, int] = {}
    for t in result.values():
        by_target[t] = by_target.get(t, 0) + 1
    print(f"{len(result)} apps classées, {len(known & set().union(*charts.values()))} déjà choisies à la main, "
          f"{untranslated} dans une catégorie Play non traduite, {conflicts} écartées (catégories contradictoires)")
    print(", ".join(f"{t} {n}" for t, n in sorted(by_target.items(), key=lambda x: -x[1])))


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "fetch":
        fetch()
    elif command == "check":
        check(int(sys.argv[2]) if len(sys.argv) > 2 else 20)
    elif command == "build":
        build()
    else:
        sys.exit(__doc__)
