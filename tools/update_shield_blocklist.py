#!/usr/bin/env python3
"""Génère la liste de blocage du Bouclier VYLIA (VPN local, filtrage DNS sur le téléphone).

Le bouclier bloque des traqueurs, pas des publicités : aucune liste de publicité (EasyList, AdGuard DNS filter, Mobile
Ads ou French, retirées le 2026-11-14) et aucune régie publicitaire des apps. Google Play interdit aux apps de gêner
l'affichage des pubs d'autres apps.

Sources, téléchargées sur le poste de dev (l'app peut les mettre à jour depuis Paramètres › Bases de données) :
  - Exodus Privacy, champ network_signature de /api/trackers (ODbL 1.0) : domaines contactés par les SDK de
    traçage, avec le nom du traqueur, affiché dans l'app à chaque blocage ;
  - EasyPrivacy (pistage, https://easylist.to, GPL v3 ou CC BY-SA 3.0, au choix ; VYLIA étant sous GPL v3, c'est elle
    qui s'applique) : seules les règles qui visent un domaine entier ont un sens au niveau DNS (« ||domaine^ »,
    « ||domaine^$important », lignes hosts) ; « $badfilter » annule la règle qu'il vise ; les exceptions
    « @@||domaine^ », « @@||domaine^| » et « @@|domaine^| » sont respectées.
    L'app refait la même fusion quand elle télécharge les listes (shield/…/BlocklistBuilder.kt).
  Aucune liste de contrôle parental : VYLIA bloque des traqueurs, jamais un site pour son contenu (légal).
  - EXTRA : complément maison, domaine par domaine, pour ce qu'aucune liste ne couvre (avec la raison).

Groupes :
  - « web » : pistage des sites, mesure d'audience et profilage des apps ;
  - « mobile » : vide. Les traqueurs Exodus de catégorie Advertisement (régies des apps et des jeux) n'y sont pas
    écrits, et ni eux ni leurs sous-domaines ne restent dans « web », même si EasyPrivacy les cite. Le format garde
    le groupe pour un futur filtrage des régies, en option désactivée d'office (ShieldFilter.AVAILABLE dans l'app) ;
  - « allow » : exceptions des listes, et NEVER_BLOCK_ADS (Google AdMob, DoubleClick), jamais bloqués.

Prudence, pour ne pas casser d'apps :
  - côté Exodus, seuls les traqueurs de catégorie Advertisement, Analytics, Profiling ou Identification sont
    retenus (catégories complétées par app/src/main/assets/exodus_category_overrides.json, comme pour le score) ; les
    SDK seulement Location (cartes) ou Crash reporting ne le sont pas ;
  - NEVER_BLOCK : domaines de plateformes jamais bloqués, ni eux ni leurs sous-domaines déclarés en entier
    (une signature Exodus « \\.facebook\\.com » bloquerait sinon toute l'app Facebook) ;
  - les signatures Exodus qui ne sont pas des noms de domaine (expressions régulières, adresse IP, mot seul)
    sont écartées et listées.

Un troisième groupe, « spyware », vient des indicateurs MVT (app/src/main/assets/mvt_iocs.json, produit par
tools/update_mvt_iocs.py) : les domaines des campagnes de logiciels espions de surveillance ciblée (Pegasus,
Predator…), bloqués dès que le bouclier tourne, avec le nom de la campagne. Les domaines des stalkerwares n'y sont
volontairement pas : couper en silence le serveur d'un stalkerware peut alerter la personne qui surveille le
téléphone (Echap conseille de penser d'abord à sa sécurité).

Usage : python3 tools/update_shield_blocklist.py [--exodus api.json] [--easyprivacy easyprivacy.txt]
        python3 tools/update_shield_blocklist.py --spyware-only   # ne refait que le groupe spyware, sans réseau
Produit shield/src/main/assets/shield_blocklist.json.
"""
import argparse
import datetime
import json
import pathlib
import re
import urllib.request

EXODUS_URL = "https://reports.exodus-privacy.eu.org/api/trackers"
EASYPRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"

# Complément maison : domaines de traçage qu'aucune liste ne couvre, avec leur groupe (même liste que EXTRA dans
# shield/…/ExodusShieldDomains.kt, qui l'applique à la base Exodus téléchargée par l'app). Chaque entrée est
# vérifiée (rôle du domaine, et absence de fonction utile à l'app qui l'appelle) ; le nom est affiché dans l'app.
EXTRA = {
    # Google Analytics for Firebase : mesure d'audience des apps (événements, sessions, conversions publicitaires).
    # Le bloquer n'empêche ni la connexion, ni les notifications, ni la base de données Firebase.
    "Google Firebase Analytics": ("web", ["app-measurement.com"]),
}
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "shield/src/main/assets/shield_blocklist.json"
OVERRIDES = ROOT / "app/src/main/assets/exodus_category_overrides.json"
MVT = ROOT / "app/src/main/assets/mvt_iocs.json"

