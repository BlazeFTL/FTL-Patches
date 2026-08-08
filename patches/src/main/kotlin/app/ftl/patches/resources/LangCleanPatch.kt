package app.ftl.patches.resources

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.stringsOption
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("LangCleanPatch")

// Rare 2-3 letter segments that are Android qualifiers, NOT language codes.
private val KNOWN_NON_LANGUAGE_SEGMENTS = setOf(
    "car",      // uiMode=car
    "any",      // part of anydpi
)

/**
 * Extracts language codes from an Android resource directory name.
 *
 * Android resource dirs can have language qualifiers on ANY type:
 *   values-en, drawable-ru-hdpi, mipmap-fr, raw-es, xml-de, layout-ja,
 *   values-zh-rCN, values-b+sr+Latn, etc.
 *
 * Language codes are ISO 639-1 (2-letter) or ISO 639-2 (3-letter).
 * Region suffixes (-rXX) are ignored. BCP 47 tags (b+<lang>+<script>) are parsed.
 */
private fun extractLanguageCodes(dirName: String): List<String> {
    val segments = dirName.split("-")
    if (segments.size < 2) return emptyList()

    val languages = mutableListOf<String>()

    for (seg in segments.drop(1)) {
        // BCP 47 tag: values-b+sr+Latn → segment is "b+sr+Latn"
        if (seg.startsWith("b+")) {
            val parts = seg.split("+")
            if (parts.size >= 2) {
                languages.add(parts[1].lowercase())
            }
            continue
        }

        // Region code: -rCN, -rUS — skip, not a language
        if (seg.startsWith("r") && seg.length == 3 && seg.drop(1).all { it.isUpperCase() }) {
            continue
        }

        // Language code: 2-3 lowercase letters, not a known non-language qualifier
        if (seg.length in 2..3 && seg.all { it.isLowerCase() } && seg !in KNOWN_NON_LANGUAGE_SEGMENTS) {
            languages.add(seg)
        }
    }

    return languages
}

val langCleanPatch = rawResourcePatch(
    name = "Remove Languages",
    description = "Removes translations for languages you don\'t use across ALL resource types (strings, drawables, layouts, raw, xml, etc.). Only keeps the languages you pick. Base resources with no language code are always preserved.",
    default = false,
) {
    val keepLanguages by stringsOption(
        key = "keepLanguages",
        default = listOf("en", "ru"),
        title = "Languages to keep",
        description = "Language codes (e.g. en, ru, es) to preserve. All other language variants are deleted.",
    )

    execute {
        // Get APK root via AndroidManifest.xml to avoid arsclib package lookup issues
        val manifestFile = get("AndroidManifest.xml", false)
        val apkRoot = manifestFile.parentFile ?: File(".")
        val resDir = File(apkRoot, "res")

        if (!resDir.isDirectory) {
            logger.warning("Language cleanup: res/ directory not found")
            return@execute
        }

        val keep = (keepLanguages ?: emptyList()).map { it.lowercase() }.toSet()
        var removedDirs = 0
        var keptDirs = 0

        resDir.listFiles { file -> file.isDirectory }?.forEach { dir ->
            val langs = extractLanguageCodes(dir.name)

            // No language qualifier → base resource, always keep
            if (langs.isEmpty()) {
                keptDirs++
                return@forEach
            }

            // Keep if ANY language in this dir is in the keep list
            val shouldKeep = langs.any { it in keep }

            if (shouldKeep) {
                keptDirs++
            } else {
                val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                dir.deleteRecursively()
                removedDirs++
                logger.fine("Removed ${dir.name} (${size / 1024}KB) — languages: ${langs.joinToString()}")
            }
        }

        logger.info("Language cleanup: kept $keptDirs dirs, removed $removedDirs dirs")
    }
}
