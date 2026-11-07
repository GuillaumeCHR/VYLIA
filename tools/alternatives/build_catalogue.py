#!/usr/bin/env python3
"""Construit app/src/main/assets/alternatives.json depuis le classement AlternativeTo (filtre F-Droid).

Usage : python3 tools/alternatives/build_catalogue.py ~/alternativeto-fdroid/classement_alternativeto_fdroid.xlsx
Dépendance : openpyxl.

Règles (affichées aussi dans l'app, onglet Alternatives › « Comment cette liste est construite ») :
  1. Source : les apps du filtre F-Droid d'AlternativeTo dont les plateformes citent Android (classeur, onglet
     « Toutes les apps »), regroupées selon les catégories du Play Store (PLAY ci-dessous).
  2. Tri : les apps gratuites (« Free ») d'abord, quelle que soit leur licence, puis par nombre de likes.
  3. Diversité : une seule app par type AlternativeTo dans une catégorie, tant qu'il reste d'autres types.
  4. Exclusions, avec leur raison (EXCLUSIONS) : root, serveur à héberger soi-même, outil de développeur, pas d'app
     Android, plus distribuée.
  5. Disponibilité : identifiant vérifié sur le Play Store ou sur F-Droid (PACKAGES) ; le lien ouvert est celui du
     Play Store quand la fiche existe, sinon F-Droid.
  6. Trois apps par catégorie au plus (deux quand il n'y en a pas plus).
  7. Remplacements choisis à la main (REPLACEMENTS), avec leur raison : une app que le classement F-Droid ne peut pas
     proposer (absente de F-Droid) prend la place d'une autre.
  8. Note affichée : les étoiles de la fiche Google Play (tools/alternatives/play_ratings.json, relevé par
     fetch_play_ratings.py) ; rien si l'app n'est pas sur Google Play ou sans note. Les likes AlternativeTo servent
     au classement seulement.
"""
import json
import sys
from datetime import date
from pathlib import Path

from openpyxl import load_workbook

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/assets/alternatives.json"
PER_CATEGORY = 3

# Catégories du Play Store → types AlternativeTo (colonne « categorie_source »).
PLAY = [
    ("communication", "Communication", "Communication",
     ["Web Browser", "Email Client", "WebMail Provider", "Instant Messenger", "Encrypted Chat App", "Group Chat App", "IRC Client",
      "Video Calling App", "Phone Dialer", "Unwanted Call Blocker", "Anti-Spam Tool"]),
    ("social", "Social", "Réseaux sociaux",
     ["Social Network", "Mastodon Client", "Lemmy Client", "Pixelfed Client", "Bluesky Client", "Twitter Client", "Reddit Client",
      "Hacker News Client"]),
    ("music", "Music & Audio", "Musique et audio",
     ["Audio Player", "Podcast Player", "Audiobook Reader", "Music Streaming Service", "Internet Radio Service", "Audio Recorder",
      "Music Discovery Service", "Lyrics Displayer", "Music Recognition App"]),
    ("video", "Video Players & Editors", "Lecteurs et éditeurs vidéo",
     ["Media Player", "YouTube Client", "Video Streaming App", "Video Downloader", "YouTube Downloader", "IPTV App", "Video Editor"]),
    ("photo", "Photography", "Photographie",
     ["Photo Manager", "Camera App", "Image Editor", "Digital Painting Tool", "Image Optimizer"]),
    ("productivity", "Productivity", "Productivité",
     ["Note-taking Tool", "Sticky Notes App", "Todo List Manager", "Task Management Tool", "Office Suite", "Word Processor",
      "Spreadsheet App", "PDF Editor", "Text Editor", "Calendar App", "Contact Manager", "Document Scanner",
      "Large File Transfer Service", "File Sync Tool", "Cloud Storage Service", "Pomodoro Timer", "Time Tracking Tool"]),
    ("tools", "Tools", "Outils",
     ["App Store", "Mobile Keyboard", "File Manager", "Calculator", "Internet Speed Test App", "Software Uninstaller",
      "Battery Saver Utility", "Software Updater", "Alarm Clock", "Barcode Scanner", "QR Code Reader", "Android Root Manager"]),
    ("security", "Privacy & Security", "Vie privée et sécurité",
     ["Ad Blocker", "DNS Resolver", "Authenticator", "Anonymizing Network", "Privacy Tool", "Password Manager", "VPN Service",
      "VPN Client", "File Encryption Software", "URL Cleaner", "Firewall"]),
    ("maps", "Maps & Navigation", "Cartes et navigation", ["GPS Navigation Service", "Map Service", "Travel Planner"]),
    ("weather", "Weather", "Météo", ["Weather Forecast Tool"]),
    ("finance", "Finance", "Finances",
     ["Budget Manager", "Personal Finance Tool", "Crypto Wallet", "Crypto Portfolio Tracker"]),
    ("health", "Health & Fitness", "Santé et remise en forme",
     ["Period Tracker", "Habit Tracker", "Calorie Tracker", "Nutrition Tracker", "Fitness Tracker", "Run Tracker", "Meditation Tool",
      "Pill Reminder"]),
    ("books", "Books & Reference", "Livres et références",
     ["Encyclopedia", "PDF Reader", "Ebook Reader", "Flashcard Learning Tool", "Dictionary", "Translator", "Comic and Manga Reader"]),
    ("news", "News & Magazines", "Actualités et magazines", ["RSS Reader", "Read It Later Tool"]),
    ("personalization", "Personalization", "Personnalisation", ["Application Launcher", "Wallpapers Hub"]),
    ("ai", "AI", "Intelligence artificielle",
     ["AI Chatbot", "Large Language Model (LLM) Tool", "AI Image Generator", "Virtual Assistant", "Audio Transcription Tool"]),
]

