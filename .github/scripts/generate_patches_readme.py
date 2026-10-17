#!/usr/bin/env python3
"""
Injects the patches list into README.md (between <!-- PATCHES_START --> and
<!-- PATCHES_END -->) from patches-list.json.

App icons are automatic: each app's icon is fetched from its Google Play listing
by package name and saved to .github/assets/icons/<package>.png. Apps that already
have a file are skipped. To override one or cover an app not on Play, drop your own
.github/assets/icons/<package>.png in. Apps with no icon render with a package emoji.

python3 generate_patches_readme.py <owner/repo> <branch> [patches-list.json] [README.md]
"""

import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
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
        rows = ["| 🥋&nbsp;Patch | 🔥&nbsp;Description | ⚙️&nbsp;Options |", "|----------|----------------|-----------|"]
    else:
        rows = ["| 🥋&nbsp;Patch | 🔥&nbsp;Description |", "|----------|----------------|"]
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
PLAY_URL = "https://play.google.com/store/apps/details?id={}&hl=en&gl=US"


def icon_file(pkg):
    path = ICON_DIR / f"{pkg}.png"
    return path.name if path.is_file() else None


def fetch_play_icon_url(pkg):
    request = urllib.request.Request(PLAY_URL.format(pkg), headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            page = response.read().decode("utf-8", "replace")
    except (urllib.error.URLError, OSError):
        return None
    icon = re.search(r'"image":"(https://play-lh\.googleusercontent\.com/[^"]+)"', page)
    return icon.group(1) if icon else None


def download_icon(url, pkg):
    try:
        with urllib.request.urlopen(f"{url}=s96-cc", timeout=20) as response:
            blob = response.read()
    except (urllib.error.URLError, OSError):
        return
    if blob.startswith(b"\x89PNG\r\n\x1a\n"):
        ICON_DIR.mkdir(parents=True, exist_ok=True)
        (ICON_DIR / f"{pkg}.png").write_bytes(blob)


def fetch_missing_icons(pkgs):
    for pkg in pkgs:
        if icon_file(pkg):
            continue
        url = fetch_play_icon_url(pkg)
        if url:
            download_icon(url, pkg)


def icon_img(pkg):
    name = icon_file(pkg) if pkg else None
    if not name:
        return "📦&nbsp;"
    return f'<img src=".github/assets/icons/{name}" width="18" align="top">&nbsp;&nbsp;'


def spoiler(label, count, targets, tbl, pkg=None):
    noun = "patch" if count == 1 else "patches"
    vtbl = versions_table(targets)
    versions = f"**🐉 Supported versions:**\n\n{vtbl}\n\n" if vtbl else ""
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
    fetch_missing_icons(list(by_pkg))

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
