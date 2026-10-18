#!/usr/bin/env python3
"""
Injects the patches list into README.md (between <!-- PATCHES_START --> and
<!-- PATCHES_END -->) from patches-list.json.

App icons are automatic, saved to .github/assets/icons/<package>.<ext>.
Sources, tried in order for any app with no real icon yet:
  1. .github/assets/icons/sources.json   overrides: {"package": "https://image-url"}
     or {"package": "page:https://official-site/"} to take the site's touch icon
     or {"package": "og:https://store-page/"} to take the page's og:image (app store
     pages); a list of any of these is tried in order
  2. Google Play listing
  3. Aptoide API (covers many apps that are off Play)
  4. generated initials badge (<package>.svg), retried against 1-3 on every run
Drop your own <package>.png in the folder to pin an icon; real files are never
overwritten, except packages listed in sources.json, which are re-fetched from
their listed URL every run (a failed fetch keeps the current file). Set NO_ICONS=1 to skip all fetching (offline preview).

python3 generate_patches_readme.py <owner/repo> <branch> [patches-list.json] [README.md]
"""

import io
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import zlib
from html import escape
from pathlib import Path

if len(sys.argv) < 3:
    print("Usage: generate_patches_readme.py <owner/repo> <branch> [json] [readme]")
    sys.exit(1)

repo_full = sys.argv[1]
branch = sys.argv[2]
json_path = Path(sys.argv[3]) if len(sys.argv) > 3 else Path("patches-list.json")
readme_path = Path(sys.argv[4]) if len(sys.argv) > 4 else Path("README.md")

if "/" not in repo_full:
    raise ValueError(f"Invalid repo format: {repo_full} (expected owner/repo)")
owner, repo = repo_full.split("/", 1)

with open(json_path, encoding="utf-8") as f:
    data = json.load(f)

by_pkg = {}
universal = {}

for patch in data["patches"]:
    cp = patch.get("compatiblePackages")
    if not cp:
        universal.setdefault(patch["name"], patch)
        continue
    for entry in cp:
        pkg = entry["packageName"]
        slot = by_pkg.setdefault(pkg, {"name": entry.get("name") or pkg, "patches": {}, "targets": []})
        for target in entry.get("targets") or []:
            if target not in slot["targets"]:
                slot["targets"].append(target)
        slot["patches"].setdefault(patch["name"], patch)


def slug(text):
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", text.lower())).strip("-")


PATCHES_SRC = Path("patches/src/main/kotlin")


def balanced(text, start):
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1:i]
    return ""


def build_source_index():
    """(patch name, packageName) -> source file, plus name -> files fallback."""
    if not PATCHES_SRC.is_dir():
        return {}, {}

    files = {p: p.read_text(encoding="utf-8") for p in PATCHES_SRC.rglob("*.kt")}

    const_pkg = {}
    for path, text in files.items():
        for m in re.finditer(r"val (\w+)\s*=\s*Compatibility\(", text):
            body = balanced(text, m.end() - 1)
            pkg = re.search(r'packageName\s*=\s*"([^"]+)"', body)
            if pkg:
                const_pkg[(path, m.group(1))] = pkg.group(1)

    def resolve(path, const):
        if (path, const) in const_pkg:
            return const_pkg[(path, const)]
        for (p, c), pkg in const_pkg.items():
            if c == const and p.parent == path.parent:
                return pkg
        for (p, c), pkg in const_pkg.items():
            if c == const:
                return pkg
        return None

    exact, by_name = {}, {}
    for path, text in files.items():
        names = re.findall(r'^\s{4}name\s*=\s*"([^"]+)"', text, re.MULTILINE)
        names += re.findall(r'\w+Patch\(\s*"([^"]+)"', text)
        if not names:
            continue
        str_consts = dict(re.findall(r'const val (\w+)\s*=\s*"([^"]+)"', text))
        pkgs = set()
        for m in re.finditer(r"compatibleWith\(", text):
            body = balanced(text, m.end() - 1)
            pkgs.update(re.findall(r'packageName\s*=\s*"([^"]+)"', body))
            for ident in re.findall(r'packageName\s*=\s*([A-Z][A-Z0-9_]*)\b', body):
                if ident in str_consts:
                    pkgs.add(str_consts[ident])
            for const in re.findall(r"\b([A-Z][A-Z0-9_]*)\b", body):
                pkg = resolve(path, const)
                if pkg:
                    pkgs.add(pkg)
        for name in names:
            key = name.lower()
            by_name.setdefault(key, []).append(path.as_posix())
            for pkg in pkgs or [None]:
                exact.setdefault((key, pkg), path.as_posix())
    return exact, by_name


