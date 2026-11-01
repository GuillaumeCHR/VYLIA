#!/usr/bin/env python3
"""Télécharge l'icône de chaque alternative du catalogue et l'embarque dans l'app (aucune requête depuis l'app).

Usage : python3 tools/alternatives/fetch_icons.py   (après build_catalogue.py)
Source : la fiche Play Store de l'app (image og:image), sinon sa fiche F-Droid. Une requête par seconde.
Sortie : app/src/main/assets/alt_icons/<paquet>.webp, 128 × 128 px. Dépendance : Pillow.
"""
import io
import json
import re
import time
import urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
CATALOGUE = ROOT / "app/src/main/assets/alternatives.json"
OUT = ROOT / "app/src/main/assets/alt_icons"
SIZE = 128
UA = "VYLIA-catalogue-icons/1.0 (contact@alcyconie.com)"


def get(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "en"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return response.read()


def play_icon(package: str) -> str | None:
    html = get(f"https://play.google.com/store/apps/details?id={package}").decode("utf-8", "ignore")
    match = re.search(r'<meta property="og:image" content="([^"]+)"', html)
    return re.sub(r"=[a-z0-9-]+$", "", match.group(1)) + f"=s{SIZE}" if match else None


def fdroid_icon(package: str) -> str | None:
    html = get(f"https://f-droid.org/packages/{package}/").decode("utf-8", "ignore")
    match = re.search(r'<img[^>]+class="package-icon"[^>]+src="([^"]+)"', html) or \
        re.search(r'src="([^"]+)"[^>]+class="package-icon"', html)
    if not match:
        return None
    src = match.group(1)
    return src if src.startswith("http") else "https://f-droid.org" + src


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    missing = []
    for group in catalogue["groups"]:
        for alt in group["alternatives"]:
            target = OUT / f"{alt['package']}.webp"
            if target.exists():
                continue
            url = None
            try:
                if alt.get("play"):
                    url = play_icon(alt["play"])
                    time.sleep(1)
                if not url and alt.get("fdroid"):
                    url = fdroid_icon(alt["fdroid"])
                    time.sleep(1)
                if not url:
                    missing.append(alt["name"])
                    continue
                image = Image.open(io.BytesIO(get(url))).convert("RGBA").resize((SIZE, SIZE), Image.LANCZOS)
                image.save(target, "WEBP", quality=90)
                time.sleep(1)
                print(f"{alt['name']} ← {url[:80]}")
            except Exception as error:  # noqa: BLE001 — un échec n'empêche pas les autres icônes
                missing.append(f"{alt['name']} ({error})")
    print("Sans icône :", ", ".join(missing) or "aucune")


if __name__ == "__main__":
    main()
