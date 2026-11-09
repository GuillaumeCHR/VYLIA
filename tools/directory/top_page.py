#!/usr/bin/env python3
"""Page web du classement pour vylia-site, en français (fr/classement.html) et en anglais (ranking.html), à partir
des mêmes données que directory/top-google-play.md.

Page statique, lisible sans script ; `classement.js` (local, sans dépendance) ajoute les tris et les filtres. Aucune
ressource externe ni style en ligne : la CSP de la page n'autorise que les fichiers du site. Tous les chiffres du
texte sont calculés à partir des données.

Usage : top_page.py <site> <apps.json> <directory.json> [--compare <apps.json> <directory.json>]
        [--trending <apps.json> <directory.json>] [--oem <apps.json> <directory.json>]
"""
import html
import json
import shutil
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
GRADES = "ABCDE"
SECTION_KEYS = ("top", "pre", "trending", "oem", "compare")
TIER_SORT = {"10B": 10000, "5B": 5000, "1B": 1000, "500M": 500}
e = html.escape

T = {
    "fr": {
        "tiers": {"10B": "10 Md+", "5B": "5 Md+", "1B": "1 Md+", "500M": "500 M+"},
        "categories": {
            "MESSAGING": "Messagerie", "SOCIAL": "Réseau social", "GAME": "Jeu", "MEDIA_PLAYER": "Vidéo, musique",
            "PRODUCTIVITY_CLOUD": "Bureautique, stockage", "AI_ASSISTANT": "Assistant IA", "EDUCATION_FAMILY": "Éducation",
            "NEWS_READING": "Lecture, actualités", "TRANSPORT": "Transport", "PHOTO_VIDEO": "Photo, montage",
            "ECOMMERCE": "Achats", "TRAVEL_LEISURE": "Voyage", "UTILITY": "Utilitaire", "FINANCE_ACCOUNTS": "Argent",
            "NAVIGATION": "Cartes", "BROWSER": "Navigateur", "KEYBOARD": "Clavier", "EMAIL": "E-mail",
            "SECURITY_AUTH": "Sécurité", "HEALTH_ADMIN": "Santé, démarches", "FITNESS": "Sport, habitudes",
            "ACCESSIBILITY": "Accessibilité", "VEHICLE": "Voiture", "WEATHER": "Météo",
            "WEARABLE_COMPANION": "Objets connectés", "DATING": "Rencontres", "HEALTH_TRACKING": "Suivi santé",
            "DEFAULT": "Autre",
        },
        "sections": {"top": "Les plus téléchargées", "pre": "Préinstallées sur certains téléphones",
                     "trending": "En ce moment", "oem": "Google et constructeurs", "compare": "Alternatives"},
        "rank": "n° {r} {c}",
        "no_tracker": "aucun traqueur connu", "none": "aucune",
        "report": "rapport Exodus {v}, {d}", "old": "ancien", "no_report": "pas de rapport Exodus",
        "trackers_n": "Traqueurs ({n})", "perms_n": "Permissions pénalisées ({n})",
        "median": "score médian", "apps": "apps",
    },
    "en": {
        "tiers": {"10B": "10B+", "5B": "5B+", "1B": "1B+", "500M": "500M+"},
        "categories": {
            "MESSAGING": "Messaging", "SOCIAL": "Social network", "GAME": "Game", "MEDIA_PLAYER": "Video, music",
            "PRODUCTIVITY_CLOUD": "Office, storage", "AI_ASSISTANT": "AI assistant", "EDUCATION_FAMILY": "Education",
            "NEWS_READING": "Reading, news", "TRANSPORT": "Transport", "PHOTO_VIDEO": "Photo, editing",
            "ECOMMERCE": "Shopping", "TRAVEL_LEISURE": "Travel", "UTILITY": "Utility", "FINANCE_ACCOUNTS": "Money",
            "NAVIGATION": "Maps", "BROWSER": "Browser", "KEYBOARD": "Keyboard", "EMAIL": "Email",
            "SECURITY_AUTH": "Security", "HEALTH_ADMIN": "Health, admin", "FITNESS": "Sport, habits",
            "ACCESSIBILITY": "Accessibility", "VEHICLE": "Car", "WEATHER": "Weather",
            "WEARABLE_COMPANION": "Connected devices", "DATING": "Dating", "HEALTH_TRACKING": "Health tracking",
            "DEFAULT": "Other",
        },
        "sections": {"top": "Most downloaded", "pre": "Pre-installed on some phones", "trending": "Right now",
                     "oem": "Google and phone makers", "compare": "Alternatives"},
        "rank": "#{r} {c}",
        "no_tracker": "no known tracker", "none": "none",
        "report": "Exodus report {v}, {d}", "old": "old", "no_report": "no Exodus report",
        "trackers_n": "Trackers ({n})", "perms_n": "Penalized permissions ({n})",
        "median": "median score", "apps": "apps",
    },
}


def num(x, lang, digits=0):
    text = f"{x:.{digits}f}" if digits else f"{round(x)}"
    return text.replace(".", ",") if lang == "fr" else text


def pct(part, whole, lang):
    if not whole:
        return "–"
    return f"{round(100 * part / whole)} %" if lang == "fr" else f"{round(100 * part / whole)}%"


def med(values):
    values = list(values)
    return statistics.median(values) if values else 0


class App:
    def __init__(self, meta, entry, section, old_before):
        self.meta, self.entry, self.section = meta, entry, section
        self.name = meta["name"]
        self.package = meta["package"]
        self.cat_id = meta["category"]
        self.maker = meta.get("maker")
        self.scored = entry["status"] == "SCORED"
        self.score = entry.get("score")
        self.grade = entry.get("grade")
        self.trackers = entry.get("trackers", [])
        self.permissions = [p for p in entry.get("permissions", []) if p["counted"] > 0]
        ex = entry.get("exodus") or {}
        self.report_url, self.updated, self.version = ex.get("url"), ex.get("updated", ""), ex.get("version_name", "")
        self.old = bool(self.updated) and self.updated < old_before

    def category(self, lang):
        return T[lang]["categories"].get(self.cat_id, self.cat_id)

    def reach(self, lang):
        m = self.meta
        if "tier" in m:
            return T[lang]["tiers"][m["tier"]], TIER_SORT[m["tier"]]
        ranks = [(c.upper(), m["rank_" + c]) for c in ("fr", "us") if "rank_" + c in m]
        if ranks:
            return " · ".join(T[lang]["rank"].format(r=r, c=c) for c, r in ranks), 300 - min(r for _, r in ranks)
        return (self.maker or "–"), 0

    def label(self, lang):
        if self.section == "oem" and self.maker:
            return self.maker
        return T[lang]["sections"][self.section]


