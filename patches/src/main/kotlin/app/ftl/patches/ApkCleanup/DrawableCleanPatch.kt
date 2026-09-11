package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import java.util.logging.Logger

private val DENSITIES = listOf("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

private const val DEFAULT_DENSITY = "xhdpi"

private val KNOWN_UI_MODES = setOf("car", "desk", "television", "appliance", "watch", "vrheadset")

private val logger = Logger.getLogger("Remove Duplicate Graphics")

private val DRAWABLE_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg", "gif")
private val MIPMAP_EXTENSIONS = setOf("png", "webp", "xml")

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

private fun dirPartOf(entryPath: String): String = entryPath.removePrefix("res/").substringBefore("/")

private fun fileNameOf(entryPath: String): String = entryPath.substringAfterLast("/")

private fun isFileEntry(path: String): Boolean = !path.endsWith("/") && path.count { it == '/' } >= 2

/**
 * Every archive entry under res/[prefix]*, via listApkEntries -- not File.listFiles(), since
 * not every resource entry gets staged to the working directory (native libs are the
 * documented example, but plenty of other entries are also left packed); a plain filesystem
 * walk silently misses those.
 */
private fun ResourcePatchContext.entriesUnder(prefix: String): List<String> =
    listApkEntries("res/$prefix").filter(::isFileEntry)

private fun densityDirParts(dirParts: Set<String>, density: String): Set<String> {
    val suffix = "-$density"
    return dirParts.filterTo(mutableSetOf()) { it.contains(suffix) }
}

/**
 * One dedup pass for a resource type ([typePrefix] "drawable" or "mipmap"): for each density
 * in [order], collect every file with an extension in [extensions] under that density's
 * directories, then delete any same-named file found under any *other* directory of the same
 * type -- density-qualified or not. Directories that share a density (e.g. a plain and a
 * "night" variant both at hdpi) are never deduped against each other in the same step, only
 * against directories outside that density's set. Matching is exact filename (extension
 * included); "icon.png" is never treated as a duplicate of "icon.webp". A local mutable copy
 * of the entry listing tracks deletions as they happen, since listApkEntries always reflects
 * the original input APK and never a patch's own prior deletions in the same run.
 */
private fun ResourcePatchContext.dedupeType(typePrefix: String, extensions: Set<String>, order: List<String>): Int {
    var removed = 0
    val allEntries = entriesUnder(typePrefix)
    val allDirParts = allEntries.map(::dirPartOf).toSet()

    val live: MutableMap<String, MutableSet<String>> = allEntries
        .groupBy({ dirPartOf(it) }, { fileNameOf(it) })
        .mapValuesTo(mutableMapOf()) { it.value.toMutableSet() }

    for (density in order) {
        val refDirParts = densityDirParts(allDirParts, density)
        if (refDirParts.isEmpty()) continue

        val refNames = refDirParts.flatMap { dp ->
            live[dp].orEmpty().filter { name -> name.substringAfterLast(".", "").lowercase() in extensions }
        }.toSet()
        if (refNames.isEmpty()) continue

        val victimDirParts = allDirParts - refDirParts
        for (dp in victimDirParts) {
            val names = live[dp] ?: continue
            val toRemove = names.filter { it in refNames }
            for (name in toRemove) {
                delete("res/$dp/$name")
                names.remove(name)
                removed++
            }
        }
    }

    return removed
}

private fun ResourcePatchContext.stripUiModeDirs(uiModes: Set<String>): Int {
    if (uiModes.isEmpty()) return 0
    val byDirPart = listApkEntries("res/").filter(::isFileEntry).groupBy(::dirPartOf)

    var removedFiles = 0
    for ((dirPart, entries) in byDirPart) {
        val qualifiers = dirPart.split("-").drop(1)
        if (qualifiers.none { it in uiModes }) continue

        entries.forEach { delete(it) }
        removedFiles += entries.size
        logger.fine("Removed $dirPart/ (${entries.size} file(s)) -- matched uiMode qualifier.")
    }
    return removedFiles
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
        val stripSet = buildSet {
            if (stripSmartwatch == true) add("watch")
            if (stripAndroidTv == true) add("television")
            if (stripOtherFormFactors == true) addAll(setOf("car", "desk", "appliance", "vrheadset"))
        }
        if (stripSet.isNotEmpty()) {
            val strippedFiles = stripUiModeDirs(stripSet)
            logger.info("Removed $strippedFiles device-specific resource file(s) for: $stripSet")
        }

        val preferred = targetDensity?.takeIf { it in DENSITIES } ?: DEFAULT_DENSITY.also {
            logger.warning("targetDensity option was unset or invalid; using \"$it\".")
        }
        val order = densityPriorityOrder(preferred)
        logger.info("Deduplicating drawable/mipmap resources; density priority: $order")

        val removedDrawable = dedupeType("drawable", DRAWABLE_EXTENSIONS, order)
        val removedMipmap = dedupeType("mipmap", MIPMAP_EXTENSIONS, order)
        logger.info(
            "Done. Removed $removedDrawable duplicate drawable file(s) and $removedMipmap " +
                "duplicate mipmap file(s).",
        )
    }
}