# Apps écartées, avec la raison (montrée dans le récapitulatif du script).
EXCLUSIONS = {
    "Nextcloud": "serveur à héberger soi-même",
    "Ollama App": "serveur Ollama à héberger soi-même",
    "Immich": "serveur à héberger soi-même",
    "selfoss": "serveur à héberger soi-même",
    "Magisk": "demande le root",
    "GNU Emacs": "outil de développeur pensé pour l'ordinateur",
    "MusicBrainz": "base de données, pas d'app Android",
    "Quad9": "service DNS, pas d'app (réglable dans le DNS privé de VYLIA)",
    "ToS;DR (Terms of Service; Didn't Read)": "extension de navigateur, pas d'app Android",
    "DNS66": "plus distribuée (retirée de F-Droid)",
    "AdAway": "bloqueur par VPN local : fait doublon avec le Bouclier VYLIA (un seul VPN à la fois)",
}

# Remplacements validés le 2026-11-07 : Bluesky, réseau social ouvert (protocole AT, app sous licence MIT) plus
# répandu, à la place de Tusky, simple client Mastodon qui fait doublon avec l'app Mastodon. Absent de F-Droid.
REPLACEMENTS = {
    "Tusky": {
        "name": "Bluesky", "package": "xyz.blueskyweb.app", "play": "xyz.blueskyweb.app", "open_source": True, "cost": "Free",
        "pitch": {"en": "Open social network (AT Protocol), chronological or custom feeds, no ads.",
                  "fr": "Réseau social ouvert (protocole AT), fil chronologique ou personnalisé, sans publicité."},
    },
}
RATINGS = Path(__file__).resolve().parent / "play_ratings.json"

