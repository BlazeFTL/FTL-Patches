package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringsOption
import java.util.logging.Logger

private val logger = Logger.getLogger("LangCleanPatch")

private val KNOWN_NON_LANGUAGE_SEGMENTS = setOf("car", "any")

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

val langCleanPatch = resourcePatch(
    name = "Remove Languages",
    description = "Removes translations for languages you don't use. Only keeps the languages you pick. ",
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
        val keepSet: Set<Pair<String, String?>> = (keepLanguages ?: emptyList()).map { raw ->
            val parts = raw.split("-")
            val lang = parts[0].lowercase()
            val region = parts.getOrNull(1)
                ?.takeIf { it.length == 3 && it.startsWith("r", ignoreCase = true) }
                ?.drop(1)?.lowercase()
            lang to region
        }.toSet()

        // Base package decodes to "res". Additional ARSC packages (e.g. feature
        // modules like .tr, .drive) decode to "package_1/res", "package_2/res", etc.
        // Probe dynamically - no hardcoded package count or names.
        val resRoots = mutableListOf("res")
        var pkgIndex = 1
        while (true) {
            val candidate = "package_$pkgIndex/res"
            if (!get(candidate).isDirectory) break
            resRoots.add(candidate)
            pkgIndex++
        }

        var removedDirs = 0
        var keptDirs = 0

        for (resRoot in resRoots) {
            val resDir = get(resRoot)
            if (!resDir.isDirectory) continue

            resDir.listFiles { file -> file.isDirectory }?.forEach { dir ->
                val qualifiers = extractLanguageQualifiers(dir.name)

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
                    logger.fine("Removed $resRoot/${dir.name} (${size / 1024}KB) — languages: $label")
                }
            }
        }

        logger.info("Language cleanup: kept $keptDirs dirs, removed $removedDirs dirs, scanned ${resRoots.size} package(s)")
    }
}
