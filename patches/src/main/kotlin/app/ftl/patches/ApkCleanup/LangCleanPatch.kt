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

    val dirFiles = mutableMapOf<String, MutableList<String>>()

    for (entry in listApkEntries()) {
        if (entry.endsWith("/")) continue
        val parts = entry.split("/")
        val resIdx = parts.indexOf("res")
        if (resIdx == -1 || parts.size < resIdx + 3) continue
        val dirPath = parts.subList(0, resIdx + 2).joinToString("/")
        dirFiles.getOrPut(dirPath) { mutableListOf() }.add(entry)
    }

    var removedDirs = 0
    var removedFiles = 0
    var keptDirs = 0

    dirFiles.forEach { (dirPath, files) ->
        val dirName = dirPath.substringAfterLast("/")
        val qualifiers = extractLanguageQualifiers(dirName)

        if (qualifiers.isEmpty()) {
            keptDirs++
            return@forEach
        }

        val shouldKeep = qualifiers.any { q -> (q.lang to q.region) in keepSet }

        if (shouldKeep) {
            keptDirs++
        } else {
            files.forEach(::delete)
            removedFiles += files.size
            removedDirs++
            val label = qualifiers.joinToString { q ->
                if (q.region != null) "${q.lang}-r${q.region.uppercase()}" else q.lang
            }
            logger.fine("Removed $dirPath (${files.size} files) — languages: $label")
        }
    }

    logger.info("Language cleanup: kept $keptDirs dirs, removed $removedDirs dirs, $removedFiles files")
    }
