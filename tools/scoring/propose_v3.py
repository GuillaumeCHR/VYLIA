#!/usr/bin/env python3
"""Migration de la grille de score v2.1 → v3 (validée le 2026-11-01).

Produit, depuis une seule source (ce fichier) et les règles v2.1 archivées dans docs/scoring/ :
  - app/src/main/assets/vylia_rules_v3.json : règles v3 embarquées dans l'app ;
  - docs/scoring/matrice_v3.html : page de relecture (poids, groupes, matrice permission × catégorie, nouveautés).

Pour faire évoluer la grille : modifier ce fichier, relancer le script, puis comparer les scores sur le téléphone
(`adb logcat -s VyliaScores`, version de développement).

Usage : python3 tools/scoring/propose_v3.py   (depuis la racine du dépôt)

Contexte d'une permission pour une catégorie : J = justifiée (×0), T = tolérée (×0,4), I = inattendue (×1, valeur par
défaut quand la catégorie ne cite pas la permission), X = interdite (×1,6).
"""
import copy
import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "docs/scoring/vylia_rules_v2_1.json"
OUT_JSON = ROOT / "app/src/main/assets/vylia_rules_v3.json"
OUT_HTML = ROOT / "docs/scoring/matrice_v3.html"

# ---------------------------------------------------------------------------------------------------------------------
# 1. Ensembles de permissions (nouveau dans le moteur : un nom « @… » vaut toutes les permissions qu'il regroupe)
# ---------------------------------------------------------------------------------------------------------------------
H = "health."
PERMISSION_SETS = {
    "@HEALTH_INTIMATE": [H + p for p in (
        "READ_MENSTRUATION", "READ_OVULATION_TEST", "READ_SEXUAL_ACTIVITY", "READ_CERVICAL_MUCUS",
        "READ_INTERMENSTRUAL_BLEEDING", "READ_BLOOD_GLUCOSE", "READ_BLOOD_PRESSURE", "READ_BASAL_BODY_TEMPERATURE",
        "READ_BODY_TEMPERATURE", "READ_SKIN_TEMPERATURE")],
    "@HEALTH_VITALS": [H + p for p in (
        "READ_HEART_RATE", "READ_HEART_RATE_VARIABILITY", "READ_RESTING_HEART_RATE", "READ_OXYGEN_SATURATION",
        "READ_RESPIRATORY_RATE", "READ_SLEEP", "READ_WEIGHT", "READ_BODY_FAT", "READ_LEAN_BODY_MASS", "READ_BONE_MASS",
        "READ_BODY_WATER_MASS", "READ_BASAL_METABOLIC_RATE", "READ_HEIGHT", "READ_NUTRITION", "READ_HYDRATION",
        "READ_VO2_MAX", "READ_MINDFULNESS")],
    "@HEALTH_ACTIVITY": [H + p for p in (
        "READ_STEPS", "READ_DISTANCE", "READ_EXERCISE", "READ_ACTIVE_CALORIES_BURNED", "READ_TOTAL_CALORIES_BURNED",
        "READ_SPEED", "READ_POWER", "READ_ELEVATION_GAINED", "READ_FLOORS_CLIMBED", "READ_STEPS_CADENCE",
        "READ_CYCLING_PEDALING_CADENCE", "READ_WHEELCHAIR_PUSHES", "READ_PLANNED_EXERCISE", "READ_EXERCISE_ROUTES")],
    "@HEALTH_BACKGROUND": [H + "READ_HEALTH_DATA_HISTORY", H + "READ_HEALTH_DATA_IN_BACKGROUND"],
}

