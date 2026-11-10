#!/usr/bin/env python3
"""Tableau ponctuel sans clé API : lit les pages publiques des rapports Exodus Privacy et les écrit dans le cache de
`directory-cli` (même forme que `/api/search/{paquet}/details`), pour `generate --offline`.

- Une page `https://reports.exodus-privacy.eu.org/en/reports/<paquet>/latest/` par app ; aucune autre requête.
- 30 s entre deux requêtes (Crawl-delay du robots.txt d'Exodus), User-Agent du projet.
- Une app déjà en cache n'est pas redemandée (reprise possible après une coupure).
- `_trackers.json` vient de app/src/main/assets/exodus_trackers.json (copie de `/api/trackers`, même identifiants).

Données Exodus sous ODbL 1.0 : le tableau publié cite Exodus Privacy.

Le numéro dans l'adresse de l'icône n'est PAS celui du rapport : `--resolve` lit la page de recherche de chaque app
(`/en/reports/search/<paquet>/`) pour retrouver le vrai numéro de rapport et, si le dernier rapport vient de F-Droid,
lit à la place le dernier rapport Google Play (les versions F-Droid sont souvent allégées des services Google).

Usage : exodus_pages.py <apps.json> <dossier cache> [--resolve]
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


_last = [0.0]
_stamp = [None]  # fichier qui garde l'heure de la dernière requête d'une exécution à l'autre


def get(path):
    """Une page du site d'Exodus, 30 s au moins après la précédente ; None si 404."""
    if _stamp[0] is not None and _stamp[0].exists():
        _last[0] = max(_last[0], float(_stamp[0].read_text() or 0))
    wait = _last[0] + DELAY_S - time.time()
    if wait > 0:
        time.sleep(wait)
    _last[0] = time.time()
    if _stamp[0] is not None:
        _stamp[0].write_text(str(_last[0]))
    request = urllib.request.Request(BASE + path, headers={"User-Agent": USER_AGENT})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read(5_000_000).decode("utf-8")
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if e.code < 500 or attempt == 2:
                raise
        except urllib.error.URLError:
            if attempt == 2:
                raise
        time.sleep(DELAY_S * 4)  # erreur passagère du serveur : on attend avant de réessayer
        _last[0] = time.time()
        if _stamp[0] is not None:
            _stamp[0].write_text(str(_last[0]))


def fetch(package):
    """Page du dernier rapport, ou None si Exodus ne connaît pas l'app (404)."""
    return get(f"/en/reports/{package}/latest/")


SEARCH_ENTRY = re.compile(r'/reports/(\d+)/icon.*?href="/[a-z]{2}/reports/(\d+)/".*?Version\s+(.*?)\s+-\s+([a-z-]+)\s*</b>', re.S)


def search(package):
    """Rapports d'une app : liste de (numéro d'icône, numéro de rapport, version, source)."""
    page = get(f"/en/reports/search/{package}/")
    if page is None:
        return []
    return [(int(i), int(r), html.unescape(v), src) for i, r, v, src in SEARCH_ENTRY.findall(page)]


def resolve(package, target):
    """Vrai numéro de rapport ; dernier rapport Google Play si le rapport lu vient d'ailleurs."""
    report = json.loads(target.read_text())[0]
    if report.get("resolved"):
        return None
    entries = search(package)
    if report.get("source") == "google":
        match = [r for i, r, _, _ in entries if i == report["report"]]
        if not match:
            raise ValueError(f"{package} : rapport introuvable dans la recherche")
        report["report"] = match[0]
        note = f"rapport {match[0]}"
    else:
        google = sorted((e for e in entries if e[3] == "google"), key=lambda e: -e[1])
        if not google:
            report["resolved"] = True
            target.write_text(json.dumps([report], indent=1))
            return f"{package} : aucun rapport Google Play (dernier : {report.get('source')})"
        rid = google[0][1]
        page = get(f"/en/reports/{rid}/")
        report = parse(page)
        report["report"] = rid
        note = f"rapport Google Play {rid} du {report['updated']} à la place de F-Droid, {len(report['trackers'])} traqueurs"
    report["resolved"] = True
    target.write_text(json.dumps([report], indent=1))
    return f"{package} : {note}"


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


def main(apps_file, cache_dir, resolve_ids=False):
    cache = Path(cache_dir)
    cache.mkdir(parents=True, exist_ok=True)
    _stamp[0] = cache / ".last_request"
    shutil.copyfile(TRACKERS_ASSET, cache / "_trackers.json")
    apps = json.loads(Path(apps_file).read_text())["apps"]
    missing = []
    for app in apps:
        package = app["package"]
        if not HANDLE.match(package):
            raise SystemExit(f"paquet invalide : {package}")
        target = cache / f"{package}.json"
        if resolve_ids:
            if target.exists():
                message = resolve(package, target)
                if message:
                    print(message, flush=True)
            continue
        if target.exists() or (cache / f"{package}.missing").exists():
            continue
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
    if len(sys.argv) not in (3, 4) or (len(sys.argv) == 4 and sys.argv[3] != "--resolve"):
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2], resolve_ids=len(sys.argv) == 4)
