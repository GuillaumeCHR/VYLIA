#!/usr/bin/env python3
"""Rafraîchit le snapshot local des signatures de traqueurs Exodus Privacy.

Source  : https://reports.exodus-privacy.eu.org/api/trackers (public, sans authentification)
Licence : base et résultats de l'API sous Open Database License (ODbL 1.0),
          contenus individuels sous Database Contents License (DbCL 1.0).
          Le snapshot produit est une base dérivée : il reste sous ODbL, avec attribution.

Seuls id, name, code_signature, categories et website sont conservés : l'app n'a besoin que
de ça et le fichier passe d'environ 570 Ko à environ 50 Ko.

Les catégories manquantes côté Exodus peuvent être complétées par
tools/exodus_category_overrides.json, appliqué uniquement si "validated" vaut true.

Usage : python3 tools/update_exodus_trackers.py [--source fichier.json]
"""
import argparse
import datetime
import json
import pathlib
import urllib.request

API_URL = "https://reports.exodus-privacy.eu.org/api/trackers"
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "app/src/main/assets/exodus_trackers.json"
OVERRIDES = ROOT / "tools/exodus_category_overrides.json"
EXODUS_CATEGORIES = {"Advertisement", "Analytics", "Crash reporting", "Identification", "Location", "Profiling"}


def load_source(path):
    if path:
        return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    request = urllib.request.Request(API_URL, headers={"Accept": "application/json", "User-Agent": "vylia-snapshot"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def load_overrides():
    if not OVERRIDES.exists():
        return {}
    data = json.loads(OVERRIDES.read_text(encoding="utf-8"))
    if not data.get("validated"):
        print(f"{OVERRIDES.name} non validé : catégories Exodus brutes conservées.")
        return {}
    mapping = data["categories"]
    unknown = {c for cats in mapping.values() for c in cats} - EXODUS_CATEGORIES
    if unknown:
        raise SystemExit(f"Catégories inconnues dans {OVERRIDES.name} : {sorted(unknown)}")
    return mapping


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", help="fichier JSON de l'API déjà téléchargé (sinon téléchargement)")
    args = parser.parse_args()

    trackers = load_source(args.source)["trackers"]
    overrides = load_overrides()
    applied = 0
    slim = {}
    for key, tracker in sorted(trackers.items(), key=lambda item: int(item[0])):
        categories = tracker.get("categories") or []
        if not categories and tracker["name"] in overrides:
            categories = overrides[tracker["name"]]
            applied += 1
        slim[key] = {
            "id": tracker["id"],
            "name": tracker["name"],
            "code_signature": tracker.get("code_signature", ""),
            "categories": categories,
            "website": tracker.get("website", ""),
        }

    snapshot = {
        "source": API_URL,
        "license": "ODbL 1.0 (https://opendatacommons.org/licenses/odbl/1-0/) - Exodus Privacy",
        "fetched": datetime.date.today().isoformat(),
        "category_overrides_applied": applied,
        "trackers": slim,
    }
    OUTPUT.write_text(json.dumps(snapshot, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    missing = sum(1 for t in slim.values() if not t["categories"])
    print(f"{len(slim)} traqueurs -> {OUTPUT.relative_to(ROOT)} ({OUTPUT.stat().st_size // 1024} Ko), "
          f"{applied} catégories complétées, {missing} encore sans catégorie.")


if __name__ == "__main__":
    main()
