package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.resourcePatch

val cleanSidebarMorePatch = resourcePatch(
    name = "Clean sidebar More menu",
    description = "Hides the \"Help\" section (What's New, Features, FAQ, Check for Update, " +
        "Bug Report, About) from the sidebar's More menu.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
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
