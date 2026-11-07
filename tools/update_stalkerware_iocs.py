#!/usr/bin/env python3
"""Génère la base de stalkerwares connus embarquée dans VYLIA, à partir des indicateurs d'Echap.

Source, téléchargée sur le poste de dev uniquement (l'app ne télécharge rien) :
  Echap, stalkerware-indicators (https://github.com/AssoEchap/stalkerware-indicators), fichier ioc.yaml,
  sous licence CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). Attribution : « Echap,
  stalkerware-indicators », avec le commit source, dans le fichier produit et dans l'écran À propos de l'app.

Modifications (à signaler au titre de la licence) : seuls les stalkerwares d'ioc.yaml sont repris (pas les
« watchwares », apps de surveillance qui ne se cachent pas, comme le contrôle parental), et de chacun seulement
son nom, ses noms de paquet Android et les empreintes SHA-1 de ses certificats de signature. Les sites web, les
serveurs de commande (C2) et les autres champs sont laissés de côté : VYLIA ne compare que les apps installées.

Le fichier téléchargé est traité comme non fiable : chaque valeur est vérifiée (nom de paquet, empreinte
hexadécimale, nom lisible et court), une entrée invalide fait échouer la génération plutôt que d'être embarquée.

Usage : python3 tools/update_stalkerware_iocs.py                       # télécharge ioc.yaml (branche master)
        python3 tools/update_stalkerware_iocs.py --ioc ioc.yaml --commit <sha>
Produit app/src/main/assets/stalkerware_iocs.json. Nécessite PyYAML.
"""
import argparse
import datetime
import json
import pathlib
import re
import urllib.request

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/stalkerware_iocs.json"
REPO = "AssoEchap/stalkerware-indicators"
IOC_URL = f"https://raw.githubusercontent.com/{REPO}/master/ioc.yaml"
COMMIT_URL = f"https://api.github.com/repos/{REPO}/commits/master"
USER_AGENT = "VYLIA-dev-tools"

PACKAGE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
CERTIFICATE = re.compile(r"^[0-9A-Fa-f]{40}$")
NAME = re.compile(r"^[\w .,'()&+\-]{1,80}$")
MAX_APPS = 2_000


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read(8 * 1024 * 1024).decode("utf-8")


def convert(entries):
    if not isinstance(entries, list) or len(entries) > MAX_APPS:
        raise SystemExit("ioc.yaml : une liste d'apps est attendue")
    found = []
    for entry in entries:
        if not isinstance(entry, dict) or entry.get("type") != "stalkerware":
            continue
        name = entry.get("name")
        if not isinstance(name, str) or not NAME.match(name):
            raise SystemExit(f"nom d'app invalide : {name!r}")
        packages = sorted(set(entry.get("packages") or []))
        certificates = sorted({c.upper() for c in entry.get("certificates") or [] if isinstance(c, str)})
        for package in packages:
            if not isinstance(package, str) or not PACKAGE.match(package):
                raise SystemExit(f"{name} : nom de paquet invalide : {package!r}")
        for certificate in certificates:
            if not CERTIFICATE.match(certificate):
                raise SystemExit(f"{name} : empreinte de certificat invalide : {certificate!r}")
        if packages or certificates:
            found.append({"name": name, "packages": packages, "certificates": certificates})
    # Un même paquet sous deux noms reste au premier dans l'ordre des noms : l'app lit une base téléchargée de la
    # même façon (StalkerwareDatabase), et la détection est la même.
    apps, seen_packages = [], set()
    for app in sorted(found, key=lambda a: a["name"].lower()):
        app["packages"] = [p for p in app["packages"] if p not in seen_packages]
        seen_packages.update(app["packages"])
        if app["packages"] or app["certificates"]:
            apps.append(app)
    return apps


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--ioc", help="ioc.yaml déjà téléchargé")
    parser.add_argument("--commit", help="commit source d'ioc.yaml (sinon : dernier commit de master)")
    args = parser.parse_args()

    text = pathlib.Path(args.ioc).read_text(encoding="utf-8") if args.ioc else fetch(IOC_URL)
    commit = args.commit or json.loads(fetch(COMMIT_URL))["sha"]
    if not re.match(r"^[0-9a-f]{7,40}$", commit):
        raise SystemExit(f"commit invalide : {commit!r}")
    apps = convert(yaml.safe_load(text))

    out = {
        "source": "Echap, stalkerware-indicators",
        "url": f"https://github.com/{REPO}",
        "license": "CC BY 4.0",
        "commit": commit,
        "generated": datetime.date.today().isoformat(),
        "changes": "Stalkerwares d'ioc.yaml uniquement ; nom, noms de paquet et empreintes SHA-1 des certificats.",
        "apps": apps,
    }
    OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    packages = sum(len(a["packages"]) for a in apps)
    certificates = sum(len(a["certificates"]) for a in apps)
    print(f"{OUT.relative_to(ROOT)} : {len(apps)} stalkerwares, {packages} paquets, {certificates} certificats ({commit[:7]})")


if __name__ == "__main__":
    main()
