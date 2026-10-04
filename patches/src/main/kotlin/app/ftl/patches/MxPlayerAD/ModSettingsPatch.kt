package app.ftl.patches.mxplayerad

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val ME_PAGE_LAYOUT = "res/layout/layout_local_me_page_fragment.xml"
private const val MOD_SETTINGS_ROW_CLASS = "app.ftl.extension.mxplayerad.ModSettingsRow"
private const val MOD_FLAGS_DIR = "assets/ftl_mod"
private const val MOD_ICON_FILE = "res/drawable/ic_me_tab_mod_settings.xml"
private const val APP_NAMESPACE = "http://schemas.android.com/apk/res-auto"
private const val MOD_VIEW_HIDER_CLASS = "app.ftl.extension.mxplayerad.ModViewHider"

internal const val KEY_ME_HIDE_STATUS_SAVER = "me_hide_status_saver"
internal const val KEY_ME_HIDE_LEGAL_HELP = "me_hide_legal_help"
internal const val KEY_ME_HIDE_TILES_PAGER = "me_hide_tiles_pager"
internal const val KEY_ME_HIDE_MUSIC_PLAYER = "me_hide_music_player"
internal const val KEY_ME_HIDE_CLOUD_DRIVE = "me_hide_cloud_drive"
internal const val KEY_ME_SHOW_NETWORK_STREAM = "me_show_network_stream"
internal const val KEY_HIDE_PRIVATE_FOLDER = "hide_private_folder"
internal const val KEY_HIDE_FILE_TRANSFER = "hide_file_transfer"
internal const val KEY_HIDE_ADD_TO_PLAYLIST = "hide_add_to_playlist"
internal const val KEY_SIDEBAR_HIDE_BOOKMARK = "sidebar_hide_bookmark"
internal const val KEY_SIDEBAR_HIDE_FAVOURITE = "sidebar_hide_favourite"
internal const val KEY_SIDEBAR_HIDE_ADD_TO_PLAYLIST = "sidebar_hide_add_to_playlist"
internal const val KEY_SIDEBAR_HIDE_TUTORIAL = "sidebar_hide_tutorial"
internal const val KEY_SIDEBAR_HIDE_PLAYING_QUEUE = "sidebar_hide_playing_queue"
internal const val KEY_SIDEBAR_HIDE_VIDEO_DISPLAY = "sidebar_hide_video_display"
internal const val KEY_SIDEBAR_HIDE_HELP = "sidebar_hide_help"
internal const val KEY_SUBTITLE_OPEN_SETTINGS = "subtitle_open_settings"
internal const val KEY_SHORTCUTS_CHANGE_DEFAULTS = "shortcuts_change_defaults"
internal const val KEY_HOME_HIDE_BOTTOM_BAR = "home_hide_bottom_bar"
internal const val KEY_ME_HIDE_RECYCLE_BIN = "me_hide_recycle_bin"

internal fun Element.addModViewHider(key: String, target: String, mode: String) {
    val parent = parentNode as? Element
        ?: error("@id/$target is the layout root, so a hider cannot be added next to it.")
    val marker = ownerDocument.createElement(MOD_VIEW_HIDER_CLASS)
    marker.setAttribute("android:layout_width", "0dp")
    marker.setAttribute("android:layout_height", "0dp")
    marker.setAttribute("android:visibility", "gone")
    marker.setAttribute("android:tag", "$key|$target|$mode")
    parent.insertBefore(marker, this)
}

private val MOD_ICON_XML = """
    <?xml version="1.0" encoding="utf-8"?>
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="24dp"
        android:height="24dp"
        android:viewportWidth="24"
        android:viewportHeight="24">
        <path
            android:fillColor="#00000000"
            android:strokeColor="#FFFFFFFF"
            android:strokeWidth="1.8"
            android:strokeLineCap="round"
            android:strokeLineJoin="round"
            android:pathData="M4,7h2.8 M11.2,7H20 M6.8,7a2.2,2.2 0 1,0 4.4,0a2.2,2.2 0 1,0 -4.4,0 M4,12h9.8 M18.2,12H20 M13.8,12a2.2,2.2 0 1,0 4.4,0a2.2,2.2 0 1,0 -4.4,0 M4,17h1.8 M10.2,17H20 M5.8,17a2.2,2.2 0 1,0 4.4,0a2.2,2.2 0 1,0 -4.4,0" />
    </vector>
""".trimIndent() + "\n"

