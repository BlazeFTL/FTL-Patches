package app.ftl.patches.firefox

import app.morphe.patcher.patch.resourcePatch

private fun tabIconXml(path: String) = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FF000000"
        android:strokeWidth="2"
        android:strokeLineJoin="round"
        android:strokeLineCap="round"
        android:pathData="$path" />
</vector>
"""

val tabIconsPatch = resourcePatch {
    execute {
        mapOf(
            "ftl_ic_scroll_up" to "M12,19L12,5M6,11L12,5L18,11",
            "ftl_ic_scroll_down" to "M12,5L12,19M6,13L12,19L18,13",
            "ftl_ic_tab_add_end" to "M12,2.5L12,10.5M8,6.5L16,6.5M12,14L12,21.5M8.5,18L12,21.5L15.5,18",
        ).forEach { (name, path) ->
            get("res/drawable/$name.xml", copy = false).apply {
                parentFile?.mkdirs()
                writeText(tabIconXml(path))
            }
        }
    }
}
