#!/usr/bin/env bash
set -euo pipefail

cd patches/src/main/kotlin/app/ftl/patches

git mv allvideodownloader AllVideoDownloader
git mv xender Xender
git mv snaptube SnapTube
git mv rsfileexplorer RsFileExplorer
git mv toast Toast

git status