# ---------------------------------------------------------------------------------------------------------------------
# 2. Nouvelles permissions notées : poids, thème, contexte par catégorie
#    "J"/"T"/"X" : catégories citées ; "rest" : contexte des catégories non citées (I = inattendue, X = interdite).
# ---------------------------------------------------------------------------------------------------------------------
AD = "com.google.android.gms.permission.AD_ID"
NEW = {
    # Arrière-plan sensible.
    "FOREGROUND_SERVICE_MICROPHONE": dict(w=6, theme="Fonctionnement de l'app", rest="X",
        J=["MESSAGING", "AI_ASSISTANT", "ACCESSIBILITY"],
        T=["SOCIAL", "MEDIA_PLAYER", "NAVIGATION", "VEHICLE", "PRODUCTIVITY_CLOUD", "BROWSER", "DATING",
           "EDUCATION_FAMILY", "WEARABLE_COMPANION", "PHOTO_VIDEO", "KEYBOARD", "TRANSPORT"],
        why="Écouter après avoir quitté l'app : normal pour un appel ou un assistant vocal ; toléré pour les transports "
            "(appel avec le chauffeur) ; interdit pour un jeu, une boutique ou une banque."),
    "FOREGROUND_SERVICE_CAMERA": dict(w=6, theme="Fonctionnement de l'app", rest="X",
        J=["MESSAGING"],
        T=["SOCIAL", "VEHICLE", "PHOTO_VIDEO", "BROWSER", "AI_ASSISTANT", "ACCESSIBILITY", "DATING"],
        why="Filmer en arrière-plan : appel vidéo, caméra embarquée, aide visuelle. Interdit ailleurs."),
    "FOREGROUND_SERVICE_LOCATION": dict(w=6, theme="Localisation", rest="X",
        J=["NAVIGATION", "FITNESS", "WEARABLE_COMPANION", "VEHICLE"],
        T=["TRANSPORT", "TRAVEL_LEISURE", "MESSAGING", "SOCIAL", "WEATHER", "EDUCATION_FAMILY", "ACCESSIBILITY"],
        why="Suivi de position continu avec notification : la façon transparente de suivre un trajet ; toléré pour "
            "le partage de position en direct (messageries, réseaux sociaux). "
            "Compté avec la position en arrière-plan (la plus lourde des deux)."),
    "FOREGROUND_SERVICE_MEDIA_PROJECTION": dict(w=4, theme="Écran et capture", rest="I",
        T=["PRODUCTIVITY_CLOUD", "MESSAGING", "SOCIAL", "GAME", "ACCESSIBILITY", "AI_ASSISTANT", "EDUCATION_FAMILY",
           "PHOTO_VIDEO", "MEDIA_PLAYER"],
        why="Capturer tout l'écran en arrière-plan : partage d'écran, direct, diffusion (Chromecast). Android demande "
            "votre accord à chaque capture : comptée comme seulement déclarée."),
    # Santé (Health Connect), par paliers de sensibilité.
    "@HEALTH_INTIMATE": dict(w=8, theme="Capteurs, santé et activité", rest="X",
        J=["HEALTH_TRACKING"], T=["WEARABLE_COMPANION", "FITNESS", "HEALTH_ADMIN"],
        why="Cycle, ovulation, activité sexuelle, glycémie, tension, température : données de santé les plus intimes."),
    "@HEALTH_VITALS": dict(w=5, theme="Capteurs, santé et activité", rest="X",
        J=["FITNESS", "WEARABLE_COMPANION", "HEALTH_TRACKING"], T=["HEALTH_ADMIN"],
        why="Rythme cardiaque, sommeil, poids, oxygène, nutrition."),
    "@HEALTH_ACTIVITY": dict(w=2, theme="Capteurs, santé et activité", rest="X",
        J=["FITNESS", "WEARABLE_COMPANION", "HEALTH_TRACKING"], T=["HEALTH_ADMIN", "GAME", "TRAVEL_LEISURE"],
        why="Pas, distance, exercices, calories."),
    "@HEALTH_BACKGROUND": dict(w=4, theme="Capteurs, santé et activité", rest="X",
        J=["WEARABLE_COMPANION"], T=["FITNESS", "HEALTH_TRACKING"],
        why="Lire l'historique complet ou en arrière-plan."),
    # Mineures.
    "USE_FULL_SCREEN_INTENT": dict(w=2, theme="Fonctionnement de l'app", rest="I",
        J=["MESSAGING"], T=["SOCIAL", "DATING", "HEALTH_ADMIN", "HEALTH_TRACKING", "WEARABLE_COMPANION",
                            "PRODUCTIVITY_CLOUD", "EDUCATION_FAMILY", "SECURITY_AUTH"],
        X=["GAME", "NEWS_READING", "ECOMMERCE"], why="Prendre tout l'écran : appels entrants et alarmes."),
    "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS": dict(w=2, theme="Fonctionnement de l'app", rest="I",
        J=["WEARABLE_COMPANION"], T=["MESSAGING", "FITNESS", "NAVIGATION", "VEHICLE", "HEALTH_TRACKING",
                                     "PRODUCTIVITY_CLOUD", "MEDIA_PLAYER", "ACCESSIBILITY"],
        X=["GAME", "ECOMMERCE", "NEWS_READING"], why="Échapper à l'économiseur pour rester active."),
    "WRITE_SETTINGS": dict(w=3, theme="Contrôle du téléphone", rest="I",
        T=["UTILITY", "ACCESSIBILITY", "VEHICLE"], X=["GAME", "ECOMMERCE", "NEWS_READING", "FINANCE_ACCOUNTS", "SOCIAL"],
        why="Modifier luminosité, sonnerie, veille."),
    "BLUETOOTH_CONNECT": dict(w=2, theme="Appareils à proximité", rest="I",
        J=["WEARABLE_COMPANION", "VEHICLE", "FITNESS", "MEDIA_PLAYER", "PHOTO_VIDEO", "HEALTH_TRACKING"],
        T=["MESSAGING", "NAVIGATION", "GAME", "ACCESSIBILITY", "AI_ASSISTANT", "PRODUCTIVITY_CLOUD"],
        why="Révèle la liste de vos appareils associés."),
    "SCHEDULE_EXACT_ALARM": dict(w=1, theme="Fonctionnement de l'app", rest="I",
        T=["PRODUCTIVITY_CLOUD", "HEALTH_TRACKING", "HEALTH_ADMIN", "EDUCATION_FAMILY", "MESSAGING", "FITNESS",
           "WEARABLE_COMPANION", "TRANSPORT"],
        why="Se réveiller à heure fixe : réveils, rappels, agendas."),
    "READ_MEDIA_VISUAL_USER_SELECTED": dict(w=1, theme="Photos, vidéos et fichiers", rest="copy:READ_MEDIA_IMAGES",
        why="Photos choisies une à une par vous : bien moins intrusif que toute la galerie. Mêmes contextes que les photos."),
}

