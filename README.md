# 👋🧩 FTL Patches

Personal collection of my Morphe Patches

## ❓ About

Strips ads and analytics/crash-reporting SDKs at the bytecode level, cleans build artifacts and dex debug info, trims resource bloat — unused density buckets, unused language packs, lossless PNG recompression — and lets you scale the app's display density independent of system settings, for smaller, cleaner APKs.

## 🩹 Patches list

<!-- PATCHES_START -->
> **[v1.43.3-dev.1](https://github.com/BlazeFTL/FTL-Patches/releases/tag/v1.43.3-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;90 patches total
<details>
<summary>📦 All Video Downloader & Ace Player&nbsp;&nbsp;•&nbsp;&nbsp;8 patches</summary>
<br>

**🎯 Supported versions:**

| 1.9.8 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Boost Splash Screen](#boost-splash-screen) | Fixes Remove Ads And Remove Ads Lite Gettings Stuck In Splash Screen Useless if you also select skip splash and language activity patch. Also stops the splash from hiding the on-screen navigation buttons. | • Splash duration (ms) |
| [Disable ad dialog when reopening app](#disable-ad-dialog-when-reopening-app) | Prevents the full-screen "loading ad" dialog from appearing when the app is reopened after being minimized. |  |
| [Disable downloader from download menu](#disable-downloader-from-download-menu) | Strips WebDownloadActivity's intent-filter data so it no longer offers itself as a handler in the system download/"complete action using" chooser. |  |
| [Disable rate dialogs](#disable-rate-dialogs) | Prevents the rate and star rating dialogs from showing. |  |
| [Hide Scan (AD) menu item](#hide-scan-ad-menu-item) | Removes the Scan (AD) row from the browser overflow menu. |  |
| [Remove from default browser list](#remove-from-default-browser-list) | Removes the unscoped http/https <data> entries from MainActivity's first intent-filter carrying them so the app stops appearing as a candidate in the system's default browser / "open with" chooser. |  |
| [Skip splash and language screens](#skip-splash-and-language-screens) | Jumps straight to the main activity from the splash screen, skipping the splash animation, the language-selection screen, DONT UNSELECT THIS,IT HAS SIGNATURE BYPASS INCLUDED. |  |
| [Skip tips screen](#skip-tips-screen) | Closes the onboarding tips screen as soon as it opens. |  |

</details>

<details>
<summary>📦 MX Player&nbsp;&nbsp;•&nbsp;&nbsp;11 patches</summary>
<br>

**🎯 Supported versions:**

| 3.3.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Boost Splash Screen Time](#boost-splash-screen-time) | Dont Apply These Patches Over Some Other Persons Mod, Use Untouched Apk. Disables MX Player's welcome/splash ad so playback starts immediately. |  |
| [Clean Me Tab](#clean-me-tab) | Adds Mod Settings switches, on by default, that hide promo rows, unused tiles, and Private Folder / File Transfer / Add to Playlist entries. |  |
| [Configure Smart Enhance](#configure-smart-enhance) | Removes the Smart Enhance disable toast and adds Mod Settings switches for the intro popup and the enable toast. |  |
| [Configure SpeedUp overlay](#configure-speedup-overlay) | Fixes the stock leftover-visible-view bug in the long-press SpeedUp overlay. "No UI" is toggled in Me tab > Mod Settings. |  |
| [Disable Bottom Bar And Add Me Tab To Top](#disable-bottom-bar-and-add-me-tab-to-top) | Adds a permanent Me tab button to the toolbar. Hiding the bottom bar itself is toggled live in Me tab > Mod Settings, not here - the Me tab button always stays wired, on purpose, since Mod Settings lives behind it and turning it off should never be able to lock you out of turning it back on. |  |
| [Kill Signature Verification](#kill-signature-verification) | Patches libmx-bh.so to kill signature verification. |  |
| [Remove Recycle Bin](#remove-recycle-bin) | Deleted files are always removed permanently, whenever this patch is applied - there's no safe way to make that half a runtime switch without the stock (unpatched) delete-dialog code to fall back to. The Me tab tile itself is a Mod Settings switch: off just brings the tile back, it doesn't restore recycling. |  |
| [Replace FFmpeg codec](#replace-ffmpeg-codec) | Replaces libffmpeg.mx.so in every lib/<abi>/ folder present in the APK with the matching file from a selected codec zip (EAC3 support). neon64 -> arm64-v8a, neon -> armeabi-v7a, x86 -> x86, x86_64 -> x86_64. ABI folders missing from the APK or from the zip are skipped. | • FFmpeg codec zip |
| [Sidebar & Player Defaults](#sidebar-player-defaults) | Cleans the player sidebar and More menu; sets default shortcuts and subtitle view. Configurable in Mod Settings, except the default shortcuts bitmask, still a Morphe option pending its Mod Settings move. | • Change default shortcuts<br>• Default shortcuts bitmask (hex) |
| [Smart Enhance Always On](#smart-enhance-always-on) | Applies Smart Enhance to every video at a level set in Mod Settings, and keeps it after lock/unlock. |  |
| [Smart Enhance Slider](#smart-enhance-slider) | Replaces the Smart Enhance toggle with a 0-100% popup slider and keeps the level after lock/unlock. |  |

</details>

<details>
<summary>📦 File Manager - XFolder&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 1.5.5.7 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Change Cleaner & Media Tab Defaults](#change-cleaner-media-tab-defaults) | Changes first-run defaults: hides the Image, Video, Audio and Cleaner tabs, and shows hidden files by default. Only affects users who have never changed the setting themselves - the preference override still takes priority. |  |
| [Disable Rate Us Dialog](#disable-rate-us-dialog) | Prevents the in-app rate-us dialog from ever being shown. |  |
| [Unlock Pro & Skip Splash Screen](#unlock-pro-skip-splash-screen) | Forces the ad-removed/Pro check to always return true, and renames the launcher activity from SplashActivity to MainActivity so the splash screen is skipped. |  |

</details>

<details>
<summary>📦 XPlayer - Video Player&nbsp;&nbsp;•&nbsp;&nbsp;15 patches</summary>
<br>

**🎯 Supported versions:**

| 2.9.2 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Clean 3 Dot Menu](#clean-3-dot-menu) | Removes Lock and Add to playlist from the video and folder 3-dot bottom sheets. Toggle in Mod Settings. |  |
| [Disable Rate Dialog](#disable-rate-dialog) | Disables the rate dialog counters so it never appears. |  |
| [Extra File Info](#extra-file-info) | Shows resolution, date, size and last played on video rows, and video count, size and path on folder rows. Toggle in Mod Settings. |  |
| [Hide Bottom Bar](#hide-bottom-bar) | Hides the bottom tab bar on the home screen. Toggle in Mod Settings. |  |
| [Hide Cast Button](#hide-cast-button) | Hides the cast button in the toolbar menus. Toggle it in Mod Settings. |  |
| [Hide Cast FF FB In Player](#hide-cast-ff-fb-in-player) | Hides the cast, custom and 10 second forward/backward buttons in the player. Toggle in Mod Settings. |  |
| [Hide Home Tiles](#hide-home-tiles) | Hides the tile row at the top of the home page (All Videos, Downloader, Privacy, Cleaner...). Toggle in Mod Settings. |  |
| [Hide Recent Videos](#hide-recent-videos) | Hides the recent videos row on the home page. Toggle in Mod Settings. |  |
| [Hide Widgets Menu Item](#hide-widgets-menu-item) | Removes Widgets from the home 3-dot menu. |  |
| [Mod Settings](#mod-settings) | Adds a Mod Settings entry below Settings in the home 3-dot menu. |  |
| [Player Side Cleaned](#player-side-cleaned) | Cleans the player side menu: audio, subtitle, cast, bookmark, favorite, play mode, brightness and volume. Applied while the menu inflates so remaining items stay aligned. Toggle in Mod Settings. |  |
| [Remove Tutorial](#remove-tutorial) | Removes the player tutorial. |  |
| [Skip Splash Screen](#skip-splash-screen) | Launches directly into the file explorer instead of the splash screen. |  |
| [Unlock Pro](#unlock-pro) | Unlocks all pro features. |  |
| [Volume Booster](#volume-booster) | Raises the volume boost limit in the player and background playback. Toggle in Mod Settings. |  |

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
| [Remove from default browser list](#remove-from-default-browser-list) | Strips the LinkHandleActivity alias's unscoped so the app stops appearing as a candidate in the system's default browser / "open with" chooser. |  |

</details>

<details>
<summary>📦 Xender&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 18.8.0.prime |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Clean main UI](#clean-main-ui) | Hides the bottom navigation bar, the top-right guide icon, and the Rate/Help/About drawer items, keeps the connect/create/join buttons on top, and stops them from being auto-hidden. |  |
| [Skip splash screen](#skip-splash-screen) | Jumps straight to the main activity from the splash screen, skipping the splash animation entirely. |  |
| [Speed up splash screen](#speed-up-splash-screen) | Enters the main activity directly after the splash screen. |  |

</details>

<details>
<summary>📦 RS File Manager&nbsp;&nbsp;•&nbsp;&nbsp;6 patches</summary>
<br>

**🎯 Supported versions:**

| 2.3.1.2 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Clean sidebar](#clean-sidebar) | Hides the Bookmarks, remote-connection and Category sections from the navigation sidebar, and hides Encrypt and Downloader from the Storage section. |  |
| [Disable downloader from download menu](#disable-downloader-from-download-menu) | Strips RsDownloadActivity's intent filters so it no longer offers itself as a handler in the system download/"complete action using" chooser. |  |
| [Disable rate us dialog](#disable-rate-us-dialog) | Overrides show() on the in-app "rate us" dialog so it's still built but never displayed. |  |
| [Hide more actions](#hide-more-actions) | Hides Hide, Add to desktop, Encrypt, Decrypt, Add bookmark, Web Search, Copy to, Move to, Transfer, and Playing from the "More actions" menu. |  |
| [Hide network, tools and bookmarks on home page](#hide-network-tools-and-bookmarks-on-home-page) | Hides the Network, Tools and Bookmarks sections from the home page section list. |  |
| [Skip splash screen](#skip-splash-screen) | Skips Splash Screen From 2nd App Opening |  |

</details>

<details>
<summary>📦 MiXplorer&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable From Download Menu Of Browsers](#disable-from-download-menu-of-browsers) | Removes only the http/https <data> entries from MiXplorer's Explore/Download/Copy to/Extract to shell activities' VIEW intent filters, so the app stops showing up multiple times in browsers download link chooser. |  |

</details>

<details>
<summary>📦 Video Downloader&nbsp;&nbsp;•&nbsp;&nbsp;6 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable downloader from download menu](#disable-downloader-from-download-menu) | Removes the http/https <data> entries from BrowserDownloaderActivity's so the app stops offering itself in the system "Download file with" chooser for ordinary web downloads. |  |
| [Popup blocker](#popup-blocker) | Blocks popups and popunder redirects with Allow / Block / Always Block prompts. Adds "Manage popup rules" as the last item of the 3-dot menu. |  |
| [Remove from default browser list](#remove-from-default-browser-list) | Removes http/https <data> entries from MainTabsActivity's so the app stops appearing as a candidate in the system's default browser / "open with" chooser. |  |
| [Skip splash screen](#skip-splash-screen) | Skips splash screen so the app opens directly to the main screen. |  |
| [Unlock Pro](#unlock-pro) | Signature verification is spoofed automatically if this patch is selected the purchase check passes. |  |
| [Use Your Own Host File For Stronger AdBlock](#use-your-own-host-file-for-stronger-adblock) | Replaces res/raw/hosts.txt with a text/host file you select. | • Host file |

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
| [Hide File Transfer, Video Playlist, Private Folder tiles](#hide-file-transfer-video-playlist-private-folder-tiles) | Removes the File Transfer, Video Playlist, and Private Folder tiles from settings Page. | • Hide File Transfer<br>• Hide Private Folder<br>• Hide Video Playlists |
| [Hide Settings Page UseLess Buttons](#hide-settings-page-useless-buttons) | Collapses the WhatsApp, Legal, and Help entries on the Me tab. | • Hide WhatsApp<br>• Hide Legal<br>• Hide Help |
| [Hide top tiles](#hide-top-tiles) | Hides the top tiles that appears in top of video folders in homescreen |  |
| [Skip Splash Screen](#skip-splash-screen) | Skips Splash Screen so the app boots straight past the splash and update screen. |  |

</details>

<details>
<summary>📦 Video Player&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 1.4 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Hide bottom bar](#hide-bottom-bar) | Hides the bottom navigation bar. |  |
| [Skip splash screen](#skip-splash-screen) | Launches the app directly into the main screen. |  |

</details>

<details>
<summary>📦 Ampere&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Material Colors Upgrade Peach And Purple](#material-colors-upgrade-peach-and-purple) | Updates Accent/Primary/CardBgDark to the newer Material color palette. |  |

</details>

<details>
<summary>📦 Firefox Nightly&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 159.0a1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Old style 3 dot menu](#old-style-3-dot-menu) | Adds "Mod Settings" to the 3 dot menu: switch between the stock bottom sheet menu and the old style popup menu, and pin extensions to the search bar. |  |
| [Remember tab scroll position](#remember-tab-scroll-position) | Keeps the tab tray scroll position when it is closed and reopened. |  |

</details>

<details>
<summary>📦 WPSApp Pro&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.7.05 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Remove License Verification](#remove-license-verification) | Spoofs every PackageManager.getInstallerPackageName() check to report "com.android.vending", so Pro features unlock regardless of install source. |  |

</details>

<details>
<summary>📦 Calendar&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 1.0.34 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Skip/Boost splash screen](#skip-boost-splash-screen) | By default, skips the splash screen entirely on launch. Turn on "Boost splash" to instead keep it briefly on screen (configurable delay) Useless To Boost Splash Screen If You Select Unlock Premium Patch Too Use If You Want To Skip Splash Screen Entirely | • Boost splash instead of skipping<br>• Boost delay (ms) |
| [Unlock premium](#unlock-premium) | Unlocks premium features and removes ads. |  |

</details>

<details>
<summary>📦 ImpoStack: PDF Imposition&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.4.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Forces SecurityTracker.isPremium() to always return true, so every feature gated by the "st_01" premium flag stays unlocked. |  |

</details>

<details>
<summary>📦 Bangla Dictionary&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 11.5.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Forces the purchase check to always return true, unlocking Pro. |  |

</details>

<details>
<summary>📦 Video to Photo&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 5.0.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks Pro features. |  |

</details>

<details>
<summary>📦 WIFI WPS WPA TESTER&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| rc-5.164 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Forces the Pro flag true at every read and write site in the dex, unlocking Pro. |  |

</details>

<details>
<summary>📦 WiFi Analyzer&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 9.4 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro & Disable Rate Dialog](#unlock-pro-disable-rate-dialog) | Forces the premium check to always return true, unlocking every Pro feature, and stops the in-app "rate us" dialog from ever triggering. |  |

</details>

<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;15 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [APK Junk Cleanup](#apk-junk-cleanup) | Removes junk and useless files with no runtime purpose inside apk. Native libraries are left untouched unless the 'Strip native architectures' toggle is enabled. | • Strip native architectures<br>• Architecture to keep |
| [Add Save Data](#add-save-data) | Unpacks bundled save-data zip(s) into the app's storage on first launch. | • 1. Select data/data zip<br>• 2. Select Android/data zip<br>• 3. Select Android/obb zip<br>• Add toast<br>• Toast message<br>• Show toast once |
| [Add Toast](#add-toast) | Shows a custom toast message when the app starts. Works on any app. | • Toast message<br>• Show once |
| [Change Display Size](#change-display-size) | Change any app's display size without touching your phone's system settings. Make it bigger if things look too small, or smaller to fit more on screen. You Need To Configure 100(No Change), 90(10% Smaller App Ui), 110(10% Bigger App Ui). | • Display scale |
| [Png Optimizer](#png-optimizer) | Quantizes PNG images to a 64-color palette (matching pngquant) and re-picks the optimal per-row filter before recompressing at maximum zlib compression (matching optipng), stripping hidden metadata along the way. |  |
| [Remove Ads](#remove-ads) | Cleans Apps Code From Ads Junk. Works In Most Apps Where There Isn't Any Check For Ads Loaded Or Not. If There Is A Check You Will Be Stuck In SplashActivity Due To Custom Ads Load Checks But It Is Superior. |  |
| [Remove Ads Lite (Adobo)](#remove-ads-lite-adobo) | Based On (Adobo's Block Ads+Mobile Ads) Use When Remove Ads Patch Caused Problem. It Is Weaker But Effective, No Need To Select A Host File Or Configure Anything. In Future It May Replace Remove Ads Patch If I Find No Problems. | • Redirection IP<br>• Additional hosts file (optional) |
| [Remove Ads Ultra Lite](#remove-ads-ultra-lite) | Call finish on ad activities. Use Where Remove Ads And Remove Ads Lite (Adobo) Caused Problem. Its In Very Early Stage So Test And Provide FeedBack If You Still See Ads In Some App. |  |
| [Remove Analytics](#remove-analytics) | Disables tracking and crash-reporting tools, corrupts analytics web links inside the code, and removes background tracking services. |  |
| [Remove Analytics New](#remove-analytics-new) | DONT CHOOSE 2 SAME PATCHES...Corrupts analytics network URLs and split-scheme string obfuscation inside the code, and strips matching manifest components. |  |
| [Remove Debug Info](#remove-debug-info) | Removes debug information (line numbers, variable names, source file references) from every class in the .dex files to reduce overall APK size. |  |
| [Remove Duplicate Graphics](#remove-duplicate-graphics) | Keeps only one screen-density copy of every duplicated drawable/mipmap file, Mipmaps follow this same order. Optionally strips device-specific resources (smartwatch, Android TV, etc.) entirely. | • Target density<br>• Remove smartwatch (Wear OS) resources<br>• Remove Android TV resources<br>• Remove other device-specific resources (car, desk dock, VR headset) |
| [Remove Languages](#remove-languages) | Removes translations for languages you don't use, in EVERY resource package of resources.arsc.  | • Languages to keep |
| [Skip Splash Screen - Expert Only](#skip-splash-screen-expert-only) | EXPERT USERS ONLY. Manually Configure It To Point At Real Splash And Main Activity As Many Apps Use Other Names. Check the log to know what the patch is doing. Ensure App Doesnt Ask For Permissions In Splash Screen. | • Splash activity name<br>• Real main activity name |
| [Spoof App Signature](#spoof-app-signature) | Makes the app think its signing certificate is unchanged after Morphe re-signs it, useful when an app crashes or shows a tamper warning because it checks its own certificate. Does not bypass Play Integrity / SafetyNet hardware attestation. Apply with Original app certificate patch. | • Package name override (optional) |

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
