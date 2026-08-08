package app.ftl.patches.cleanup

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.stringOption
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("ApkCleanupPatch")

private val PROTECTED_PATTERNS = listOf(
    Regex(""".*META-INF/MANIFEST\.MF$"""),
    Regex(""".*META-INF/services/.*"""),
    Regex(""".*META-INF/.*\.(RSA|SF|DSA|EC)$"""),
    Regex(""".*classes\d*\.dex$"""),
    Regex(""".*resources\.arsc$"""),
    Regex(""".*AndroidManifest\.xml$"""),
)

private val JUNK_PATTERNS = listOf(
    Regex(""".*play-services-.*\.properties$"""),
    Regex(""".*firebase-.*\.properties$"""),
    Regex(""".*app-update\.properties$"""),
    Regex(""".*billing\.properties$"""),
    Regex(""".*hsdp\.properties$"""),
    Regex(""".*core-common\.properties$"""),
    Regex(""".*user-messaging-platform\.properties$"""),
    Regex(""".*\.proto$"""),
    Regex(""".*DebugProbesKt\.bin$"""),
    Regex(""".*\.version$"""),
    Regex(""".*androidsupportmultidexversion\.txt$"""),
    Regex(""".*stamp-cert-sha256$"""),
    Regex(""".*version-control-info\.textproto$"""),
    Regex(""".*META-INF/CHANGES$"""),
    Regex(""".*META-INF/README\.md$"""),
    Regex(""".*META-INF/NOTICE.*"""),
    Regex(""".*META-INF/LICENSE.*"""),
)

private val EXCLUDED_PREFIXES = listOf("assets/", "res/")

val apkCleanupPatch = resourcePatch(
    name = "APK Junk Cleanup",
    description = "Removes build artifacts and metadata that bloat the APK: Play Services / Firebase version files, protobuf descriptors, debug probes, kotlin builtins, META-INF subfolder clutter, and misc junk. Safe — only removes files with no runtime purpose.",
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
        val manifestFile = get("AndroidManifest.xml")
        val apkRoot = manifestFile.parentFile ?: File(".")

        var removedFiles = 0
        var freedBytes = 0L

        fun isProtected(relativePath: String) = PROTECTED_PATTERNS.any { it.matches(relativePath) }

        fun removeTree(path: String) {
            val entry = get(path)
            if (entry.isDirectory) {
                val children = entry.list()
                val preview = children?.take(5)?.joinToString()
                logger.info("APK Cleanup: $path/ -> ${children?.size ?: -1} entries (e.g. $preview)")
                children?.forEach { child -> removeTree("$path/$child") }
            } else if (entry.isFile) {
                if (isProtected(path)) return
                val size = entry.length()
                if (entry.delete()) {
                    removedFiles++
                    freedBytes += size
                    logger.fine("Removed: $path (${size}B)")
                } else {
                    logger.warning("APK Cleanup: failed to delete $path")
                }
            } else {
                logger.info("APK Cleanup: $path -> neither file nor directory")
            }
        }

        apkRoot.walkTopDown()
            .filter { it.isFile }
            .toList()
            .forEach { file ->
                val relativePath = file.relativeTo(apkRoot).path.replace("\\", "/")

                if (isProtected(relativePath)) return@forEach
                if (EXCLUDED_PREFIXES.any { relativePath.startsWith(it) }) return@forEach

                if (JUNK_PATTERNS.any { it.matches(relativePath) }) {
                    val size = file.length()
                    if (file.delete()) {
                        removedFiles++
                        freedBytes += size
                        logger.fine("Removed file: $relativePath (${size}B)")
                    }
                }
            }

        try {
            removeTree("kotlin")
        } catch (e: Exception) {
            logger.severe("APK Cleanup: failed removing kotlin/ folder: ${e.message}")
        }

        try {
            val metaInf = get("META-INF")
            if (metaInf.isDirectory) {
                metaInf.list()?.forEach { name ->
                    if (name.lowercase() == "services") return@forEach
                    try {
                        removeTree("META-INF/$name")
                    } catch (e: Exception) {
                        logger.severe("APK Cleanup: failed removing META-INF/$name/: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.severe("APK Cleanup: failed scanning META-INF/: ${e.message}")
        }

        apkRoot.walkBottomUp()
            .filter { it.isDirectory && it != apkRoot && it.listFiles()?.isEmpty() == true }
            .forEach { it.delete() }

        if (splitByArch == true) {
            val archToKeep = targetArch ?: "arm64-v8a"
            val libDir = get("lib")

            if (libDir.isDirectory) {
                val archNames = libDir.list()?.toList() ?: emptyList()
                val hasTarget = archNames.contains(archToKeep)

                if (hasTarget) {
                    archNames.filter { it != archToKeep }.forEach { arch ->
                        try {
                            removeTree("lib/$arch")
                        } catch (e: Exception) {
                            logger.severe("APK Cleanup: failed removing lib/$arch/: ${e.message}")
                        }
                    }
                } else {
                    logger.warning(
                        "APK Cleanup: selected architecture \"$archToKeep\" not found in lib/. " +
                        "Available: ${archNames.joinToString()}. Keeping all architectures."
                    )
                }
            }
        }

        logger.info("APK Cleanup: removed $removedFiles files, freed ${freedBytes / 1024}KB")
    }
}
