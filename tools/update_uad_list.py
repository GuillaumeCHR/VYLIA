#!/usr/bin/env python3
"""Met à jour la liste des apps préinstallées embarquée dans VYLIA, à partir d'Universal Android Debloater (UAD-ng).

Source, téléchargée sur le poste de dev (l'app sait aussi la mettre à jour, depuis Paramètres › Bases de données) :
  Universal Android Debloater Next Generation
  (https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation),
  fichier resources/assets/uad_lists.json, sous licence GPL v3. Attribution dans les Crédits et les licences de l'app.

Le fichier est embarqué tel quel, sans modification : l'app le lit avec le même code que la version téléchargée
(`UadList.parse`), qui écarte les entrées invalides. Ce script vérifie seulement qu'il s'agit bien de la liste
(un objet JSON d'au moins MIN_ENTRIES paquets, avec les champs attendus) avant de l'écrire, et note le commit source
dans uad_lists.source.json.

Usage : python3 tools/update_uad_list.py                         # télécharge la branche main
        python3 tools/update_uad_list.py --source uad_lists.json --commit <sha>
"""
import argparse
import datetime
import json
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
OUT = ASSETS / "uad_lists.json"
SOURCE_OUT = ASSETS / "uad_lists.source.json"
REPO = "Universal-Debloater-Alliance/universal-android-debloater-next-generation"
PATH = "resources/assets/uad_lists.json"
COMMIT_URL = f"https://api.github.com/repos/{REPO}/commits?path={PATH}&per_page=1"
USER_AGENT = "VYLIA-dev-tools"
MIN_ENTRIES = 4_000
MAX_BYTES = 8 * 1024 * 1024
LISTS = {"Oem", "Google", "Carrier", "Misc", "Aosp"}
REMOVALS = {"Recommended", "Advanced", "Expert", "Unsafe"}


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise SystemExit(f"{url} : plus de {MAX_BYTES} octets, ce n'est pas la liste attendue")
    return data


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path, help="uad_lists.json déjà téléchargé")
    parser.add_argument("--commit", help="commit source, avec --source")
    args = parser.parse_args()

    if args.source:
        raw, commit = args.source.read_bytes(), args.commit or "inconnu"
    else:
        commit = json.loads(fetch(COMMIT_URL))[0]["sha"]
        raw = fetch(f"https://raw.githubusercontent.com/{REPO}/{commit}/{PATH}")

    data = json.loads(raw)
    if not isinstance(data, dict) or len(data) < MIN_ENTRIES:
        raise SystemExit(f"liste inattendue : {len(data) if isinstance(data, dict) else type(data).__name__} entrées")
    valid = sum(
        1 for v in data.values()
        if isinstance(v, dict) and v.get("list") in LISTS and v.get("removal") in REMOVALS
    )
    if valid < MIN_ENTRIES:
        raise SystemExit(f"seulement {valid} entrées valides")

    OUT.write_bytes(raw)
    SOURCE_OUT.write_text(json.dumps({
        "source": "Universal Android Debloater Next Generation (UAD-ng)",
        "url": f"https://github.com/{REPO}",
        "license": "GPL-3.0",
        "commit": commit,
        "generated": datetime.date.today().isoformat(),
        "entries": len(data),
    }, ensure_ascii=False, indent=2) + "\n")
    print(f"{OUT.relative_to(ROOT)} : {len(data)} paquets ({valid} valides), commit {commit[:12]}")


if __name__ == "__main__":
    main()
