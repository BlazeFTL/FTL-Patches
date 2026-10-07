package app.ftl.patches.firefox

import app.morphe.patcher.patch.resourcePatch

internal const val MOD_ICON_NAME = "ftl_ic_mod_settings"

private const val MOD_ICON_XML = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <group
        android:pivotX="12"
        android:pivotY="12"
        android:scaleX="-1">
        <path
            android:fillColor="#FF000000"
            android:fillType="nonZero"
            android:pathData="M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9z M11.5,9.5L9,4L6.5,9.5L1,12l5.5,2.5L9,20l2.5,-5.5L17,12L11.5,9.5z M9.99,12.99L9,15.17l-0.99,-2.18L5.83,12l2.18,-0.99L9,8.83l0.99,2.18l2.18,0.99L9.99,12.99z M19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z" />
    </group>
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
