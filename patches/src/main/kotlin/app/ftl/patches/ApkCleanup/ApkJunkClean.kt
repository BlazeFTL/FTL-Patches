package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.rawResourcePatch
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("ApkCleanupPatch")

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
)

// Note: "assets/" was removed so we can target specific ad SDKs inside assets/.
// "res/" remains excluded because deleting raw resource files can break resources.arsc.
private val EXCLUDED_PREFIXES = listOf("res/")

val apkCleanupPatch = rawResourcePatch(
    name = "APK Junk Cleanup",
    description = "Removes junk and useless files with no runtime purpose inside apk. " +
        "To keep only one CPU architecture, use the patcher's strip-libs option " +
        "(Morphe Manager) or --striplibs (Morphe Desktop).",
    default = false,
) {
    execute {
        var removedFiles = 0
        var freedBytes = 0L

        fun isProtected(relativePath: String) = PROTECTED_PATTERNS.any { it.matches(relativePath) }

        fun deleteEntry(entryName: String) {
            if (isProtected(entryName)) return
            try {
                val file = get(entryName)
                if (file.isFile) {
                    val size = file.length()
                    if (file.delete()) {
                        removedFiles++
                        freedBytes += size
                        logger.fine("Removed file: $entryName (${size}B)")
                    } else {
                        logger.warning("APK Cleanup: failed to delete $entryName")
                    }
                }
            } catch (e: Exception) {
                logger.warning("APK Cleanup: failed to access $entryName: ${e.message}")
            }
        }

        listApkEntries().forEach { entryName ->
            // Native libraries are never staged (PR #192). Deleting them silently fails
            // because the encoder restores them from the original APK.
            if (entryName.startsWith("lib/")) return@forEach
            
            if (EXCLUDED_PREFIXES.any { entryName.startsWith(it) }) return@forEach

            val shouldDelete = when {
                JUNK_PATTERNS.any { it.matches(entryName) } -> true
                entryName == "kotlin" || entryName.startsWith("kotlin/") -> true
                
                // Explicitly target audience network and other ad SDKs in assets
                entryName == "assets/audience_network.dex" ||
                    entryName.startsWith("assets/audience_network/") -> true
                // Add other specific asset folders here if needed:
                // entryName.startsWith("assets/some_other_ad_sdk/") -> true
                
                // PROTECTED_PATTERNS already guards signatures, MANIFEST.MF, and services/
                entryName.startsWith("META-INF/") -> true
                
                else -> false
            }

            if (shouldDelete) deleteEntry(entryName)
        }

        val shippedAbis = listApkEntries("lib/")
            .mapNotNull { it.split("/").getOrNull(1) }
            .distinct()
            
        if (shippedAbis.isNotEmpty()) {
            logger.info(
                "APK Cleanup: Detected native ABIs: ${shippedAbis.joinToString()}. " +
                "To strip unused architectures, enable 'Strip unused architectures' in Morphe Manager " +
                "or use '--striplibs' in Morphe Desktop."
            )
        }

        val manifestFile = get("AndroidManifest.xml")
        val apkRoot = manifestFile.parentFile ?: File(".")

        apkRoot.walkBottomUp()
            .filter { it.isDirectory && it != apkRoot && it.listFiles()?.isEmpty() == true }
            .forEach { it.delete() }

        logger.info("APK Cleanup: removed $removedFiles files, freed ${freedBytes / 1024}KB")
    }
}
