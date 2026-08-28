package app.ftl.patches.mxplayer

import app.morphe.patcher.patch.resourcePatch

val hideLocalTilesPatch = resourcePatch(
    name = "Hide top tiles",
    description = "Hides the top tiles.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)

    execute {
        document("res/layout/layout_local_tiles.xml").use { document ->
            val root = document.documentElement
            root.collapse()
            root.findById("tiles_list")?.collapse(
                "android:layout_marginTop",
                "android:layout_marginBottom",
            )
        }
    }
}