# Identifiants vérifiés (2026-11-01) : play = fiche Play Store, fdroid = fiche F-Droid ; None si absente.
PACKAGES = {
    "Mozilla Firefox": ("org.mozilla.firefox", None),
    "Thunderbird": ("net.thunderbird.android", "net.thunderbird.android"),
    "Signal": ("org.thoughtcrime.securesms", None),
    "Mastodon": ("org.joinmastodon.android", "org.joinmastodon.android"),
    "Tusky": ("com.keylesspalace.tusky", "com.keylesspalace.tusky"),
    "RedReader": ("org.quantumbadger.redreader", "org.quantumbadger.redreader"),
    "AntennaPod": ("de.danoeh.antennapod", "de.danoeh.antennapod"),
    "Spotube": (None, "oss.krtirtho.spotube"),
    "Audile": (None, "com.mrsep.musicrecognizer"),
    "Vanilla Music": (None, "ch.blinkenlights.android.vanilla"),
    "VLC Media Player": ("org.videolan.vlc", "org.videolan.vlc"),
    "NewPipe": (None, "org.schabi.newpipe"),
    "PeerTube": ("org.framasoft.peertube", "org.framasoft.peertube"),
    "Open Camera": ("net.sourceforge.opencamera", "net.sourceforge.opencamera"),
    "Fossify Gallery": ("org.fossify.gallery", "org.fossify.gallery"),
    "Image Toolbox": ("ru.tech.imageresizershrinker", "ru.tech.imageresizershrinker"),
    "LocalSend": ("org.localsend.localsend_app", "org.localsend.localsend_app"),
    "Super Productivity": ("com.superproductivity.superproductivity", "com.superproductivity.superproductivity"),
    "Logseq": (None, "com.logseq.app"),
    "F-Droid": (None, "org.fdroid.fdroid"),
    "HeliBoard": (None, "helium314.keyboard"),
    "Amaze File Manager": ("com.amaze.filemanager", "com.amaze.filemanager"),
    "Aegis Authenticator": ("com.beemdevelopment.aegis", "com.beemdevelopment.aegis"),
    "I2P": ("net.i2p.android", None),
    "KeePassDX": ("com.kunzisoft.keepass.free", "com.kunzisoft.keepass.libre"),
    "Organic Maps": ("app.organicmaps", "app.organicmaps"),
    "KDE Itinerary": ("org.kde.itinerary", None),
    "StreetComplete": ("de.westnordost.streetcomplete", "de.westnordost.streetcomplete"),
    "CoMaps": ("app.comaps.google", "app.comaps.fdroid"),
    "Breezy Weather": (None, "org.breezyweather"),
    "Bura": (None, "com.davidtakac.bura"),
    "MoneyManager Ex": (None, "com.money.manager.ex"),
    "Unstoppable Wallet": ("io.horizontalsystems.bankwallet", "io.horizontalsystems.bankwallet"),
    "Recurring Expense Tracker": ("de.dbauer.expensetracker", "de.dbauer.expensetracker"),
    "log28": (None, "com.log28"),
    "Loop Habit Tracker": ("org.isoron.uhabits", "org.isoron.uhabits"),
    "Open Food Facts": ("org.openfoodfacts.scanner", None),
    "Wikipedia": ("org.wikipedia", "org.wikipedia"),
    "MuPDF": ("com.artifex.mupdf.viewer.app", "com.artifex.mupdf.viewer.app"),
    "AnkiDroid": ("com.ichi2.anki", "com.ichi2.anki"),
    "Feeder": ("com.nononsenseapps.feeder.play", "com.nononsenseapps.feeder"),
    "Read You": (None, "me.ash.reader"),
    "FeedFlow": ("com.prof18.feedflow", "com.prof18.feedflow"),
    "Lawnchair": ("app.lawnchair.play", None),
    "Paperize": (None, "com.anthonyla.paperize"),
    "Kvæsitso": (None, "de.mm20.launcher2.release"),
    "FUTO Voice Input": ("org.futo.voiceinput", None),
    "Dicio": (None, "org.stypox.dicio"),
    "Maid (AI)": ("com.danemadsen.maid", "com.danemadsen.maid"),
}