SOURCES, SOURCES_BY_NAME = build_source_index()


def source_for(name, pkg):
    key = name.lower()
    path = SOURCES.get((key, pkg))
    if not path:
        candidates = SOURCES_BY_NAME.get(key, [])
        path = candidates[0] if len(set(candidates)) == 1 else None
    return urllib.parse.quote(path) if path else None


def patch_cell(patch, pkg, pid):
    src = source_for(patch["name"], pkg)
    label = f'[{patch["name"]}]({src})' if src else patch["name"]
    return f'<a id="{pid}"></a>{label}'


def patches_table(patches, app_slug, pkg=None):
    patches = sorted(patches, key=lambda x: x["name"])
    has_options = any(p.get("options") for p in patches)
    if has_options:
        rows = ["| 🩹&nbsp;Patch | 🔥&nbsp;Description | ⚙️&nbsp;Options |", "|----------|----------------|-----------|"]
    else:
        rows = ["| 🩹&nbsp;Patch | 🔥&nbsp;Description |", "|----------|----------------|"]
    for p in patches:
        cell = patch_cell(p, pkg, f"{app_slug}-{slug(p['name'])}")
        desc = (p.get("description") or "").replace("\n", "<br>")
        if not has_options:
            rows.append(f"| {cell} | {desc} |")
            continue
        titles = [o.get("title") or o.get("key") or "" for o in p.get("options") or []]
        rows.append(f"| {cell} | {desc} | {'<br>'.join(f'• {t}' for t in titles)} |")
    return "\n".join(rows)


ANY_VERSION = "Any version"


def versions_table(targets):
    columns = []
    for t in targets:
        ver = t.get("version") or ANY_VERSION
        label = f"🧪&nbsp;{ver}" if t.get("isExperimental") else ver
        if label not in [c[0] for c in columns]:
            columns.append((label, (t.get("description") or "").replace("\n", "<br>")))
    if len(columns) > 1:
        columns = [c for c in columns if c[0] != ANY_VERSION]
    if not columns:
        return ""
    rows = [
        "| " + " | ".join(label for label, _ in columns) + " |",
        "| " + " | ".join(":---:" for _ in columns) + " |",
    ]
    if any(desc for _, desc in columns):
        rows.append("| " + " | ".join(desc for _, desc in columns) + " |")
    return "\n".join(rows)


ICON_DIR = Path(".github/assets/icons")
EXTS = ("png", "jpg", "webp", "svg")
PLAY_URL = "https://play.google.com/store/apps/details?id={}&hl=en&gl=US"
APTOIDE_URL = "https://ws75.aptoide.com/api/7/app/get/package_name={}"
NO_ICONS = os.environ.get("NO_ICONS") == "1"
PALETTES = (("#FFB38A", "#FF8F5E"), ("#5FD0D8", "#2AA7E0"), ("#FFA270", "#F26B4A"), ("#2AA7E0", "#1D6FD8"))


def icon_file(pkg):
    for ext in EXTS:
        if (ICON_DIR / f"{pkg}.{ext}").is_file():
            return f"{pkg}.{ext}"
    return None


def http_get(url):
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.read()
    except (urllib.error.URLError, OSError, ValueError):
        return None


def image_ext(blob):
    if blob.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if blob.startswith(b"\xff\xd8\xff"):
        return "jpg"
    if blob[:4] == b"RIFF" and blob[8:12] == b"WEBP":
        return "webp"
    return None


