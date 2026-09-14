package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.stringOption
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("ApkCleanupPatch")

private const val DEFAULT_ABI = "arm64-v8a"
private val KNOWN_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

private val PROTECTED_PATTERNS = listOf(
    Regex(""".*META-INF/MANIFEST\.MF$"""),
    Regex(""".*META-INF/services/.*"""),
    Regex(""".*META-INF/.*\.(RSA|SF|DSA|EC)$"""),
    Regex("""^(root/)?classes\d*\.dex$"""),
    Regex(""".*resources\.arsc$"""),
    Regex(""".*AndroidManifest\.xml$"""),
)

private val JUNK_PATTERNS = listOf(
    Regex(""".*play-services-.*\.properties$"""),
    Regex(""".*firebase-.*\.properties$"""),
    Regex(""".*app-update\.properties$"""),
    Regex(""".*billing\.properties$"""),
    Regex(""".*billing-ktx\.properties$"""),
    Regex(""".*review\.properties$"""),
    Regex(""".*hsdp\.properties$"""),
    Regex(""".*core-common\.properties$"""),
    Regex(""".*user-messaging-platform\.properties$"""),
    Regex(""".*feature-delivery.*\.properties$"""),
    Regex(""".*ads-mobile-sdk\.properties$"""),
    Regex(""".*\.proto$"""),
    Regex(""".*DebugProbesKt\.bin$"""),
    Regex(""".*\.version$"""),
    Regex(""".*_VERSION$"""),
    Regex(""".*androidsupportmultidexversion\.txt$"""),
    Regex(""".*stamp-cert-sha256$"""),
    Regex(""".*version-control-info\.textproto$"""),
    Regex(""".*kotlin-tooling-metadata\.json$"""),
    Regex(""".*META-INF/CHANGES$"""),
    Regex(""".*META-INF/README\.md$"""),
    Regex(""".*META-INF/NOTICE.*"""),
    Regex(""".*META-INF/LICENSE.*"""),
    Regex(""".*(?:^|/)LICENSES$"""),
    Regex(""".*ion-java\.properties$"""),
    Regex(""".*THIRD-PARTY-NOTICES\.txt$"""),
    Regex(""".*licenses\.md$"""),
    Regex(""".*debug\.keystore$"""),
    Regex(""".*_trackers\.xml$"""),
    Regex(""".*version\.properties$"""),
    Regex(""".*integrity\.properties$"""),
    Regex(""".*androidannotations-api\.properties$"""),
    Regex(""".*transport-.*\.properties$"""),
    Regex(""".*jetty-dir\.css$"""),
    // ART baseline profiles (also catches APKs that ship them outside assets/dexopt/)
    Regex(""".*(?:^|/)baseline\.profm?$"""),
    // MX Player ad-webview templates / notices at assets root (exact names)
    Regex("""^assets/(?:privacy_notice|index|image_interstitial|fyb_static_endcard_tmpl|fyb_iframe_endcard_tmpl)\.html$"""),
    // libphonenumber per-region proto blobs in assets/data/: EVERY match goes, seen or
    // unseen (approved regex). Covers all families found so far; CarrierSpecificsProto
    // is pre-covered as the only remaining family libphonenumber ships (no-op if absent).
    Regex("""^assets/data/(?:ShortNumberMetadataProto|PhoneNumberMetadataProto|PhoneNumberAlternateFormatsProto|CarrierSpecificsProto).*$"""),
    // Optional: catch future GTM container ids too (uncomment if you ever want that):
    // Regex("""^assets/containers/GTM-.*\.json$"""),
    // Optional extra ad-stack files at assets root (still NOT selected by you):
    // Regex("""^assets/(?:aps_mobile_client_config|customConfiguration)\.json$"""),
    // Regex("""^assets/customConfiguration$"""),
    // Regex("""^assets/(?:mini|hbde)\.db$"""),
)