NEW_GROUPS = [
    {"id": "location_background", "mode": "max", "members": ["ACCESS_BACKGROUND_LOCATION", "FOREGROUND_SERVICE_LOCATION"]},
    {"id": "health_connect", "mode": "max", "members": list(PERMISSION_SETS)},
]
# Les photos choisies rejoignent le groupe « médias » (la plus lourde compte).
MEDIA_GROUP_EXTRA = ["READ_MEDIA_VISUAL_USER_SELECTED"]

# ---------------------------------------------------------------------------------------------------------------------
# 3. Nouvelles catégories (contextes des permissions déjà notées) et rattachements d'apps
# ---------------------------------------------------------------------------------------------------------------------
NEW_CATEGORIES = {
    "HEALTH_TRACKING": dict(display_name="Suivi de santé (cycle, glycémie, tension)",
        description="Apps de suivi médical personnel : elles ont besoin de données de santé intimes, pas du reste.",
        allowed=["ACTIVITY_RECOGNITION", "BODY_SENSORS", "BLUETOOTH_SCAN"],
        tolerated=["CAMERA", "READ_MEDIA_IMAGES", "READ_CALENDAR", "WRITE_CALENDAR", "ACCESS_COARSE_LOCATION",
                   "BODY_SENSORS_BACKGROUND"],
        forbidden=["READ_CONTACTS", "READ_SMS", "RECEIVE_SMS", "SEND_SMS", "READ_CALL_LOG", "RECORD_AUDIO",
                   "ACCESS_BACKGROUND_LOCATION", "ACCESS_FINE_LOCATION", "GET_ACCOUNTS"]),
    "DATING": dict(display_name="Rencontres",
        description="Profils, photos et proximité : la ville suffit, le carnet d'adresses n'a rien à y faire.",
        allowed=["CAMERA", "READ_MEDIA_IMAGES", "READ_EXTERNAL_STORAGE", "ACCESS_COARSE_LOCATION"],
        tolerated=["ACCESS_FINE_LOCATION", "RECORD_AUDIO", "READ_MEDIA_VIDEO"],
        forbidden=["READ_CONTACTS", "READ_SMS", "RECEIVE_SMS", "SEND_SMS", "READ_CALL_LOG", "ACCESS_BACKGROUND_LOCATION",
                   "READ_CALENDAR", "BODY_SENSORS", "GET_ACCOUNTS"]),
    "KEYBOARD": dict(display_name="Claviers",
        description="Un clavier voit tout ce que vous tapez : il ne doit rien demander d'autre que le micro (dictée).",
        allowed=[], tolerated=["RECORD_AUDIO", "READ_CONTACTS"],
        forbidden=["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION", "READ_SMS",
                   "RECEIVE_SMS", "SEND_SMS", "READ_CALL_LOG", "READ_CALENDAR", "CAMERA", "GET_ACCOUNTS",
                   "READ_PHONE_STATE"]),
}
NEW_OVERRIDES = {
    "de.axelspringer.yana.zeropage": "NEWS_READING",
    "org.eu.exodus_privacy.exodusprivacy": "SECURITY_AUTH",
    "org.futo.inputmethod.latin.playstore": "KEYBOARD",
}
NEW_EXCLUDED = ["com.vipacy.app"]  # ancien nom de VYLIA

