package app.ftl.patches.skipsplashexpert

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element
import java.util.logging.Logger

private val logger = Logger.getLogger("UniversalSkipSplashScreenPatch")

// The categories that mark "this filter is just another way to launch the app" - LEANBACK_LAUNCHER
// (Android TV) and MULTIWINDOW_LAUNCHER (legacy multi-window) are separate launcher surfaces for
// the SAME activity, not extra functionality, so a filter using only these plus MAIN is still bare.
private val PURE_LAUNCHER_MARKERS = setOf(
    "android.intent.category.LAUNCHER",
    "android.intent.category.LEANBACK_LAUNCHER",
)
private val KNOWN_LAUNCHER_CATEGORIES = PURE_LAUNCHER_MARKERS + setOf(
    "android.intent.category.DEFAULT",
    "android.intent.category.MULTIWINDOW_LAUNCHER",
)

// Below this combined confidence score, auto-detect refuses to rename anything.
private const val SPLASH_SCORE_THRESHOLD = 4

private fun Element.attr(name: String): String = getAttribute(name)

private fun Element.children(tag: String): List<Element> {
    val nodes = getElementsByTagName(tag)
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}

private fun Element.resolvedName(packageName: String): String {
    val name = attr("android:name")
    return if (name.startsWith(".")) packageName + name else name
}

private fun findLauncherActivity(document: org.w3c.dom.Document): Element? =
    document.documentElement.children("activity").firstOrNull { activity ->
        activity.children("intent-filter").any { filter ->
            val actions = filter.children("action").map { it.attr("android:name") }
            val categories = filter.children("category").map { it.attr("android:name") }
            actions.contains("android.intent.action.MAIN") &&
                categories.any { it in PURE_LAUNCHER_MARKERS }
        }
    }

/** A filter that does nothing but launch (via any launcher-surface variant): MAIN action, only
 *  launcher-marker/DEFAULT/multiwindow categories, no data. Multiple such filters on one activity
 *  (e.g. phone + TV + multi-window entries) are still bare - they're the same launch, not new features. */
private fun isBareLauncherFilter(filter: Element): Boolean {
    val actions = filter.children("action").map { it.attr("android:name") }
    val categories = filter.children("category").map { it.attr("android:name") }
    val hasData = filter.children("data").isNotEmpty()
    return actions == listOf("android.intent.action.MAIN") &&
        categories.any { it in PURE_LAUNCHER_MARKERS } &&
        categories.all { it in KNOWN_LAUNCHER_CATEGORIES } &&
        !hasData
}

private fun describeFilter(filter: Element): String {
    val actions = filter.children("action").map { it.attr("android:name") }
    val categories = filter.children("category").map { it.attr("android:name") }
    val dataElements = filter.children("data")

    fun distinctAttr(attrName: String) =
        dataElements.map { it.attr(attrName) }.filter { it.isNotBlank() }.distinct()

    val schemes = distinctAttr("android:scheme")
    val hosts = distinctAttr("android:host")
    val mimeTypes = distinctAttr("android:mimeType")
    val pathPrefixes = distinctAttr("android:pathPrefix")
    val pathPatternCount = dataElements.count { it.attr("android:pathPattern").isNotBlank() }

    val dataParts = buildList {
        if (schemes.isNotEmpty()) add("scheme=${schemes.joinToString("|")}")
        if (hosts.isNotEmpty()) add("host=${hosts.joinToString("|")}")
        if (mimeTypes.isNotEmpty()) add("mimeType=${mimeTypes.joinToString("|")}")
        if (pathPrefixes.isNotEmpty()) add("pathPrefix=${pathPrefixes.joinToString("|")}")
        if (pathPatternCount > 0) add("pathPattern×$pathPatternCount")
    }

    return buildString {
        append("action=").append(actions.joinToString("+").ifBlank { "none" })
        if (categories.isNotEmpty()) append(" category=").append(categories.joinToString("+"))
        if (dataParts.isNotEmpty()) append(" data(").append(dataParts.joinToString(" ")).append(")")
    }
}

/**
 * The two names are so overwhelmingly the standard convention that finding them literally is
 * treated as conclusive on its own - skip the confidence score entirely. This only decides WHICH
 * activities are source/target; it does not skip the risky-intent-filter warning below, since even
 * a activity literally named SplashActivity can carry share/file-open filters that don't belong on
 * the renamed target (see the Xender case: SplashActivity also owns GREEN_LIST, REPLICATE_PHONE,
 * and several VIEW/data filters that MainActivity's code doesn't implement).
 */
private fun isExactSplashName(name: String) = name.substringAfterLast('.').equals("SplashActivity", ignoreCase = true)
private fun isExactMainName(name: String) = name.substringAfterLast('.').equals("MainActivity", ignoreCase = true)

