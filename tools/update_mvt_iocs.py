#!/usr/bin/env python3
"""Génère la base des logiciels espions de surveillance ciblée embarquée dans VYLIA, à partir des indicateurs MVT.

Source, téléchargée sur le poste de dev uniquement (l'app ne télécharge rien) :
  MVT, mvt-indicators (https://github.com/mvt-project/mvt-indicators, licence MIT), fichier indicators.yaml : la liste
  des campagnes, chacune un fichier STIX2 hébergé par MVT ou par Amnesty International (AmnestyTech/investigations,
  dépôt sans licence déclarée : indicateurs publiés pour la détection, repris avec attribution). Commits figés
  ci-dessous, pour que deux générations donnent le même fichier.

Repris de chaque campagne, sauf celle des stalkerwares (c'est la base d'Echap, déjà intégrée par
update_stalkerware_iocs.py) :
  - « app:id » (nom de paquet Android) et « app:cert.sha1 / sha256 » (empreinte du certificat de signature) : VYLIA
    les compare aux apps installées, comme les stalkerwares d'Echap ;
  - « domain-name:value » : serveurs d'infection et de commande, bloqués par le bouclier (groupe « spyware », voir
    update_shield_blocklist.py). Les domaines de plateformes (NEVER_BLOCK) et leurs sous-domaines sont écartés.
Laissés de côté : empreintes de fichiers (il faudrait hacher chaque APK), adresses IP (le bouclier ne voit que le
DNS), processus, chemins et profils iOS, adresses e-mail.

Le contenu téléchargé est traité comme non fiable : chaque valeur est vérifiée (paquet, empreinte hexadécimale, nom
de domaine), une valeur invalide est écartée et comptée.

Usage : python3 tools/update_mvt_iocs.py
Produit app/src/main/assets/mvt_iocs.json.
"""
import datetime
import json
import pathlib
import re
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/mvt_iocs.json"
USER_AGENT = "VYLIA-dev-tools"
MVT_REPO = "mvt-project/mvt-indicators"
MVT_COMMIT = "b22ddf05e1a31e7732b8895676987c5c3482ef65"
# Commit figé de chaque dépôt cité par indicators.yaml (la branche y est mobile).
COMMITS = {
    "mvt-project/mvt-indicators": MVT_COMMIT,
    "AmnestyTech/investigations": "3d8f248a0d015f183724ae7d096a5c46a8bb5fc7",
}
SKIPPED_SOURCES = {"AssoEchap/stalkerware-indicators"}

# Nom affiché dans l'app (fiche, alerte, bouclier), court et sans traduction : des noms propres.
SHORT_NAMES = {
    "NSO Group Pegasus Indicators of Compromise": "Pegasus (NSO Group)",
    "Predator Spyware Indicators of Compromise": "Predator (Intellexa)",
    "RCS Lab Spyware Indicators of Compromise": "Hermit (RCS Lab)",
    "Surveillance campaign linked to mercenary spyware company": "Mercenary spyware (Amnesty, 2023)",
    "Quadream KingSpawn Indicators of Compromise": "KingSpawn (QuaDream)",
    "Operation Triangulation Indicators of Compromise": "Operation Triangulation",
    "WyrmSpy and DragonEgg Indicators of Compromise": "WyrmSpy / DragonEgg",
    "Wintego Helios Indicators of Compromise": "Helios (Wintego)",
    "NoviSpy (Serbia) Indicators of Compromise": "NoviSpy",
    "EagleMsgSpy Indicators of Compromise": "EagleMsgSpy",
    "Candiru (DevilsTongue) Indicators of Compromise": "DevilsTongue (Candiru)",
    "ResidentBat Indicators of Compromise": "ResidentBat",
    "Cellebrite Indicators of Compromise": "Cellebrite",
    "DarkSword Indicators of Compromise": "DarkSword",
    "Coruna Indicators of Compromise": "Coruna",
    "Morpheus Indicators of Compromise": "Morpheus",
    "BTMOB Indicators of Compromise": "BTMOB",
    "Spyrtacus Indicators of Compromise": "Spyrtacus",
}