def ico_to_png(blob):
    if not blob.startswith(b"\x00\x00\x01\x00"):
        return None
    try:
        from PIL import Image

        ico = Image.open(io.BytesIO(blob))
        size = max(ico.ico.sizes())
        if size[0] < 48:
            return None
        ico.size = size
        out = io.BytesIO()
        ico.convert("RGBA").save(out, "PNG")
        return out.getvalue()
    except Exception:
        return None


def page_icon(url, og_only=False):
    """Icon advertised by a web page: touch icon / icon links (largest first), then og:image."""
    page = http_get(url)
    if not page:
        return None
    text = page.decode("utf-8", "replace")
    found = []
    for tag in [] if og_only else re.findall(r"<link\b[^>]*>", text, re.I):
        rel = re.search(r'rel=["\']([^"\']+)', tag, re.I)
        href = re.search(r'href=["\']([^"\']+)', tag, re.I)
        if not (rel and href and "icon" in rel.group(1).lower()):
            continue
        sizes = [int(n) for n in re.findall(r"(\d+)x\d+", tag)]
        rank = (2 if "apple-touch" in rel.group(1).lower() else 1, max(sizes or [0]))
        found.append((rank, href.group(1)))
    found.sort(reverse=True)
    urls = [href for _, href in found]
    og = re.search(r'<meta[^>]+property=["\']og:image["\'][^>]+content=["\']([^"\']+)', text, re.I)
    if og:
        urls.append(og.group(1))
    for href in urls:
        blob = http_get(urllib.parse.urljoin(url, href))
        if blob and image_ext(blob):
            return blob
        png = ico_to_png(blob) if blob else None
        if png:
            return png
    return None


def pinned_sources():
    try:
        return json.loads((ICON_DIR / "sources.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def override_icon(pkg):
    value = pinned_sources().get(pkg)
    for spec in [value] if isinstance(value, str) else value or []:
        if spec.startswith("page:"):
            blob = page_icon(spec[5:])
        elif spec.startswith("og:"):
            blob = page_icon(spec[3:], og_only=True)
        else:
            blob = http_get(spec)
        if blob and image_ext(blob):
            return blob
    return None


def play_icon(pkg):
    page = http_get(PLAY_URL.format(pkg))
    if not page:
        return None
    icon = re.search(r'"image":"(https://play-lh\.googleusercontent\.com/[^"]+)"', page.decode("utf-8", "replace"))
    return http_get(f"{icon.group(1)}=s96-cc") if icon else None


def find_icon_url(node):
    if isinstance(node, dict):
        icon = node.get("icon")
        if isinstance(icon, str) and icon.startswith("http"):
            return icon
        node = list(node.values())
    if isinstance(node, list):
        for child in node:
            found = find_icon_url(child)
            if found:
                return found
    return None


def aptoide_icon(pkg):
    blob = http_get(APTOIDE_URL.format(pkg))
    if not blob:
        return None
    try:
        url = find_icon_url(json.loads(blob))
    except ValueError:
        return None
    return http_get(url) if url else None


def rounded(blob, ext):
    """Square-fit to 96x96 and cut to a circle, to match Play's round icons. Needs
    Pillow; without it the original image is kept as is."""
    try:
        from PIL import Image, ImageDraw, ImageOps
    except ImportError:
        return blob, ext
    try:
        img = ImageOps.fit(Image.open(io.BytesIO(blob)).convert("RGBA"), (96, 96))
    except Exception:
        return blob, ext
    mask = Image.new("L", (96, 96), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, 95, 95), fill=255)
    img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (96, 96), 0), mask))
    out = io.BytesIO()
    img.save(out, "PNG", optimize=True)
    return out.getvalue(), "png"


def initials(name):
    words = re.findall(r"[A-Za-z0-9]+", name)
    if not words:
        return "?"
    if len(words[0]) <= 3 and words[0].isupper():
        return words[0]
    if len(words) > 1:
        return (words[0][0] + words[1][0]).upper()
    caps = re.findall(r"[A-Z]", words[0])
    return "".join(caps[:2]) if len(caps) >= 2 else words[0][:2].upper()


