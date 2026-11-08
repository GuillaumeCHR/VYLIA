#!/usr/bin/env python3
"""
Met en page les captures de la fiche Google Play : une accroche en deux lignes en haut, la capture du téléphone en
dessous, au format 1080 x 1920 (9:16, accepté par Google : côtés entre 320 et 3 840 px, le plus grand au plus deux
fois le plus petit ; une capture brute de téléphone récent, plus allongée, est refusée).

    python3 -I tools/store/compose_screenshots.py DOSSIER_DES_CAPTURES

DOSSIER_DES_CAPTURES contient en/ et fr/, avec les captures brutes nommées comme dans SHOTS (01-help.png…), prises
avec `adb exec-out screencap -p` (mode démo de la barre d'état : 10:00, batterie pleine, sans notifications). Elles
montrent les apps d'un vrai téléphone : elles restent hors du dépôt, seules les images composées y sont.

Les images sont écrites dans docs/store/screenshots/<langue>/, numérotées dans l'ordre de la fiche, à côté de
l'image de présentation (tools/store/make_store_graphics.py).
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/store/screenshots"
INTER = [Path("/usr/share/fonts/truetype/inter-zorin-os/Inter-SemiBold.ttf"),
         Path("/usr/share/fonts/truetype/inter/Inter-SemiBold.ttf")]

W, H = 1080, 1920
SLATE900 = (0x0F, 0x17, 0x2A)
SLATE300 = (0xCB, 0xD5, 0xE1)
CYAN = (0x38, 0xBD, 0xF8)
MARGIN_TOP = 96
LINE_GAP = 12
CAPTION_GAP = 56
SHOT_BOTTOM = 72
RADIUS = 40

# Dans l'ordre de la fiche : la pédagogie d'abord.
SHOTS = ["01-help", "02-scan", "03-detail", "04-permission", "05-trackers", "06-manage", "07-filter", "08-celebrate"]
CAPTIONS = {
    "en": [
        ("Understand your apps,", "in simple words."),
        ("A privacy score", "for every app."),
        ("Every point", "is explained."),
        ("What each permission", "really allows."),
        ("What trackers do", "with your data."),
        ("Revoke what your apps", "don't need."),
        ("Anti-tracker VPN,", "right on your phone."),
        ("Track your progress,", "earn badges."),
    ],
    "fr": [
        ("Comprenez vos apps,", "avec des mots simples."),
        ("Un score de vie privée", "pour chaque app."),
        ("Chaque point", "est expliqué."),
        ("Ce que chaque permission", "permet vraiment."),
        ("Ce que les traqueurs", "font de vos données."),
        ("Retirez ce dont vos apps", "n'ont pas besoin."),
        ("VPN anti-traqueurs,", "sur votre téléphone."),
        ("Suivez vos progrès,", "gagnez des badges."),
    ],
}


def font(size):
    for path in INTER:
        if path.is_file():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default(size)


def compose(raw: Path, caption: tuple[str, str], out: Path):
    img = Image.new("RGB", (W, H), SLATE900)
    draw = ImageDraw.Draw(img)
    size = 66
    f = font(size)
    while max(draw.textlength(line, font=f) for line in caption) > W - 2 * 64 and size > 40:
        size -= 2
        f = font(size)
    y = MARGIN_TOP
    for line, color in zip(caption, (CYAN, SLATE300)):
        draw.text((W / 2, y), line, font=f, fill=color, anchor="ma")
        y += size + LINE_GAP
    top = y + CAPTION_GAP

    shot = Image.open(raw).convert("RGB")
    h = H - top - SHOT_BOTTOM
    w = round(shot.width * h / shot.height)
    shot = shot.resize((w, h), Image.LANCZOS)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius=RADIUS, fill=255)
    img.paste(shot, ((W - w) // 2, top), mask)
    img.save(out, optimize=True)


def main(src: Path):
    for lang, captions in CAPTIONS.items():
        dest = OUT / lang
        dest.mkdir(parents=True, exist_ok=True)
        for name, caption in zip(SHOTS, captions):
            compose(src / lang / f"{name}.png", caption, dest / f"{name}.png")
        print("écrit", dest)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(Path(sys.argv[1]))
