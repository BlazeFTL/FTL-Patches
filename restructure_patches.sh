#!/usr/bin/env bash
set -euo pipefail

cd patches/src/main/kotlin/app/ftl/patches

# display-only renames, package untouched
git mv mxplayer MxPlayer
git mv esfileexplorer EsFileExplorer

# analytics -> RemoveAnalytics (folder + package)
git mv analytics RemoveAnalytics
find RemoveAnalytics -name '*.kt' -exec sed -i \
  's/^package app\.ftl\.patches\.analytics$/package app.ftl.patches.removeanalytics/' {} +

# dpi -> CustomDPI (folder + package)
git mv dpi CustomDPI
find CustomDPI -name '*.kt' -exec sed -i \
  's/^package app\.ftl\.patches\.dpi$/package app.ftl.patches.customdpi/' {} +
sed -i \
  -e 's/app\.ftl\.patches\.dpi\.AppEntryPoint\b/app.ftl.patches.customdpi.AppEntryPoint/' \
  -e 's/app\.ftl\.patches\.dpi\.findAppEntryPointPatch/app.ftl.patches.customdpi.findAppEntryPointPatch/' \
  toast/AddToastPatch.kt

echo "=== leftover old refs (mxplayer/esfileexplorer lines below are expected, package unchanged) ==="
grep -rn 'app\.ftl\.patches\.mxplayer\b\|app\.ftl\.patches\.esfileexplorer\b\|app\.ftl\.patches\.analytics\b\|app\.ftl\.patches\.dpi\b' . || echo clean

git status