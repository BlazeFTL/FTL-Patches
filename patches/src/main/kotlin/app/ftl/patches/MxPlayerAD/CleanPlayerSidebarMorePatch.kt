package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.resourcePatch

// name = null - only reached via cleanSidebarShortcutsPatch's dependsOn below.
internal val cleanSidebarMorePatch = resourcePatch(
    name = null,
    description = "Adds a Mod Settings switch for the sidebar's More menu Help section.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(modSettingsPatch, modSettingFlagPatch(KEY_SIDEBAR_HIDE_HELP))

    execute {
        document("res/layout/menu_sub_more.xml").use { document ->
            val root = document.documentElement
            root.findById("textView4")
                ?.addModViewHider(KEY_SIDEBAR_HIDE_HELP, "textView4", "both+m")
            listOf(
                "tv_whats_new",
                "tv_features",
                "tv_faq",
                "tv_check_for_update",
                "tv_bug_report",
                "tv_about",
            ).forEach { id ->
                root.findById(id)?.addModViewHider(KEY_SIDEBAR_HIDE_HELP, id, "collapse")
            }
        }
    }
}