/**
 * Merges a name hint ("...Splash..." in the class name) with a structural check (no intent-filter
 * content beyond a bare MAIN/LAUNCHER) into one confidence score, instead of requiring either one
 * alone to be conclusive. Either signal on its own can clear the threshold; filters that clearly
 * do real work (extra actions, deep-link data) pull the score back down so a busy "splash" activity
 * - one that also handles sharing, file-open, or other real intents - still gets refused by default.
 * Only reached when the exact-name shortcut above didn't already decide it.
 */
private fun splashConfidence(originalName: String, riskyFilters: List<Element>): Int {
    val nameHint = originalName.substringAfterLast('.').contains("splash", ignoreCase = true)
    val bare = riskyFilters.isEmpty()
    val hasData = riskyFilters.any { it.children("data").isNotEmpty() }
    return (if (nameHint) 4 else 0) + (if (bare) 4 else 0) -
        minOf(riskyFilters.size, 4) - (if (hasData) 2 else 0)
}

private val HUB_NAME_KEYWORDS = listOf("dashboard", "home", "main", "hub", "index")

/**
 * Scores every other activity declared by the app itself (same package prefix, so bundled
 * SDK/ad-library activities like com.google.android.gms.ads.AdActivity are never candidates)
 * to guess which one is the "real" main screen. singleTask/singleInstance launch mode is a
 * strong root-activity signal; VIEW intent-filters and general filter count suggest it's the hub.
 * taskAffinity="" is the standard marker for deliberately isolating an overlay/standalone screen
 * (e.g. an in-call UI) into its own task, so it's treated as strong evidence AGAINST being the
 * app's home screen even when it also happens to be singleTask + exported.
 */
private fun guessRealMainActivity(
    document: org.w3c.dom.Document,
    packageName: String,
    launcher: Element,
): Pair<Element, Int>? {
    return document.documentElement.children("activity")
        .filter { it !== launcher }
        .filter { it.resolvedName(packageName).startsWith("$packageName.") }
        .map { activity ->
            val filters = activity.children("intent-filter")
            var score = 0
            when (activity.attr("android:launchMode")) {
                "singleTask", "singleInstance" -> score += 3
                "singleTop" -> score += 1
            }
            // A bare VIEW filter with no <data> at all is typically an internal launch entry
            // (search, shortcuts, same-app navigation) - real hub-screen evidence. A VIEW filter
            // with specific <data> (scheme/mimeType), with or without BROWSABLE, is a content-type
            // handler for files/links from OTHER apps - a utility activity (a player screen that
            // accepts video/* or rtsp/mp4/etc.), not the home screen, even though it's often the
            // busiest and most "exported-looking" activity in the whole manifest. Reward the former,
            // penalize the latter instead of treating "more intent-filters" as uniformly positive.
            var genericView = false
            var contentHandlerFilters = 0
            filters.forEach { f ->
                val actions = f.children("action").map { it.attr("android:name") }
                val hasData = f.children("data").isNotEmpty()
                if (actions.contains("android.intent.action.VIEW")) {
                    if (hasData) contentHandlerFilters++ else genericView = true
                }
            }
            if (genericView) score += 3
            score -= minOf(contentHandlerFilters, 3) * 2
            if (activity.attr("android:alwaysRetainTaskState") == "true") score += 1
            if (activity.attr("android:exported") == "true") score += 1
            if (activity.attr("android:taskAffinity") == "") score -= 5
            // android.app.searchable / default_searchable meta-data marks the app's designated
            // content-browsing screen - a rare, specific signal, stronger than launch-mode guesses.
            if (activity.children("meta-data").any {
                    it.attr("android:name") in setOf("android.app.searchable", "android.app.default_searchable")
                }
            ) {
                score += 3
            }
            if (HUB_NAME_KEYWORDS.any { activity.attr("android:name").substringAfterLast('.').contains(it, ignoreCase = true) }) {
                score += 4
            }
            activity to score
        }
        .maxByOrNull { it.second }
}