# ---------------------------------------------------------------------------------------------------------------------
# 4. Badges : nouvelles règles (deux types ajoutés au moteur) et nouveaux badges
# ---------------------------------------------------------------------------------------------------------------------
CAPTURE_SET = ["FOREGROUND_SERVICE_MEDIA_PROJECTION", "FOREGROUND_SERVICE_CAMERA", "FOREGROUND_SERVICE_MICROPHONE"]
BACKGROUND_SET = ["ACCESS_BACKGROUND_LOCATION", "FOREGROUND_SERVICE_LOCATION", "FOREGROUND_SERVICE_MICROPHONE",
                  "FOREGROUND_SERVICE_CAMERA", "FOREGROUND_SERVICE_MEDIA_PROJECTION", "BODY_SENSORS_BACKGROUND",
                  "@HEALTH_BACKGROUND", "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "USE_FULL_SCREEN_INTENT",
                  "SCHEDULE_EXACT_ALARM"]
NEW_BADGES = [
    {"id": "PERM_ROLLING", "label": "Moteur, ça tourne !", "icon": "ic_badge_clapper", "axis": "permissions",
     "polarity": "negative", "severity": "danger",
     "description": "Peut filmer ou enregistrer le son en arrière-plan, accès accordé, sans que sa finalité le justifie.",
     "rule": {"type": "permission_set_context", "permissions": CAPTURE_SET, "contexts": ["UNEXPECTED", "FORBIDDEN"],
              "granted": True, "min": 1}},
    {"id": "PERM_NIGHT_OWL", "label": "Insomniaque", "icon": "ic_badge_owl", "axis": "permissions",
     "polarity": "negative", "severity": "warning",
     "description": "Au moins deux moyens actifs de tourner en arrière-plan (position, micro, réveils, batterie…) que sa finalité ne justifie pas.",
     "rule": {"type": "permission_set_context", "permissions": BACKGROUND_SET, "contexts": ["UNEXPECTED", "FORBIDDEN"],
              "granted": True, "min": 2}},
    {"id": "PERM_DORMANT", "label": "Sait se faire oublier", "icon": "ic_badge_moon", "axis": "permissions",
     "polarity": "positive", "severity": "success",
     "description": "Ne déclare aucun moyen de capter ou de tourner en arrière-plan : quand vous la fermez, elle s'arrête.",
     "rule": {"type": "permissions_absent", "permissions": BACKGROUND_SET}},
]
# Permissions publicitaires neutralisées : le ciblage publicitaire est déjà sanctionné par les traqueurs (régies,
# profilage) ; les compter aussi ici le pénaliserait deux fois. Listées dans les règles pour la transparence.
NEUTRAL_PERMISSIONS = ["ACCESS_ADSERVICES_TOPICS", "ACCESS_ADSERVICES_CUSTOM_AUDIENCE", "ACCESS_ADSERVICES_ATTRIBUTION",
                       "ACCESS_ADSERVICES_AD_ID", AD, "AD_SERVICES_CONFIG"]
