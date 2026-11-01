#!/usr/bin/env python3
"""Génère la liste de blocage du Bouclier VYLIA (VPN local, filtrage DNS sur le téléphone).

Sources, téléchargées sur le poste de dev uniquement (l'app ne télécharge rien pour le bouclier) :
  - Exodus Privacy, champ network_signature de /api/trackers (ODbL 1.0) : domaines contactés par les SDK de
    traçage, avec le nom du traqueur, affiché dans l'app à chaque blocage ;
  - EasyPrivacy (pistage) et EasyList (publicité) (https://easylist.to, GPL v3 ou CC BY-SA 3.0, au choix ; VYLIA
    étant sous GPL v3, c'est elle qui s'applique) : seules les règles qui visent un domaine entier, « ||domaine^ »
    sans chemin ni option, ont un sens au niveau DNS ; pour EasyList, ce sont surtout ses serveurs de publicité.
    Les exceptions de même forme (« @@||domaine^ ») de l'une ou l'autre liste sont respectées ;
  - AdGuard Mobile Ads filter (https://github.com/AdguardTeam/AdguardFilters, GPL v3) : les régies des apps et jeux
    mobiles (ironSource, Meta Audience Network, Pangle, Mintegral, Unity Ads…), qu'EasyList, faite pour le web, ne
    couvre pas ; mêmes règles de domaine entier ;
  - MOBILE_EXTRA : complément maison, domaine par domaine, pour ce qu'aucune liste ne couvre (avec la raison).

Prudence, pour ne pas casser d'apps :
  - côté Exodus, seuls les traqueurs de catégorie Advertisement, Analytics, Profiling ou Identification sont
    bloqués (catégories complétées par tools/exodus_category_overrides.json, comme pour le score) ; les SDK
    seulement Location (cartes) ou Crash reporting ne le sont pas ;
  - NEVER_BLOCK : domaines de plateformes jamais bloqués, ni eux ni leurs sous-domaines déclarés en entier
    (une signature Exodus « \\.facebook\\.com » bloquerait sinon toute l'app Facebook) ;
  - les signatures Exodus qui ne sont pas des noms de domaine (expressions régulières, adresse IP, mot seul)
    sont écartées et listées.

Usage : python3 tools/update_shield_blocklist.py [--exodus api.json] [--easyprivacy easyprivacy.txt]
                                                  [--easylist easylist.txt] [--adguard-mobile 11.txt]
Produit app/src/main/assets/shield_blocklist.json.
"""
import argparse
import datetime
import json
import pathlib
import re
import urllib.request

EXODUS_URL = "https://reports.exodus-privacy.eu.org/api/trackers"
EASYPRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
ADGUARD_MOBILE_URL = "https://filters.adtidy.org/extension/ublock/filters/11.txt"

# Complément maison : domaines de traçage ou de pub des apps mobiles qu'aucune liste ne couvre. Chaque entrée est
# vérifiée (rôle du domaine, et absence de fonction utile à l'app qui l'appelle) ; le nom est affiché dans l'app.
MOBILE_EXTRA = {
    # Google Analytics for Firebase : mesure d'audience des apps (événements, sessions, conversions publicitaires).
    # Le bloquer n'empêche ni la connexion, ni les notifications, ni la base de données Firebase.
    "Google Firebase Analytics": ["app-measurement.com"],
}
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "app/src/main/assets/shield_blocklist.json"
OVERRIDES = ROOT / "tools/exodus_category_overrides.json"

BLOCKED_CATEGORIES = {"Advertisement", "Analytics", "Profiling", "Identification"}

# Domaines (et leurs sous-domaines déclarés en entier) qui ne sont jamais bloqués : bloquer l'un d'eux casserait des
# apps entières. Un sous-domaine précis (ex. firebaselogging-pa.googleapis.com) reste bloquable.
NEVER_BLOCK = {
    "google.com", "googleapis.com", "gstatic.com", "youtube.com", "android.com",
    "facebook.com", "fbcdn.net", "instagram.com", "whatsapp.net", "whatsapp.com",
    "apple.com", "icloud.com", "microsoft.com", "live.com", "amazon.com", "amazonaws.com",
    "cloudfront.net", "akamaihd.net", "akamaized.net", "cloudflare.com", "cloudflare.net",
    "github.com", "githubusercontent.com", "twitter.com", "x.com", "tiktok.com", "yahoo.com", "qq.com", "baidu.com",
}

DOMAIN = re.compile(r"^(?=.{4,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$")
EASY_RULE = re.compile(r"^\|\|([a-z0-9.-]+)\^$")
EASY_EXCEPTION = re.compile(r"^@@\|\|([a-z0-9.-]+)\^$")


