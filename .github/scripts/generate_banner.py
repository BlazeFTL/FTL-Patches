#!/usr/bin/env python3
"""
Builds .github/assets/banner-light.svg and banner-dark.svg.

Put your own Goku art (PNG/JPG/WebP) in .github/assets/goku/. One is picked per
day and sits left of the title. Art with transparency is used as-is; opaque art
is cropped square (focus: upper right) and its edges feathered into the page.
With no art there, a four-star dragon ball is drawn instead.

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
from PIL import Image, ImageOps

ASSETS = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "assets"
TITLE_FONT = TTFont(ASSETS / "fonts" / "Poppins-BoldItalic.ttf")
SUB_FONT = TTFont(ASSETS / "fonts" / "Poppins-Medium.ttf")

W, H = 1120, 320
ART = H
TEXT_X = 340
MAX_TITLE_W = W - TEXT_X - 20
THEMES = {
    "light": {"title": "#1f2328", "sub": "#59636e"},
    "dark": {"title": "#f0f6fc", "sub": "#9198a1"},
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
    cx, cy, r = ART // 2, H // 2, 105
    stars = "".join(
        f'<polygon points="{star(cx + dx, cy + dy, 21)}" fill="#c1121f"/>'
        for dx, dy in ((-29, -20), (29, -20), (-29, 27), (29, 27))
    )
    return f"""<circle cx="{cx}" cy="{cy}" r="{ART // 2}" fill="url(#aura)"/>
<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#ball)"/>
<ellipse cx="{cx - 34}" cy="{cy - 50}" rx="38" ry="20" fill="#fff" opacity=".35" transform="rotate(-28 {cx - 34} {cy - 50})"/>
{stars}"""


def feather(img):
    img = ImageOps.fit(img.convert("RGB"), (ART * 2, ART * 2), Image.LANCZOS, centering=(0.62, 0.15))
    n = img.width
    px = img.load()
    out = Image.new("RGBA", img.size)
    op = out.load()
    for y in range(n):
        for x in range(n):
            r = (((x - n / 2) / (n / 2)) ** 2 + ((y - n / 2) / (n / 2)) ** 2) ** 0.5
            t = min(max((1 - r) / 0.14, 0), 1)
            op[x, y] = (*px[x, y], round(255 * t * t * (3 - 2 * t)))
    return out


def goku_art(path):
    img = Image.open(path).convert("RGBA")
    if img.getchannel("A").getextrema()[0] == 255:
        img = feather(img)
    scale = min(ART / img.width, ART / img.height)
    w, h = round(img.width * scale), round(img.height * scale)
    if img.width > w * 2:
        img = img.resize((w * 2, h * 2), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, "WEBP", quality=90, method=6)
    uri = "data:image/webp;base64," + base64.b64encode(buf.getvalue()).decode()
    x = (ART - w) // 2
    return f'<circle cx="{ART // 2}" cy="{H // 2}" r="{ART // 2}" fill="url(#aura)"/>\n<image href="{uri}" x="{x}" y="{H - h}" width="{w}" height="{h}"/>'


def build(mode, art):
    colors = THEMES[mode]
    full = NAME + " " + ACCENT
    size = 84
    if text_width(TITLE_FONT, full, size) > MAX_TITLE_W:
        size = size * MAX_TITLE_W / text_width(TITLE_FONT, full, size)
    base_y = 132
    name_d, name_w = text_path(TITLE_FONT, NAME + " ", size, TEXT_X, base_y)
    acc_d, acc_w = text_path(TITLE_FONT, ACCENT, size, TEXT_X + name_w, base_y)
    sub_d, _ = text_path(SUB_FONT, SUBTITLE, 29, TEXT_X + 4, base_y + 72)
    tag_d, tag_w = text_path(TITLE_FONT, TAGLINE, 21, TEXT_X + 4, base_y + 112)
    bar_w = name_w + acc_w
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" role="img" aria-label="BlazeFTL&#8217;s patches">
<defs>
<linearGradient id="accent" x1="0" x2="1"><stop offset="0" stop-color="#ff7a00"/><stop offset="1" stop-color="#ffc400"/></linearGradient>
<linearGradient id="bar" x1="0" x2="1"><stop offset="0" stop-color="#ff7a00"/><stop offset=".6" stop-color="#ffc400"/><stop offset="1" stop-color="#1e88e5"/></linearGradient>
<radialGradient id="ball" cx=".38" cy=".32" r=".8"><stop offset="0" stop-color="#ffd23f"/><stop offset=".55" stop-color="#ff9a1f"/><stop offset="1" stop-color="#e65100"/></radialGradient>
<radialGradient id="aura"><stop offset="0" stop-color="#ffd23f" stop-opacity=".55"/><stop offset=".6" stop-color="#ff9a1f" stop-opacity=".18"/><stop offset="1" stop-color="#ff9a1f" stop-opacity="0"/></radialGradient>
</defs>
{art}
<path d="{name_d}" fill="{colors['title']}"/>
<path d="{acc_d}" fill="url(#accent)"/>
<path d="{sub_d}" fill="{colors['sub']}"/>
<path d="{tag_d}" fill="#ff7a00"/>
<polygon points="{TEXT_X + 6},{base_y + 28} {TEXT_X + bar_w + 6},{base_y + 28} {TEXT_X + bar_w - 8},{base_y + 35} {TEXT_X - 8},{base_y + 35}" fill="url(#bar)" opacity=".9"/>
</svg>
"""


def main():
    pics = sorted(p for pat in ("*.png", "*.jpg", "*.jpeg", "*.webp") for p in (ASSETS / "goku").glob(pat))
    art = goku_art(pics[datetime.date.today().toordinal() % len(pics)]) if pics else dragon_ball()
    for mode in THEMES:
        (ASSETS / f"banner-{mode}.svg").write_text(build(mode, art), encoding="utf-8")
    print(f"banner built ({len(pics)} Goku pictures)")


if __name__ == "__main__":
    main()
