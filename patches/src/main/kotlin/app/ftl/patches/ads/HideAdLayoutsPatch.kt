package app.ftl.patches.ads

import app.morphe.patcher.patch.resourcePatch

private val AD_ID_TAG = Regex(
    "android:id=\"@id/(?i)(ads?|banner)[a-z_]*\"|android:id=\"@\\+id/(?i)(ads?|banner|nativead)[a-z_]*\"",
)
private val ADVIEW_TAG = Regex("<com\\.google\\.android\\.gms\\.ads\\.AdView")
private val WIDTH_ATTR = Regex("android:layout_width=\"[^\"]*\"")
private val HEIGHT_ATTR = Regex("android:layout_height=\"[^\"]*\"")
private val VISIBILITY_ATTR = Regex("android:visibility=\"[^\"]*\"")

private fun hideAdElements(xml: String): String {
    if (!AD_ID_TAG.containsMatchIn(xml) && !ADVIEW_TAG.containsMatchIn(xml)) return xml

    return xml.lineSequence().joinToString("\n") { line ->
        if (!AD_ID_TAG.containsMatchIn(line) && !ADVIEW_TAG.containsMatchIn(line)) return@joinToString line

        var patched = line
        patched = WIDTH_ATTR.replace(patched, "android:layout_width=\"0.0dip\"")
        patched = HEIGHT_ATTR.replace(patched, "android:layout_height=\"0.0dip\"")
        patched = if (VISIBILITY_ATTR.containsMatchIn(patched)) {
            VISIBILITY_ATTR.replace(patched, "android:visibility=\"gone\"")
        } else {
            "$patched android:visibility=\"gone\""
        }
        patched
    }
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
