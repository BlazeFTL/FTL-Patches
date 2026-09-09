package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch

// Unregistered here - cleanSidebarShortcutsPatch registers it, so it's configured from there.
internal val hideMoreMenuHelpOption = booleanOption(
    key = "hideMoreMenuHelp",
    default = true,
    title = "Hide More menu Help section",
    description = "Hides What's New, Features, FAQ, Check for Update, Bug Report, and About.",
)

// name = null - only reached via cleanSidebarShortcutsPatch's dependsOn below.
internal val cleanSidebarMorePatch = resourcePatch(
    name = null,
    description = "Hides the Help section from the sidebar's More menu.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        if (hideMoreMenuHelpOption.value != true) return@execute

        document("res/layout/menu_sub_more.xml").use { document ->
            val root = document.documentElement
            root.findById("textView4")?.let {
                it.hide()
                it.collapse("android:layout_marginTop")
            }
            root.findById("tv_whats_new")?.collapse()
            root.findById("tv_features")?.collapse()
            root.findById("tv_faq")?.collapse()
            root.findById("tv_check_for_update")?.collapse()
            root.findById("tv_bug_report")?.collapse()
            root.findById("tv_about")?.collapse()
        }
    }
}
