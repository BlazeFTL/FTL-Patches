package app.ftl.patches.resources

import app.morphe.patcher.patch.rawResourcePatch
import java.io.File

private val DENSITIES = listOf("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
private val DRAWABLE_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg", "gif")
private val MIPMAP_EXTENSIONS = setOf("png", "xml")

private fun densityDirs(resDir: File, prefix: String, density: String): List<File> =
    resDir.listFiles { f -> f.isDirectory && f.name.startsWith("$prefix-$density") }?.toList() ?: emptyList()

private fun dedupeByBaselineDensity(resDir: File, prefix: String, baseline: String, extensions: Set<String>) {
    val baselineNames = densityDirs(resDir, prefix, baseline)
        .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in extensions } }
        .map { it.name }
        .toSet()
    if (baselineNames.isEmpty()) return

    DENSITIES.filter { it != baseline }.forEach { density ->
        densityDirs(resDir, prefix, density).forEach { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in extensions && it.name in baselineNames }
                .forEach { it.delete() }
        }
    }
}

val drawableCleanPatch = rawResourcePatch(
    name = "Drawable clean",
    description = "Keeps drawable/mipmap resources only in the target density bucket and removes duplicate-named copies from every other density bucket, relying on Android's density fallback to resolve them.",
) {
    val targetDensity by stringOption(name = "Target density")

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
