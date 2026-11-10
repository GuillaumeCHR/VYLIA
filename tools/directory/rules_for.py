#!/usr/bin/env python3
"""Règles de score complétées des catégories d'une liste d'apps absentes de package_overrides (catégorie DEFAULT
ignorée). La sortie peut être une copie (classement seul) ou la grille de l'app elle-même : le fichier garde sa forme
(JSON indenté de 2 espaces), seules les nouvelles lignes de package_overrides changent.

Usage : rules_for.py <règles.json> <sortie.json> <apps.json>...
"""
import json
import sys
from pathlib import Path

rules_path, out, *lists = sys.argv[1:]
rules = json.loads(Path(rules_path).read_text())
added = 0
for apps in lists:
    for a in json.loads(Path(apps).read_text())["apps"]:
        if a["package"] not in rules["package_overrides"] and a["category"] != "DEFAULT":
            rules["package_overrides"][a["package"]] = a["category"]
            added += 1
Path(out).write_text(json.dumps(rules, ensure_ascii=False, indent=2) + "\n")
print(f"{out} : {added} catégories ajoutées pour le classement")
