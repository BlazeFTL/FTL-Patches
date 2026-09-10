package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringsOption
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("LangCleanPatch")

// Rare 2-3 letter segments that are Android qualifiers, NOT language codes.
private val KNOWN_NON_LANGUAGE_SEGMENTS = setOf(
    "car",      // uiMode=car
    "any",      // part of anydpi
)

private data class LangQualifier(val lang: String, val region: String?)

private fun extractLanguageQualifiers(dirName: String): List<LangQualifier> {
    val segments = dirName.split("-")
    if (segments.size < 2) return emptyList()

    val rest = segments.drop(1)
    val result = mutableListOf<LangQualifier>()
    var i = 0

    while (i < rest.size) {
        val seg = rest[i]

        if (seg.startsWith("b+")) {
            val parts = seg.split("+")
            if (parts.size >= 2) {
                val lang = parts[1].lowercase()
                val region = parts.getOrNull(2)
                    ?.takeIf { it.length == 2 && it.all { c -> c.isUpperCase() } }
                    ?.lowercase()
                result.add(LangQualifier(lang, region))
            }
            i++
            continue
        }

        if (seg.length in 2..3 && seg.all { it.isLowerCase() } && seg !in KNOWN_NON_LANGUAGE_SEGMENTS) {
            val next = rest.getOrNull(i + 1)
            val isRegion = next != null && next.startsWith("r") && next.length == 3 &&
                next.drop(1).all { it.isUpperCase() }
            val region = if (isRegion) next!!.drop(1).lowercase() else null
            result.add(LangQualifier(seg, region))
            i += if (isRegion) 2 else 1
            continue
        }

        i++
    }

    return result
}

/** ARSCLib writes one directory per ARSC package, tagged with package.json. */
private fun packageNameOf(pkgDir: File): String {
    val json = pkgDir.resolve("package.json").takeIf { it.isFile } ?: return pkgDir.name
    return Regex("\"package_name\"\\s*:\\s*\"([^\"]+)\"")
        .find(json.readText())?.groupValues?.get(1) ?: pkgDir.name
}

val langCleanPatch = resourcePatch(
    name = "Remove Languages",
    description = "Removes translations for languages you don't use, in EVERY resource package of resources.arsc. ",
    default = false,
) {
    val keepLanguages by stringsOption(
        key = "keepLanguages",
        default = listOf("en", "en-rIN", "ru"),
        title = "Languages to keep",
        description = "Exact resource variants to preserve. \"ru\" keeps ONLY the unqualified ru dir " +
            "(values-ru); it does NOT pull in ru-rRU or any other region. \"en-rIN\" keeps ONLY that " +
            "region. List every variant you want kept, e.g. en, en-rIN, ru — anything not listed is removed.",
    )

    execute {
        // get("res") is scoped to the MANIFEST package only. Morphe decodes each ARSC package
        // (com.mxtech.videoplayer.ad, .ad.tr, .ad.drive, ...) into its own directory under
        // <work>/resources/, each with its own res/. res -> packageDir -> resourcesRoot.
        val mainRes = get("res")
        if (!mainRes.isDirectory) {
            logger.warning("Language cleanup: res/ directory not found")
            return@execute
        }
        val resourcesRoot = mainRes.parentFile.parentFile

        val keepSet: Set<Pair<String, String?>> = (keepLanguages ?: emptyList()).map { raw ->
            val parts = raw.split("-")
            val lang = parts[0].lowercase()
            val region = parts.getOrNull(1)
                ?.takeIf { it.length == 3 && it.startsWith("r", ignoreCase = true) }
                ?.drop(1)?.lowercase()
            lang to region
        }.toSet()

        var removedDirs = 0
        var keptDirs = 0

        resourcesRoot.listFiles { f -> f.isDirectory }.orEmpty().forEach { pkgDir ->
            val resDir = pkgDir.resolve("res")
            if (!resDir.isDirectory) return@forEach
            val pkgName = packageNameOf(pkgDir)

            resDir.listFiles { file -> file.isDirectory }.orEmpty().forEach { dir ->
                val qualifiers = extractLanguageQualifiers(dir.name)

                // No language qualifier → base resource, always keep
                if (qualifiers.isEmpty()) {
                    keptDirs++
                    return@forEach
                }

                val shouldKeep = qualifiers.any { q -> (q.lang to q.region) in keepSet }

                if (shouldKeep) {
                    keptDirs++
                } else {
                    val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    dir.deleteRecursively()
                    removedDirs++
                    val label = qualifiers.joinToString { q ->
                        if (q.region != null) "${q.lang}-r${q.region.uppercase()}" else q.lang
                    }
                    logger.fine("Removed $pkgName:${dir.name} (${size / 1024}KB) — languages: $label")
                }
            }
        }

        logger.info("Language cleanup: kept $keptDirs dirs, removed $removedDirs dirs (all packages)")
    }
}