def placeholder_svg(pkg, name):
    top, bottom = PALETTES[zlib.crc32(pkg.encode()) % len(PALETTES)]
    text = escape(initials(name))
    size = 38 if len(text) <= 2 else 30
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96" width="96" height="96">'
        f'<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{top}"/>'
        f'<stop offset="1" stop-color="{bottom}"/></linearGradient></defs>'
        '<circle cx="48" cy="48" r="48" fill="url(#g)"/>'
        f'<text x="48" y="48" text-anchor="middle" dominant-baseline="central" '
        f'font-family="Poppins,Segoe UI,Roboto,Arial,sans-serif" font-size="{size}" font-weight="700" '
        f'fill="#fff">{text}</text></svg>\n'
    )


def ensure_icon(pkg, name):
    have = icon_file(pkg)
    real = bool(have) and not have.endswith(".svg")
    pinned = pkg in pinned_sources()
    if real and not pinned:
        return
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    sources = ((override_icon, True), (play_icon, False), (aptoide_icon, True))
    if real:
        sources = sources[:1]
    for fetch, tidy in sources:
        blob = fetch(pkg)
        ext = image_ext(blob) if blob else None
        if not ext:
            continue
        if tidy:
            blob, ext = rounded(blob, ext)
        for old in EXTS:
            (ICON_DIR / f"{pkg}.{old}").unlink(missing_ok=True)
        (ICON_DIR / f"{pkg}.{ext}").write_bytes(blob)
        return
    if not have:
        (ICON_DIR / f"{pkg}.svg").write_text(placeholder_svg(pkg, name), encoding="utf-8")


def icon_img(pkg):
    name = icon_file(pkg) if pkg else None
    if not name:
        return "📦&nbsp;"
    return f'<img src=".github/assets/icons/{name}" width="18" align="absmiddle">&nbsp;&nbsp;'


def spoiler(label, count, targets, tbl, pkg=None):
    noun = "patch" if count == 1 else "patches"
    vtbl = versions_table(targets)
    versions = f"**🎯 Supported versions:**\n\n{vtbl}\n\n" if vtbl else ""
    return f"""<a id="{slug(label)}"></a>
<details>
<summary>{icon_img(pkg)}{label}&nbsp;&nbsp;•&nbsp;&nbsp;{count} {noun}</summary>
<br>

{versions}{tbl}

</details>"""


def build_content():
    lines = [
        f"> **[v{ver}](https://github.com/{owner}/{repo}/releases/tag/v{ver})**"
        f"&nbsp;&nbsp;•&nbsp;&nbsp;`{branch}`&nbsp;&nbsp;•&nbsp;&nbsp;{total} patches total"
    ]
    if not NO_ICONS:
        for pkg, entry in by_pkg.items():
            ensure_icon(pkg, entry["name"])

    for pkg, entry in by_pkg.items():
        patches = list(entry["patches"].values())
        label = entry["name"]
        lines.append(spoiler(label, len(patches), entry["targets"], patches_table(patches, slug(label), pkg), pkg))
        lines.append("")

    if universal:
        uni = list(universal.values())
        noun = "patch" if len(uni) == 1 else "patches"
        lines.append(f"""<a id="universal"></a>
<details open>
<summary>🌐&nbsp;Universal&nbsp;&nbsp;•&nbsp;&nbsp;{len(uni)} {noun}</summary>
<br>

{patches_table(uni, "universal")}

</details>""")
        lines.append("")

    return "\n".join(lines)


ver = data["version"].lstrip("v")
total = sum(len(e["patches"]) for e in by_pkg.values()) + len(universal)

readme = readme_path.read_text(encoding="utf-8")
START = "<!-- PATCHES_START -->"
END = "<!-- PATCHES_END -->"

if START not in readme or END not in readme:
    print(build_content())
    sys.stderr.write(f"Markers {START} / {END} not found in {readme_path}.\n")
    sys.exit(1)

new_readme = re.sub(
    rf"{re.escape(START)}.*?{re.escape(END)}",
    lambda _: f"{START}\n{build_content()}\n{END}",
    readme,
    flags=re.DOTALL,
)
readme_path.write_text(new_readme, encoding="utf-8")
print(f"Injected patches section into {readme_path} (v{ver}, {branch}, {total} patches)")