// Exact file entries audited from screenshots. A future file added beside these survives.
private val JUNK_ENTRIES = setOf(
    // assets/PlayReady/ — 14/14 files shown
    "assets/PlayReady/zprivsig.dat",
    "assets/PlayReady/zprivencr.dat",
    "assets/PlayReady/zgpriv.dat",
    "assets/PlayReady/unsignedtemplate.dat",
    "assets/PlayReady/priv.dat",
    "assets/PlayReady/prinit.dat",
    "assets/PlayReady/ndrpriv.dat",
    "assets/PlayReady/ndrgpriv.dat",
    "assets/PlayReady/ndrcerttemplate.dat",
    "assets/PlayReady/devcerttemplate.dat",
    "assets/PlayReady/devcert.dat",
    "assets/PlayReady/bgroupcert.dat",
    "assets/PlayReady/bDomainCertSecL0.dat",
    "assets/PlayReady/bdevcert.dat",
    // assets/META-INF/ — file entry shown
    "assets/META-INF/\$catalog.json",
    // assets/json/ — 1/1 shown
    "assets/json/sampleResponse.json",
    // assets/fatafat/ — 2/2 shown
    "assets/fatafat/metadata.json",
    "assets/fatafat/bundled.zip",
    // assets/containers/ — 1/1 shown
    "assets/containers/GTM-KZ83HD3.json",
    // assets/ root ad-stack files (selected in your screenshot)
    "assets/omsdk-v1.js",
    "assets/ia_mraid_bridge.txt",
    "assets/ia_js_load_monitor.txt",
    "assets/dtb-m.js",
    "assets/aps-mraid.js",
    "assets/api_key.txt",
)

// Folder entries shown as junk in screenshots: the folder itself + its interior go.
// New siblings added next to them in future builds are NOT touched.
private val JUNK_TREES = listOf(
    "assets/META-INF/proguard",
    "assets/composeResources/mxmediaadslib.vidadlibrary.generated.resources",
    "assets/com/appsflyer",
    "assets/com.amazon.avod.watchlist.room.ModifyWatchlistDatabase",
    "assets/com.amazon.avod.watchlist.offline.ModifyWatchlistDatabase",
    "assets/com.amazon.avod.search.room.LocalSearchQueryDatabase",
    "assets/com.amazon.avod.cache.room.ResponseCacheDatabase",
)

// Directories whose ENTIRE content gets deleted, no matter what's inside.
private val JUNK_DIRECTORY_PREFIXES = listOf(
    "assets/dexopt/",
    "com/clevertap/",
    "org/jacoco/",
    "org/joda/",
    "services/",
)

// "res/" stays excluded: deleting raw resource files without updating resources.arsc breaks the app.
private val EXCLUDED_PREFIXES = listOf("res/")

