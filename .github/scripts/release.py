#!/usr/bin/env python3
import datetime
import json
import os
import re
import subprocess
import sys

REPO = os.environ["GITHUB_REPOSITORY"]
BRANCH = os.environ.get("GITHUB_REF_NAME", "")
OUTPUT = os.environ.get("GITHUB_OUTPUT")
NOTES_FILE = os.environ.get("NOTES_FILE", "/tmp/release-notes.md")

MAX_REV = 99
MAX_PATCH = 50

SECTIONS = {
    "fix": "🐛 Bug Fixes",
    "feat": "✨ New Features",
    "bump": "🚀 Updated App Support",
    "perf": "🔧 Improvements",
}

STABLE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?$")
COMMIT = re.compile(r"^(\w+)(?:\(([^)]*)\))?(!)?: (.+)$")


def sh(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()


def fmt(v):
    s = f"{v[0]}.{v[1]}.{v[2]}"
    return s + (f".{v[3]}" if v[3] else "")


def bump(v):
    major, minor, patch, rev = v
    if rev < MAX_REV:
        return (major, minor, patch, rev + 1)
    if patch < MAX_PATCH:
        return (major, minor, patch + 1, 0)
    return (major, minor + 1, 0, 0)


def out(**kw):
    if OUTPUT:
        with open(OUTPUT, "a") as f:
            for k, val in kw.items():
                f.write(f"{k}={val}\n")
    print(kw)


def main():
    if BRANCH not in ("main", "dev"):
        print(f"branch {BRANCH} does not release")
        return out(released="false")

    tags = sh("git", "tag", "-l", "v*").split()
    stable = {t: tuple(int(x or 0) for x in m.groups()) for t in tags if (m := STABLE.match(t))}
    if not stable:
        sys.exit("no stable tag found")
    latest_tag = max(stable, key=stable.get)
    latest = stable[latest_tag]

    if BRANCH == "main":
        merged = set(sh("git", "tag", "--merged", "HEAD", "-l", "v*").split())
        merged_stable = [t for t in stable if t in merged]
        base_tag = max(merged_stable, key=stable.get) if merged_stable else None
    else:
        try:
            base_tag = sh("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*", "HEAD")
        except subprocess.CalledProcessError:
            base_tag = None

    rng = f"{base_tag}..HEAD" if base_tag else "HEAD"
    raw = sh("git", "log", rng, "--format=%H%x1f%s%x1e")
    groups = {}
    releasable = False
    for rec in raw.split("\x1e"):
        rec = rec.strip()
        if not rec:
            continue
        sha, subject = rec.split("\x1f", 1)
        m = COMMIT.match(subject)
        if not m:
            continue
        ctype, scope, _, desc = m.groups()
        if ctype in SECTIONS:
            releasable = True
            groups.setdefault(SECTIONS[ctype], []).append((sha, scope, desc))
        elif ctype == "build" and scope == "Needs bump":
            releasable = True

    if not releasable:
        print("no releasable commits")
        return out(released="false")

    base = fmt(bump(latest))
    if BRANCH == "dev":
        n = 0
        for t in tags:
            m = re.fullmatch(rf"v{re.escape(base)}-dev\.(\d+)", t)
            if m:
                n = max(n, int(m.group(1)))
        version = f"{base}-dev.{n + 1}"
    else:
        version = base
    tag = f"v{version}"

    now = datetime.datetime.now(datetime.timezone.utc)
    date = now.strftime("%Y-%m-%d")
    head = f"## [{version}](https://github.com/{REPO}/compare/{base_tag}...{tag}) ({date})" if base_tag else f"## {version} ({date})"
    parts = [head]
    for title in sorted(groups):
        lines = []
        for sha, scope, desc in groups[title]:
            pre = f"**{scope}:** " if scope else ""
            lines.append(f"* {pre}{desc} ([{sha[:7]}](https://github.com/{REPO}/commit/{sha}))")
        parts.append(f"### {title}\n\n" + "\n".join(lines))
    notes = "\n\n".join(parts)

    with open(NOTES_FILE, "w") as f:
        f.write(notes + "\n")

    old = open("CHANGELOG.md").read() if os.path.exists("CHANGELOG.md") else ""
    with open("CHANGELOG.md", "w") as f:
        f.write(notes + "\n\n" + old)

    with open("patches-bundle.json", "w") as f:
        json.dump(
            {
                "created_at": now.strftime("%Y-%m-%dT%H:%M:%S"),
                "description": notes,
                "download_url": f"https://github.com/{REPO}/releases/download/{tag}/patches-{version}.mpp",
                "signature_download_url": "",
                "version": version,
            },
            f,
            indent=2,
            ensure_ascii=False,
        )
        f.write("\n")

    props = open("gradle.properties").read()
    props = re.sub(r"(?m)^version\s*=.*$", f"version = {version}", props)
    with open("gradle.properties", "w") as f:
        f.write(props)

    out(released="true", version=version, tag=tag, prerelease="true" if BRANCH == "dev" else "false")


main()
