#!/usr/bin/env python3
"""Génère tous les textes traduits de VYLIA (anglais par défaut, français) et les tables Kotlin qui les référencent.

Usage, depuis la racine du dépôt : python3 tools/strings/generate.py

Sources (une ligne = clé, anglais, français) :
  glossary.py, glossary_extra.py  lexiques (permissions, traqueurs, catégories d'app, badges de récompense)
  ui_*.py                         textes des écrans (S : chaînes, P : pluriels)

Produit :
  app/src/main/res/values{,-fr}/strings.xml           textes des écrans
  app/src/main/res/values{,-fr}/strings_glossary.xml  textes des lexiques
  les blocs Kotlin entre « // region generated:… » et « // endregion » (PermissionGlossary, CategoryRationale,
  AppBadges) : ne pas les modifier à la main.

Règles d'écriture : « %1$s » pour un paramètre ; « %% » pour un « % » littéral (converti en « % » avec
formatted="false" quand la chaîne n'a pas de paramètre).
"""
import importlib
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
RES = ROOT / "app/src/main/res"
SRC = ROOT / "app/src/main/java/com/vylia/privacy"
sys.path.insert(0, str(HERE))

import glossary  # noqa: E402
import ui_badges  # noqa: E402
from glossary import esc  # noqa: E402

SCREENS = [
    ("Écran Scan, cartes et en-tête", "ui_base"),
    ("Navigation, Scan et détail d'une app", "ui_scan"),
    ("Analyse", "ui_analysis"),
    ("Agir", "ui_act"),
    ("Comprendre", "ui_understand"),
    ("Paramètres, À propos et grille de score", "ui_settings"),
    ("Badges et sécurité", "ui_badges"),
]
ARG = re.compile(r"%\d+\$")


def entry(name, text):
    if ARG.search(text):
        return f'    <string name="{name}">{esc(text)}</string>'
    text = text.replace("%%", "%")
    attr = ' formatted="false"' if "%" in text else ""
    return f'    <string name="{name}"{attr}>{esc(text)}</string>'


def screens_xml(lang):
    i = 1 if lang == "en" else 2
    seen = set()
    out = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Généré par tools/strings/generate.py : ne pas modifier. -->", "<resources>"]
    if lang == "en":
        out += ['    <string name="app_name" translatable="false">VYLIA</string>',
                '    <string name="app_tagline" translatable="false">The Privacy Scorer</string>']
    for title, module in SCREENS:
        mod = importlib.import_module(module)
        out.append(f"\n    <!-- {title} -->")
        for row in mod.S:
            assert row[0] not in seen, f"clé en double : {row[0]}"
            seen.add(row[0])
            out.append(entry(row[0], row[i]))
        for name, en, fr in mod.P:
            assert name not in seen, f"clé en double : {name}"
            seen.add(name)
            one, other = en if lang == "en" else fr
            out.append(f'    <plurals name="{name}">')
            out.append(f"        <item quantity=\"one\">{esc(one)}</item>")
            if lang == "fr":
                out.append(f"        <item quantity=\"many\">{esc(other)}</item>")
            out.append(f"        <item quantity=\"other\">{esc(other)}</item>")
            out.append("    </plurals>")
    out.append("</resources>\n")
    return "\n".join(out)


def glossary_xml(lang):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Généré par tools/strings/generate.py : ne pas modifier. -->\n'
            "<resources>\n" + glossary.xml(lang) + "</resources>\n")


def resource_key(permission):
    return permission.replace(".*", "_all").split(".")[-1].lower()


BLOCKS = {
    "permissions": [
        f'        info("{p}", PermissionTheme.{theme}, GrantMode.{grant}, R.string.perm_{resource_key(p)}_title, '
        f"R.string.perm_{resource_key(p)}_allows, R.string.perm_{resource_key(p)}_why),"
        for p, theme, grant, *_ in glossary.PERMS
    ],
    "categories": [
        f'        "{k}" to Texts(R.string.category_{k.lower()}, R.string.category_{k.lower()}_rationale),'
        for k in glossary.CATEGORIES
    ],
    "badges": [
        f'    "{i}" to (R.string.badgedef_{i.lower()} to R.string.badgedef_{i.lower()}_description),' for i, *_ in ui_badges.B
    ],
    "security": [
        f'    "{i}" to (R.string.security_{i.lower()} to R.string.security_{i.lower()}_description),' for i, *_ in ui_badges.SEC
    ],
}
KOTLIN = ["domain/learn/PermissionGlossary.kt", "domain/learn/CategoryRationale.kt", "ui/components/AppBadges.kt"]
REGION = re.compile(r"(// region generated:(\w+)[^\n]*\n)(.*?)(\n\s*// endregion)", re.S)


def write(path, content):
    path = Path(path)
    if not path.exists() or path.read_text() != content:
        path.write_text(content)
        print("écrit", path.relative_to(ROOT))


if __name__ == "__main__":
    for lang, folder in (("en", "values"), ("fr", "values-fr")):
        write(RES / folder / "strings.xml", screens_xml(lang))
        write(RES / folder / "strings_glossary.xml", glossary_xml(lang))
    for rel in KOTLIN:
        path = SRC / rel
        text = path.read_text()
        new = REGION.sub(lambda m: m.group(1) + "\n".join(BLOCKS[m.group(2)]) + m.group(4), text)
        write(path, new)