# Serveurs publicitaires de Google (AdMob, DoubleClick) : jamais bloqués, ni eux ni leurs sous-domaines (groupe
# « allow »). Même liste que NEVER_BLOCK_ADS dans shield/…/BlocklistBuilder.kt.
NEVER_BLOCK_ADS = {
    "2mdn.net", "dmtry.com", "doubleclick.com", "doubleclick.net", "mng-ads.com", "mobileads.google.com",
    "ads.google.com", "googlesyndication.com", "googleadservices.com", "adservice.google.com", "adservice.g.cn",
    "admob.com", "googletagservices.com",
}

BLOCKED_CATEGORIES = {"Advertisement", "Analytics", "Profiling", "Identification"}

# Domaines (et leurs sous-domaines déclarés en entier) qui ne sont jamais bloqués : bloquer l'un d'eux casserait des
# apps entières. Un sous-domaine précis (ex. firebaselogging-pa.googleapis.com) reste bloquable. Même liste que
# NEVER_BLOCK dans shield/…/BlocklistBuilder.kt.
NEVER_BLOCK = {
    "google.com", "googleapis.com", "gstatic.com", "youtube.com", "android.com",
    "facebook.com", "fbcdn.net", "instagram.com", "whatsapp.net", "whatsapp.com",
    "apple.com", "icloud.com", "microsoft.com", "live.com", "amazon.com", "amazonaws.com",
    "cloudfront.net", "akamaihd.net", "akamaized.net", "cloudflare.com", "cloudflare.net",
    "github.com", "githubusercontent.com", "twitter.com", "x.com", "tiktok.com", "yahoo.com", "qq.com", "baidu.com",
}

DOMAIN = re.compile(r"^(?=.{4,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$")
EASY_RULE = re.compile(r"^\|\|([a-z0-9.-]+)\^(\$important)?$")
EASY_BADFILTER = re.compile(r"^\|\|([a-z0-9.-]+)\^\$(important,)?badfilter$")
EASY_EXCEPTION = re.compile(r"^@@\|\|?([a-z0-9.-]+)\^\|?(\$important)?$")
HOSTS_RULE = re.compile(r"^(?:0\.0\.0\.0|127\.0\.0\.1|::|::1)\s+([a-z0-9.-]+)\s*(?:#.*)?$")


