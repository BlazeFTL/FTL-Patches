package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.Label
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction20t
import org.w3c.dom.Element

private const val MENU_MORE_LAYOUT = "res/layout/menu_more.xml"

// Unregistered here - cleanSidebarShortcutsPatch registers it, so it's configured from there.
internal val hideVideoDisplayOption = booleanOption(key = "hideVideoDisplay", default = true, title = "Hide Video Display")

// name = null - only reached via cleanSidebarShortcutsPatch's dependsOn below.
internal val hideVideoDisplayPatch = resourcePatch(
    name = null,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        if (hideVideoDisplayOption.value != true) return@execute

        document(MENU_MORE_LAYOUT).use { document ->
            fun collapse(id: String, vararg marginAttrs: String) {
                val nodes = document.getElementsByTagName("*")
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as? Element ?: continue
                    val nodeId = node.getAttribute("android:id")
                    if (nodeId != "@id/$id" && nodeId != "@+id/$id") continue

                    node.setAttribute("android:visibility", "gone")
                    node.setAttribute("android:layout_width", "0dp")
                    node.setAttribute("android:layout_height", "0dp")
                    marginAttrs.forEach { node.setAttribute("android:$it", "0dp") }
                }
            }

            collapse("tv_video_display", "layout_marginLeft", "layout_marginTop")
            collapse("sw_video_display", "layout_marginRight")
        }
    }
}

private class AnyInvokeVirtualFilter(location: InstructionLocation = InstructionLocation.MatchAfterAnywhere()) :
    OpcodesFilter(listOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE), location)

context(patchContext: BytecodePatchContext)
private fun Fingerprint.target(matchIndex: Int): Label {
    val index = instructionMatches[matchIndex].index
    return (method.implementation!!.instructions[index] as BuilderOffsetInstruction).target
}

internal object BookmarkFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        fieldAccess(name = "ic_menu_bookmark", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

internal object FavouriteFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        fieldAccess(name = "ic_more_add_to_favourites", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

internal object AddToPlaylistFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.NEW_INSTANCE),
        fieldAccess(name = "ic_video_playlist_navigation", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

internal object TutorialFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.SGET_BOOLEAN),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        fieldAccess(name = "icon_tutorial", opcode = Opcode.SGET, location = MatchAfterImmediately()),
    ),
)

internal object PlayingQueueFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.NEW_INSTANCE),
        fieldAccess(name = "ic_video_playlist_white", opcode = Opcode.SGET, location = MatchAfterImmediately()),
        opcode(Opcode.DIV_INT_2ADDR, location = MatchAfterWithin(20)),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        AnyInvokeVirtualFilter(location = MatchAfterImmediately()),
        opcode(Opcode.GOTO, location = MatchAfterImmediately()),
    ),
)

val cleanSidebarShortcutsPatch = bytecodePatch(
    name = "Sidebar & Player Defaults",
    description = "Cleans the player sidebar and More menu; sets default shortcuts and subtitle view.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)
    dependsOn(hideVideoDisplayPatch, cleanSidebarMorePatch, defaultShortcutsPatch, openSubtitleSettingsByDefaultPatch)

    hideVideoDisplayOption()
    hideMoreMenuHelpOption()
    enableDefaultShortcutsOption()
    defaultShortcutsMaskOption()
    openSubtitleSettingsOption()

    val hideBookmark by booleanOption(key = "hideBookmark", default = true, title = "Hide Bookmark")
    val hideFavourite by booleanOption(key = "hideFavourite", default = true, title = "Hide Favourite")
    val hideAddToPlaylist by booleanOption(key = "hideAddToPlaylist", default = true, title = "Hide Add to Playlist")
    val hideTutorial by booleanOption(key = "hideTutorial", default = true, title = "Hide Tutorial")
    val hidePlayingQueue by booleanOption(key = "hidePlayingQueue", default = true, title = "Hide Playing Queue")

    execute {
        if (hideBookmark == true) {
            val m = BookmarkFingerprint
            m.method.replaceInstruction(
                m.instructionMatches[1].index,
                BuilderInstruction20t(Opcode.GOTO_16, m.target(1)),
            )
        }

        val addToPlaylistStart = AddToPlaylistFingerprint.instructionMatches[0].index
        val originalFavouriteTarget = FavouriteFingerprint.target(1)

        if (hideFavourite == true) {
            val m = FavouriteFingerprint
            val narrowedTarget = m.method.implementation!!.newLabelForIndex(addToPlaylistStart)
            m.method.replaceInstruction(
                m.instructionMatches[1].index,
                BuilderInstruction20t(Opcode.GOTO_16, narrowedTarget),
            )
        }

        if (hideAddToPlaylist == true) {
            AddToPlaylistFingerprint.method.replaceInstruction(
                addToPlaylistStart,
                BuilderInstruction20t(Opcode.GOTO_16, originalFavouriteTarget),
            )
        }

        if (hideTutorial == true) {
            val m = TutorialFingerprint
            m.method.replaceInstruction(
                m.instructionMatches[1].index,
                BuilderInstruction20t(Opcode.GOTO_16, m.target(1)),
            )
        }

        if (hidePlayingQueue == true) {
            val m = PlayingQueueFingerprint
            m.method.replaceInstruction(
                m.instructionMatches[0].index,
                BuilderInstruction20t(Opcode.GOTO_16, m.target(5)),
            )
        }
    }
}
