#!/usr/bin/env python3
"""Versions allégées (« Lite ») : produit app/src/main/assets/lighter_variants.json à partir de
tools/directory/lighter_candidates.json et des notes calculées par directory-cli (même moteur que l'app).

Étapes (depuis la racine du dépôt, une dizaine de minutes pour 30 apps : 30 s entre deux pages Exodus) :
  python3 tools/directory/lighter_variants.py apps <dossier>/apps.json
  python3 tools/directory/exodus_pages.py <dossier>/apps.json <dossier>/cache
  python3 tools/directory/exodus_pages.py <dossier>/apps.json <dossier>/cache --resolve
  ./gradlew :directory-cli:installDist
  directory-cli/build/install/directory-cli/bin/directory-cli generate --date AAAA-MM-JJ --offline \\
      --apps <dossier>/apps.json --cache <dossier>/cache --out <dossier>/directory.json
  python3 tools/directory/lighter_variants.py build <dossier>/directory.json AAAA-MM-JJ

Une version est retenue si elle n'est pas marquée « skip », si elle est mieux notée que l'app d'origine et si son
rapport Exodus a moins de MAX_AGE_DAYS jours. Les autres sont listées avec la raison, pour la revue.
Données Exodus sous ODbL 1.0.
"""
import json
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CANDIDATES = ROOT / "tools/directory/lighter_candidates.json"
ASSET = ROOT / "app/src/main/assets/lighter_variants.json"
MAX_AGE_DAYS = 548  # 18 mois


def apps(out):
    pairs = json.loads(CANDIDATES.read_text())["pairs"]
    listed = []
    for p in pairs:
        listed.append({"package": p["replaces"][0], "name": p["original_name"], "category": p["category"]})
        listed.append({"package": p["lite"], "name": p["lite_name"], "category": p["category"]})
    Path(out).write_text(json.dumps({"comment": "Candidats Lite", "source": str(CANDIDATES.relative_to(ROOT)), "apps": listed}, indent=1))
    print(f"{len(listed)} apps -> {out}")


def build(directory_file, today):
    scored = {a["package"]: a for a in json.loads(Path(directory_file).read_text())["apps"] if a["status"] == "SCORED"}
    variants = []
    for p in json.loads(CANDIDATES.read_text())["pairs"]:
        lite, original = scored.get(p["lite"]), scored.get(p["replaces"][0])
        why = None
        if "skip" in p:
            why = p["skip"]
        elif lite is None:
            why = "pas de rapport Exodus"
        elif original is not None and lite["score"] <= original["score"]:
            why = f"pas mieux notée ({lite['score']} contre {original['score']})"
        elif (date.fromisoformat(today) - date.fromisoformat(lite["exodus"]["updated"])).days > MAX_AGE_DAYS:
            why = f"rapport trop ancien ({lite['exodus']['updated']})"
        if why:
            print(f"écartée  {p['lite_name']} : {why}")
            continue
        variants.append({
            "replaces": p["replaces"],
            "package": p["lite"],
            "name": p["lite_name"],
            "grade": lite["grade"],
            "score": lite["score"],
            "trackers": len(lite.get("trackers", [])),
            "exodus_report": lite["exodus"]["report"],
            "exodus_date": lite["exodus"]["updated"],
            "loss": p["loss"],
        })
        print(f"retenue  {p['lite_name']} : {lite['grade']} {lite['score']} (rapport {lite['exodus']['report']} du {lite['exodus']['updated']})")
    asset = {
        "version": "1.1",
        "last_update": today,
        "description": "Versions allégées mieux notées qu'une app répandue (même éditeur, même service). Notes calculées "
                       "comme dans l'annuaire du site (module :scoring, rapport Exodus Privacy, toutes les permissions "
                       "déclarées comptées), produites par tools/directory/lighter_variants.py et relues à la main. "
                       "Données Exodus sous ODbL 1.0.",
        "variants": variants,
    }
    ASSET.write_text(json.dumps(asset, ensure_ascii=False, indent=2) + "\n")
    print(f"{len(variants)} versions -> {ASSET.relative_to(ROOT)}")


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "apps":
        apps(sys.argv[2])
    elif len(sys.argv) == 4 and sys.argv[1] == "build":
        build(sys.argv[2], sys.argv[3])
    else:
        raise SystemExit(__doc__)
