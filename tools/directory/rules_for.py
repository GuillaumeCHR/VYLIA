#!/usr/bin/env python3
"""Copie des règles de score avec les catégories d'une liste d'apps absentes de package_overrides, pour noter le
classement sans changer les règles de l'app (pas de changement de grille pendant le test fermé).

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
