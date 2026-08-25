package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.stringOption
import java.io.File

private val DENSITIES = listOf("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
private val DRAWABLE_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg", "gif")
private val MIPMAP_EXTENSIONS = setOf("png", "xml")

private fun groupedDensityDirs(resDir: File, prefix: String): Map<String, MutableMap<String, File>> {
    val groups = mutableMapOf<String, MutableMap<String, File>>()
    resDir.listFiles { f -> f.isDirectory && f.name.split("-").first() == prefix }?.forEach { dir ->
        val tokens = dir.name.split("-")
        val density = tokens.last()
        if (density !in DENSITIES) return@forEach
        val groupKey = tokens.dropLast(1).joinToString("-")
        groups.getOrPut(groupKey) { mutableMapOf() }[density] = dir
    }
    return groups
}

private fun dedupeByBaselineDensity(resDir: File, prefix: String, baseline: String, extensions: Set<String>) {
    groupedDensityDirs(resDir, prefix).values.forEach { densityMap ->
        val baselineDir = densityMap[baseline] ?: return@forEach
        val baselineNames = baselineDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in extensions }
            .map { it.name }
            .toSet()
        if (baselineNames.isEmpty()) return@forEach

        densityMap.forEach { (density, dir) ->
            if (density == baseline) return@forEach
            dir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in extensions && it.name in baselineNames }
                .forEach { it.delete() }
        }
    }
}

val drawableCleanPatch = rawResourcePatch(
    name = "Remove Duplicate Graphics",
    description = "Keeps images for only one screen density (like xhdpi) and removes copies for all other densities. Android will automatically scale the kept images, making the app significantly smaller.",
    default = false,
) {
    val targetDensity by stringOption(
        key = "targetDensity",
        default = "xhdpi",
        values = DENSITIES.associateWith { it },
        title = "Target density",
        description = "Density bucket to keep; duplicates are stripped from every other bucket.",
    )

    execute {
        val resDir = get("res", false)
        val baseline = targetDensity?.takeIf { it in DENSITIES } ?: "xxhdpi"

        dedupeByBaselineDensity(resDir, "drawable", baseline, DRAWABLE_EXTENSIONS)
        dedupeByBaselineDensity(resDir, "mipmap", baseline, MIPMAP_EXTENSIONS)

        resDir.walkBottomUp()
            .filter { it.isDirectory && it.listFiles()?.isEmpty() == true }
            .forEach { it.delete() }
    }
}
