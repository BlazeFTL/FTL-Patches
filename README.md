# 👋🧩 FTL Patches

Personal collection of my Morphe Patches.

## ❓ About

Strips ads and analytics/crash-reporting SDKs at the bytecode level, cleans build artifacts and dex debug info, trims resource bloat — unused density buckets, unused language packs, lossless PNG recompression — and lets you scale the app's display density independent of system settings, for smaller, cleaner APKs.

## 🩹 Patches list

<!-- PATCHES_START -->
> **[v1.42.0-dev.6](https://github.com/BlazeFTL/FTL-Patches/releases/tag/v1.42.0-dev.6)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;38 patches total
<details>
<summary>📦 All Video Downloader & Ace Player&nbsp;&nbsp;•&nbsp;&nbsp;5 patches</summary>
<br>

**🎯 Supported versions:**

| 1.9.7 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Boost Splash Screen](#boost-splash-screen) | Fixes Remove Ads And Remove Ads Lite Gettings Stuck In Splash Screen Useless if you also select skip splash and language activity patch. Also stops the splash from hiding the on-screen navigation buttons. | • Splash duration (ms) |
| [Disable ad dialog when reopening app](#disable-ad-dialog-when-reopening-app) | Prevents the full-screen "loading ad" dialog from appearing when the app is reopened after being minimized. |  |
| [Disable downloader from download menu](#disable-downloader-from-download-menu) | Strips WebDownloadActivity's scheme/mimeType/host/pathPattern intent-filter data (action and category tags kept) so it no longer offers itself as a handler in the system download/"complete action using" chooser. |  |
| [Remove from default browser list](#remove-from-default-browser-list) | Removes the unscoped http/https <data> entries from MainActivity's first intent-filter carrying them so the app stops appearing as a candidate in the system's default browser / "open with" chooser. Other schemes and mimeTypes on the same and other filters (about, javascript, inline, file, text/html, etc., used for the app's internal WebView) are left in place. |  |
| [Skip splash and language screens](#skip-splash-and-language-screens) | Jumps straight to the main activity from the splash screen, skipping the splash animation, the language-selection screen, and any ad/app-open dialog normally shown first. |  |

</details>

<details>
<summary>📦 SnapTube&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

**🎯 Supported versions:**

| 7.64.0.76450210 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Clean SnapTube Settings Page](#clean-snaptube-settings-page) | Removes the Download tools and Phone clean categories, and their sub-items, from Settings. |  |
| [Disable Annoying Snaptube Notifications](#disable-annoying-snaptube-notifications) | Turns off the Toolbar, Recommended contents, and Tool notifications channels by default. |  |
| [Remove Watch Ad To Download](#remove-watch-ad-to-download) | Removes the requirement to watch a rewarded ad before a download starts. |  |
| [Remove from default browser list](#remove-from-default-browser-list) | Strips the data schemes from the LinkHandleActivity alias's unscoped http/https intent-filter (identified by its unique snaptube.intent.action.OPEN_WEBVIEW action, not by position) so the app stops appearing as a candidate in the system's default browser / "open with" chooser. The other host-scoped deep-link filters on the same alias (facebook.com, tiktok.com, youtube.com, etc.) are untouched. |  |

</details>

<details>
<summary>📦 Xender&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 18.8.0.prime |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Clean main UI](#clean-main-ui) | Hides the bottom navigation bar, the top-right guide icon, and the Rate/Help/About drawer items, keeps the connect/create/join buttons on top, and stops them from being auto-hidden. Reapplied on create, resume, and drawer open (and retried for ~1.8s after each) since some of these views are inflated lazily. |  |
| [Skip splash screen](#skip-splash-screen) | Jumps straight to the main activity from the splash screen, skipping the splash animation entirely. Also restores the storage-permission request and local media scan that the splash screen normally performs, so the Apps/Photo/Video tabs still load. |  |
| [Speed up splash screen](#speed-up-splash-screen) | Enters the main activity directly after the splash permission check instead of delaying for the additional external-storage check. |  |

</details>

<details>
<summary>📦 MiXplorer&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable download chooser duplicates](#disable-download-chooser-duplicates) | Removes the ACTION_VIEW http/https intent filters from MiXplorer's Explore/Download/Copy to/Extract to shell activities so the app stops showing up multiple times in Firefox's (and other browsers') download-complete chooser. Share-to (SEND/SEND_MULTIPLE) entries and local/ftp/smb file handling are untouched. |  |

</details>

<details>
<summary>📦 RS File Manager&nbsp;&nbsp;•&nbsp;&nbsp;5 patches</summary>
<br>

**🎯 Supported versions:**

| 2.3.0.4 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable downloader from download menu](#disable-downloader-from-download-menu) | Strips RsDownloadActivity's file-extension and scheme/mimeType intent filters so it no longer offers itself as a handler in the system download/"complete action using" chooser. |  |
| [Disable rate us dialog](#disable-rate-us-dialog) | Overrides show() on the in-app "rate us" dialog so it's still built but never displayed. |  |
| [Hide more actions](#hide-more-actions) | Hides Hide, Add to desktop, Encrypt, Decrypt, Add bookmark, Web Search, Copy to, Move to, Transfer, and Playing from the "More actions" menu. |  |
| [Hide network, tools and bookmarks on home page](#hide-network-tools-and-bookmarks-on-home-page) | Hides the Network, Tools and Bookmarks sections from the home page section list. |  |
| [Skip splash screen](#skip-splash-screen) | Moves the launcher intent filter to the main activity and calls the all-files-access permission request directly, instead of showing the splash activity and its full-screen 'grant storage access' dialog. |  |

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
<summary>📦 Ampere&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Material Colors Upgrade Peach And Purple](#material-colors-upgrade-peach-and-purple) | Updates Accent/Primary/CardBgDark to the newer Material color palette. |  |

</details>

<details>
<summary>📦 Calendar&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 1.0.34 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Skip/Boost splash screen](#skip-boost-splash-screen) | By default, skips the splash screen entirely on launch. Turn on "Boost splash" to instead keep it briefly on screen (configurable delay) rather than skipping it outright. Useless To Boost Splash Screen If You Select Unlock Premium Patch Too Use If You Want To Skip Splash Screen Entirely | • Boost splash instead of skipping<br>• Boost delay (ms) |
| [Unlock premium](#unlock-premium) | Unlocks premium features and removes ads. |  |

</details>

<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;12 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [APK Junk Cleanup](#apk-junk-cleanup) | Removes junk and useless files with no runtime purpose inside apk. | • Keep Only One Architecture<br>• Target architecture |
| [Add Toast](#add-toast) | Shows a custom toast message when the app starts. Works on any app. | • Toast message<br>• Show once |
| [Change Display Size](#change-display-size) | Change any app's display size without touching your phone's system settings. Make it bigger if things look too small, or smaller to fit more on screen. You Need To Configure 100(No Change), 90(10% Smaller App Ui), 110(10% Bigger App Ui). | • Display scale |
| [Png Optimizer](#png-optimizer) | Compresses PNG images without losing quality and strips hidden metadata (DPI, timestamps, text) to make the app smaller. Only rewrites files when the result is actually smaller. |  |
| [Remove Ads](#remove-ads) | Cleans Apps Code From Ads Junk. Works In Most Apps Where There Isn't Any Check For Ads Loaded Or Not. If There Is A Check You Will Be Stuck In SplashActivity Due To Custom Ads Load Checks But It Is Superior. |  |
| [Remove Ads Lite (Adobo)](#remove-ads-lite-adobo) | Based On (Adobo's Block Ads+Mobile Ads) Use When Remove Ads Patch Caused Problem. It Is Weaker But Effective, No Need To Select A Host File Or Configure Anything. In Future It May Replace Remove Ads Patch If I Find No Problems. | • Redirection IP<br>• Additional hosts file (optional) |
| [Remove Ads Ultra Lite](#remove-ads-ultra-lite) | Call finish on ad activities. Use Where Remove Ads And Remove Ads Lite (Adobo) Caused Problem. Its In Very Early Stage So Test And Provide FeedBack If You Still See Ads In Some App. |  |
| [Remove Analytics](#remove-analytics) | Disables tracking and crash-reporting tools, corrupts analytics web links inside the code, and removes background tracking services. |  |
| [Remove Debug Info](#remove-debug-info) | Removes debug information (line numbers, variable names, source file references) from every class in the .dex files to reduce overall APK size. |  |
| [Remove Duplicate Graphics](#remove-duplicate-graphics) | Keeps images for only one screen density (like xhdpi) and removes copies for all other densities. Android will automatically scale the kept images, making the app significantly smaller. | • Target density |
| [Remove Languages](#remove-languages) | Removes translations for languages you don't use across ALL resource types (strings, drawables, layouts, raw, xml, etc.). Only keeps the languages you pick. Base resources with no language code are always preserved. | • Languages to keep |
| [Remove Splash Screen - Expert Only](#remove-splash-screen-expert-only) | EXPERT USERS ONLY. Manually Configure It To Point At Real Splash And Main Activity As Many Apps Use Other Names. Check the log to know what the patch is doing Ensure App Doesnt Ask For Permission In Splash Screen. | • Splash activity name<br>• Real main activity name |

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
