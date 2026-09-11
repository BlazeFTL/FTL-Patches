package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import java.io.File
import java.util.logging.Logger

private val DENSITIES = listOf("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

private const val DEFAULT_DENSITY = "xhdpi"

private val KNOWN_UI_MODES = setOf("car", "desk", "television", "appliance", "watch", "vrheadset")

private val logger = Logger.getLogger("Remove Duplicate Graphics")

private val DRAWABLE_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg", "gif")
private val MIPMAP_EXTENSIONS = setOf("png", "xml")

/**
 * Priority order in which density variants are considered for a single file: start at
 * [target], walk down through every lower density (target-1 ... ldpi), then wrap around to
 * the top and walk down through whatever's left above target (xxxhdpi ... target+1). The
 * first density in this order that actually carries a given filename is the one kept; the
 * same filename is then removed from every other density directory of the same resource
 * type. Equivalent to: sort densities high-to-low, then rotate that list to start at target.
 */
private fun densityPriorityOrder(target: String): List<String> {
    val descending = DENSITIES.asReversed()
    val index = descending.indexOf(target).takeIf { it >= 0 } ?: descending.indexOf(DEFAULT_DENSITY)
    return descending.subList(index, descending.size) + descending.subList(0, index)
}

private fun typeDirs(resDir: File, typePrefix: String): List<File> =
    resDir.listFiles { f -> f.isDirectory && f.name.startsWith(typePrefix) }?.toList() ?: emptyList()

private fun densityDirs(dirs: List<File>, density: String): List<File> {
    val suffix = "-$density"
    return dirs.filter { it.name.contains(suffix) }
}

/**
 * One dedup pass for a resource type ([typePrefix] "drawable" or "mipmap"): for each density
 * in [order], collect every file with an extension in [extensions] under that density's
 * directories, then delete any same-named file found under any *other* directory of the same
 * type -- density-qualified or not. Directories that share a density (e.g. a plain and a
 * "night" variant both at hdpi) are never deduped against each other in the same step, only
 * against directories outside that density's set. Matching is exact filename (extension
 * included); a "icon.png" is never treated as a duplicate of an "icon.webp".
 */
private fun dedupeType(resDir: File, typePrefix: String, extensions: Set<String>, order: List<String>): Int {
    var removed = 0
    val allDirs = typeDirs(resDir, typePrefix)
    logger.info("[$typePrefix] all matched dirs (${allDirs.size}): ${allDirs.map { it.name }.sorted()}")

    for (density in order) {
        val refDirs = densityDirs(allDirs, density)
        if (refDirs.isEmpty()) continue

        val refNames = refDirs.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in extensions }.map { it.name }
        }.toSet()
        logger.info(
            "[$typePrefix] density=$density refDirs=${refDirs.map { it.name }} refNameCount=${refNames.size}",
        )
        if (refNames.isEmpty()) continue

        val victimDirs = allDirs.filter { it !in refDirs }
        var deletedThisStep = 0
        for (dir in victimDirs) {
            dir.walkTopDown()
                .filter { it.isFile && it.name in refNames }
                .toList()
                .forEach {
                    if (it.delete()) {
                        removed++
                        deletedThisStep++
                    } else {
                        logger.warning("[$typePrefix] FAILED to delete ${it.path}")
                    }
                }
        }
        logger.info("[$typePrefix] density=$density deleted=$deletedThisStep")
    }

    return removed
}

private fun stripUiModeDirs(resDir: File, uiModes: Set<String>): Int {
    if (uiModes.isEmpty()) return 0
    var removedDirs = 0
    resDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
        val qualifiers = dir.name.split("-").drop(1)
        if (qualifiers.none { it in uiModes }) return@forEach

        val fileCount = dir.walkTopDown().count { it.isFile }
        if (dir.deleteRecursively()) {
            removedDirs++
            logger.fine("Removed ${dir.name}/ ($fileCount file(s)) -- matched uiMode qualifier.")
        }
    }
    return removedDirs
}

// DrawableCleanPatch.kt
val drawableCleanPatch = resourcePatch(
    name = "Remove Duplicate Graphics",
    description = "Keeps only one screen-density copy of every duplicated drawable/mipmap file " +
        "and removes the rest, letting Android's density fallback scale the kept copy. For " +
        "each file, densities are tried starting at the target density, then downward through " +
        "every lower density, then wrapping around to try whatever's left at the top -- so a " +
        "duplicate is normally kept at the smallest available copy at or below the target " +
        "density, only falling back to a higher-density copy if no lower one exists. Mipmaps " +
        "follow this same order. Optionally strips device-specific resources (smartwatch, " +
        "Android TV, etc.) entirely.",
    default = false,
) {
    val targetDensity by stringOption(
        key = "targetDensity",
        default = DEFAULT_DENSITY,
        values = DENSITIES.associateWith { it },
        title = "Target density",
        description = "Density bucket to prefer for images and other graphics; duplicates are " +
            "stripped from every other bucket. If a particular resource was never shipped at " +
            "this density, the next higher density available for it is kept instead, falling " +
            "back to a lower one only if nothing higher exists either. Launcher icons ignore " +
            "this setting and always keep their highest-quality copy.",
    )

    val stripSmartwatch by booleanOption(
        key = "stripSmartwatch",
        default = false,
        title = "Remove smartwatch (Wear OS) resources",
        description = "Entirely removes resources qualified for smartwatches -- not just " +
            "duplicate copies, all of them -- regardless of density. Off by default since it " +
            "changes what device types the app supports.",
    )

    val stripAndroidTv by booleanOption(
        key = "stripAndroidTv",
        default = false,
        title = "Remove Android TV resources",
        description = "Entirely removes resources qualified for Android TV -- not just " +
            "duplicate copies, all of them -- regardless of density. Off by default since it " +
            "changes what device types the app supports.",
    )

    val stripOtherFormFactors by booleanOption(
        key = "stripOtherFormFactors",
        default = false,
        title = "Remove other device-specific resources (car, desk dock, VR headset)",
        description = "Entirely removes resources qualified for car head units, desk docks, or " +
            "VR headsets -- not just duplicate copies, all of them -- regardless of density. " +
            "Off by default since it changes what device types the app supports.",
    )

    execute {
        val resDir = get("res", false)

        val stripSet = buildSet {
            if (stripSmartwatch == true) add("watch")
            if (stripAndroidTv == true) add("television")
            if (stripOtherFormFactors == true) addAll(setOf("car", "desk", "appliance", "vrheadset"))
        }
        if (stripSet.isNotEmpty()) {
            val strippedDirs = stripUiModeDirs(resDir, stripSet)
            logger.info("Removed $strippedDirs device-specific resource director(y/ies) for: $stripSet")
        }

        val preferred = targetDensity?.takeIf { it in DENSITIES } ?: DEFAULT_DENSITY.also {
            logger.warning("targetDensity option was unset or invalid; using \"$it\".")
        }
        val order = densityPriorityOrder(preferred)
        logger.info("Deduplicating drawable/mipmap resources; density priority: $order")

        val removedDrawable = dedupeType(resDir, "drawable", DRAWABLE_EXTENSIONS, order)
        val removedMipmap = dedupeType(resDir, "mipmap", MIPMAP_EXTENSIONS, order)
        logger.info(
            "Done. Removed $removedDrawable duplicate drawable file(s) and $removedMipmap " +
                "duplicate mipmap file(s).",
        )

        resDir.walkBottomUp()
            .filter { it.isDirectory && it.listFiles()?.isEmpty() == true }
            .forEach { it.delete() }
    }
}
