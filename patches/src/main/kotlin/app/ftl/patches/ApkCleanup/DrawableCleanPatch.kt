package app.ftl.patches.apkcleanup

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import java.io.File
import java.util.logging.Logger

private val DENSITIES = listOf("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

/** Default/target-fallback density: prefer xhdpi, falling upward (xxhdpi, xxxhdpi) before downward. */
private const val DEFAULT_DENSITY = "xhdpi"

/** Android's uiMode qualifier values -- device categories, not densities. */
private val KNOWN_UI_MODES = setOf("car", "desk", "television", "appliance", "watch", "vrheadset")

private val logger = Logger.getLogger("Remove Duplicate Graphics")

/**
 * Android always places the platform-version qualifier ("-v4", "-v8", "-v13", "-v17", ...) last,
 * after every other qualifier -- including density. Real resource trees are full of directories
 * like "drawable-hdpi-v4", "drawable-night-hdpi-v8", "drawable-sw600dp-xhdpi-v13", or
 * "drawable-ldrtl-hdpi-v17": the density is there, it's just not the final path segment.
 * `tokens.last()` alone misses all of these; this only additionally strips a trailing version
 * qualifier before checking for the density, so it doesn't misfire on qualifiers we don't
 * otherwise understand.
 */
private val VERSION_QUALIFIER = Regex("""^v\d+$""")

/**
 * Finds the density among a directory's qualifier tokens (i.e. everything after the resource
 * type prefix, e.g. "drawable"/"mipmap"/"layout"/"anim"/...), tolerating one trailing version
 * qualifier.
 *
 * @return the density and the remaining qualifiers with the density removed (order preserved),
 *   or null if no recognized density qualifier is present.
 */
private fun extractDensity(qualifiers: List<String>): Pair<String, List<String>>? {
    if (qualifiers.isEmpty()) return null
    val hasVersion = VERSION_QUALIFIER.matches(qualifiers.last())
    val densityIndex = qualifiers.size - if (hasVersion) 2 else 1
    val density = qualifiers.getOrNull(densityIndex) ?: return null
    if (density !in DENSITIES) return null
    val remaining = qualifiers.filterIndexed { index, _ -> index != densityIndex }
    return density to remaining
}

/**
 * Every top-level resource-type prefix actually present under res/ -- "drawable", "mipmap",
 * "layout", "anim", "menu", "raw", "font", "xml", "color", "animator", "interpolator",
 * "transition", or anything else a given app happens to ship. Deduplication is attempted
 * against every one of them; a type with no density-qualified directories (which, per Android's
 * own resource-qualifier rules, is true for most non-graphic types) simply contributes no groups
 * and costs nothing.
 */
private fun discoverTypePrefixes(resDir: File): Set<String> =
    resDir.listFiles { f -> f.isDirectory }
        ?.mapTo(mutableSetOf()) { it.name.substringBefore('-') }
        ?: emptySet()

private fun groupedDensityDirs(resDir: File, prefix: String): Map<String, MutableMap<String, File>> {
    val groups = mutableMapOf<String, MutableMap<String, File>>()
    val matchedDirs = resDir.listFiles { f -> f.isDirectory && f.name.split("-").first() == prefix }
        ?: emptyArray()
    var skipped = 0

    matchedDirs.forEach { dir ->
        val qualifiers = dir.name.split("-").drop(1) // drop the resource-type prefix token
        val parsed = extractDensity(qualifiers)
        if (parsed == null) {
            skipped++
            logger.fine("$prefix: \"${dir.name}\" has no recognized density qualifier -- not grouped.")
            return@forEach
        }
        val (density, remaining) = parsed
        val groupKey = (listOf(prefix) + remaining).joinToString("-")
        val bucket = groups.getOrPut(groupKey) { mutableMapOf() }
        val previous = bucket[density]
        if (previous != null && previous != dir) {
            // Two differently-named directories both normalized to the same (groupKey, density)
            // pair -- one is about to silently shadow the other's files in this dedup pass.
            logger.warning(
                "$groupKey/$density: both \"${previous.name}\" and \"${dir.name}\" map here; " +
                    "\"${dir.name}\" wins, files unique to \"${previous.name}\" will be missed.",
            )
        }
        bucket[density] = dir
    }

    logger.info(
        "$prefix: ${matchedDirs.size} director(y/ies) scanned, ${groups.size} group(s) formed, " +
            "$skipped not density-qualified.",
    )
    return groups
}

/**
 * The order in which density variants are considered for a single file, starting at [preferred]
 * and moving to progressively higher (sharper) densities before falling back to lower ones.
 *
 * Not every resource ships at every density -- a lot of third-party/SDK assets only exist up to
 * a certain density, and forcing one fixed baseline directory (as the old code did) means any
 * file missing from that one directory never gets deduplicated at all, no matter how many
 * duplicate copies of it exist elsewhere. Resolving the source per file, with a sensible
 * fallback chain, means every real duplicate gets caught. Preferring a higher density over a
 * lower one when the exact target isn't available also keeps quality: downscaling a sharper
 * image looks better than upscaling a blurrier one.
 */
private fun densityPreferenceOrder(preferred: String): List<String> {
    val index = DENSITIES.indexOf(preferred).takeIf { it >= 0 } ?: DENSITIES.indexOf(DEFAULT_DENSITY)
    val sameAndHigher = DENSITIES.subList(index, DENSITIES.size)
    val lower = DENSITIES.subList(0, index).asReversed()
    return sameAndHigher + lower
}

/** Always keep the sharpest copy available, regardless of [DEFAULT_DENSITY]/[targetDensity]. */
private val HIGHEST_QUALITY_ORDER = DENSITIES.asReversed()

/**
 * Strips a relative file path down to an extension-agnostic key: same directory, same base name,
 * extension dropped. This lets e.g. "icon.png" in one density directory and "icon.webp" in
 * another be recognized as the same logical resource. Files with no extension are left as-is.
 */
private fun extensionAgnosticKey(relativePath: String): String {
    val slash = relativePath.lastIndexOf('/')
    val parent = if (slash >= 0) relativePath.substring(0, slash + 1) else ""
    val name = if (slash >= 0) relativePath.substring(slash + 1) else relativePath
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    return parent + base
}

/**
 * Deduplicates every density-qualified resource group found under [resDir] for the given type
 * [prefix], keeping -- per file -- whichever density comes first in [order]. Matching is by
 * extension-agnostic relative path within the qualifier group -- same type, same non-density
 * qualifiers, same directory, same base file name regardless of extension -- so e.g. a PNG copy
 * at one density and a WebP copy of the same asset at another density are recognized as
 * duplicates of each other, not kept side by side.
 *
 * @return the number of duplicate files removed, and a count of how many resources ended up
 *   kept at each density (for logging/verification).
 */
private fun dedupeByOrder(resDir: File, prefix: String, order: List<String>): DedupeStats {
    var removed = 0
    val keptByDensity = mutableMapOf<String, Int>()

    groupedDensityDirs(resDir, prefix).forEach { (groupKey, densityMap) ->
        try {
            // Per density: extension-agnostic key -> the actual file on disk (with its real
            // extension), so lookups below can match by key but still delete/resolve the real
            // file. If two files in the same directory collide on the same stripped key (e.g.
            // "icon.png" and "icon.webp" both present at the same density), the later one wins
            // and the other is left alone -- that's a same-density conflict, not this pass's job.
            val filesByDensity: Map<String, Map<String, File>> = densityMap.mapValues { (_, dir) ->
                dir.walkTopDown()
                    .filter { it.isFile }
                    .associateBy { file -> extensionAgnosticKey(file.relativeTo(dir).path) }
            }

            val allKeys = filesByDensity.values.flatMap { it.keys }.toSet()

            allKeys.forEach { key ->
                // The first density (by preference) that actually carries this resource is the
                // one kept; the same resource is then removed from every other density that also
                // has it, regardless of what extension each copy used.
                val keepDensity = order.firstOrNull { density -> filesByDensity[density]?.containsKey(key) == true }
                if (keepDensity == null) {
                    // Structurally this shouldn't happen: key came from walking one of these exact
                    // directories, so it should always be found there. If it isn't, something
                    // (path encoding, a symlink, a case mismatch) is making the two passes
                    // disagree -- worth surfacing instead of silently leaving every copy untouched.
                    logger.warning(
                        "$groupKey/$key: listed under one of ${densityMap.keys} but found in " +
                            "none of them -- left untouched.",
                    )
                    return@forEach
                }

                keptByDensity.merge(keepDensity, 1, Int::plus)

                var removedForThisFile = 0
                filesByDensity.forEach { (density, files) ->
                    if (density == keepDensity) return@forEach
                    val file = files[key] ?: return@forEach
                    if (file.isFile && file.delete()) removedForThisFile++
                }
                if (removedForThisFile > 0) {
                    removed += removedForThisFile
                    logger.fine(
                        "$groupKey/$key: kept $keepDensity, removed from " +
                            densityMap.keys.filter { it != keepDensity }.joinToString(", "),
                    )
                }
            }
        } catch (e: Exception) {
            // One bad file/group (locked file, unexpected symlink, whatever) shouldn't take the
            // rest of this resource type down with it -- log it and keep going.
            logger.warning("Skipped group \"$groupKey\" ($prefix) due to an error: $e")
        }
    }

    return DedupeStats(removed, keptByDensity)
}

private data class DedupeStats(val removedFiles: Int, val keptByDensity: Map<String, Int>) {
    operator fun plus(other: DedupeStats) = DedupeStats(
        removedFiles + other.removedFiles,
        (keptByDensity.keys + other.keptByDensity.keys).associateWith { density ->
            (keptByDensity[density] ?: 0) + (other.keptByDensity[density] ?: 0)
        },
    )
}

/**
 * Deletes every resource directory (of any type) whose qualifiers include one of [uiModes] --
 * e.g. "watch" (Wear OS) or "television" (Android TV). Unlike the density dedup above, this
 * isn't about trimming a redundant copy of something that's also available elsewhere -- it drops
 * resources for a whole device category the app doesn't need to support, wholesale, regardless
 * of density.
 */
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
    description = "Keeps only one screen-density copy of every duplicated resource (drawables, " +
        "layouts, and any other resource type shipped at multiple densities) and removes the " +
        "rest, letting Android scale the kept copy. Mipmaps (the launcher icon) always keep " +
        "their highest-quality copy instead of following the target density, since that's the " +
        "one resource users actually see blown up on their home screen. Optionally strips " +
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
        logger.info("Deduplicating resources, preferring density \"$preferred\" (mipmaps keep highest quality).")

        var total = DedupeStats(0, emptyMap())
        discoverTypePrefixes(resDir).sorted().forEach { prefix ->
            val order = if (prefix == "mipmap") HIGHEST_QUALITY_ORDER else densityPreferenceOrder(preferred)
            val stats = dedupeByOrder(resDir, prefix, order)
            if (stats.removedFiles > 0) {
                logger.info(
                    "$prefix: removed ${stats.removedFiles} duplicate file(s); kept density " +
                        "breakdown: ${stats.keptByDensity.toSortedMap()}",
                )
            }
            total += stats
        }

        logger.info(
            "Done. Removed ${total.removedFiles} duplicate file(s) in total; kept density " +
                "breakdown: ${total.keptByDensity.toSortedMap()}",
        )

        resDir.walkBottomUp()
            .filter { it.isDirectory && it.listFiles()?.isEmpty() == true }
            .forEach { it.delete() }
    }
}
