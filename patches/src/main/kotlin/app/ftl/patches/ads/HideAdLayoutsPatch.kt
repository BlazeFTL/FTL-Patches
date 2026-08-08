package app.ftl.patches.ads

import app.morphe.patcher.patch.resourcePatch

private val TAG_SPAN = Regex("<[^>]+>")
private val AD_ID_ATTR = Regex(
    "android:id=\"@\\+?id/(ads?|banner)[a-z_]*\"|android:id=\"@\\+?id/nativead[a-z_]*\"",
    RegexOption.IGNORE_CASE,
)
private val ADVIEW_TAG = Regex("^<com\\.google\\.android\\.gms\\.ads\\.AdView\\b")
private val WIDTH_ATTR = Regex("android:layout_width=\"[^\"]*\"")
private val HEIGHT_ATTR = Regex("android:layout_height=\"[^\"]*\"")
private val VISIBILITY_ATTR = Regex("android:visibility=\"[^\"]*\"")

private fun hideAdElements(xml: String): String =
    TAG_SPAN.replace(xml) { match ->
        val tag = match.value
        val isAdTag = AD_ID_ATTR.containsMatchIn(tag) || ADVIEW_TAG.containsMatchIn(tag)
        if (!isAdTag || !WIDTH_ATTR.containsMatchIn(tag) || !HEIGHT_ATTR.containsMatchIn(tag)) {
            return@replace tag
        }

        var patched = tag
        patched = WIDTH_ATTR.replace(patched, "android:layout_width=\"0.0dip\"")
        patched = HEIGHT_ATTR.replace(patched, "android:layout_height=\"0.0dip\"")
        patched = if (VISIBILITY_ATTR.containsMatchIn(patched)) {
            VISIBILITY_ATTR.replace(patched, "android:visibility=\"gone\"")
        } else {
            patched.replaceFirst(Regex("\\s*/?>$"), " android:visibility=\"gone\"${if (patched.trimEnd().endsWith("/>")) " />" else ">"}")
        }
        patched
    }

val hideAdLayoutsPatch = resourcePatch(
    name = "Hide ad layouts",
    description = "Zeroes size and hides visibility of ad-related view containers in layout XML.",
) {
    execute {
        val layoutDir = get("res/layout", false)
        layoutDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
            .forEach { file -> file.writeText(hideAdElements(file.readText())) }
    }
}
