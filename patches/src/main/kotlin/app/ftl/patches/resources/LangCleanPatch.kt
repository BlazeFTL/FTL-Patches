package app.ftl.patches.resources

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.stringsOption

// Matches values-<lang>[-<qualifier>...], e.g. values-en, values-zh-rTW, values-fil.
// The base "values" directory (no suffix) never matches and is always kept.
private val LANG_DIR = Regex("^values-([a-z]{2,3})(?:-.+)?$")

val langCleanPatch = rawResourcePatch(
    name = "Language clean",
    description = "Removes language resource directories (values-<lang>) for languages not in the keep list, freeing up space used by unused translations. The default \"values\" directory is always kept.",
) {
    val keepLanguages by stringsOption(
        key = "keepLanguages",
        default = listOf("en", "ru"),
        title = "Languages to keep",
        description = "Language codes (e.g. en, ru, es) whose resources should not be removed. Add a code to keep more languages, or remove one to also delete it.",
    )

    execute {
        val resDir = get("res", false)
        if (!resDir.isDirectory) return@execute

        val keep = (keepLanguages ?: emptyList()).map { it.lowercase() }.toSet()

        resDir.listFiles { file -> file.isDirectory }
            ?.forEach { dir ->
                val lang = LANG_DIR.matchEntire(dir.name)?.groupValues?.get(1) ?: return@forEach
                if (lang !in keep) dir.deleteRecursively()
            }
    }
}
