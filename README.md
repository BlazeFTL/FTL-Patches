# 👋🧩 FTL Patches

Personal collection of my Morphe Patches.

## ❓ About

Strips ads and analytics/crash-reporting SDKs at the bytecode level, and trims resource bloat — unused density buckets, unused language packs, and lossless PNG recompression — for smaller, cleaner APKs.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.5.0](https://github.com/BlazeFTL/FTL-Patches/releases/tag/v1.5.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;5 patches total
<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;5 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Drawable clean](#drawable-clean) | Keeps drawable/mipmap resources only in the target density bucket and removes duplicate-named copies from every other density bucket, relying on Android's density fallback to resolve them. | • Target density |
| [Language clean](#language-clean) | Removes language resource directories (values-<lang>) for languages not in the keep list, freeing up space used by unused translations. The default "values" directory is always kept. | • Languages to keep |
| [Png optimizer](#png-optimizer) | Losslessly recompresses png resources: re-deflates image data at maximum compression and strips non-rendering metadata (tEXt/zTXt/iTXt/tIME). Pure JVM, no native binaries — files are only rewritten when the result is smaller. |  |
| [Remove ads](#remove-ads) | Neuters ad-load entry points for major ad SDKs, poisons const-string ad network hosts/unit-id prefixes across all bytecode, and hides leftover ad view containers in layout XML. |  |
| [Remove analytics](#remove-analytics) | Neuters logging entry points for major analytics/crash-reporting SDKs, poisons const-string analytics hosts and component names across all bytecode, and strips Firebase receiver/service declarations from the manifest. |  |

</details>

<!-- PATCHES_END -->

#### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=BlazeFTL/FTL-Patches

Or manually add this repository url as a patch source in Morphe: https://github.com/BlazeFTL/FTL-Patches

### 🛠️ Building

To build FTL Patches,
you can follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## 📜 License

FTL Patches are licensed under the [GNU General Public License v3.0](LICENSE)
Public License v3.0](LICENSE)