def fetch(url, accept):
    request = urllib.request.Request(url, headers={"Accept": accept, "User-Agent": "vylia-snapshot"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode("utf-8")


def overrides():
    data = json.loads(OVERRIDES.read_text(encoding="utf-8"))
    return data["categories"] if data.get("validated") else {}


def is_protected(domain):
    return domain in NEVER_BLOCK


def exodus_domains(api, mapping):
    """{domaine: nom du traqueur} et la liste des signatures écartées."""
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
                domains.setdefault(domain, tracker["name"])
    return domains, skipped


def adblock_domains(text):
    """Règles de domaine entier d'une liste au format Adblock Plus : (domaines, exceptions, version)."""
    rules, exceptions = set(), set()
    for line in text.splitlines():
        if m := EASY_RULE.match(line):
            rules.add(m.group(1))
        elif m := EASY_EXCEPTION.match(line):
            exceptions.add(m.group(1))
    version = next((l.split(":", 1)[1].strip() for l in text.splitlines()[:10] if l.startswith("! Version:")), "?")
    kept = {d for d in rules if DOMAIN.match(d) and not is_protected(d)}
    return kept, exceptions, version


def drop_redundant(domains):
    """Retire les sous-domaines d'un domaine déjà bloqué (la recherche se fait par suffixe)."""
    def covered(domain):
        labels = domain.split(".")
        return any(".".join(labels[i:]) in domains for i in range(1, len(labels) - 1))
    return {d for d in domains if not covered(d)}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--exodus", help="réponse de l'API Exodus déjà téléchargée")
    parser.add_argument("--easyprivacy", help="easyprivacy.txt déjà téléchargé")
    parser.add_argument("--easylist", help="easylist.txt déjà téléchargé")
    parser.add_argument("--adguard-mobile", help="filtre AdGuard Mobile Ads (11.txt) déjà téléchargé")
    args = parser.parse_args()

    api = json.loads(pathlib.Path(args.exodus).read_text(encoding="utf-8") if args.exodus else fetch(EXODUS_URL, "application/json"))
    def load_list(path, url):
        return pathlib.Path(path).read_text(encoding="utf-8") if path else fetch(url, "text/plain")

    exodus, skipped = exodus_domains(api, overrides())
    easyprivacy, privacy_exceptions, privacy_version = adblock_domains(load_list(args.easyprivacy, EASYPRIVACY_URL))
    easylist, list_exceptions, list_version = adblock_domains(load_list(args.easylist, EASYLIST_URL))
    mobile, mobile_exceptions, mobile_version = adblock_domains(load_list(args.adguard_mobile, ADGUARD_MOBILE_URL))
    for name, domains in MOBILE_EXTRA.items():
        for domain in domains:
            assert DOMAIN.match(domain) and not is_protected(domain), domain
            exodus.setdefault(domain, name)  # même place que les domaines Exodus : le nom est affiché
    exceptions = privacy_exceptions | list_exceptions | mobile_exceptions
    lists = (easyprivacy | easylist | mobile) - exceptions
    kept = drop_redundant(set(exodus) | lists)
    trackers = {}
    for domain in sorted(kept):
        if domain in exodus:
            trackers.setdefault(exodus[domain], []).append(domain)
    others = sorted(d for d in kept if d not in exodus)

    snapshot = {
        "version": 1,
        "generated": datetime.date.today().isoformat(),
        "sources": [
            {"name": "Exodus Privacy", "url": EXODUS_URL, "license": "ODbL 1.0"},
            {"name": "EasyPrivacy", "url": EASYPRIVACY_URL, "license": "GPL v3", "version": privacy_version},
            {"name": "EasyList", "url": EASYLIST_URL, "license": "GPL v3", "version": list_version},
            {"name": "AdGuard Mobile Ads filter", "url": ADGUARD_MOBILE_URL, "license": "GPL v3", "version": mobile_version},
            {"name": "VYLIA (complément maison)", "license": "GPL v3"},
        ],
        # nom du traqueur (Exodus ou complément maison) -> domaines (le nom est affiché dans l'app à chaque blocage)
        "trackers": trackers,
        # domaines EasyPrivacy, EasyList et AdGuard sans traqueur nommé
        "domains": others,
    }
    OUTPUT.write_text(json.dumps(snapshot, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Exodus : {len(exodus)} domaines ({len(skipped)} signatures écartées) ; EasyPrivacy {privacy_version} : "
          f"{len(easyprivacy)} domaines ; EasyList {list_version} : {len(easylist)} domaines ; "
          f"AdGuard Mobile {mobile_version} : {len(mobile)} domaines ; maison : {sum(map(len, MOBILE_EXTRA.values()))} ; "
          f"{len(exceptions)} exceptions.")
    print(f"{len(kept)} domaines après fusion -> {OUTPUT.relative_to(ROOT)} ({OUTPUT.stat().st_size // 1024} Ko)")
    for line in skipped:
        print("  écartée :", line)


if __name__ == "__main__":
    main()
