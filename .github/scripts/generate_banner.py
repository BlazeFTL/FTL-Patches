#!/usr/bin/env python3
"""
Builds .github/assets/banner-light.svg and banner-dark.svg.

Put your own Goku art (PNG/JPG/WebP) in .github/assets/goku/. One is picked per
day and shown whole, uncropped, in a rounded frame left of the title. With no
art there, a four-star dragon ball is drawn instead.

Needs: pip install fonttools pillow

python3 .github/scripts/generate_banner.py [assets-dir]
"""

import base64
import datetime
import io
import sys
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from PIL import Image

ASSETS = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "assets"
TITLE_FONT = TTFont(ASSETS / "fonts" / "Poppins-BoldItalic.ttf")
SUB_FONT = TTFont(ASSETS / "fonts" / "Poppins-Medium.ttf")

W, H = 1120, 360
PAD = 14
ART_H = H - 2 * PAD
THEMES = {
    "light": {"title": "#1f2328", "sub": "#59636e", "peach": ("#f27f4d", "#ffaa80"), "blue": "#1b8bbf"},
    "dark": {"title": "#f0f6fc", "sub": "#9198a1", "peach": ("#ffa77a", "#ffd2b4"), "blue": "#5fd0f0"},
}
NAME = "BlazeFTL\u2019s"
ACCENT = "patches"
SUBTITLE = "Android app patches for use with Morphe"
TAGLINE = "POWER LEVEL: OVER 9000"


def text_path(font, text, size, x, y):
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    scale = size / font["head"].unitsPerEm
    pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.1f}")
    cursor = 0
    for ch in text:
        glyph = glyphs[cmap[ord(ch)]]
        glyph.draw(TransformPen(pen, (scale, 0, 0, -scale, x + cursor * scale, y)))
        cursor += glyph.width
    return pen.getCommands(), cursor * scale


def text_width(font, text, size):
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    return sum(glyphs[cmap[ord(c)]].width for c in text) * size / font["head"].unitsPerEm


def star(cx, cy, r):
    import math

    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rad = r if i % 2 == 0 else r * 0.42
        pts.append(f"{cx + rad * math.cos(ang):.1f},{cy + rad * math.sin(ang):.1f}")
    return " ".join(pts)


def dragon_ball():
    cx, cy, r = 150, H // 2, 110
    stars = "".join(
        f'<polygon points="{star(cx + dx, cy + dy, 22)}" fill="#c1121f"/>'
        for dx, dy in ((-30, -21), (30, -21), (-30, 28), (30, 28))
    )
    return f"""<circle cx="{cx}" cy="{cy}" r="165" fill="url(#aura)"/>
<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#ball)"/>
<ellipse cx="{cx - 36}" cy="{cy - 52}" rx="40" ry="21" fill="#fff" opacity=".35" transform="rotate(-28 {cx - 36} {cy - 52})"/>
{stars}""", 2 * 150


def goku_art(path):
    img = Image.open(path).convert("RGBA")
    w = round(ART_H * img.width / img.height)
    if img.height > ART_H * 2:
        img = img.resize((w * 2, ART_H * 2), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, "WEBP", quality=88, method=6)
    uri = "data:image/webp;base64," + base64.b64encode(buf.getvalue()).decode()
    x, y, rx = PAD + 2, PAD, 28
    art = f"""<ellipse cx="{x + w / 2}" cy="{H / 2}" rx="{w / 2 + 34}" ry="{ART_H / 2 + 30}" fill="url(#aura)"/>
<clipPath id="frame"><rect x="{x}" y="{y}" width="{w}" height="{ART_H}" rx="{rx}"/></clipPath>
<image href="{uri}" x="{x}" y="{y}" width="{w}" height="{ART_H}" preserveAspectRatio="xMidYMid slice" clip-path="url(#frame)"/>
<rect x="{x}" y="{y}" width="{w}" height="{ART_H}" rx="{rx}" fill="none" stroke="url(#bar)" stroke-width="4"/>"""
    return art, x + w + 6


def build(mode, art, art_right):
    colors = THEMES[mode]
    text_x = art_right + 40
    max_title_w = W - text_x - 24
    full = NAME + " " + ACCENT
    size = 84
    if text_width(TITLE_FONT, full, size) > max_title_w:
        size = size * max_title_w / text_width(TITLE_FONT, full, size)
    base_y = H // 2 - 28
    name_d, name_w = text_path(TITLE_FONT, NAME + " ", size, text_x, base_y)
    acc_d, acc_w = text_path(TITLE_FONT, ACCENT, size, text_x + name_w, base_y)
    sub_d, _ = text_path(SUB_FONT, SUBTITLE, 29, text_x + 4, base_y + 72)
    tag_d, _ = text_path(TITLE_FONT, TAGLINE, 21, text_x + 4, base_y + 112)
    bar_w = name_w + acc_w
    p1, p2 = colors["peach"]
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" role="img" aria-label="BlazeFTL&#8217;s patches">
<defs>
<linearGradient id="accent" x1="0" x2="1"><stop offset="0" stop-color="{p1}"/><stop offset="1" stop-color="{p2}"/></linearGradient>
<linearGradient id="bar" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{p1}"/><stop offset=".55" stop-color="{p2}"/><stop offset="1" stop-color="#2aa7e0"/></linearGradient>
<radialGradient id="ball" cx=".38" cy=".32" r=".8"><stop offset="0" stop-color="#ffd8bd"/><stop offset=".55" stop-color="#ffa070"/><stop offset="1" stop-color="#e8683a"/></radialGradient>
<radialGradient id="aura"><stop offset="0" stop-color="#ffb38a" stop-opacity=".5"/><stop offset=".62" stop-color="#ff9e6d" stop-opacity=".15"/><stop offset="1" stop-color="#2aa7e0" stop-opacity="0"/></radialGradient>
</defs>
{art}
<path d="{name_d}" fill="{colors['title']}"/>
<path d="{acc_d}" fill="url(#accent)"/>
<path d="{sub_d}" fill="{colors['sub']}"/>
<path d="{tag_d}" fill="{colors['blue']}"/>
<polygon points="{text_x + 6},{base_y + 28} {text_x + bar_w + 6},{base_y + 28} {text_x + bar_w - 8},{base_y + 35} {text_x - 8},{base_y + 35}" fill="url(#bar)" opacity=".95"/>
</svg>
"""


def main():
    pics = sorted(p for pat in ("*.png", "*.jpg", "*.jpeg", "*.webp") for p in (ASSETS / "goku").glob(pat))
    art, art_right = goku_art(pics[datetime.date.today().toordinal() % len(pics)]) if pics else dragon_ball()
    for mode in THEMES:
        (ASSETS / f"banner-{mode}.svg").write_text(build(mode, art, art_right), encoding="utf-8")
    print(f"banner built ({len(pics)} Goku pictures)")


if __name__ == "__main__":
    main()
