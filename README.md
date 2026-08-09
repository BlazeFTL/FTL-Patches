# 👋🧩 FTL Patches

Personal collection of my Morphe Patches

## ❓ About

Strips ads and analytics/crash-reporting SDKs at the bytecode level, and trims resource bloat — unused density buckets, unused language packs, and lossless PNG recompression — for smaller, cleaner APKs.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.22.0](https://github.com/BlazeFTL/FTL-Patches/releases/tag/v1.22.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;8 patches total
<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;8 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [APK Junk Cleanup](#apk-junk-cleanup) | Removes build artifacts and metadata that bloat the APK: Play Services / Firebase version files, protobuf descriptors, debug probes, kotlin builtins, META-INF subfolder clutter, and misc junk. Safe — only removes files with no runtime purpose. | • Keep Only One Architecture<br>• Target architecture |
| [Custom DPI (Universal)](#custom-dpi-universal) | Change this app's display size without touching your phone's system settings. Make it bigger if things look too small, or smaller to fit more on screen. Works on any app. | • Display scale |
| [Png Optimizer](#png-optimizer) | Compresses PNG images without losing quality and strips hidden metadata (DPI, timestamps, text) to make the app smaller. Only rewrites files when the result is actually smaller. |  |
| [Remove Ads](#remove-ads) | Disables ad SDKs, ruins ad network request URLs, and hides empty ad containers across the app. |  |
| [Remove Analytics](#remove-analytics) | Disables tracking and crash-reporting tools, corrupts analytics web links inside the code, and removes background tracking services. |  |
| [Remove Debug Info](#remove-debug-info) | Removes debug information (line numbers, variable names, source file references) from every class in the .dex files to reduce overall APK size. |  |
| [Remove Duplicate Graphics](#remove-duplicate-graphics) | Keeps images for only one screen density (like xhdpi) and removes copies for all other densities. Android will automatically scale the kept images, making the app significantly smaller. | • Target density |
| [Remove Languages](#remove-languages) | Removes translations for languages you don't use across ALL resource types (strings, drawables, layouts, raw, xml, etc.). Only keeps the languages you pick. Base resources with no language code are always preserved. | • Languages to keep |

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