def load(apps_file, directory_file, section, old_before):
    metas = json.loads(Path(apps_file).read_text())
    entries = {a["package"]: a for a in json.loads(Path(directory_file).read_text())["apps"]}
    out = []
    for m in metas["apps"]:
        s = "pre" if m.get("preinstalled_some") else section
        out.append(App(m, entries[m["package"]], s, old_before))
    return out, metas


def bar(width_pct, cls):
    return f'<span class="hbar"><span class="{cls} w{max(0, min(100, round(width_pct)))}"></span></span>'


def grade_bar(apps, label, lang):
    scored = [a for a in apps if a.scored]
    counts = Counter(a.grade for a in scored)
    parts = "".join(f'<span class="g-{g.lower()} f{counts[g]}" title="{g} : {counts[g]}">{g}</span>'
                    for g in GRADES if counts[g])
    legend = " · ".join(f"{g} {counts[g]}" for g in GRADES)
    return (f'<div class="dist"><div class="dist-label"><strong>{e(label)}</strong>'
            f'<span class="muted">{len(scored)} {T[lang]["apps"]} · {T[lang]["median"]} '
            f'{num(med(a.score for a in scored), lang)}</span></div>'
            f'<div class="dist-bar" role="img" aria-label="{e(label)} : {legend}">{parts}</div>'
            f'<div class="dist-legend muted">{legend}</div></div>')


def pill(app):
    if not app.scored:
        return '<span class="pill pill-none">?</span>'
    return f'<span class="pill g-{app.grade.lower()}">{app.grade}</span>'


def row(app, lang):
    t = T[lang]
    trackers = "".join(f"<li>{e(x['name'])}</li>" for x in app.trackers) or f"<li>{t['no_tracker']}</li>"
    perms = "".join(f"<li><code>{e(p['name'])}</code></li>" for p in app.permissions) or f"<li>{t['none']}</li>"
    if app.report_url:
        report = (f'<a href="{e(app.report_url)}" rel="noreferrer">{e(t["report"].format(v=app.version, d=app.updated))}</a>'
                  + (f' <span class="old">{t["old"]}</span>' if app.old else ""))
    else:
        report = t["no_report"]
    details = (f'<details><summary>{e(app.name)}</summary><div class="det">'
               f'<p class="muted"><code>{e(app.package)}</code> · {report}</p>'
               f'<div class="det-cols"><div><strong>{t["trackers_n"].format(n=len(app.trackers))}</strong><ul>{trackers}</ul></div>'
               f'<div><strong>{t["perms_n"].format(n=len(app.permissions))}</strong><ul>{perms}</ul></div></div>'
               f'</div></details>')
    score = app.score if app.scored else -1
    score_html = (f'<span class="score"><span class="score-n">{app.score}</span>'
                  f'<span class="score-bar"><span class="g-{app.grade.lower()} w{app.score}"></span></span></span>'
                  if app.scored else "–")
    reach, reach_sort = app.reach(lang)
    return (f'<tr data-section="{app.section}" data-grade="{app.grade or "-"}" data-score="{score}" '
            f'data-trackers="{len(app.trackers) if app.scored else -1}" data-perms="{len(app.permissions) if app.scored else -1}" '
            f'data-reach="{reach_sort}" data-name="{e(app.name.lower())}" data-cat="{e(app.category(lang))}">'
            f'<td class="c-app">{details}<span class="sec muted">{e(app.label(lang))}</span></td>'
            f'<td class="c-grade">{pill(app)}</td><td class="c-score">{score_html}</td>'
            f'<td class="c-num">{len(app.trackers) if app.scored else "–"}</td>'
            f'<td class="c-num c-perms">{len(app.permissions) if app.scored else "–"}</td>'
            f'<td class="c-cat">{e(app.category(lang))}</td><td class="c-reach">{e(reach)}</td></tr>')


def why(app, lang):
    t, p = len(app.trackers), len(app.permissions)
    if lang == "fr":
        parts = ["aucun traqueur connu" if t == 0 else f"{t} traqueur{'s' if t > 1 else ''}",
                 "aucune permission pénalisée" if p == 0 else f"{p} permission{'s' if p > 1 else ''} pénalisée{'s' if p > 1 else ''}"]
    else:
        parts = ["no known tracker" if t == 0 else f"{t} tracker{'s' if t > 1 else ''}",
                 "no penalized permission" if p == 0 else f"{p} penalized permission{'s' if p > 1 else ''}"]
    return ", ".join(parts).capitalize() + "."


def good_card(app, lang):
    return (f'<article class="good"><div class="good-head">{pill(app)}<div><strong>{e(app.name)}</strong>'
            f'<span class="muted">{e(app.category(lang))}</span></div>'
            f'<span class="good-score">{app.score}<small>/100</small></span></div><p>{e(why(app, lang))}</p></article>')


def prevalence(apps, key, lang, limit):
    """Lignes « nom · barre · part des apps » pour les traqueurs ou les permissions les plus fréquents."""
    counts = Counter(name for a in apps for name in {x["name"] for x in getattr(a, key)})
    rows = []
    for name, c in counts.most_common(limit):
        label = f"<code>{e(name)}</code>" if key == "permissions" else e(name)
        rows.append(f'<li><span class="pv-name">{label}</span>{bar(100 * c / len(apps), "pv")}'
                    f'<span class="pv-n">{pct(c, len(apps), lang)}</span></li>')
    return f'<ol class="prevalence">{"".join(rows)}</ol>'


def category_table(apps, lang):
    groups = defaultdict(list)
    for a in apps:
        groups[a.cat_id].append(a)
    rows = []
    for cid, items in sorted(groups.items(), key=lambda kv: -med(a.score for a in kv[1])):
        if len(items) < 3:
            continue
        m = med(a.score for a in items)
        grade = next((g for g, lo in zip(GRADES, (80, 65, 50, 35, -1)) if m >= lo), "E")
        rows.append(f'<tr><td>{e(T[lang]["categories"].get(cid, cid))}</td><td class="c-num">{len(items)}</td>'
                    f'<td><span class="score"><span class="score-n">{num(m, lang)}</span>'
                    f'<span class="score-bar"><span class="g-{grade.lower()} w{round(m)}"></span></span></span></td>'
                    f'<td class="c-num">{num(med(len(a.trackers) for a in items), lang)}</td>'
                    f'<td class="c-num">{num(med(len(a.permissions) for a in items), lang)}</td></tr>')
    h = ("<th>Catégorie</th><th class='c-num'>Apps</th><th>Score médian</th><th class='c-num'>Traqueurs (médiane)</th>"
         "<th class='c-num'>Permissions pénalisées (médiane)</th>") if lang == "fr" else (
        "<th>Category</th><th class='c-num'>Apps</th><th>Median score</th><th class='c-num'>Trackers (median)</th>"
        "<th class='c-num'>Penalized permissions (median)</th>")
    return f'<div class="table-wrap"><table class="small"><thead><tr>{h}</tr></thead><tbody>{"".join(rows)}</tbody></table></div>'


