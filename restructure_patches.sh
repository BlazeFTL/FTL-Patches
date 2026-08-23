#!/usr/bin/env bash
set -euo pipefail

cd patches/src/main/kotlin/app/ftl/patches

git mv removeadslite RemoveAdsLite

git mv ads RemoveAds
find RemoveAds -name '*.kt' -exec sed -i \
  's/^package app\.ftl\.patches\.ads$/package app.ftl.patches.removeads/' {} +
sed -i 's/app\.ftl\.patches\.ads\.hideAdLayoutsPatch/app.ftl.patches.removeads.hideAdLayoutsPatch/' \
  RemoveAdsLite/RemoveAdsLitePatch.kt
sed -i 's/app\.ftl\.patches\.ads\.forceHideAdViewsPatch/app.ftl.patches.removeads.forceHideAdViewsPatch/' \
  RemoveAdsLite/RemoveAdsLitePatch.kt

git mv adactivities RemoveAdsUltraLite
find RemoveAdsUltraLite -name '*.kt' -exec sed -i \
  's/^package app\.ftl\.patches\.adactivities$/package app.ftl.patches.removeadsultralite/' {} +
sed -i 's/app\.ftl\.patches\.ads\.hideAdLayoutsPatch/app.ftl.patches.removeads.hideAdLayoutsPatch/' \
  RemoveAdsUltraLite/CallFinishOnAdActivitiesPatch.kt

mkdir ApkCleanup
git mv cleanup/ApkCleanupPatch.kt ApkCleanup/
git mv DexDebugInfo/RemoveDebugInfoPatch.kt ApkCleanup/
git mv resources/DrawableCleanPatch.kt ApkCleanup/
git mv resources/LangCleanPatch.kt ApkCleanup/
git mv resources/PngOptimizerPatch.kt ApkCleanup/
rmdir cleanup DexDebugInfo resources
find ApkCleanup -name '*.kt' -exec sed -i -E \
  's/^package app\.ftl\.patches\.(cleanup|DexDebugInfo|resources)$/package app.ftl.patches.apkcleanup/' {} +

echo "=== leftover old refs (should be empty) ==="
grep -rn \
  'app\.ftl\.patches\.ads\b\|app\.ftl\.patches\.adactivities\b\|app\.ftl\.patches\.cleanup\b\|app\.ftl\.patches\.DexDebugInfo\b\|app\.ftl\.patches\.resources\b' \
  . || echo "clean"

git status
EOF