# Présentation courte, rédigée pour VYLIA (aucun texte d'AlternativeTo n'est repris).
PITCH = {
    "Mozilla Firefox": ("Independent browser that blocks many trackers by default and accepts add-ons.", "Navigateur indépendant qui bloque de nombreux traqueurs par défaut et accepte les extensions."),
    "Thunderbird": ("Email app from Mozilla, for all your accounts, without ads.", "Messagerie e-mail de Mozilla, pour tous vos comptes, sans publicité."),
    "Signal": ("End-to-end encrypted messages and calls, run by a non-profit.", "Messages et appels chiffrés de bout en bout, par une fondation à but non lucratif."),
    "Mastodon": ("Official app of the decentralised social network, chronological feed, no ads.", "App officielle du réseau social décentralisé, fil chronologique, sans publicité."),
    "Tusky": ("Light, fast Mastodon client.", "Client Mastodon léger et rapide."),
    "RedReader": ("Reddit reader without ads or tracking.", "Lecteur Reddit sans publicité ni pistage."),
    "AntennaPod": ("Podcast player: subscriptions, downloads, no account.", "Lecteur de podcasts : abonnements, téléchargements, sans compte."),
    "Spotube": ("Music streaming client without a paid account or ads.", "Client de musique en streaming, sans compte payant ni publicité."),
    "Audile": ("Recognises the song playing around you.", "Reconnaît la chanson qui passe autour de vous."),
    "VLC Media Player": ("Plays almost any video or audio file, no ads.", "Lit presque tous les fichiers vidéo et audio, sans publicité."),
    "NewPipe": ("Watch YouTube without a Google account or ads, with downloads.", "YouTube sans compte Google ni publicité, avec téléchargement."),
    "PeerTube": ("Decentralised video platform, run by independent hosts.", "Plateforme vidéo décentralisée, portée par des hébergeurs indépendants."),
    "Open Camera": ("Full-featured camera, no ads, no data collection.", "Appareil photo complet, sans publicité ni collecte de données."),
    "Fossify Gallery": ("Photo gallery that stays offline.", "Galerie photo qui reste hors ligne."),
    "Image Toolbox": ("Resize, crop, convert and edit images on the phone.", "Redimensionner, recadrer, convertir et retoucher des images sur le téléphone."),
    "LocalSend": ("Send files to nearby devices over Wi-Fi, without the cloud.", "Envoyer des fichiers aux appareils proches par le Wi-Fi, sans cloud."),
    "Super Productivity": ("Tasks, timer and focus sessions, without an account.", "Tâches, minuteur et sessions de concentration, sans compte."),
    "Logseq": ("Notes and outlines kept on your device, in plain files.", "Notes et plans gardés sur votre appareil, en fichiers simples."),
    "F-Droid": ("Catalogue of free apps, checked and built from their source code.", "Catalogue d'apps libres, vérifiées et compilées depuis leur code source."),
    "HeliBoard": ("Keyboard with no Internet access, so nothing you type leaves the phone.", "Clavier sans accès à Internet : rien de ce que vous tapez ne quitte le téléphone."),
    "Amaze File Manager": ("Simple file manager, no ads.", "Gestionnaire de fichiers simple, sans publicité."),
    "Aegis Authenticator": ("Two-factor codes stored encrypted on the phone.", "Codes de double authentification stockés chiffrés sur le téléphone."),
    "I2P": ("Anonymous network that hides who talks to whom.", "Réseau anonyme qui masque qui communique avec qui."),
    "KeePassDX": ("Password manager with an encrypted file you keep yourself.", "Gestionnaire de mots de passe dans un fichier chiffré que vous gardez vous-même."),
    "Organic Maps": ("Offline maps and navigation, from OpenStreetMap, no tracking.", "Cartes et navigation hors ligne, issues d'OpenStreetMap, sans pistage."),
    "KDE Itinerary": ("Travel assistant: tickets and bookings in one place, kept on the phone.", "Assistant de voyage : billets et réservations réunis, gardés sur le téléphone."),
    "StreetComplete": ("Improve OpenStreetMap with simple questions about your surroundings.", "Améliorer OpenStreetMap par de petites questions sur votre quartier."),
    "CoMaps": ("Community fork of Organic Maps: offline maps and navigation.", "Version communautaire d'Organic Maps : cartes et navigation hors ligne."),
    "Breezy Weather": ("Detailed forecasts from several sources, no ads.", "Prévisions détaillées de plusieurs sources, sans publicité."),
    "Bura": ("Minimal, ad-free weather app.", "Météo épurée, sans publicité."),
    "MoneyManager Ex": ("Budget and accounts kept on the phone.", "Budget et comptes gardés sur le téléphone."),
    "Unstoppable Wallet": ("Crypto wallet where you alone hold the keys.", "Portefeuille crypto dont vous seul détenez les clés."),
    "Recurring Expense Tracker": ("Keep track of your subscriptions and recurring costs.", "Suivre vos abonnements et dépenses récurrentes."),
    "log28": ("Period and symptom tracking that stays on the phone.", "Suivi du cycle et des symptômes qui reste sur le téléphone."),
    "Loop Habit Tracker": ("Build habits with simple charts, no account.", "Prendre de bonnes habitudes avec des graphiques simples, sans compte."),
    "Open Food Facts": ("Scan food to see its ingredients and Nutri-Score, from an open database.", "Scanner un aliment pour voir sa composition et son Nutri-Score, depuis une base ouverte."),
    "Wikipedia": ("The official encyclopedia app, with offline reading lists.", "L'app officielle de l'encyclopédie, avec listes de lecture hors ligne."),
    "MuPDF": ("Fast PDF and ebook reader.", "Lecteur PDF et livres numériques rapide."),
    "AnkiDroid": ("Flashcards with spaced repetition to memorise anything.", "Cartes mémoire à répétition espacée pour tout retenir."),
    "Feeder": ("Follow sites by RSS, without an account or tracking.", "Suivre des sites par RSS, sans compte ni pistage."),
    "Read You": ("Elegant RSS reader.", "Lecteur RSS élégant."),
    "FeedFlow": ("RSS reader that syncs without a dedicated account.", "Lecteur RSS synchronisable sans compte dédié."),
    "Lawnchair": ("Customisable home screen close to the Pixel one.", "Écran d'accueil personnalisable, proche de celui des Pixel."),
    "Paperize": ("Rotates your wallpapers automatically.", "Change vos fonds d'écran automatiquement."),
    "FUTO Voice Input": ("Voice typing recognised on the phone itself, nothing sent online.", "Dictée vocale reconnue sur le téléphone même, rien n'est envoyé en ligne."),
    "Dicio": ("Voice assistant that works offline.", "Assistant vocal qui fonctionne hors ligne."),
    "Maid (AI)": ("Chat with AI models that can run on the phone itself.", "Discuter avec des modèles d'IA qui peuvent tourner sur le téléphone même."),
    "Kvæsitso": ("Search-centred launcher, everything one swipe away.", "Lanceur centré sur la recherche, tout à portée d'un geste."),
}