def maker_table(oem, lang):
    groups = defaultdict(list)
    for a in oem:
        groups[a.maker].append(a)
    rows = []
    for maker, items in sorted(groups.items(), key=lambda kv: -med(a.score for a in kv[1])):
        counts = Counter(a.grade for a in items)
        parts = "".join(f'<span class="g-{g.lower()} f{counts[g]}" title="{g} : {counts[g]}">{g}</span>'
                        for g in GRADES if counts[g])
        rows.append(f'<tr><td><strong>{e(maker)}</strong></td><td class="c-num">{len(items)}</td>'
                    f'<td class="c-num">{num(med(a.score for a in items), lang)}</td>'
                    f'<td><div class="dist-bar mini">{parts}</div></td>'
                    f'<td class="c-num">{num(med(len(a.trackers) for a in items), lang)}</td>'
                    f'<td class="c-num">{num(med(len(a.permissions) for a in items), lang)}</td></tr>')
    h = ("<th>Constructeur</th><th class='c-num'>Apps notées</th><th class='c-num'>Score médian</th><th>Notes</th>"
         "<th class='c-num'>Traqueurs (médiane)</th><th class='c-num'>Permissions pénalisées (médiane)</th>") if lang == "fr" else (
        "<th>Maker</th><th class='c-num'>Scored apps</th><th class='c-num'>Median score</th><th>Grades</th>"
        "<th class='c-num'>Trackers (median)</th><th class='c-num'>Penalized permissions (median)</th>")
    return f'<div class="table-wrap"><table class="small"><thead><tr>{h}</tr></thead><tbody>{"".join(rows)}</tbody></table></div>'


FAMILIES = [
    ("TikTok", ["com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go", "com.tiktok.lite.go"]),
    ("Facebook", ["com.facebook.katana", "com.facebook.lite"]),
    ("WhatsApp", ["com.whatsapp", "com.whatsapp.w4b"]),
    ("Google", ["com.google.android.googlequicksearchbox", "com.google.android.apps.searchlite"]),
    ("DramaBox", ["com.storymatrix.drama", "com.storymatrix.lite"]),
]


def variants(apps, lang):
    """Variantes d'une même app (Lite, Go, Business…) : paquets différents, rapports et notes différents."""
    by_pkg = {a.package: a for a in apps if a.scored}
    cards = []
    for family, packages in FAMILIES:
        items = [by_pkg[p] for p in packages if p in by_pkg]
        if len(items) < 2:
            continue
        lines = "".join(
            f'<li>{pill(a)}<span class="v-name"><strong>{e(a.name)}</strong><code>{e(a.package)}</code></span>'
            f'<span class="v-score">{a.score}</span><span class="muted v-meta">'
            + (f"{len(a.trackers)} traqueur{'s' if len(a.trackers) > 1 else ''} · rapport {e(a.updated)}" if lang == "fr"
               else f"{len(a.trackers)} tracker{'s' if len(a.trackers) > 1 else ''} · report {e(a.updated)}")
            + (f' <span class="old">{T[lang]["old"]}</span>' if a.old else "") + "</span></li>"
            for a in sorted(items, key=lambda a: -a.score))
        cards.append(f'<article class="variant"><h3>{e(family)}</h3><ul>{lines}</ul></article>')
    return "".join(cards)


VARIANT_EXAMPLE = {"fr": "", "en": ""}


def variant_example(apps):
    """Phrase d'exemple pour la carte « version allégée », à partir des notes réelles."""
    by_pkg = {a.package: a for a in apps if a.scored}
    pairs = [(by_pkg.get(a), by_pkg.get(b)) for a, b in (("com.facebook.katana", "com.facebook.lite"),
                                                          ("com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go"))]
    pairs = [(full, lite) for full, lite in pairs if full and lite and not full.old and not lite.old]
    fr_parts = [f"{lite.name} obtient {lite.grade} ({lite.score}) contre {full.grade} ({full.score}) pour {full.name}" for full, lite in pairs]
    en_parts = [f"{lite.name} gets {lite.grade} ({lite.score}) against {full.grade} ({full.score}) for {full.name}" for full, lite in pairs]
    return {"fr": (" ; ".join(fr_parts) + ". À usage égal, c'est un gain d'exposition immédiat.") if pairs else "",
            "en": ("; ".join(en_parts) + ". For the same use, the exposure gain is immediate.") if pairs else ""}


def stats(apps):
    top = [a for a in apps if a.section == "top" and a.scored]
    games = [a for a in top if a.cat_id == "GAME"]
    others = [a for a in top if a.cat_id != "GAME"]
    msg = [a for a in apps if a.section in ("top", "pre", "trending") and a.scored and a.cat_id in ("MESSAGING", "SOCIAL")]
    tracker_count = Counter(t["name"] for a in top for t in a.trackers)
    return {
        "top": top, "games": games, "others": others, "msg": msg,
        "de": sum(1 for a in top if a.grade in "DE"),
        "with_trackers": sum(1 for a in top if a.trackers),
        "firebase": tracker_count.get("Google Firebase Analytics", 0),
    }


