# 👋🧩 FTL Patches

Personal collection of my Morphe Patches

## ❓ About

Strips ads and analytics/crash-reporting SDKs at the bytecode level, cleans build artifacts and dex debug info, trims resource bloat — unused density buckets, unused language packs, lossless PNG recompression — and lets you scale the app's display density independent of system settings, for smaller, cleaner APKs.

## 🩹 Patches list

<!-- PATCHES_START -->
> **[v1.35.0-dev.17](https://github.com/BlazeFTL/FTL-Patches/releases/tag/v1.35.0-dev.17)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;25 patches total
<details>
<summary>📦 Xender&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 18.8.0.prime |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Clean main UI](#clean-main-ui) | Adds the wanted register-isolated MainActivity UI helpers and reapplies them at safe lifecycle points. |  |
| [Skip splash screen](#skip-splash-screen) | Restores MainActivity local-data and startup-permission helpers, then reduces SplashActivity to the wanted direct MainActivity launch flow. |  |
| [Speed up splash screen](#speed-up-splash-screen) | Enters the main activity directly after the splash permission check instead of delaying for the additional external-storage check. |  |

</details>

<details>
<summary>📦 SnapTube&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 7.64.0.76450210 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Notification Defaults](#disable-notification-defaults) | Turns off the Toolbar, Recommended contents, and Tool notifications channels by default. |  |
| [Hide Settings Items](#hide-settings-items) | Hides the Download tools and Phone clean categories, and their sub-items, from Settings. |  |
| [Remove Watch Ad To Download](#remove-watch-ad-to-download) | Removes the requirement to watch a rewarded ad before a download starts. |  |

</details>

<details>
<summary>📦 All Video Downloader & Ace Player&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 1.9.7 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable ad dialog when reopening app](#disable-ad-dialog-when-reopening-app) | Prevents the full-screen "loading ad" dialog from appearing when the app is reopened after being minimized. |  |
| [Skip splash and language screens](#skip-splash-and-language-screens) | Jumps straight to the main activity from the splash screen, skipping the splash animation, the language-selection screen, and any ad/app-open dialog normally shown first. |  |

</details>

<details>
<summary>📦 ES File Explorer&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.4.3.7 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [ES File Explorer Ui Cleanup](#es-file-explorer-ui-cleanup) | Removes BookMark, New Files, Cleaner Row In HomePage, Cleans More menu actions |  |

</details>

<details>
<summary>📦 MX Player Pro&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Hide File Transfer, Video Playlist, Private Folder tiles](#hide-file-transfer-video-playlist-private-folder-tiles) | Removes the File Transfer, Video Playlist, and Private Folder tiles from the tools tile list. |  |
| [Hide Me tab promo items](#hide-me-tab-promo-items) | Collapses the WhatsApp, Legal, and Help entries on the Me tab. |  |
| [Hide local tiles banner](#hide-local-tiles-banner) | Collapses the local device tiles banner. |  |
| [Skip welcome screen](#skip-welcome-screen) | Moves the launcher intent filters and app shortcuts from the welcome/splash activity to the main activity, so the app boots straight past the splash and update screen. |  |

</details>

<details>
<summary>📦 RS File Manager&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 2.3.0.4 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Hide more actions](#hide-more-actions) | Hides Hide, Add to desktop, Encrypt, Decrypt, Add bookmark, Web Search, Copy to, Move to, Transfer, and Playing from the "More actions" menu. |  |
| [Hide network, tools and bookmarks on home page](#hide-network-tools-and-bookmarks-on-home-page) | Hides the Network, Tools and Bookmarks sections from the home page section list. |  |
| [Skip splash screen](#skip-splash-screen) | Moves the launcher intent filter to the main activity and calls the all-files-access permission request directly, instead of showing the splash activity and its full-screen 'grant storage access' dialog. |  |

</details>

<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;9 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [APK Junk Cleanup](#apk-junk-cleanup) | Removes build artifacts and metadata that bloat the APK: Play Services / Firebase version files, protobuf descriptors, debug probes, kotlin builtins, META-INF subfolder clutter, and misc junk. Safe — only removes files with no runtime purpose. | • Keep Only One Architecture<br>• Target architecture |
| [Add Toast](#add-toast) | Shows a custom toast message when the app starts. Works on any app. | • Toast message<br>• Show once |
| [Custom DPI (Universal)](#custom-dpi-universal) | Change this app's display size without touching your phone's system settings. Make it bigger if things look too small, or smaller to fit more on screen. Works on any app. | • Display scale |
| [Png Optimizer](#png-optimizer) | Compresses PNG images without losing quality and strips hidden metadata (DPI, timestamps, text) to make the app smaller. Only rewrites files when the result is actually smaller. |  |
| [Remove Ads](#remove-ads) | Stubs known ad SDK entry points and poisons ad-network URL strings across the whole app. |  |
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
