package app.ftl.patches.cleanup

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.stringOption
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("ApkCleanupPatch")

// === NEVER REMOVE THESE ===
private val PROTECTED_PATTERNS = listOf(
    Regex(""".*META-INF/MANIFEST\.MF$"""),
    Regex(""".*META-INF/services/.*"""),
    Regex(""".*META-INF/.*\.(RSA|SF|DSA|EC)$"""),
    Regex(""".*classes\d*\.dex$"""),
    Regex(""".*resources\.arsc$"""),
    Regex(""".*AndroidManifest\.xml$"""),
)

// === SAFE JUNK ===
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

val apkCleanupPatch = rawResourcePatch(
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
        // In rawResourcePatch, CWD is the decoded APK root.
        // Do NOT use get().parentFile — it may point to a temp copy.
        val apkRoot = File(".").absoluteFile.normalize()
        logger.info("APK root: ${apkRoot.path}")

        var removedFiles = 0
        var removedDirs = 0
        var freedBytes = 0L

        // --- 1. Remove junk files ---
        apkRoot.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(apkRoot).path.replace("\\", "/")

                if (PROTECTED_PATTERNS.any { it.matches(relativePath) }) {
                    return@forEach
                }

                if (JUNK_PATTERNS.any { it.matches(relativePath) }) {
                    val size = file.length()
                    if (file.delete()) {
                        removedFiles++
                        freedBytes += size
                        logger.fine("Removed file: $relativePath (${size}B)")
                    }
                }
            }

        // --- 2. Remove kotlin/ folder ---
        val kotlinDir = File(apkRoot, "kotlin")
        if (kotlinDir.isDirectory) {
            val size = kotlinDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            kotlinDir.deleteRecursively()
            removedDirs++
            freedBytes += size
            logger.info("Removed kotlin/ folder (${size / 1024}KB)")
        } else {
            logger.fine("kotlin/ folder not found at ${kotlinDir.path}")
        }

        // --- 3. Remove useless META-INF subfolders (keep services/ and signatures) ---
        val metaInfDir = File(apkRoot, "META-INF")
        if (metaInfDir.isDirectory) {
            metaInfDir.listFiles()?.forEach { entry ->
                if (!entry.isDirectory) return@forEach

                val name = entry.name.lowercase()
                if (name == "services") return@forEach

                val size = entry.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                entry.deleteRecursively()
                removedDirs++
                freedBytes += size
                logger.info("Removed META-INF/$name/ (${size / 1024}KB)")
            }
        } else {
            logger.fine("META-INF/ not found at ${metaInfDir.path}")
        }

        // Clean up empty directories left behind
        apkRoot.walkBottomUp()
            .filter { it.isDirectory && it != apkRoot && it.listFiles()?.isEmpty() == true }
            .forEach { it.delete() }

        // --- 4. Architecture split ---
        if (splitByArch == true) {
            val archToKeep = targetArch ?: "arm64-v8a"
            val libDir = File(apkRoot, "lib")

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

        logger.info("APK Cleanup: removed $removedFiles files + $removedDirs dirs, freed ${freedBytes / 1024}KB")
    }
}