def fetch(url, accept):
    request = urllib.request.Request(url, headers={"Accept": accept, "User-Agent": "vylia-snapshot"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode("utf-8")


def overrides():
    data = json.loads(OVERRIDES.read_text(encoding="utf-8"))
    return data["categories"] if data.get("validated") else {}


def is_protected(domain):
    return domain in NEVER_BLOCK


def spyware_group():
    """Groupe « spyware » : {nom de campagne: domaines}, depuis les indicateurs MVT embarqués dans l'app."""
    mvt = json.loads(MVT.read_text(encoding="utf-8"))
    trackers, seen = {}, set()
    for campaign in mvt["campaigns"]:
        domains = sorted(d for d in campaign["domains"] if DOMAIN.match(d) and not is_protected(d) and d not in seen)
        seen.update(domains)
        if domains:
            trackers[campaign["name"]] = domains
    source = {"name": "MVT, mvt-indicators", "url": mvt["url"], "license": "MIT", "version": mvt["commit"][:12]}
    return {"trackers": trackers, "domains": []}, source


def exodus_domains(api, mapping):
    """{domaine: (nom du traqueur, groupe)} et la liste des signatures écartées."""
    domains, skipped = {}, []
    for tracker in sorted(api["trackers"].values(), key=lambda t: t["id"]):
        categories = set(tracker.get("categories") or mapping.get(tracker["name"], []))
        if not categories & BLOCKED_CATEGORIES:
            continue
        for part in filter(None, (tracker.get("network_signature") or "").split("|")):
            domain = part.replace("\\.", ".").replace("\\-", "-").lower()
            domain = re.sub(r"^(\.\*)?\.", "", domain)  # « .*\.x\.com » ou « \.x\.com » : x.com et ses sous-domaines
            if not DOMAIN.match(domain):
                skipped.append(f"{tracker['name']} : {part}")
            elif not is_protected(domain):
                group = "mobile" if "Advertisement" in categories else "web"
                domains.setdefault(domain, (tracker["name"], group))
    return domains, skipped


def adblock_domains(text):
    """Règles de domaine entier d'une liste au format Adblock Plus : (domaines, exceptions, version)."""
    rules, exceptions, cancelled = set(), set(), set()
    for raw in text.splitlines():
        line = raw.strip().lower()
        if m := EASY_RULE.match(line):
            rules.add(m.group(1))
        elif m := EASY_BADFILTER.match(line):
            cancelled.add(m.group(1))
        elif m := EASY_EXCEPTION.match(line):
            exceptions.add(m.group(1))
        elif m := HOSTS_RULE.match(line):
            rules.add(m.group(1))
    version = next((l.split(":", 1)[1].strip() for l in text.splitlines()[:10] if l.startswith("! Version:")), "?")
    kept = {d for d in rules - cancelled if DOMAIN.match(d) and not is_protected(d)}
    return kept, exceptions, version


def parents(domain):
    labels = domain.split(".")
    return (".".join(labels[i:]) for i in range(1, len(labels) - 1))


def drop_redundant(domains, also_covered_by=frozenset()):
    """Retire les sous-domaines d'un domaine déjà présent (la recherche se fait par suffixe)."""
    return {d for d in domains if not any(p in domains or p in also_covered_by for p in parents(d))}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--exodus", help="réponse de l'API Exodus déjà téléchargée")
    parser.add_argument("--easyprivacy", help="easyprivacy.txt déjà téléchargé")
    parser.add_argument("--spyware-only", action="store_true", help="ne refait que le groupe spyware (MVT)")
    args = parser.parse_args()
    if args.spyware_only:
        snapshot = json.loads(OUTPUT.read_text(encoding="utf-8"))
        spyware, source = spyware_group()
        snapshot["groups"]["spyware"] = spyware
        snapshot["sources"] = [s for s in snapshot["sources"] if not s["name"].startswith("MVT")] + [source]
        OUTPUT.write_text(json.dumps(snapshot, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
        count = sum(len(d) for d in spyware["trackers"].values())
        print(f"spyware : {count} domaines, {len(spyware['trackers'])} campagnes -> {OUTPUT.relative_to(ROOT)}")
        return

    api = json.loads(pathlib.Path(args.exodus).read_text(encoding="utf-8") if args.exodus else fetch(EXODUS_URL, "application/json"))
    privacy_text = pathlib.Path(args.easyprivacy).read_text(encoding="utf-8") if args.easyprivacy else fetch(EASYPRIVACY_URL, "text/plain")

    spyware, spyware_source = spyware_group()
    exodus, skipped = exodus_domains(api, overrides())
    easyprivacy, privacy_exceptions, privacy_version = adblock_domains(privacy_text)
    for name, (group, domains) in EXTRA.items():
        for domain in domains:
            assert group == "web" and DOMAIN.match(domain) and not is_protected(domain), domain
            exodus.setdefault(domain, (name, group))  # même place que les domaines Exodus : le nom est affiché
    exceptions = privacy_exceptions | NEVER_BLOCK_ADS
    named = {d: v for d, v in exodus.items() if d not in exceptions}

    # Régies publicitaires (traqueurs Exodus Advertisement) : ni elles ni leurs sous-domaines ne sont bloqués.
    ads = {d for d, (_, g) in named.items() if g == "mobile"}
    web_all = ({d for d, (_, g) in named.items() if g == "web"} | easyprivacy) - exceptions - ads
    web = drop_redundant(web_all, also_covered_by=ads)
    web = {d for d in web if not any(p in exceptions for p in parents(d))}

    def group_file(domains):
        trackers = {}
        for domain in sorted(domains):
            if domain in named:
                trackers.setdefault(named[domain][0], []).append(domain)
        return {
            # nom du traqueur (Exodus ou complément maison) -> domaines (le nom est affiché à chaque blocage)
            "trackers": trackers,
            # domaines des listes sans traqueur nommé
            "domains": sorted(d for d in domains if d not in named),
        }

    snapshot = {
        "version": 2,
        "generated": datetime.date.today().isoformat(),
        "sources": [
            {"name": "Exodus Privacy", "url": EXODUS_URL, "license": "ODbL 1.0"},
            {"name": "EasyPrivacy", "url": EASYPRIVACY_URL, "license": "GPL v3", "version": privacy_version},
            {"name": "VYLIA (complément maison)", "license": "GPL v3"},
            spyware_source,
        ],
        # « allow » : exceptions ; ni ces domaines ni leurs sous-domaines ne sont bloqués (logiciels espions exceptés),
        # même si un domaine parent est listé.
        "groups": {"web": group_file(web), "mobile": group_file(set()), "spyware": spyware,
                   "allow": {"trackers": {}, "domains": sorted(d for d in exceptions if DOMAIN.match(d))}},
    }
    OUTPUT.write_text(json.dumps(snapshot, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Exodus : {len(exodus)} domaines ({len(skipped)} signatures écartées, {len(ads)} de régies non bloqués) ; "
          f"EasyPrivacy {privacy_version} : {len(easyprivacy)} domaines ; maison : {sum(len(d) for _, d in EXTRA.values())} ; "
          f"{len(exceptions)} exceptions.")
    print(f"web : {len(web)} domaines -> {OUTPUT.relative_to(ROOT)} ({OUTPUT.stat().st_size // 1024} Ko)")
    for line in skipped:
        print("  écartée :", line)


if __name__ == "__main__":
    main()
