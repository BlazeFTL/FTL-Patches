package app.ftl.patches.apkcleanup

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

private typealias ResDirs = MutableMap<String, MutableList<String>>

private fun extensionOf(name: String) = name.substringAfterLast('.', "").lowercase()

private fun dedupeType(
    dirs: ResDirs,
    typePrefix: String,
    extensions: Set<String>,
    order: List<String>,
    remove: (String, String) -> Unit,
): Int {
    var removed = 0
    val typeDirNames = dirs.keys.filter { it.startsWith(typePrefix) }

    for (density in order) {
        val suffix = "-$density"
        val refDirs = typeDirNames.filter { it.contains(suffix) }.toSet()
        if (refDirs.isEmpty()) continue

        val refNames = refDirs.flatMap { dir ->
            dirs[dir].orEmpty().filter { extensionOf(it) in extensions }
        }.toSet()
        if (refNames.isEmpty()) continue

        for (dir in typeDirNames) {
            if (dir in refDirs) continue
            val files = dirs[dir] ?: continue
            val victims = files.filter { it in refNames }
            victims.forEach { remove(dir, it) }
            files.removeAll(victims.toSet())
            removed += victims.size
        }
    }

    return removed
}

private fun stripUiModeDirs(dirs: ResDirs, uiModes: Set<String>, remove: (String, String) -> Unit): Int {
    if (uiModes.isEmpty()) return 0
    var removedDirs = 0
    dirs.entries.toList().forEach { (dirName, files) ->
        val qualifiers = dirName.split("-").drop(1)
        if (qualifiers.none { it in uiModes }) return@forEach

        files.toList().forEach { remove(dirName, it) }
        logger.fine("Removed $dirName/ (${files.size} file(s)) -- matched uiMode qualifier.")
        dirs.remove(dirName)
        removedDirs++
    }
    return removedDirs
}

// DrawableCleanPatch.kt
val drawableCleanPatch = resourcePatch(
    name = "Remove Duplicate Graphics",
    description = "Keeps only one screen-density copy of every duplicated drawable/mipmap file, " +
        "Mipmaps follow this same order. Optionally strips " +
        "device-specific resources (smartwatch, Android TV, etc.) entirely.",
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
        val packages = resourcePackageDirs().keys
        if (packages.isEmpty()) {
            logger.warning("Remove Duplicate Graphics: no resource packages found")
            return@execute
        }

        val stripSet = buildSet {
            if (stripSmartwatch == true) add("watch")
            if (stripAndroidTv == true) add("television")
            if (stripOtherFormFactors == true) addAll(setOf("car", "desk", "appliance", "vrheadset"))
        }

        val preferred = targetDensity?.takeIf { it in DENSITIES } ?: DEFAULT_DENSITY.also {
            logger.warning("targetDensity option was unset or invalid; using \"$it\".")
        }
        val order = densityPriorityOrder(preferred)
        logger.info("Deduplicating drawable/mipmap resources; density priority: $order")

        var strippedTotal = 0
        var drawableTotal = 0
        var mipmapTotal = 0

        packages.forEach { pkgName ->
            val dirs = resDirectories(pkgName)
            val remove = { dir: String, file: String -> delete("res/$dir/$file", pkgName) }

            if (stripSet.isNotEmpty()) {
                strippedTotal += stripUiModeDirs(dirs, stripSet, remove)
            }
            drawableTotal += dedupeType(dirs, "drawable", DRAWABLE_EXTENSIONS, order, remove)
            mipmapTotal += dedupeType(dirs, "mipmap", MIPMAP_EXTENSIONS, order, remove)
        }

        if (stripSet.isNotEmpty()) {
            logger.info("Removed $strippedTotal device-specific resource director(y/ies) for: $stripSet")
        }
        logger.info(
            "Done. Removed $drawableTotal duplicate drawable file(s) and $mipmapTotal " +
                "duplicate mipmap file(s), across ${packages.size} resource package(s).",
        )
    }
}