internal val addModSettingsEntryPatch = resourcePatch(
    name = null,
    description = "Adds a Mod Settings card to the Me tab, right after the Legal/Help group.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        get(MOD_ICON_FILE, false).apply {
            parentFile?.mkdirs()
            writeText(MOD_ICON_XML)
        }

        document(ME_PAGE_LAYOUT).use { document ->
            val root = document.documentElement
            val anchor = root.findById("group_b")
                ?: error("@id/group_b not found in $ME_PAGE_LAYOUT")
            val parent = anchor.parentNode as? Element
                ?: error("@id/group_b has no parent element in $ME_PAGE_LAYOUT")
            if (!parent.tagName.endsWith("LinearLayout") && !parent.tagName.endsWith("LinearLayoutCompat")) {
                error("Unsupported parent <${parent.tagName}> for @id/group_b in $ME_PAGE_LAYOUT")
            }

            if (root.getAttribute("xmlns:app").isEmpty()) {
                root.setAttribute("xmlns:app", APP_NAMESPACE)
            }

            fun node(tag: String, vararg attributes: Pair<String, String>): Element {
                val element = document.createElement(tag)
                attributes.forEach { (name, value) -> element.setAttribute(name, value) }
                return element
            }

            val row = node(
                MOD_SETTINGS_ROW_CLASS,
                "android:orientation" to "horizontal",
                "android:background" to "@drawable/mxskin__ott_me_group_bg__light",
                "android:foreground" to "?attr/selectableItemBackground",
                "android:layout_width" to "match_parent",
                "android:layout_height" to "@dimen/dp60",
                "android:layout_marginLeft" to "@dimen/dp12",
                "android:layout_marginRight" to "@dimen/dp12",
                "app:mxSkin" to "0x40",
            )

            row.appendChild(
                node(
                    "androidx.appcompat.widget.AppCompatImageView",
                    "android:layout_gravity" to "center_vertical",
                    "android:layout_width" to "@dimen/dp24",
                    "android:layout_height" to "@dimen/dp24",
                    "android:src" to "@drawable/ic_me_tab_mod_settings",
                    "android:layout_marginStart" to "@dimen/dp12",
                    "app:mxSkin" to "0x2",
                    "app:tint" to "@color/mxskin__505a78_dadde4__light",
                ),
            )
            row.appendChild(
                node(
                    "androidx.appcompat.widget.AppCompatTextView",
                    "android:textSize" to "16sp",
                    "android:textStyle" to "normal",
                    "android:textColor" to "@color/mxskin__505a78_dadde4__light",
                    "android:layout_gravity" to "center_vertical",
                    "android:layout_width" to "0dp",
                    "android:layout_height" to "wrap_content",
                    "android:layout_marginLeft" to "@dimen/dp12",
                    "android:layout_marginRight" to "@dimen/dp12",
                    "android:text" to "Mod Settings",
                    "android:layout_weight" to "1.0",
                    "app:fontFamily" to "@font/font_muli",
                    "app:mxSkin" to "0x4",
                ),
            )
            row.appendChild(
                node(
                    "androidx.appcompat.widget.AppCompatImageView",
                    "android:layout_gravity" to "center_vertical",
                    "android:layout_width" to "16dp",
                    "android:layout_height" to "16dp",
                    "android:src" to "@drawable/ic_me_tab_right_arrow",
                    "android:layout_marginEnd" to "16dp",
                    "app:mxSkin" to "0x2",
                    "app:tint" to "@color/mxskin__96a2ba_85929c__light",
                ),
            )

            parent.insertBefore(row, anchor.nextSibling)
        }
    }
}

internal val modSettingsPatch = bytecodePatch(
    name = null,
    description = "Merges the Mod Settings extension and adds its Me tab entry.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(addModSettingsEntryPatch)

    extendWith("extensions/mxplayerad.mpe")
}

internal fun modSettingFlagPatch(key: String) = resourcePatch(
    name = null,
    description = "Lists \"$key\" in Mod Settings.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        get("$MOD_FLAGS_DIR/$key", false).writeText("1")
    }
}
