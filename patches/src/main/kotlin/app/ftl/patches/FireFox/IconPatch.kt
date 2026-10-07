package app.ftl.patches.firefox

import app.morphe.patcher.patch.resourcePatch

internal const val MOD_ICON_NAME = "ftl_ic_mod_settings"

private const val MOD_ICON_XML = """<?xml version="1.0" encoding="utf-8"?>
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
        android:pathData="M14.8,5Q16.7,10.1 21.8,12Q16.7,13.9 14.8,19Q12.9,13.9 7.8,12Q12.9,10.1 14.8,5Z" />
    <path
        android:fillColor="#FF000000"
        android:strokeColor="#FF000000"
        android:strokeWidth="1"
        android:strokeLineJoin="round"
        android:pathData="M3.7,2.3Q4.1,4.3 6.1,4.7Q4.1,5.1 3.7,7.1Q3.3,5.1 1.3,4.7Q3.3,4.3 3.7,2.3Z" />
    <path
        android:fillColor="#FF000000"
        android:strokeColor="#FF000000"
        android:strokeWidth="1"
        android:strokeLineJoin="round"
        android:pathData="M3.7,18.3Q4.1,20.3 6.1,20.7Q4.1,21.1 3.7,23.1Q3.3,21.1 1.3,20.7Q3.3,20.3 3.7,18.3Z" />
</vector>
"""

val modIconPatch = resourcePatch {
    execute {
        get("res/drawable/$MOD_ICON_NAME.xml", copy = false).apply {
            parentFile?.mkdirs()
            writeText(MOD_ICON_XML)
        }
    }
}
