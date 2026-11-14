#!/usr/bin/env python3
"""Traductions françaises des descriptions d'UAD-ng : lots à traduire, et fusion dans le fichier de l'app.

Les descriptions sont indexées par l'empreinte de leur texte anglais (SHA-256, 16 premiers caractères hexadécimaux,
du texte rogné et coupé à 1 000 caractères, comme `UadList.keyOf` dans l'app) : une description identique partagée
par plusieurs paquets n'est traduite qu'une fois, et une description modifiée par UAD-ng perd sa traduction (l'app
montre alors le texte anglais).

Usage :
  python3 tools/translate_uad.py export <dossier>          # lots JSON {clé: texte anglais} des descriptions sans traduction
  python3 tools/translate_uad.py merge <dossier>           # fusionne les lots traduits {clé: texte français} du dossier
  python3 tools/translate_uad.py status                    # descriptions traduites, manquantes, et traductions orphelines

Produit app/src/main/assets/uad_descriptions_fr.json (GPL v3, comme la liste d'UAD-ng), publié dans ce dépôt et
téléchargé par l'app (Paramètres › Bases de données).
"""
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
UAD = ROOT / "app/src/main/assets/uad_lists.json"
OUT = ROOT / "app/src/main/assets/uad_descriptions_fr.json"
MAX_DESCRIPTION = 1_000
MAX_TEXT = 1_500
BATCH_CHARS = 42_000
URL = re.compile(r"https?://\S+")
PACKAGE = re.compile(r"\b[a-z][a-z0-9_]*(?:\.[a-z0-9_]+){2,}\b")


def key_of(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]


def sources() -> dict:
    texts = {}
    for value in json.loads(UAD.read_text()).values():
        text = (value.get("description") or "").strip()[:MAX_DESCRIPTION]
        if text:
            texts[key_of(text)] = text
    return texts


def current() -> dict:
    return json.loads(OUT.read_text())["translations"] if OUT.exists() else {}


def urls(text: str) -> list:
    """Adresses web du texte, sans la ponctuation qui les suit (« ). , : ; »), que la phrase française déplace."""
    return sorted(u.rstrip(").,:;»") for u in URL.findall(text))


def check(key: str, english: str, french: str) -> list:
    """Contrôles d'une traduction : non vide, bornée, mêmes adresses web et mêmes noms de paquets que l'original."""
    problems = []
    if not french.strip():
        problems.append("vide")
    if len(french) > MAX_TEXT:
        problems.append(f"trop longue ({len(french)})")
    if urls(english) != urls(french):
        problems.append("adresses web différentes")
    if not set(PACKAGE.findall(english)) <= set(PACKAGE.findall(french)):
        problems.append("nom de paquet perdu")
    return problems


def export(folder: pathlib.Path) -> None:
    folder.mkdir(parents=True, exist_ok=True)
    done = current()
    todo = sorted((k, t) for k, t in sources().items() if k not in done)
    batches, batch, size = [], {}, 0
    for k, t in todo:
        if size + len(t) > BATCH_CHARS and batch:
            batches.append(batch)
            batch, size = {}, 0
        batch[k] = t
        size += len(t)
    if batch:
        batches.append(batch)
    for i, b in enumerate(batches, 1):
        (folder / f"batch_{i:02d}.json").write_text(json.dumps(b, ensure_ascii=False, indent=1))
    print(f"{len(todo)} descriptions à traduire, {len(batches)} lots dans {folder}")


def merge(folder: pathlib.Path) -> None:
    english = sources()
    merged = current()
    rejected = 0
    for path in sorted(folder.glob("*.json")):
        for k, french in json.loads(path.read_text()).items():
            if k not in english or not isinstance(french, str):
                rejected += 1
                continue
            problems = check(k, english[k], french)
            if problems:
                print(f"{path.name} {k} : {', '.join(problems)}")
                rejected += 1
                continue
            merged[k] = french.strip()
    # Les traductions des textes qu'UAD-ng n'a plus sont retirées.
    merged = {k: v for k, v in sorted(merged.items()) if k in english}
    OUT.write_text(json.dumps({
        "format": 1,
        "language": "fr",
        "source": "Universal Android Debloater Next Generation (UAD-ng), descriptions traduites par VYLIA",
        "license": "GPL-3.0",
        "uad_commit": json.loads((ROOT / "app/src/main/assets/uad_lists.source.json").read_text())["commit"],
        "translations": merged,
    }, ensure_ascii=False, indent=1) + "\n")
    print(f"{len(merged)}/{len(english)} descriptions traduites, {rejected} traductions écartées")


def status() -> None:
    english, done = sources(), current()
    print(f"{sum(k in done for k in english)}/{len(english)} traduites, "
          f"{sum(k not in english for k in done)} orphelines (texte anglais changé ou retiré)")


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else "status"
    if command == "export":
        export(pathlib.Path(sys.argv[2]))
    elif command == "merge":
        merge(pathlib.Path(sys.argv[2]))
    else:
        status()