def landscape(third_party, lang):
    """Commentaire sur les catégories et les traqueurs les plus répandus (apps tierces)."""
    n = lambda x: num(x, lang)
    groups = defaultdict(list)
    for a in third_party:
        groups[a.cat_id].append(a)
    cats = sorted(((med(x.score for x in v), k) for k, v in groups.items() if len(v) >= 3), reverse=True)
    total = len(third_party)
    counts = Counter(name for a in third_party for name in {t["name"] for t in a.trackers})
    top3 = counts.most_common(3)
    google = sum(1 for a in third_party if any(t["name"].startswith("Google") for t in a.trackers))
    meta = sum(1 for a in third_party if any(t["name"].startswith("Facebook") for t in a.trackers))
    if not cats or not top3:
        return ""
    label = lambda k: e(T[lang]["categories"].get(k, k).lower())
    (n1, c1), (n2, c2), (n3, c3) = (top3 + [("", 0)] * 3)[:3]
    if lang == "fr":
        return (f"<p><strong>Par catégorie et par traqueur.</strong> L'écart entre catégories est net : les apps "
                f"« {label(cats[0][1])} » obtiennent un score médian de {n(cats[0][0])}, les apps « {label(cats[-1][1])} » "
                f"de {n(cats[-1][0])}. Les mêmes traqueurs reviennent partout : {e(n1)} est présent dans "
                f"{pct(c1, total, lang)} des apps tierces, {e(n2)} dans {pct(c2, total, lang)} et {e(n3)} dans "
                f"{pct(c3, total, lang)}. {pct(google, total, lang)} des apps embarquent au moins un outil de Google et "
                f"{pct(meta, total, lang)} au moins un kit de Facebook : quelques entreprises voient ainsi passer les "
                f"données d'une grande partie de vos apps. Détail en bas de page.</p>")
    return (f"<p><strong>By category and by tracker.</strong> The gap between categories is clear: "
            f"\"{label(cats[0][1])}\" apps get a median score of {n(cats[0][0])}, \"{label(cats[-1][1])}\" apps "
            f"{n(cats[-1][0])}. The same trackers come up everywhere: {e(n1)} is in {pct(c1, total, lang)} of "
            f"third-party apps, {e(n2)} in {pct(c2, total, lang)} and {e(n3)} in {pct(c3, total, lang)}. {pct(google, total, lang)} of apps embed at least one Google tool and {pct(meta, total, lang)} "
            f"at least one Facebook kit: a few companies thus see data from a large share of your apps. Details at "
            f"the bottom of the page.</p>")


def analysis(lang, s, now, oem, alt, third_party):
    n = lambda x, d=0: num(x, lang, d)
    top, games, others, msg = s["top"], s["games"], s["others"], s["msg"]
    out = []
    if lang == "fr":
        out.append(f"<p>Le niveau général est <strong>médiocre</strong>. Parmi les {len(top)} apps les plus téléchargées, "
                   f"{sum(1 for a in top if a.grade == 'A')} obtient A, {s['de']} sont notées D ou E, et le score médian "
                   f"plafonne à {n(med(a.score for a in top))} sur 100. {s['with_trackers']} apps sur {len(top)} embarquent "
                   f"au moins un traqueur connu ; Google Firebase Analytics est présent dans {s['firebase']} d'entre elles.</p>")
        if games:
            out.append(f"<p><strong>Les jeux concentrent les traqueurs.</strong> Les {len(games)} jeux de la liste en embarquent "
                       f"{n(med(len(a.trackers) for a in games))} en médiane, contre {n(med(len(a.trackers) for a in others))} "
                       f"pour les autres apps : des kits de monétisation et de profilage qui suivent le joueur d'une app à "
                       f"l'autre. Leur note reste parfois moyenne parce qu'ils demandent peu de permissions "
                       f"({n(med(len(a.permissions) for a in games))} pénalisée en médiane).</p>")
        if msg:
            out.append(f"<p><strong>Messageries et réseaux sociaux : moins de traqueurs, plus d'accès.</strong> Les "
                       f"{len(msg)} apps de ces catégories embarquent {n(med(len(a.trackers) for a in msg))} traqueurs en "
                       f"médiane, mais {n(med(len(a.permissions) for a in msg))} permissions pénalisées : contacts, "
                       f"journal d'appels, localisation, micro. C'est là que vos réglages comptent le plus.</p>")
        if now:
            out.append(f"<p><strong>Les apps du moment ne font pas mieux.</strong> Parmi les {len(now)} apps des tops du jour "
                       f"en France et aux États-Unis, le score médian est de {n(med(a.score for a in now))} et "
                       f"{pct(sum(1 for a in now if a.grade in 'DE'), len(now), lang)} sont notées D ou E.</p>")
        if oem:
            by = defaultdict(list)
            for a in oem:
                by[a.maker].append(a.score)
            ranked = sorted(((med(v), m) for m, v in by.items() if len(v) >= 3), reverse=True)
            if ranked:
                out.append(f"<p><strong>Les apps des constructeurs, souvent impossibles à désinstaller.</strong> Elles "
                           f"obtiennent un score médian de {n(med(a.score for a in oem))}. Parmi les constructeurs dont au "
                           f"moins trois apps ont un rapport, {e(ranked[0][1])} fait le mieux ({n(ranked[0][0])}) et "
                           f"{e(ranked[-1][1])} le moins bien ({n(ranked[-1][0])}). Ces apps embarquent peu de traqueurs "
                           f"connus, mais beaucoup d'accès : elles font partie du système.</p>")
        if alt:
            out.append(f"<p>À l'inverse, les alternatives que propose l'app VYLIA obtiennent un score médian de "
                       f"<strong>{n(med(a.score for a in alt))}</strong>, et {sum(1 for a in alt if a.grade in 'AB')} sur "
                       f"{len(alt)} sont notées A ou B. Une bonne note est donc possible : elle dépend de choix de "
                       f"conception, pas du type d'app.</p>")
    else:
        out.append(f"<p>The overall level is <strong>poor</strong>. Of the {len(top)} most downloaded apps, "
                   f"{sum(1 for a in top if a.grade == 'A')} gets an A, {s['de']} are graded D or E, and the median score "
                   f"tops out at {n(med(a.score for a in top))} out of 100. {s['with_trackers']} apps out of {len(top)} embed "
                   f"at least one known tracker; Google Firebase Analytics is in {s['firebase']} of them.</p>")
        if games:
            out.append(f"<p><strong>Games concentrate trackers.</strong> The {len(games)} games in the list embed "
                       f"{n(med(len(a.trackers) for a in games))} of them on median, against "
                       f"{n(med(len(a.trackers) for a in others))} for the other apps: monetization and profiling kits that "
                       f"follow the player from one app to the next. Their grade sometimes stays average because they ask "
                       f"for few permissions ({n(med(len(a.permissions) for a in games))} penalized on median).</p>")
        if msg:
            out.append(f"<p><strong>Messaging and social apps: fewer trackers, more access.</strong> The {len(msg)} apps in "
                       f"these categories embed {n(med(len(a.trackers) for a in msg))} trackers on median, but "
                       f"{n(med(len(a.permissions) for a in msg))} penalized permissions: contacts, call log, location, "
                       f"microphone. This is where your settings matter most.</p>")
        if now:
            out.append(f"<p><strong>Today's hits do no better.</strong> Among the {len(now)} apps of the daily charts in "
                       f"France and the United States, the median score is {n(med(a.score for a in now))} and "
                       f"{pct(sum(1 for a in now if a.grade in 'DE'), len(now), lang)} are graded D or E.</p>")
        if oem:
            by = defaultdict(list)
            for a in oem:
                by[a.maker].append(a.score)
            ranked = sorted(((med(v), m) for m, v in by.items() if len(v) >= 3), reverse=True)
            if ranked:
                out.append(f"<p><strong>Phone makers' apps, often impossible to uninstall.</strong> They get a median score "
                           f"of {n(med(a.score for a in oem))}. Among the makers with at least three reported apps, "
                           f"{e(ranked[0][1])} does best ({n(ranked[0][0])}) and {e(ranked[-1][1])} worst "
                           f"({n(ranked[-1][0])}). These apps embed few known trackers but a lot of access: they are part "
                           f"of the system.</p>")
        if alt:
            out.append(f"<p>By contrast, the alternatives suggested by the VYLIA app get a median score of "
                       f"<strong>{n(med(a.score for a in alt))}</strong>, and {sum(1 for a in alt if a.grade in 'AB')} out of "
                       f"{len(alt)} are graded A or B. A good grade is possible: it depends on design choices, not on the "
                       f"kind of app.</p>")
    out.insert(min(3, len(out)), landscape(third_party, lang))
    return "\n".join(out)