# Capacités d'arrière-plan sans effet tant que l'accès dont elles dépendent n'est pas accordé (une seule suffit).
PERMISSION_REQUIRES = {
    "FOREGROUND_SERVICE_MICROPHONE": ["RECORD_AUDIO"],
    "FOREGROUND_SERVICE_CAMERA": ["CAMERA"],
    "FOREGROUND_SERVICE_LOCATION": ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"],
}
# Accord de l'utilisateur redemandé à chaque usage : jamais acquis d'avance.
CONSENT_EACH_TIME = ["FOREGROUND_SERVICE_MEDIA_PROJECTION"]
POLARITY = {"negative": "Point d’attention", "positive": "Bonne pratique"}
BADGE_EMOJI = {"ic_badge_clapper": "🎬", "ic_badge_owl": "🦉", "ic_badge_moon": "🌙"}

THEME_ORDER = ["Localisation", "Messages et appels", "Identité, contacts et comptes", "Micro et caméra", "Agenda",
               "Capteurs, santé et activité", "Photos, vidéos et fichiers", "Appareils à proximité", "Vos applications",
               "Contrôle du téléphone", "Écran et capture", "Publicité et mesure", "Fonctionnement de l'app"]
# Thème des permissions déjà notées (même découpage que le lexique de l'app).
OLD_THEMES = {
    "Localisation": ["ACCESS_BACKGROUND_LOCATION", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_MEDIA_LOCATION"],
    "Messages et appels": ["READ_SMS", "RECEIVE_SMS", "SEND_SMS", "RECEIVE_MMS", "READ_CALL_LOG", "WRITE_CALL_LOG",
                           "PROCESS_OUTGOING_CALLS", "CALL_PHONE", "ANSWER_PHONE_CALLS", "READ_PHONE_STATE",
                           "READ_PHONE_NUMBERS"],
    "Identité, contacts et comptes": ["READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS"],
    "Micro et caméra": ["RECORD_AUDIO", "CAMERA"],
    "Agenda": ["READ_CALENDAR", "WRITE_CALENDAR"],
    "Capteurs, santé et activité": ["BODY_SENSORS", "BODY_SENSORS_BACKGROUND", "ACTIVITY_RECOGNITION"],
    "Photos, vidéos et fichiers": ["READ_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES",
                                   "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO"],
    "Appareils à proximité": ["BLUETOOTH_SCAN", "NEARBY_WIFI_DEVICES"],
    "Vos applications": ["QUERY_ALL_PACKAGES", "REQUEST_INSTALL_PACKAGES", "PACKAGE_USAGE_STATS"],
    "Contrôle du téléphone": ["SYSTEM_ALERT_WINDOW", "BIND_ACCESSIBILITY_SERVICE", "BIND_NOTIFICATION_LISTENER_SERVICE",
                              "BIND_DEVICE_ADMIN"],
}

LABELS = {"J": "Justifiée", "T": "Tolérée", "I": "Inattendue", "X": "Interdite"}


def context(cat, perm):
    if perm in cat.get("allowed", []):
        return "J"
    if perm in cat.get("tolerated", []):
        return "T"
    if perm in cat.get("forbidden", []):
        return "X"
    return "I"


def build():
    base = json.loads(BASE.read_text())
    v3 = copy.deepcopy(base)
    v3["version"] = "3.0"
    v3["last_update"] = "2026-11-01"
    v3["permission_sets"] = PERMISSION_SETS
    v3["permission_requires"] = PERMISSION_REQUIRES
    v3["consent_each_time"] = CONSENT_EACH_TIME
    v3["neutral_permissions"] = NEUTRAL_PERMISSIONS
    v3["categories"].update(copy.deepcopy(NEW_CATEGORIES))
    v3["package_overrides"].update(NEW_OVERRIDES)
    v3["excluded_packages"] = sorted(set(v3["excluded_packages"]) | set(NEW_EXCLUDED))
    groups = v3["permission_rules"]["groups"]
    for g in groups:
        if g["id"] == "media":
            g["members"] += MEDIA_GROUP_EXTRA
    groups += NEW_GROUPS
    defs = v3["badges"]["definitions"]
    # Avant les combinaisons, qui s'évaluent sur les badges de base.
    first_combo = next(i for i, d in enumerate(defs) if d["rule"]["type"].startswith("axes_"))
    defs[first_combo:first_combo] = copy.deepcopy(NEW_BADGES)
    for perm, spec in NEW.items():
        v3["permission_weights"][perm] = spec["w"]
        for cid, cat in v3["categories"].items():
            if cid == "DEFAULT":
                continue
            rest = spec["rest"]
            if rest.startswith("copy:"):
                ctx = context(cat, rest[5:])
            elif cid in spec.get("J", []):
                ctx = "J"
            elif cid in spec.get("T", []):
                ctx = "T"
            elif cid in spec.get("X", []):
                ctx = "X"
            else:
                ctx = rest
            key = {"J": "allowed", "T": "tolerated", "X": "forbidden"}.get(ctx)
            if key:
                cat.setdefault(key, []).append(perm)
    return base, v3


# ---------------------------------------------------------------------------------------------------------------------
# Page de relecture
# ---------------------------------------------------------------------------------------------------------------------

def e(s):
    return html.escape(str(s))


def short(perm):
    return perm.replace("com.google.android.gms.permission.", "gms.")


def render(base, v3):
    cats = list(v3["categories"])
    cats.remove("DEFAULT")
    old_cats = base["categories"]
    themes = {t: list(p) for t, p in OLD_THEMES.items()}
    for perm, spec in NEW.items():
        themes.setdefault(spec["theme"], []).append(perm)

    rows = []
    for theme in THEME_ORDER:
        perms = themes.get(theme, [])
        if not perms:
            continue
        rows.append(f'<tr class="theme"><th scope="rowgroup" colspan="{len(cats) + 2}">{e(theme)}</th></tr>')
        for perm in perms:
            is_new = perm in NEW
            w_old = base["permission_weights"].get(perm)
            w_new = v3["permission_weights"][perm]
            cells = []
            for cid in cats:
                now = context(v3["categories"][cid], perm)
                title = f"{v3['categories'][cid]['display_name']} · {LABELS[now]}"
                cells.append(f'<td class="c {now}{" newc" if cid not in old_cats else ""}" title="{e(title)}">{now}</td>')
            tip = NEW[perm]["why"] if is_new else ""
            members = PERMISSION_SETS.get(perm)
            sub = f'<span class="sub">{len(members)} permissions Health Connect</span>' if members else ""
            rows.append(
                f'<tr data-new="{1 if is_new else 0}"><th scope="row" class="perm{" newp" if is_new else ""}" title="{e(tip)}">'
                f'<code>{e(short(perm))}</code>{sub}</th>'
                f'<td class="w">{"" if w_old is None else w_old}{"" if w_old == w_new else f" → <b>{w_new}</b>" if w_old is not None else f"<b>{w_new}</b>"}</td>'
                + "".join(cells) + "</tr>")

    head = "".join(
        f'<th scope="col" class="cat{" newc" if cid not in old_cats else ""}"><span>{e(v3["categories"][cid]["display_name"])}</span></th>'
        for cid in cats)

    new_rows = "".join(
        f'<tr><td><code>{e(short(p))}</code></td><td>{e(s["theme"])}</td><td class="num">{s["w"]}</td><td>{e(s["why"])}</td></tr>'
        for p, s in NEW.items())
    group_rows = "".join(
        f'<tr><td><code>{e(g["id"])}</code></td><td>{"La plus lourde compte" if g["mode"] == "max" else "La plus lourde + la moitié des autres"}</td>'
        f'<td>{", ".join(f"<code>{e(short(m))}</code>" for m in g["members"])}</td></tr>'
        for g in v3["permission_rules"]["groups"])
    cat_rows = "".join(
        f'<tr><td><b>{e(c["display_name"])}</b><br><code>{e(cid)}</code></td><td>{e(c["description"])}</td></tr>'
        for cid, c in NEW_CATEGORIES.items())
    override_rows = "".join(
        f'<tr><td><code>{e(p)}</code></td><td>{e(v3["categories"][c]["display_name"])}</td></tr>' for p, c in NEW_OVERRIDES.items())
    options = "".join(f'<option value="{e(cid)}">{e(v3["categories"][cid]["display_name"])}</option>' for cid in cats)
    data = {cid: {"name": v3["categories"][cid]["display_name"],
                  "rows": [[p, context(v3["categories"][cid], p), p in NEW or cid not in old_cats]
                           for t in THEME_ORDER for p in themes.get(t, [])]} for cid in cats}
    badge_rows = "".join(
        f'<tr><td class="emoji">{BADGE_EMOJI[b["icon"]]}</td><td><b>{e(b["label"])}</b><br><span class="pill {b["severity"]}">'
        f'{POLARITY[b["polarity"]]}</span></td><td>{e(b["description"])}</td>'
        f'<td><code>{e(b["rule"]["type"])}</code> · {", ".join(f"<code>{e(short(p))}</code>" for p in b["rule"]["permissions"])}'
        f'{" · au moins " + str(b["rule"]["min"]) if b["rule"].get("min", 1) > 1 else ""}</td></tr>'
        for b in NEW_BADGES)
    n_new = len(NEW)
    n_total = len(v3["permission_weights"])

    page = TEMPLATE
    for k, v in {
        "N_NEW": n_new, "N_TOTAL": n_total, "N_OLD": len(base["permission_weights"]), "N_CATS": len(cats),
        "HEAD": head, "ROWS": "".join(rows), "NEW_ROWS": new_rows, "GROUP_ROWS": group_rows, "CAT_ROWS": cat_rows,
        "OVERRIDE_ROWS": override_rows, "OPTIONS": options, "DATA": json.dumps(data, ensure_ascii=False),
        "CAP": v3["scoring"]["permission_cap"], "BADGE_ROWS": badge_rows,
    }.items():
        page = page.replace("{{" + k + "}}", str(v))
    return page


TEMPLATE = (Path(__file__).with_name("matrice_template.html")).read_text()

if __name__ == "__main__":
    base, v3 = build()
    OUT_JSON.write_text(json.dumps(v3, ensure_ascii=False, indent=2) + "\n")
    OUT_HTML.write_text(render(base, v3))
    print(f"{OUT_JSON.relative_to(ROOT)} : {len(v3['permission_weights'])} entrées notées, {len(v3['categories'])} catégories")
    print(f"{OUT_HTML.relative_to(ROOT)}")
