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

// === SAFE JUNK (filename match, scoped away from assets/ and res/) ===
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

// Filenames matched by JUNK_PATTERNS are only ever library/build metadata that lives
// outside these dirs. Excluding them stops a same-named real app file from being caught.
private val EXCLUDED_PREFIXES = listOf("assets/", "res/")

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
        val manifestFile = get("AndroidManifest.xml", false)
        val apkRoot = manifestFile.parentFile ?: File(".")

        var removedFiles = 0
        var removedDirs = 0
        var freedBytes = 0L

        fun isProtected(relativePath: String) = PROTECTED_PATTERNS.any { it.matches(relativePath) }

        // Deletes a directory tree but honors PROTECTED_PATTERNS; returns (filesRemoved, dirsRemoved, bytesFreed).
        fun deleteUnprotected(dir: File): Triple<Int, Int, Long> {
            var files = 0
            var dirs = 0
            var bytes = 0L
            dir.walkBottomUp().forEach { entry ->
                if (entry == dir) return@forEach
                val relativePath = entry.relativeTo(apkRoot).path.replace("\\", "/")
                if (isProtected(relativePath)) return@forEach
                if (entry.isFile) {
                    val size = entry.length()
                    if (entry.delete()) {
                        files++
                        bytes += size
                    }
                } else if (entry.isDirectory && entry.listFiles()?.isEmpty() == true) {
                    if (entry.delete()) dirs++
                }
            }
            if (dir.listFiles()?.isEmpty() == true && dir.delete()) dirs++
            return Triple(files, dirs, bytes)
        }

        // --- 1. Remove junk files ---
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

        // --- 2. Remove kotlin/ folder (kotlin_builtins, compile-time only) ---
        val kotlinDir = File(apkRoot, "kotlin")
        if (kotlinDir.isDirectory) {
            val (f, d, size) = deleteUnprotected(kotlinDir)
            removedFiles += f
            removedDirs += d
            freedBytes += size
            logger.info("Removed kotlin/ folder (${size / 1024}KB)")
        }

        // --- 3. Remove useless META-INF subfolders (keep services/ and signatures) ---
        val metaInfDir = File(apkRoot, "META-INF")
        if (metaInfDir.isDirectory) {
            metaInfDir.listFiles()?.forEach { entry ->
                if (!entry.isDirectory) return@forEach

                val name = entry.name.lowercase()
                // Keep services/ (ServiceLoader) — everything else in subfolders is junk
                if (name == "services") return@forEach

                val (f, d, size) = deleteUnprotected(entry)
                removedFiles += f
                removedDirs += d
                freedBytes += size
                logger.info("Removed META-INF/$name/ (${size / 1024}KB)")
            }
        }

        // Clean up any remaining empty directories
        apkRoot.walkBottomUp()
            .filter { it.isDirectory && it != apkRoot && it.listFiles()?.isEmpty() == true }
            .forEach { it.delete() }

        // --- 4. Architecture split ---
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
                        removedDirs++
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