// Universal patch (no compatibleWith) -> compatible with any package. `default` must stay false,
// per PatchBuilder.resolveDefaultValue(), since a universal patch cannot default to enabled.
@Suppress("unused")
val universalSkipSplashScreenPatch = resourcePatch(
    name = "Skip Splash Screen - Expert Only",
        description = "EXPERT USERS ONLY. Manually Configure It To Point At Real Splash And Main Activity As Many Apps Use Other Names. " +
        "Check the log to know what the patch is doing. " +
        "Ensure App Doesnt Ask For Permissions In Splash Screen.",
    default = false,
) {
    val sourceSuffixOption by stringOption(
        key = "sourceSuffix",
        default = null,
        title = "Splash activity name",
        description = "Type just the class name, e.g. \"SplashActivity\" - no leading dot, it's added " +
            "automatically. Leave blank to auto-detect the launcher activity and require it to score as " +
            "a confident splash guess.",
        required = false,
    )
    val targetSuffixOption by stringOption(
        key = "targetSuffix",
        default = null,
        title = "Real main activity name",
        description = "Type just the class name, e.g. \"MainActivity\" - no leading dot, it's added " +
            "automatically. Leave blank to auto-detect the app's own most main-screen-like activity.",
        required = false,
    )

    execute {
        document("AndroidManifest.xml").use { document ->
            val packageName = document.documentElement.attr("package")
            val launcher = findLauncherActivity(document)

            if (launcher == null) {
                logger.warning("Skip splash screen: no activity with a MAIN/LAUNCHER intent-filter found, nothing changed.")
                return@use
            }

            val originalName = launcher.attr("android:name")
            if (originalName.isBlank()) {
                logger.warning("Skip splash screen: launcher activity has no android:name, nothing changed.")
                return@use
            }

            val riskyFilters = launcher.children("intent-filter").filterNot(::isBareLauncherFilter)

            val forcedSource = sourceSuffixOption?.takeIf { it.isNotBlank() }
            if (forcedSource != null) {
                val suffix = ".${forcedSource.removePrefix(".")}"
                if (!originalName.endsWith(suffix)) {
                    logger.warning(
                        "Skip splash screen: launcher activity is \"$originalName\", which doesn't end with " +
                            "\"$suffix\" - nothing changed.",
                    )
                    return@use
                }
                logger.info("Skip splash screen: sourceSuffix forced; launcher activity is \"$originalName\".")
            } else if (isExactSplashName(originalName)) {
                logger.info(
                    "Skip splash screen: launcher activity \"$originalName\" is literally named " +
                        "SplashActivity - treating it as the splash without scoring.",
                )
            } else {
                val score = splashConfidence(originalName, riskyFilters)
                if (score < SPLASH_SCORE_THRESHOLD) {
                    logger.warning(
                        "Skip splash screen: launcher activity \"$originalName\" doesn't look confidently like " +
                            "a bare splash screen (confidence score $score, need >= $SPLASH_SCORE_THRESHOLD) - " +
                            "refusing to guess. Set sourceSuffix to force it if you've verified this by hand.",
                    )
                    return@use
                }
                logger.info(
                    "Skip splash screen: auto-detected launcher/splash activity \"$originalName\" " +
                        "(confidence score $score).",
                )
            }

            if (riskyFilters.isNotEmpty()) {
                logger.warning(
                    "Skip splash screen: \"$originalName\" also declares ${riskyFilters.size} intent-filter(s) " +
                        "beyond a bare launcher, which move to the renamed activity along with it - verify the " +
                        "target class actually handles these:\n" +
                        riskyFilters.joinToString("\n") { "  - ${describeFilter(it)}" },
                )
            }

            val forcedTarget = targetSuffixOption?.takeIf { it.isNotBlank() }
            val newName: String
            if (forcedTarget != null) {
                val sourceSuffix = ".${originalName.substringAfterLast('.')}"
                newName = originalName.removeSuffix(sourceSuffix) + ".${forcedTarget.removePrefix(".")}"
            } else {
                val exactMain = document.documentElement.children("activity")
                    .filter { it !== launcher }
                    .filter { it.resolvedName(packageName).startsWith("$packageName.") }
                    .firstOrNull { isExactMainName(it.attr("android:name")) }
                if (exactMain != null) {
                    newName = exactMain.resolvedName(packageName)
                    logger.info(
                        "Skip splash screen: found an activity literally named MainActivity " +
                            "(\"$newName\") - using it as the real main screen without scoring.",
                    )
                } else {
                    val guess = guessRealMainActivity(document, packageName, launcher)
                    if (guess == null) {
                        logger.warning(
                            "Skip splash screen: found no other activity belonging to package \"$packageName\" " +
                                "to use as the real main screen - nothing changed. Set targetSuffix manually.",
                        )
                        return@use
                    }
                    val (candidate, score) = guess
                    newName = candidate.resolvedName(packageName)
                    logger.info(
                        "Skip splash screen: auto-detected real main activity \"$newName\" (confidence score " +
                            "$score, higher is stronger - low scores are guesses, verify before shipping).",
                    )
                }
            }

            launcher.setAttribute("android:name", newName)
            logger.info("Skip splash screen: renamed launcher activity \"$originalName\" -> \"$newName\".")

            var aliasesRenamed = 0
            document.documentElement.children("activity-alias").forEach { alias ->
                if (alias.attr("android:targetActivity") == originalName) {
                    alias.setAttribute("android:targetActivity", newName)
                    aliasesRenamed++
                    logger.info(
                        "Skip splash screen: repointed activity-alias \"${alias.attr("android:name")}\" " +
                            "targetActivity to \"$newName\".",
                    )
                }
            }
            if (aliasesRenamed == 0) {
                logger.info("Skip splash screen: no activity-alias pointed at \"$originalName\".")
            }
        }
    }
}