val apkCleanupPatch = rawResourcePatch(
    name = "APK Junk Cleanup",
    description = "Removes junk and useless files with no runtime purpose inside apk. " +
        "Native libraries are left untouched unless the " +
        "'Strip native architectures' toggle is enabled.",
    default = false,
) {
    // Master on/off switch for ABI stripping. OFF by default, so merely selecting
    // this patch never touches lib/.
    val stripLibs by booleanOption(
        "stripLibs",
        default = false,
        title = "Strip native architectures",
        description = "OFF (default): libs are left completely untouched. " +
            "ON: deletes libs of every architecture except the one selected below.",
    )

    // Only consulted when stripLibs is ON.
    // NOTE: Morphe Manager renders the map KEYS as dropdown labels and applies the
    // map VALUES as the option value (OptionValuesKt.applyPatchOptions), so the
    // human-readable text goes on the left and the real value on the right.
    val keepAbi by stringOption(
        "keepAbi",
        default = DEFAULT_ABI,
        values = mapOf(
            "Keep only arm64-v8a (most phones)" to "arm64-v8a",
            "Keep only armeabi-v7a (old 32-bit phones)" to "armeabi-v7a",
            "Keep only x86_64 (emulators)" to "x86_64",
            "Keep only x86 (old emulators)" to "x86",
        ),
        title = "Architecture to keep",
        description = "Used only when 'Strip native architectures' is ON. " +
            "Deletes libs of every other architecture from the patched APK. " +
            "Requires patcher v1.13.0+ (delete() on unstaged entries).",
        validator = { it == null || it in KNOWN_ABIS },
    )

    execute {
        var removedFiles = 0
        var removedAbiTrees = 0

        fun isProtected(relativePath: String) = PROTECTED_PATTERNS.any { it.matches(relativePath) }

        fun inTree(entryName: String, tree: String) =
            entryName == tree || entryName.startsWith("$tree/")

        // Patcher >= 1.13.0: delete() records the entry for exclusion from the output APK
        // even when it was never staged (libs, root entries), and accepts directory names
        // to drop everything below them (morphe-patcher#199).
        fun deleteEntry(entryName: String) {
            if (isProtected(entryName)) return
            try {
                delete(entryName)
                removedFiles++
                logger.fine("Removed entry: $entryName")
            } catch (e: Exception) {
                logger.warning("APK Cleanup: failed to delete $entryName: ${e.message}")
            }
        }

        listApkEntries().forEach { entryName ->
            // Native libs are handled exclusively by the stripLibs option below.
            if (entryName.startsWith("lib/")) return@forEach
            if (EXCLUDED_PREFIXES.any { entryName.startsWith(it) }) return@forEach

            val shouldDelete = when {
                JUNK_PATTERNS.any { it.matches(entryName) } -> true
                entryName in JUNK_ENTRIES -> true
                JUNK_TREES.any { inTree(entryName, it) } -> true
                JUNK_DIRECTORY_PREFIXES.any { entryName.startsWith(it) } -> true
                entryName == "kotlin" || entryName.startsWith("kotlin/") -> true
                entryName == "assets/audience_network.dex" ||
                    entryName.startsWith("assets/audience_network/") -> true
                // Signatures / MANIFEST.MF / services are still guarded by PROTECTED_PATTERNS
                entryName.startsWith("META-INF/") -> true
                else -> false
            }

            if (shouldDelete) deleteEntry(entryName)
        }

        val shippedAbis = listApkEntries("lib/")
            .mapNotNull { it.split("/").getOrNull(1) }
            .distinct()

        if (stripLibs != true) {
            if (shippedAbis.isNotEmpty()) {
                logger.info(
                    "APK Cleanup: ABI stripping is OFF; keeping all ABIs: ${shippedAbis.joinToString()}"
                )
            }
        } else {
            val chosenAbi = keepAbi ?: DEFAULT_ABI
            when {
                shippedAbis.isEmpty() ->
                    logger.info("APK Cleanup: no native libraries in this APK, nothing to strip")
                chosenAbi !in shippedAbis ->
                    logger.warning(
                        "APK Cleanup: lib/$chosenAbi/ is not shipped by this APK " +
                            "(has: ${shippedAbis.joinToString()}); ABI stripping skipped"
                    )
                else -> {
                    val droppedAbis = shippedAbis - chosenAbi
                    droppedAbis.forEach { abi ->
                        // Directory name deletes everything below it (morphe-patcher#199).
                        delete("lib/$abi/")
                        removedAbiTrees++
                    }
                    logger.info(
                        "APK Cleanup: kept lib/$chosenAbi/, removed: " +
                            droppedAbis.joinToString(", ") { "lib/$it/" }
                    )
                }
            }
        }

        val manifestFile = get("AndroidManifest.xml")
        val apkRoot = manifestFile.parentFile ?: File(".")

        apkRoot.walkBottomUp()
            .filter { it.isDirectory && it != apkRoot && it.listFiles()?.isEmpty() == true }
            .forEach { it.delete() }

        logger.info("APK Cleanup: removed $removedFiles junk entries + $removedAbiTrees ABI tree(s)")
    }
}