def render(lang, apps, meta, directory, flags):
    t = T[lang]
    n = lambda x, d=0: num(x, lang, d)
    fr_ = lang == "fr"
    date = directory["generated"]
    s = stats(apps)
    VARIANT_EXAMPLE.update(variant_example(apps))
    top = s["top"]
    pre = [a for a in apps if a.section == "pre" and a.scored]
    now = [a for a in apps if a.section == "trending" and a.scored]
    oem = [a for a in apps if a.section == "oem" and a.scored]
    alt = [a for a in apps if a.section == "compare" and a.scored]
    popular = top + pre
    third_party = top + pre + now

    tiles = [
        (str(sum(1 for a in popular if a.grade == "A")),
         f"app notée A sur les {len(popular)} les plus téléchargées" if fr_ else f"apps graded A among the {len(popular)} most downloaded"),
        (pct(s["de"], len(top), lang), "des plus téléchargées notées D ou E" if fr_ else "of the most downloaded graded D or E"),
        (n(med(a.score for a in top)), "score médian sur 100 (les plus téléchargées)" if fr_ else "median score out of 100 (most downloaded)"),
        (n(med(len(a.trackers) for a in top)),
         (f"traqueurs par app en médiane, jusqu'à {max(len(a.trackers) for a in top)}" if fr_
          else f"trackers per app on median, up to {max(len(a.trackers) for a in top)}")),
    ]
    tiles_html = "".join(f'<div class="tile"><span class="tile-n">{v}</span><span class="tile-l">{e(l)}</span></div>' for v, l in tiles)

    dists = [grade_bar(top, t["sections"]["top"], lang), grade_bar(pre, t["sections"]["pre"], lang)]
    for group, key in ((now, "trending"), (oem, "oem"), (alt, "compare")):
        if group:
            dists.append(grade_bar(group, t["sections"][key], lang))

    best = sorted([a for a in third_party if a.grade in "AB"], key=lambda a: (-a.score, a.name))[:8]
    best_alt = sorted(alt, key=lambda a: (-a.score, a.name))[:8]
    worst = sorted(third_party, key=lambda a: (a.score, -len(a.trackers), a.name))[:8]

    section_chips = "".join(
        f'<button type="button" class="chip" data-filter="section" data-value="{k}" '
        f'aria-pressed="{"false" if k == "compare" else "true"}">{e(t["sections"][k])}</button>'
        for k in SECTION_KEYS if any(a.section == k for a in apps))
    grade_chips = "".join(
        f'<button type="button" class="chip chip-g g-{g.lower()}" data-filter="grade" data-value="{g}" aria-pressed="true">{g}</button>'
        for g in GRADES)
    cat_options = "".join(f'<option value="{e(c)}">{e(c)}</option>' for c in sorted({a.category(lang) for a in apps}))
    rows = "".join(row(a, lang) for a in sorted(apps, key=lambda a: (-(a.score if a.scored else -1), a.name.lower())))
    reports = sorted(a.updated for a in apps if a.updated)
    alt_median = n(med(a.score for a in alt)) if alt else None

    # Liens, chemins et en-tête propres à chaque langue.
    if fr_:
        path, root, other, other_label = "fr/classement.html", "../", "../ranking.html", "English"
        nav = f'<a href="./">Accueil</a><a href="confidentialite.html">Confidentialité</a><a href="{other}" hreflang="en" lang="en">{other_label}</a>'
    else:
        path, root, other, other_label = "ranking.html", "", "fr/classement.html", "Français"
        nav = f'<a href="./">Home</a><a href="privacy.html">Privacy</a><a href="{other}" hreflang="fr" lang="fr">{other_label}</a>'

    sources = []
    if fr_:
        sources.append(f'<a href="{e(meta["source"])}">Wikipedia, List of most-downloaded Google Play applications</a> '
                       "(apps gratuites de 500 millions à 10 milliards de téléchargements)")
        if flags["trending"]:
            sources.append('<a href="https://appinsider.io/android/charts/fr/all/free">AppInsider</a> pour « En ce moment » '
                           "(top du jour des apps gratuites, France et États-Unis, 9 novembre 2026)")
        if flags["oem"]:
            sources.append("pour « Google et constructeurs », les apps de Google et des constructeurs présentes dans ces deux sources")
        if flags["compare"]:
            sources.append("les alternatives de l'app VYLIA disponibles sur Google Play")
    else:
        sources.append(f'<a href="{e(meta["source"])}">Wikipedia, List of most-downloaded Google Play applications</a> '
                       "(free apps from 500 million to 10 billion downloads)")
        if flags["trending"]:
            sources.append('<a href="https://appinsider.io/android/charts/us/all/free">AppInsider</a> for "Right now" '
                           "(daily charts of free apps, France and the United States, 9 November 2026)")
        if flags["oem"]:
            sources.append('for "Google and phone makers", the apps of Google and phone makers found in those two sources')
        if flags["compare"]:
            sources.append("the alternatives of the VYLIA app available on Google Play")

    L = {}
    if fr_:
        L.update(
            title="Le bulletin vie privée des apps les plus téléchargées",
            desc="Les apps les plus téléchargées sur Google Play notées de A à E selon leurs permissions et leurs traqueurs, avec le moteur de score de VYLIA et les rapports publics d'Exodus Privacy.",
            kicker=f"Classement · {date}",
            lead=f"{len(apps)} applications Android notées de A à E selon les permissions qu'elles demandent et les traqueurs qu'elles embarquent, avec le même moteur de score que l'app VYLIA et les rapports publics d'<a href=\"https://exodus-privacy.eu.org/\">Exodus Privacy</a>.",
            note="<strong>Ce sont des notes d'exposition maximale.</strong> Hors du téléphone, on ne sait pas ce que vous avez autorisé : toutes les permissions déclarées sont comptées comme accordées. Sur votre téléphone, VYLIA ne compte pleinement que celles que vous avez accordées. La note y est souvent meilleure, et l'écart montre ce que vous gagnez en refusant des accès. Les traqueurs, eux, sont dans le code : ils pèsent autant partout. Exemple sur un téléphone de test, le 9 novembre 2026 : Telegram passe de C (50) à B (83), Instagram de E (29) à C (51) et ChatGPT de D (39) à B (71), selon les accès accordés.",
            h_findings="Le constat", h_good="Les bons élèves",
            good_intro="Parmi les apps les plus téléchargées et celles du moment, celles qui s'en sortent le mieux.",
            h_good_alt="Et parmi les alternatives", h_bad="Les mauvais élèves",
            h_var="Une app, plusieurs versions",
            var_intro="Une même app existe parfois sous plusieurs noms de paquets : version « Lite » ou « Go » pour les téléphones d'entrée de gamme, version « Business », version régionale. Ce sont des applications différentes, avec leur propre code, leurs traqueurs et leurs permissions, donc leur propre note. À usage égal, choisir la version allégée peut réduire nettement l'exposition. Attention aux rapports anciens : quand Exodus n'arrive plus à analyser une grosse app, sa note porte sur une version dépassée (c'est le cas de TikTok, dont le dernier rapport date de 2024).",
            bad_intro="À l'autre bout du classement : les apps les plus exposantes, souvent cumulant traqueurs et accès.",
            h_cat="Par catégorie", cat_intro="Apps tierces (les plus téléchargées, préinstallées sur certains téléphones, en ce moment), catégories d'au moins trois apps.",
            h_trk="Les traqueurs les plus répandus", trk_intro="Part des apps tierces qui embarquent chaque traqueur.",
            h_perm="Les permissions les plus pénalisées", perm_intro="Part des apps tierces pénalisées pour chaque permission.",
            h_oem="Google et les constructeurs",
            oem_intro="Les apps de Google et des fabricants de téléphones, souvent préinstallées et impossibles à désinstaller (on peut parfois les désactiver). Beaucoup sont des apps système sans rapport Exodus : seules celles publiées sur Google Play sont notées.",
            h_reduce="Réduire son exposition",
            reduce_intro="Une note médiocre n'est pas une fatalité. Votre <strong>surface d'exposition</strong>, c'est-à-dire tout ce que vos applications peuvent savoir de vous, dépend autant de vos réglages que des apps elles-mêmes. Entre une app à qui l'on a tout accordé et qui communique librement avec ses traqueurs, et la même app aux accès limités dont les traqueurs sont filtrés, l'écart est réel.",
            levers=[
                ("Limiter les permissions", "Refuser la localisation, les contacts, le micro ou le journal d'appels quand l'app n'en a pas besoin. C'est l'écart entre la note de ce classement et celle de votre téléphone : sur un téléphone de test, Telegram passe de C (50) à B (83) et ChatGPT de D (39) à B (71)."),
                ("Filtrer les traqueurs", "Les traqueurs sont dans le code : refuser des accès ne les retire pas. Le bouclier de VYLIA, un VPN anti-traqueurs local, les empêche de joindre leurs serveurs, dans toutes les apps, y compris celles que vous gardez. Le score, lui, mesure ce que l'app embarque et ne change pas."),
                ("Supprimer les apps inutilisées", "Une app oubliée garde ses accès et ses traqueurs. VYLIA repère celles que vous n'ouvrez plus : les désinstaller réduit l'exposition sans rien perdre."),
                ("Passer à une alternative", "Pour beaucoup d'usages, une app plus respectueuse existe. " + (f"Les alternatives que propose VYLIA obtiennent un score médian de {alt_median} dans ce classement." if alt else "VYLIA en propose par catégorie, dans l'écran Gérer.")),
                ("Utiliser la version web", "Beaucoup de services s'utilisent dans le navigateur, installés comme une app (WebAPK). L'app et ses traqueurs disparaissent du téléphone, et le navigateur limite ce que le site peut savoir."),
                ("Choisir la version allégée", "Une même app existe parfois en version « Lite » ou « Go », avec son propre code et donc sa propre note. " + VARIANT_EXAMPLE["fr"]),
            ],
            h_table="Le classement complet", search="Rechercher une app…", search_label="Rechercher une app",
            cat_label="Catégorie", all_cats="Toutes les catégories", lists="Listes", grades="Notes",
            count_tpl="{n} apps affichées sur {total}",
            th=["App", "Note", "Score", "Traqueurs", "Permissions pénalisées", "Catégorie", "Téléchargements"],
            hint="Touchez le nom d'une app pour voir ses traqueurs et ses permissions pénalisées." + (" Les alternatives sont masquées par défaut : touchez « Alternatives » pour les afficher." if alt else ""),
            h_method="Méthode",
            method=[
                f"<strong>Quelles apps.</strong> Sources : {'; '.join(sources)}. Les apps de Google, d'Apple et des fabricants (Samsung, Xiaomi, Huawei, Motorola, vivo, Oppo, Transsion) sont classées à part ; les apps des opérateurs, les services système sans fiche Google Play et les apps arrêtées sont écartées. Les apps d'éditeurs indépendants préinstallées seulement sur certains téléphones (Facebook, Instagram, TikTok, Netflix, Office…) sont classées à part, car leur compteur de téléchargements est gonflé.",
                f"<strong>Note et score.</strong> Grille VYLIA {e(directory['rules_version'])}, comme dans l'app : les permissions sont jugées selon ce que l'app fait (la localisation ne coûte rien à une app de cartes, beaucoup à un jeu), les traqueurs selon leur type.",
                "<strong>Exposition maximale.</strong> Hors du téléphone, on ne sait pas ce que vous avez accordé : toutes les permissions déclarées sont comptées comme accordées. Sur votre téléphone, une app peut obtenir une meilleure note si vous refusez des accès.",
                "<strong>Traqueurs.</strong> Signatures trouvées dans le code par l'analyse statique d'Exodus Privacy, sur la dernière version publiée sur Google Play. Leur présence ne prouve pas leur activité, et une app peut contenir des traqueurs qu'Exodus ne connaît pas encore.",
                f"<strong>Limites.</strong> Le SDK cible n'est pas connu (l'ancien accès au stockage est compté) ; ce que l'éditeur fait des données sur ses serveurs et les motifs de sécurité ne sont pas mesurés. Rapports Exodus du {e(reports[0])} au {e(reports[-1])} ; « ancien » signale un rapport de plus d'un an.",
                "<strong>Données.</strong> Le détail complet est publié sous licence ODbL 1.0 dans le <a href=\"https://github.com/GuillaumeCHR/VYLIA/tree/main/directory\">dépôt de VYLIA</a>.",
            ],
            footer=f"VYLIA · Yogi Studio · Classement ponctuel du {e(date)}, ce n'est pas un classement officiel.<br>Contient des informations d'<a href=\"https://exodus-privacy.eu.org/\">Exodus Privacy</a>, disponibles sous <a href=\"https://opendatacommons.org/licenses/odbl/1-0/\">Open Database License (ODbL 1.0)</a>. VYLIA est une application indépendante, ni affiliée ni approuvée par Exodus Privacy, Wikipedia ou AppInsider.",
        )
    else:
        L.update(
            title="The privacy report card of the most downloaded apps",
            desc="The most downloaded apps on Google Play graded from A to E on their permissions and trackers, with VYLIA's scoring engine and Exodus Privacy's public reports.",
            kicker=f"Ranking · {date}",
            lead=f"{len(apps)} Android apps graded from A to E on the permissions they ask for and the trackers they embed, with the same scoring engine as the VYLIA app and the public reports of <a href=\"https://exodus-privacy.eu.org/\">Exodus Privacy</a>.",
            note="<strong>These are maximum-exposure grades.</strong> Off the phone, nobody knows what you allowed: every declared permission is counted as granted. On your phone, VYLIA only fully counts the ones you granted. The grade there is often better, and the gap shows what you gain by refusing access. Trackers are in the code: they weigh the same everywhere. Example on a test phone, on 9 November 2026: Telegram goes from C (50) to B (83), Instagram from E (29) to C (51) and ChatGPT from D (39) to B (71), depending on the access granted.",
            h_findings="Findings", h_good="Good examples",
            good_intro="Among the most downloaded apps and today's hits, the ones that do best.",
            h_good_alt="And among the alternatives", h_bad="Bad examples",
            h_var="One app, several versions",
            var_intro="The same app sometimes exists under several package names: a \"Lite\" or \"Go\" version for entry-level phones, a \"Business\" version, a regional version. These are different apps, with their own code, trackers and permissions, hence their own grade. For the same use, picking the lighter version can cut exposure a lot. Watch out for old reports: when Exodus can no longer analyse a big app, its grade is based on an outdated version (this is the case for TikTok, whose latest report dates from 2024).",
            bad_intro="At the other end of the ranking: the most exposing apps, often combining trackers and access.",
            h_cat="By category", cat_intro="Third-party apps (most downloaded, pre-installed on some phones, right now), categories with at least three apps.",
            h_trk="The most widespread trackers", trk_intro="Share of third-party apps that embed each tracker.",
            h_perm="The most penalized permissions", perm_intro="Share of third-party apps penalized for each permission.",
            h_oem="Google and phone makers",
            oem_intro="The apps of Google and phone makers, often pre-installed and impossible to uninstall (they can sometimes be disabled). Many are system apps without an Exodus report: only the ones published on Google Play are graded.",
            h_reduce="Reducing your exposure",
            reduce_intro="A poor grade is not a fate. Your <strong>exposure surface</strong>, everything your apps can know about you, depends as much on your settings as on the apps themselves. Between an app that was granted everything and talks freely to its trackers, and the same app with limited access and filtered trackers, the gap is real.",
            levers=[
                ("Limit permissions", "Refuse location, contacts, microphone or call log when the app does not need them. That is the gap between the grade in this ranking and the one on your phone: on a test phone, Telegram goes from C (50) to B (83) and ChatGPT from D (39) to B (71)."),
                ("Filter trackers", "Trackers are in the code: refusing access does not remove them. VYLIA's shield, a local anti-tracker VPN, stops them from reaching their servers, in every app, including the ones you keep. The score measures what the app embeds and does not change."),
                ("Remove unused apps", "A forgotten app keeps its access and its trackers. VYLIA spots the ones you no longer open: uninstalling them reduces your exposure at no cost."),
                ("Switch to an alternative", "For many uses, a more respectful app exists. " + (f"The alternatives suggested by VYLIA get a median score of {alt_median} in this ranking." if alt else "VYLIA suggests some by category, in the Manage screen.")),
                ("Use the web version", "Many services work in the browser, installed like an app (WebAPK). The app and its trackers leave the phone, and the browser limits what the site can learn."),
                ("Pick the lighter version", "The same app sometimes exists as a \"Lite\" or \"Go\" version, with its own code and therefore its own grade. " + VARIANT_EXAMPLE["en"]),
            ],
            h_table="The full ranking", search="Search an app…", search_label="Search an app",
            cat_label="Category", all_cats="All categories", lists="Lists", grades="Grades",
            count_tpl="{n} of {total} apps shown",
            th=["App", "Grade", "Score", "Trackers", "Penalized permissions", "Category", "Downloads"],
            hint="Tap an app's name to see its trackers and penalized permissions." + (' Alternatives are hidden by default: tap "Alternatives" to show them.' if alt else ""),
            h_method="Method",
            method=[
                f"<strong>Which apps.</strong> Sources: {'; '.join(sources)}. Apps from Google, Apple and phone makers (Samsung, Xiaomi, Huawei, Motorola, vivo, Oppo, Transsion) are ranked apart; carrier apps, system services without a Google Play listing and discontinued apps are left out. Apps from independent publishers pre-installed only on some phones (Facebook, Instagram, TikTok, Netflix, Office…) are ranked apart, as their download count is inflated.",
                f"<strong>Grade and score.</strong> VYLIA grid {e(directory['rules_version'])}, as in the app: permissions are judged by what the app is for (location costs nothing to a maps app, a lot to a game), trackers by their type.",
                "<strong>Maximum exposure.</strong> Off the phone, nobody knows what you granted: every declared permission is counted as granted. On your phone, an app can get a better grade if you refuse access.",
                "<strong>Trackers.</strong> Signatures found in the code by Exodus Privacy's static analysis, on the latest version published on Google Play. Their presence does not prove their activity, and an app can contain trackers Exodus does not know yet.",
                f"<strong>Limits.</strong> The target SDK is unknown (legacy storage access is counted); what the publisher does with data on its servers and security patterns are not measured. Exodus reports from {e(reports[0])} to {e(reports[-1])}; \"old\" flags a report more than a year old.",
                "<strong>Data.</strong> The full details are published under the ODbL 1.0 licence in the <a href=\"https://github.com/GuillaumeCHR/VYLIA/tree/main/directory\">VYLIA repository</a>.",
            ],
            footer=f"VYLIA · Yogi Studio · One-off ranking of {e(date)}, not an official ranking.<br>Contains information from <a href=\"https://exodus-privacy.eu.org/\">Exodus Privacy</a>, made available under the <a href=\"https://opendatacommons.org/licenses/odbl/1-0/\">Open Database License (ODbL 1.0)</a>. VYLIA is an independent app, not affiliated with or endorsed by Exodus Privacy, Wikipedia or AppInsider.",
        )

    levers = "".join(f'<article class="lever"><span class="lever-n">{i}</span><h3>{h}</h3><p>{p}</p></article>'
                     for i, (h, p) in enumerate(L["levers"], 1))
    th = L["th"]
    oem_html = (f'<h2>{L["h_oem"]}</h2><p>{L["oem_intro"]}</p>{maker_table(oem, lang)}' if oem else "")
    good_alt = (f'<h3>{L["h_good_alt"]}</h3><div class="goods">{"".join(good_card(a, lang) for a in best_alt)}</div>'
                if best_alt else "")

    return path, f"""<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'self'; script-src 'self'; img-src 'self'; font-src 'self'; base-uri 'none'; form-action 'none'">
<meta name="referrer" content="no-referrer">
<title>{L["title"]} · VYLIA</title>
<meta name="description" content="{e(L["desc"])}">
<link rel="icon" href="{root}assets/logo.svg" type="image/svg+xml">
<link rel="alternate" hreflang="{"en" if fr_ else "fr"}" href="{other}">
<link rel="stylesheet" href="{root}style.css">
<link rel="stylesheet" href="{root}classement.css">
<script src="{root}classement.js" defer></script>
</head>
<body>
<main class="wide">
<header>
  <a class="logo" href="./"><img src="{root}assets/logo.svg" alt="" width="36" height="36"><span class="wordmark">VYLIA</span></a>
  <nav>{nav}</nav>
</header>

<section class="hero">
<p class="kicker">{L["kicker"]}</p>
<h1>{L["title"]}</h1>
<p class="lead">{L["lead"]}</p>
<div class="tiles">{tiles_html}</div>
<p class="note">{L["note"]}</p>
</section>

<h2>{L["h_findings"]}</h2>
<div class="dists card">{"".join(dists)}</div>
{analysis(lang, s, now, oem, alt, third_party)}

<h2>{L["h_good"]}</h2>
<p>{L["good_intro"]}</p>
<div class="goods">{"".join(good_card(a, lang) for a in best)}</div>
{good_alt}

<h2>{L["h_bad"]}</h2>
<p>{L["bad_intro"]}</p>
<div class="goods bads">{"".join(good_card(a, lang) for a in worst)}</div>

{oem_html}

<h2>{L["h_reduce"]}</h2>
<p>{L["reduce_intro"]}</p>
<div class="levers">{levers}</div>

<h2 id="ranking-table">{L["h_table"]}</h2>
<div class="controls card">
  <label class="search"><span class="visually-hidden">{L["search_label"]}</span>
    <input type="search" id="q" placeholder="{L["search"]}" autocomplete="off"></label>
  <label class="cat"><span class="visually-hidden">{L["cat_label"]}</span>
    <select id="cat"><option value="">{L["all_cats"]}</option>{cat_options}</select></label>
  <div class="chips" role="group" aria-label="{L["lists"]}">{section_chips}</div>
  <div class="chips" role="group" aria-label="{L["grades"]}">{grade_chips}</div>
  <p class="muted" id="count" aria-live="polite" data-tpl="{e(L["count_tpl"])}"></p>
</div>
<div class="table-wrap">
<table id="ranking">
<thead><tr>
  <th scope="col" data-sort="name" data-type="text">{th[0]}</th>
  <th scope="col" data-sort="score" aria-sort="descending">{th[1]}</th>
  <th scope="col" data-sort="score">{th[2]}</th>
  <th scope="col" data-sort="trackers" class="c-num">{th[3]}</th>
  <th scope="col" data-sort="perms" class="c-num c-perms">{th[4]}</th>
  <th scope="col" data-sort="cat" data-type="text" class="c-cat">{th[5]}</th>
  <th scope="col" data-sort="reach" class="c-reach">{th[6]}</th>
</tr></thead>
<tbody>{rows}</tbody>
</table>
</div>
<p class="muted">{L["hint"]}</p>

<h2>{L["h_cat"]}</h2>
<p class="muted">{L["cat_intro"]}</p>
{category_table(third_party, lang)}

<div class="two-cols">
<div><h2>{L["h_trk"]}</h2><p class="muted">{L["trk_intro"]}</p>{prevalence(third_party, "trackers", lang, 12)}</div>
<div><h2>{L["h_perm"]}</h2><p class="muted">{L["perm_intro"]}</p>{prevalence(third_party, "permissions", lang, 12)}</div>
</div>

<h2>{L["h_method"]}</h2>
<ul class="method">{"".join(f"<li>{m}</li>" for m in L["method"])}</ul>

<footer>{L["footer"]}</footer>
</main>
</body>
</html>
"""


