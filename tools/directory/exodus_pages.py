#!/usr/bin/env python3
"""Tableau ponctuel sans clé API : lit les pages publiques des rapports Exodus Privacy et les écrit dans le cache de
`directory-cli` (même forme que `/api/search/{paquet}/details`), pour `generate --offline`.

- Une page `https://reports.exodus-privacy.eu.org/en/reports/<paquet>/latest/` par app ; aucune autre requête.
- 30 s entre deux requêtes (Crawl-delay du robots.txt d'Exodus), User-Agent du projet.
- Une app déjà en cache n'est pas redemandée (reprise possible après une coupure).
- `_trackers.json` vient de app/src/main/assets/exodus_trackers.json (copie de `/api/trackers`, même identifiants).

Données Exodus sous ODbL 1.0 : le tableau publié cite Exodus Privacy.

Usage : exodus_pages.py <apps.json> <dossier cache>
"""
import html
import json
import re
import shutil
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

BASE = "https://reports.exodus-privacy.eu.org"
USER_AGENT = "vylia-annuaire/1.0 (+https://github.com/GuillaumeCHR/VYLIA)"
DELAY_S = 30
HANDLE = re.compile(r"^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$")
TRACKERS_ASSET = Path(__file__).resolve().parents[2] / "app/src/main/assets/exodus_trackers.json"


def fetch(package):
    """Page du dernier rapport, ou None si Exodus ne connaît pas l'app (404)."""
    request = urllib.request.Request(f"{BASE}/en/reports/{package}/latest/", headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.read(5_000_000).decode("utf-8")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise


def parse(page):
    """Rapport au format de l'API (champs utilisés par ReportConverter)."""
    report = re.search(r"/reports/(\d+)/icon/", page)
    if not report:
        raise ValueError("identifiant de rapport introuvable")
    version = re.search(r"Version\s+(.+?)\s+-\s*<a", page, re.S)
    created = re.search(r"Report created on\s+([A-Za-z]+)\.?\s+(\d+),\s+(\d{4})", page)
    source = re.search(r"Source:\s*(.+?)\s*<br", page, re.S)
    trackers_section = page.split('id="trackers"', 1)[1].split('id="permissions"', 1)[0]
    tracker_ids = sorted({int(t) for t in re.findall(r'href="/[a-z]{2}/trackers/(\d+)/"', trackers_section)})
    permissions = sorted(set(html.unescape(p) for p in re.findall(r'data-toggle="tooltip"[^>]*title="([^"]+)"', page.split('id="permissions"', 1)[1])))
    badge = re.search(r'reports">(\d+)</span>\s*<b>trackers</b>', page)
    if badge and int(badge.group(1)) != len(tracker_ids):
        raise ValueError(f"{badge.group(1)} traqueurs annoncés, {len(tracker_ids)} lus")
    updated = ""
    if created:
        month = datetime.strptime(created.group(1)[:3], "%b").month
        updated = f"{created.group(3)}-{month:02d}-{int(created.group(2)):02d}"
    src = re.sub(r"<[^>]+>", "", source.group(1)).strip() if source else ""
    return {
        "report": int(report.group(1)),
        "version_name": html.unescape(version.group(1).strip()) if version else "",
        "version_code": "",
        "updated": updated,
        "trackers": tracker_ids,
        "permissions": [p for p in permissions if "." in p],
        "source": "google" if "Google Play" in src else src.lower() or None,
    }


def main(apps_file, cache_dir):
    cache = Path(cache_dir)
    cache.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(TRACKERS_ASSET, cache / "_trackers.json")
    apps = json.loads(Path(apps_file).read_text())["apps"]
    missing = []
    first = True
    for app in apps:
        package = app["package"]
        if not HANDLE.match(package):
            raise SystemExit(f"paquet invalide : {package}")
        target = cache / f"{package}.json"
        if target.exists() or (cache / f"{package}.missing").exists():
            continue
        if not first:
            time.sleep(DELAY_S)
        first = False
        page = fetch(package)
        if page is None:
            (cache / f"{package}.missing").write_text("")
            missing.append(package)
            print(f"{package} : pas de rapport", flush=True)
            continue
        report = parse(page)
        target.write_text(json.dumps([report], indent=1))
        print(f"{package} : rapport {report['report']} du {report['updated']}, {len(report['trackers'])} traqueurs, "
              f"{len(report['permissions'])} permissions", flush=True)
    if missing:
        print("Sans rapport :", ", ".join(missing))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
