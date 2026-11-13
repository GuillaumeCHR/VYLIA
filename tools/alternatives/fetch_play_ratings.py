#!/usr/bin/env python3
"""Relève la note moyenne (étoiles) de chaque alternative sur sa fiche Google Play publique.

Usage : python3 tools/alternatives/fetch_play_ratings.py   (depuis la racine du dépôt)
Produit tools/alternatives/play_ratings.json ({identifiant Play: note}, une décimale), lu par build_catalogue.py.
Une app absente du Play Store, ou dont la fiche n'affiche pas de note, n'y figure pas : l'app n'affiche alors rien.
Une requête à la fois, avec une pause, comme une visite ordinaire des fiches.
"""
import json
import re
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOGUE = ROOT / "app/src/main/assets/alternatives.json"
OUT = ROOT / "tools/alternatives/play_ratings.json"
EXTRA = ["xyz.blueskyweb.app", "com.duckduckgo.mobile.android", "io.olvid.messenger"]  # REPLACEMENTS et ADDITIONS de build_catalogue.py
RATING = re.compile(r'itemprop="starRating"><div[^>]*aria-label="Rated ([0-9.]+) stars out of five stars"')
PACKAGE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")


def rating(package: str):
    url = f"https://play.google.com/store/apps/details?id={package}&hl=en&gl=FR"
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "VYLIA-dev-tools"}), timeout=20) as r:
            match = RATING.search(r.read(2_000_000).decode("utf-8", "replace"))
    except Exception:
        return None
    value = float(match.group(1)) if match else None
    return round(value, 1) if value is not None and 0 < value <= 5 else None


def main():
    catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    packages = sorted({a["play"] for g in catalogue["groups"] for a in g["alternatives"] if a.get("play")} | set(EXTRA))
    found = {}
    for package in packages:
        if not PACKAGE.match(package):
            continue
        value = rating(package)
        print(f"{package}: {value if value is not None else '-'}")
        if value is not None:
            found[package] = value
        time.sleep(1)
    OUT.write_text(json.dumps(found, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{OUT.relative_to(ROOT)} : {len(found)} notes sur {len(packages)} apps")


if __name__ == "__main__":
    main()
