package app.ftl.patches.cleanup

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.stringOption
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("ApkCleanupPatch")

// Files that are build artifacts, metadata, or debug probes.
// They bloat the APK but serve no purpose at runtime.
// All patterns match the FULL relative path, so they need .* prefix
// to catch files inside subdirectories like META-INF/.
private val JUNK_PATTERNS = listOf(
    // Kotlin metadata
    Regex(""".*\.kotlin_module$"""),
    Regex(""".*\.kotlin_builtins$"""),
    Regex(""".*kotlin-tooling-metadata\.json$"""),
    Regex(""".*DebugProbesKt\.bin$"""),

    // Version & build metadata
    Regex(""".*\.version$"""),
    Regex(""".*androidsupportmultidexversion\.txt$"""),
    Regex(""".*stamp-cert-sha256$"""),
    Regex(""".*version-control-info\.textproto$"""),
    Regex(""".*app-update\.properties$"""),
    Regex(""".*billing\.properties$"""),
    Regex(""".*hsdp\.properties$"""),
    Regex(""".*core-common\.properties$"""),
    Regex(""".*user-messaging-platform\.properties$"""),

    // Play Services / Firebase version metadata
    Regex(""".*play-services-.*\.properties$"""),
    Regex(""".*firebase-.*\.properties$"""),

    // Protobuf descriptors (reflection data, not needed at runtime)
    Regex(""".*\.proto$"""),

    // Misc META-INF clutter
    Regex(""".*META-INF/CHANGES$"""),
    Regex(""".*META-INF/README\.md$"""),
)

val apkCleanupPatch = rawResourcePatch(
    name = "APK Junk Cleanup",
    description = "Strips build artifacts and metadata (Kotlin modules, version files, protobuf descriptors, library properties) that bloat the APK but are unused at runtime. Optionally keeps native libraries for only one CPU architecture.",
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
        val manifestFile = get("AndroidManifest.xml", false)
        val apkRoot = manifestFile.parentFile ?: File(".")

        var removedFiles = 0
        var freedBytes = 0L

        // --- 1. Remove junk files ---
        apkRoot.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(apkRoot).path.replace("\\", "/")

                if (JUNK_PATTERNS.any { it.matches(relativePath) }) {
                    val size = file.length()
                    if (file.delete()) {
                        removedFiles++
                        freedBytes += size
                        logger.fine("Removed: $relativePath (${size}B)")
                    } else {
                        logger.warning("Failed to delete: $relativePath")
                    }
                }
            }

        // Clean up empty directories left behind
        apkRoot.walkBottomUp()
            .filter { it.isDirectory && it != apkRoot && it.listFiles()?.isEmpty() == true }
            .forEach { it.delete() }

        // --- 2. Architecture split (only if toggle is on) ---
        if (splitByArch == true) {
            val archToKeep = targetArch ?: "arm64-v8a"
            val libDir = get("lib", false)

            if (libDir.isDirectory) {
                val archDirs = libDir.listFiles { f -> f.isDirectory }?.toList() ?: emptyList()
                val hasTarget = archDirs.any { it.name == archToKeep }

                if (hasTarget) {
                    archDirs.filter { it.name != archToKeep }.forEach { archDir ->
                        val files = archDir.walkTopDown().filter { it.isFile }.toList()
                        val size = files.sumOf { it.length() }
                        val count = files.size

                        archDir.deleteRecursively()
                        removedFiles += count
                        freedBytes += size
                    }

                    if (libDir.listFiles()?.isEmpty() == true) {
                        libDir.delete()
                    }
                } else {
                    logger.warning(
                        "APK Cleanup: selected architecture \"$archToKeep\" not found in lib/. " +
                        "Available: ${archDirs.joinToString { it.name }}. Keeping all architectures."
                    )
                }
            }
        }

        if (removedFiles > 0) {
            logger.info("APK Cleanup: removed $removedFiles items, freed ${freedBytes / 1024}KB")
        } else {
            logger.info("APK Cleanup: nothing to remove")
        }
    }
}
