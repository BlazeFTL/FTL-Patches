package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.resourcePatch

private val OPTIONS_MENU_FILES = listOf(
    "res/menu/list.xml",
    "res/menu/menu_list_local_only.xml",
)

private const val ME_TOOLBAR_ACTION_LAYOUT = """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:background="?attr/actionBarItemBackground"
    android:layout_width="48dp"
    android:layout_height="match_parent"
    android:paddingEnd="16dp">
    <androidx.appcompat.widget.AppCompatImageView
        android:layout_gravity="center"
        android:id="@+id/iv_me_toolbar"
        android:layout_width="24dp"
        android:layout_height="24dp" />
</FrameLayout>
"""

// name = null keeps this out of the top-level patch list - the "Disable Bottom Bar
// And Add Me Tab To Top" patch pulls it in via dependsOn.
internal val addMeTabMenuResourcePatch = resourcePatch(
    name = null,
    description = "Adds a Me tab action-view item to the options menu.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        get("res/layout/me_toolbar_action.xml", false).apply {
            parentFile?.mkdirs()
            writeText(ME_TOOLBAR_ACTION_LAYOUT)
        }

        OPTIONS_MENU_FILES.forEach { path ->
            document(path).use { document ->
                val root = document.documentElement

                // Make sure the app: namespace is actually declared on the root,
                // regardless of whether the decompiled file already carried it -
                // a bare setAttribute("app:...", ...) below has no namespace
                // binding of its own, so a later strict re-parse (e.g. while
                // registering the new @+id) will throw "Undefined Prefix: app"
                // if this declaration isn't textually present.
                if (root.getAttribute("xmlns:app").isEmpty()) {
                    root.setAttribute("xmlns:app", "http://schemas.android.com/apk/res-auto")
                }

                val item = document.createElement("item")
                item.setAttribute("android:id", "@+id/me_toolbar_action")
                item.setAttribute("android:visible", "true")
                item.setAttribute("android:menuCategory", "container")
                item.setAttribute("android:orderInCategory", "5")
                item.setAttribute("android:title", "Me")
                item.setAttribute("app:actionLayout", "@layout/me_toolbar_action")
                item.setAttribute("app:showAsAction", "always")

                root.appendChild(item)
            }
        }
    }
}