# Apps courantes que chaque catégorie peut remplacer (pour le trophée et les conseils).
REPLACES = {
    "communication": ["com.whatsapp", "com.facebook.orca", "com.viber.voip", "org.telegram.messenger", "com.snapchat.android",
                      "com.android.chrome", "com.microsoft.emmx", "com.opera.browser", "com.sec.android.app.sbrowser",
                      "com.google.android.gm", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail"],
    "social": ["com.facebook.katana", "com.instagram.android", "com.twitter.android", "com.instagram.barcelona", "com.zhiliaoapp.musically"],
    "music": ["com.spotify.music", "com.soundcloud.android", "com.deezer.android.app", "com.shazam.android"],
    "video": ["com.google.android.youtube"],
    "photo": ["com.google.android.apps.photos"],
    "productivity": ["com.google.android.keep", "com.evernote", "com.microsoft.office.onenote", "com.google.android.apps.docs",
                     "com.microsoft.skydrive", "com.dropbox.android"],
    "tools": ["com.google.android.inputmethod.latin", "com.touchtype.swiftkey", "com.samsung.android.honeyboard"],
    "security": ["com.google.android.apps.authenticator2", "com.azure.authenticator", "com.authy.authy", "com.lastpass.lpandroid"],
    "maps": ["com.google.android.apps.maps", "com.waze"],
    "weather": ["com.lachainemeteo.androidapp", "com.weather.Weather", "com.accuweather.android"],
}
CATEGORIES = {
    "communication": ["MESSAGING", "EMAIL", "BROWSER"], "social": ["SOCIAL"], "music": ["MEDIA_PLAYER"], "photo": ["PHOTO_VIDEO"],
    "productivity": ["PRODUCTIVITY_CLOUD"], "tools": ["KEYBOARD"], "security": ["SECURITY_AUTH"], "maps": ["NAVIGATION"],
    "weather": ["WEATHER"], "finance": ["FINANCE_ACCOUNTS"], "health": ["FITNESS", "HEALTH_TRACKING"], "news": ["NEWS_READING"],
}


def main(xlsx: str):
    rows = list(load_workbook(xlsx, read_only=True)["Toutes les apps"].iter_rows(values_only=True))
    apps = [dict(zip(rows[0], r)) for r in rows[1:] if r[0]]
    groups, report = [], []
    ratings = json.loads(RATINGS.read_text(encoding="utf-8")) if RATINGS.exists() else {}
    for gid, en, fr, types in PLAY:
        candidates = [a for a in apps if a["android"] == "oui" and a["categorie_source"] in types]
        # Gratuites d'abord, quelle que soit la licence, puis par likes (à égalité, ordre alphabétique).
        candidates.sort(key=lambda a: (0 if a["cout"] == "Free" else 1, -(a["likes"] or 0), a["nom"].lower()))
        chosen, used_types, reported = [], set(), set()
        for diverse in (True, False):
            for a in candidates:
                if len(chosen) == PER_CATEGORY:
                    break
                if a in chosen or (diverse and a["categorie_source"] in used_types):
                    continue
                reason = EXCLUSIONS.get(a["nom"]) or (None if a["nom"] in PACKAGES else "identifiant Android non vérifié")
                if reason:
                    if a["nom"] not in reported:
                        report.append(f"  {fr} : {a['nom']} écartée ({reason})")
                        reported.add(a["nom"])
                    continue
                chosen.append(a)
                used_types.add(a["categorie_source"])
        alternatives = []
        for a in chosen:
            if a["nom"] in REPLACEMENTS:
                entry = dict(REPLACEMENTS[a["nom"]])
                report.append(f"  {fr} : {a['nom']} remplacée par {entry['name']}")
                rated(entry, ratings)
                alternatives.append(entry)
                continue
            play, fdroid = PACKAGES[a["nom"]]
            entry = {"name": a["nom"], "package": play or fdroid}
            if fdroid:
                entry["fdroid"] = fdroid
            if play:
                entry["play"] = play
            entry["open_source"] = "Open Source" in (a["licence"] or "")
            entry["cost"] = a["cout"]
            pitch_en, pitch_fr = PITCH[a["nom"]]
            entry["pitch"] = {"en": pitch_en, "fr": pitch_fr}
            rated(entry, ratings)
            alternatives.append(entry)
        group = {"id": gid, "title": {"en": en, "fr": fr}}
        if gid in REPLACES:
            group["replaces"] = REPLACES[gid]
        if gid in CATEGORIES:
            group["categories"] = CATEGORIES[gid]
        group["alternatives"] = alternatives
        groups.append(group)
        report.append(f"{fr} : {', '.join(a['nom'] for a in chosen)}")
    catalogue = {
        "version": "2.0",
        "last_update": date.today().isoformat(),
        "description": "Alternatives par catégorie du Play Store, choisies par tools/alternatives/build_catalogue.py depuis le "
                       "classement AlternativeTo (filtre F-Droid). fdroid / play : identifiant sur le magasin, absent s'il n'y est pas.",
        "groups": groups,
    }
    write(catalogue)
    print("\n".join(report))


def rated(entry: dict, ratings: dict):
    """Étoiles Google Play, placées avant la présentation ; rien sans fiche Play ou sans note."""
    pitch = entry.pop("pitch")
    value = ratings.get(entry.get("play") or "")
    if value is not None:
        entry["rating"] = value
    entry["pitch"] = pitch


def write(catalogue: dict):
    """Même mise en forme compacte que le reste des assets : une alternative par ligne, sa présentation en dessous."""
    J = lambda o: json.dumps(o, ensure_ascii=False)
    out = ["{"] + [f'  "{k}": {J(catalogue[k])},' for k in ("version", "last_update", "description")] + ['  "groups": [']
    blocks = []
    for g in catalogue["groups"]:
        lines = ["    {", f'      "id": {J(g["id"])},', f'      "title": {J(g["title"])},']
        for key in ("replaces", "categories"):
            if key in g:
                lines.append(f'      "{key}": {J(g[key])},')
        lines.append('      "alternatives": [')
        lines.append(",\n".join(
            "        " + J({k: v for k, v in a.items() if k != "pitch"})[:-1] + ',\n         "pitch": ' + J(a["pitch"]) + "}"
            for a in g["alternatives"]))
        lines += ["      ]", "    }"]
        blocks.append("\n".join(lines))
    out += [",\n".join(blocks), "  ]", "}"]
    OUT.write_text("\n".join(out) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else str(Path.home() / "alternativeto-fdroid/classement_alternativeto_fdroid.xlsx"))
