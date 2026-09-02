package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.stringOption
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

private val EXCLUDED_PREFIXES = listOf("assets/", "res/")

val apkCleanupPatch = rawResourcePatch(
    name = "APK Junk Cleanup",
    description = "Removes junk and useless files with no runtime purpose inside apk.",
    default = false,
) {
    val splitByArch by booleanOption(
        key = "splitByArch",
        default = false,
        title = "Keep Only One Architecture",
        description = "Keep native libraries (.so files) for only one CPU architecture. To generate separate APKs for each architecture, run this patch multiple times with a different architecture selected each time.",
    )

    val targetArch by stringOption(
        key = "targetArch",
        default = "arm64-v8a",
        values = mapOf(
            "arm64-v8a" to "ARM64 (arm64-v8a)",
            "armeabi-v7a" to "ARMv7 (armeabi-v7a)",
            "x86" to "x86",
            "x86_64" to "x86_64",
        ),
        title = "Target architecture",
        description = "Which architecture to keep when splitting is enabled.",
    )

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

        // Process all entries in the APK directly from the archive
        listApkEntries().forEach { entryName ->
            if (EXCLUDED_PREFIXES.any { entryName.startsWith(it) }) return@forEach
            
            var shouldDelete = false
            
            if (JUNK_PATTERNS.any { it.matches(entryName) }) {
                shouldDelete = true
            } else if (entryName.startsWith("kotlin/") || entryName == "kotlin") {
                shouldDelete = true
            } else if (entryName == "assets/audience_network.dex" || entryName.startsWith("assets/audience_network/") || entryName == "assets/audience_network") {
                shouldDelete = true
            } else if (entryName.startsWith("META-INF/") && !entryName.startsWith("META-INF/services/")) {
                shouldDelete = true
            }

            if (shouldDelete) {
                deleteEntry(entryName)
            }
        }

        if (splitByArch == true) {
            val archToKeep = targetArch ?: "arm64-v8a"
            val libEntries = listApkEntries("lib/")
            
            // Extract architecture names directly from the archive listing
            val archNames = libEntries.mapNotNull { 
                val parts = it.split("/")
                if (parts.size >= 2) parts[1] else null
            }.distinct()
            
            val hasTarget = archNames.contains(archToKeep)

            if (hasTarget) {
                libEntries.forEach { entryName ->
                    val parts = entryName.split("/")
                    if (parts.size >= 2) {
                        val arch = parts[1]
                        if (arch != archToKeep) {
                            deleteEntry(entryName)
                        }
                    }
                }
            } else {
                logger.warning(
                    "APK Cleanup: selected architecture \"$archToKeep\" not found in lib/. " +
                    "Available: ${archNames.joinToString()}. Keeping all architectures."
                )
            }
        }

        // Clean up empty directories left behind in the working directory
        val manifestFile = get("AndroidManifest.xml")
        val apkRoot = manifestFile.parentFile ?: File(".")
        
        apkRoot.walkBottomUp()
            .filter { it.isDirectory && it != apkRoot && it.listFiles()?.isEmpty() == true }
            .forEach { it.delete() }

        logger.info("APK Cleanup: removed $removedFiles files, freed ${freedBytes / 1024}KB")
    }
}