def main(site, apps_file, directory_file, compare=None, trending=None, oem=None):
    directory = json.loads(Path(directory_file).read_text())
    year, rest = directory["generated"].split("-", 1)
    old_before = f"{int(year) - 1}-{rest}"
    apps, meta = load(apps_file, directory_file, "top", old_before)
    for pair, key in ((trending, "trending"), (oem, "oem"), (compare, "compare")):
        if pair:
            apps += load(*pair, key, old_before)[0]
    flags = {"trending": bool(trending), "oem": bool(oem), "compare": bool(compare)}
    out = Path(site)
    (out / "fr").mkdir(exist_ok=True)
    for lang in ("fr", "en"):
        path, page = render(lang, apps, meta, directory, flags)
        (out / path).write_text(page)
        print(f"{out / path} : {len(apps)} apps")
    # La CSP interdit les attributs style : largeurs et proportions passent par des classes générées.
    utilities = "".join(f".w{i}{{width:{i}%}}" for i in range(101)) + "".join(f".f{i}{{flex:{i}}}" for i in range(1, 301))
    (out / "classement.css").write_text((HERE / "site" / "classement.css").read_text() + "\n" + utilities + "\n")
    shutil.copyfile(HERE / "site" / "classement.js", out / "classement.js")


if __name__ == "__main__":
    args = sys.argv[1:]
    extra = {}
    while len(args) > 3 and args[-3] in ("--compare", "--trending", "--oem"):
        extra[args[-3][2:]] = args[-2:]
        args = args[:-3]
    if len(args) != 3:
        raise SystemExit(__doc__)
    main(*args, **extra)
