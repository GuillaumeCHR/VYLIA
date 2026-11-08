#!/usr/bin/env python3
"""
Visuels de la fiche Google Play, dessinés depuis la même géométrie que l'icône de l'app
(app/src/main/res/drawable/ic_launcher_foreground.xml, viewport 108) :

- docs/store/icon-512.png : icône 512 x 512, carrée et pleine (Google Play applique lui-même l'arrondi) ;
- docs/store/screenshots/<langue>/feature-graphic.png : image de présentation 1024 x 500.

    python3 -I tools/store/make_store_graphics.py

Logotype en Unbounded ExtraBold (app/src/main/res/font, OFL), textes en Inter si la police est installée.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/store"
UNBOUNDED = ROOT / "app/src/main/res/font/unbounded.ttf"
INTER = [Path("/usr/share/fonts/truetype/inter-zorin-os/Inter-SemiBold.ttf"),
         Path("/usr/share/fonts/truetype/inter/Inter-SemiBold.ttf")]
INTER_REGULAR = [Path("/usr/share/fonts/truetype/inter-zorin-os/Inter-Regular.ttf"),
                 Path("/usr/share/fonts/truetype/inter/Inter-Regular.ttf")]

SLATE900 = (0x0F, 0x17, 0x2A)
SLATE800 = (0x1E, 0x29, 0x3B)
SLATE300 = (0xCB, 0xD5, 0xE1)
SLATE400 = (0x94, 0xA3, 0xB8)
CYAN = (0x38, 0xBD, 0xF8)
EMERALD = (0x10, 0xB9, 0x81)
SS = 4  # suréchantillonnage, pour des bords lisses

TEXTS = {
    "en": ("What do your apps", "know about you?", "Privacy score  ·  Trackers & spyware  ·  Anti-tracker VPN"),
    "fr": ("Que savent vos apps", "de vous ?", "Score de vie privée  ·  Traqueurs et espions  ·  VPN anti-traqueurs"),
}


def font(candidates, size):
    for path in candidates:
        if path.is_file():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default(size)


def draw_mark(draw: ImageDraw.ImageDraw, ox: float, oy: float, scale: float):
    """Loupe et coche de l'icône, dans le repère 108 x 108 placé en (ox, oy) à l'échelle donnée."""
    def p(x, y):
        return (ox + x * scale, oy + y * scale)

    def line(points, width, color):
        pts = [p(*pt) for pt in points]
        w = round(width * scale)
        draw.line(pts, fill=color, width=w, joint="curve")
        for x, y in pts:  # extrémités arrondies
            draw.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=color)

    r, w = 17 * scale, 6 * scale
    cx, cy = p(49, 49)
    line([(61.5, 61.5), (74, 74)], 8, CYAN)  # le manche d'abord : le verre recouvre son extrémité
    draw.ellipse((cx - r - w / 2, cy - r - w / 2, cx + r + w / 2, cy + r + w / 2), outline=CYAN, width=round(w))
    line([(41, 49.5), (46.5, 55), (57.5, 43.5)], 5.5, EMERALD)


def icon():
    size = 512 * SS
    img = Image.new("RGB", (size, size), SLATE900)
    # Le dessin occupe ~45 % de l'icône adaptative (zone sûre) ; sur le Play Store, on l'agrandit autour de son centre.
    zoom = 1.3
    scale = size / 108 * zoom
    draw_mark(ImageDraw.Draw(img), size / 2 - 53.5 * scale, size / 2 - 53.5 * scale, scale)
    img.resize((512, 512), Image.LANCZOS).save(OUT / "icon-512.png", optimize=True)


def feature_graphic(lang: str):
    w, h = 1024 * SS, 500 * SS
    img = Image.new("RGB", (w, h), SLATE900)
    draw = ImageDraw.Draw(img)
    # Pavé de l'icône à gauche, arrondi comme sur le site.
    box, x0, y0 = 300 * SS, 70 * SS, 100 * SS
    draw.rounded_rectangle((x0, y0, x0 + box, y0 + box), radius=66 * SS, fill=SLATE800)
    draw_mark(draw, x0, y0, box / 108)

    logo = ImageFont.truetype(str(UNBOUNDED), 76 * SS)
    try:
        logo.set_variation_by_axes([800])
    except OSError:
        pass
    tx = 430 * SS
    draw.text((tx, 92 * SS), "VYLIA", font=logo, fill=SLATE300)
    line1, line2, sub = TEXTS[lang]
    head = font(INTER, 44 * SS)
    draw.text((tx, 212 * SS), line1, font=head, fill=CYAN)
    draw.text((tx, 266 * SS), line2, font=head, fill=CYAN)
    size = 21
    small = font(INTER_REGULAR, size * SS)
    while draw.textlength(sub, font=small) > (1024 - 430 - 40) * SS and size > 12:  # tenir dans la largeur
        size -= 1
        small = font(INTER_REGULAR, size * SS)
    draw.text((tx, 360 * SS), sub, font=small, fill=SLATE400)

    out = OUT / "screenshots" / lang
    out.mkdir(parents=True, exist_ok=True)
    img.resize((1024, 500), Image.LANCZOS).save(out / "feature-graphic.png", optimize=True)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    icon()
    for lang in TEXTS:
        feature_graphic(lang)
    print("écrit", OUT / "icon-512.png", "et docs/store/screenshots/{en,fr}/feature-graphic.png")