# Mêmes plateformes que update_shield_blocklist.py : jamais bloquées, ni leurs sous-domaines (un hébergement
# compromis sur s3.amazonaws.com ne doit pas couper tout Amazon Web Services).
NEVER_BLOCK = {
    "google.com", "googleapis.com", "gstatic.com", "youtube.com", "android.com",
    "facebook.com", "fbcdn.net", "instagram.com", "whatsapp.net", "whatsapp.com",
    "apple.com", "icloud.com", "microsoft.com", "live.com", "amazon.com", "amazonaws.com",
    "cloudfront.net", "akamaihd.net", "akamaized.net", "cloudflare.com", "cloudflare.net",
    "github.com", "githubusercontent.com", "twitter.com", "x.com", "tiktok.com", "yahoo.com", "qq.com", "baidu.com",
    "github.io", "blogspot.com", "herokuapp.com", "azurewebsites.net", "web.app", "firebaseapp.com", "appspot.com",
    "workers.dev", "pages.dev", "netlify.app", "vercel.app",
}

PACKAGE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
CERTIFICATE = re.compile(r"^[0-9a-f]{40}$|^[0-9a-f]{64}$")
DOMAIN = re.compile(r"^(?=.{4,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$")
PATTERN = re.compile(r"^\[\s*([\w\-]+:[\w.\-]+)\s*=\s*'([^']*)'\s*\]$")


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read(50_000_000)


def campaigns(index: str):
    """Les campagnes d'indicators.yaml : (nom, dépôt, chemin). Format simple, lu sans dépendance YAML."""
    for block in index.split("\n  -\n")[1:]:
        field = lambda key: re.search(rf"^\s+{key}: (.+)$", block, re.M).group(1).strip()
        yield field("name"), f"{field('owner')}/{field('repo')}", field("path")


def blocked_platform(domain: str) -> bool:
    return any(domain == p or domain.endswith("." + p) for p in NEVER_BLOCK)


def main():
    index = fetch(f"https://raw.githubusercontent.com/{MVT_REPO}/{MVT_COMMIT}/indicators.yaml").decode()
    out, rejected = [], 0
    for name, repo, path in campaigns(index):
        if repo in SKIPPED_SOURCES:
            continue
        if repo not in COMMITS or name not in SHORT_NAMES:
            raise SystemExit(f"Campagne inconnue, à ajouter à COMMITS / SHORT_NAMES : {name} ({repo})")
        stix = json.loads(fetch(f"https://raw.githubusercontent.com/{repo}/{COMMITS[repo]}/{path}"))
        packages, certificates, domains = set(), set(), set()
        for obj in stix.get("objects", []):
            if obj.get("type") != "indicator":
                continue
            match = PATTERN.match(obj.get("pattern", "").strip())
            if not match:
                continue
            kind, value = match.group(1), match.group(2).strip()
            if kind == "app:id":
                if PACKAGE.match(value):
                    packages.add(value)
                else:
                    rejected += 1
            elif kind in ("app:cert.sha1", "app:cert.sha256"):
                value = value.lower().replace(":", "")
                if CERTIFICATE.match(value):
                    certificates.add(value)
                else:
                    rejected += 1
            elif kind == "domain-name:value":
                value = value.lower().rstrip(".")
                if DOMAIN.match(value) and not blocked_platform(value):
                    domains.add(value)
                else:
                    rejected += 1
        out.append({
            "name": SHORT_NAMES[name],
            "source": f"https://github.com/{repo}/blob/{COMMITS[repo]}/{path}",
            "packages": sorted(packages),
            "certificates": sorted(certificates),
            "domains": sorted(domains),
        })
        print(f"{SHORT_NAMES[name]:40} {len(packages):3} paquets {len(certificates):3} certificats {len(domains):5} domaines")
    data = {
        "source": "MVT, mvt-indicators",
        "url": f"https://github.com/{MVT_REPO}",
        "license": "MIT (mvt-indicators) ; fichiers d'Amnesty International repris avec attribution",
        "commit": MVT_COMMIT,
        "generated": datetime.date.today().isoformat(),
        "changes": "Sans la campagne stalkerware (base d'Echap, intégrée à part) ; noms de paquet, empreintes de "
                   "certificat et noms de domaine seulement.",
        "campaigns": out,
    }
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"{OUT.relative_to(ROOT)} : {len(out)} campagnes, {rejected} valeurs écartées")


if __name__ == "__main__":
    main()
