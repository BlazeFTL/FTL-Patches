package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22c
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import org.w3c.dom.Element

// name = null - only reached via cleanMeTabTilesPatch's dependsOn below.
internal val cleanMeTabMenusPatch = resourcePatch(
    name = null,
    description = "Removes Add to Playlist, File Transfer, and Private Folder from the " +
        "per-file \"more\" sheet, and drops Private Folder and File Transfer from the " +
        "multi-select toolbar overflow.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        // fragment_more_bottom_sheet_dialog.xml: static layout, each row a fixed-id
        // LinearLayout. Code elsewhere still findViewById()s these rows, so hide -
        // visibility=gone + 0dp - rather than remove from the tree.
        document("res/layout/fragment_more_bottom_sheet_dialog.xml").use { doc ->
            for (id in listOf("ll_add_to_playlist", "transfer_share", "option_private_folder")) {
                doc.byId(id).apply {
                    setAttribute("android:visibility", "gone")
                    setAttribute("android:layout_width", "0dp")
                    setAttribute("android:layout_height", "0dp")
                }
            }
        }

        // list_action_mode.xml: menu <item>s aren't pre-bound views, so they're
        // removed outright - matches the reference (items absent from Mod, not
        // android:visible="false").
        document("res/menu/list_action_mode.xml").use { doc ->
            for (id in listOf("option_private_folder", "mx_share")) {
                doc.byId(id).let { it.parentNode.removeChild(it) }
            }
        }
    }
}

// Document is parsed without namespace-awareness, so "android:id" is the literal
// attribute name, not a namespace-split one.
private fun org.w3c.dom.Document.byId(id: String): Element {
    val nodes = getElementsByTagName("*")
    for (i in 0 until nodes.length) {
        val element = nodes.item(i) as Element
        if (element.getAttribute("android:id") == "@id/$id") return element
    }
    throw NoSuchElementException("no element with id @id/$id")
}

// MediaListFragment.G3(Ljg;Landroid/view/Menu;)Z inflates list_action_mode.xml, then
// conditionally strips items - same idea as cleanMeTabMenusPatch, but "Add to Playlist"
// isn't declared in that XML, and the split action-mode toolbar (G0 = its Add to
// Playlist button) is a *second* view inflated from a different layout, built entirely
// in this method. Both need a bytecode edit instead of a resource one.
internal val MediaListActionModeFingerprint = Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/list/MediaListFragment;",
    returnType = "Z",
    // First param (Ljg;) is obfuscated, second is the real Menu passed to inflate.
    parameters = listOf("L", "Landroid/view/Menu;"),
    filters = listOf(
        // Edit 1 lands right after this - the only menu inflate call in the method.
        methodCall(smali = "Landroid/view/MenuInflater;->inflate(ILandroid/view/Menu;)V"),
        // Existing "Add to Playlist" id lookup (still used below to bind the G0
        // button). Reused for edit 1 instead of hardcoding the obfuscated R$id class
        // it lives in - that class renames every build, the field name doesn't.
        fieldAccess(name = "add_2_playlist", type = "I", opcode = Opcode.SGET),
        // The View field assigned right after is G0 - name looks minifier-generated
        // (A0..G0 run sequentially in this method), so it's matched by shape (type +
        // opcode, right after the id lookup above), never by name, and its reference
        // is reused the same way for edit 2.
        fieldAccess(
            type = "Landroid/view/View;",
            opcode = Opcode.IPUT_OBJECT,
            location = InstructionLocation.MatchAfterWithin(4),
        ),
        // Unique, unobfuscated end-of-method anchor - sits immediately after the block
        // edit 2 replaces.
        methodCall(smali = "Landroid/view/ViewGroup;->addView(Landroid/view/View;)V"),
    ),
)

// name = null - only reached via cleanMeTabTilesPatch's dependsOn below.
internal val cleanMeTabActionModeMenuPatch = bytecodePatch(
    name = null,
    description = "Removes Add to Playlist from the multi-select overflow menu and " +
        "hides its button in the split action-mode toolbar.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        val matches = MediaListActionModeFingerprint.instructionMatches
        val method = MediaListActionModeFingerprint.method

        val inflateIndex = matches[0].index
        val addToPlaylistIdRef = (matches[1].instruction as ReferenceInstruction).reference
        val g0Instruction = matches[2].instruction
        val g0FieldRef = (g0Instruction as ReferenceInstruction).reference
        val thisRegister = (g0Instruction as TwoRegisterInstruction).registerB
        val addViewIndex = matches[3].index
        // Parameter registers are always the method's last N slots; this method has
        // 3 (this, p1, p2), all single-width, so p1 = registerCount - 2. p1 is dead
        // right after inflate() - the next original instruction overwrites it - and
        // the reference build confirms it's safe scratch here.
        val p1Register = method.implementation!!.registerCount - 2

        // Edit 2 first (higher index) so edit 1's insertion below doesn't shift it.
        // Stock: a flag check that, when false, makes the split toolbar's Add to
        // Playlist button (G0) visible and clickable. Mod: unconditionally GONE, no
        // click listener. The :cond_14d label stays bound to the addView call itself,
        // so capturing that instruction first is all label-safety needs.
        val cond14d = method.getInstruction(addViewIndex)
        method.removeInstructions(addViewIndex - 6, 6)
        method.addInstruction(
            addViewIndex - 6,
            BuilderInstruction22c(Opcode.IGET_OBJECT, 0, thisRegister, g0FieldRef),
        )
        method.addInstructionsWithLabels(
            addViewIndex - 5,
            """
                if-eqz v0, :cond_14d
                const/16 v1, 0x8
                invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
            """.trimIndent(),
            ExternalLabel("cond_14d", cond14d),
        )

        // Edit 1: drop "Add to Playlist" from the overflow menu right after inflate,
        // same as the reference build - reusing the id lookup found above instead of
        // a fresh hardcoded one.
        method.addInstruction(
            inflateIndex + 1,
            BuilderInstruction21c(Opcode.SGET, p1Register, addToPlaylistIdRef),
        )
        method.addInstructions(
            inflateIndex + 2,
            "invoke-interface {p2, v$p1Register}, Landroid/view/Menu;->removeItem(I)V",
        )
    }
}

// name = null keeps this out of the top-level patch list - cleanMeTabPatch pulls it
// in via dependsOn, so the user only sees one "Clean Me Tab" toggle.
internal val cleanMeTabTilesPatch = bytecodePatch(
    name = null,
    description = "Removes the Music Player and Cloud Drive tiles, and forces the " +
        "MX Share and Private Folder tiles off.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    // Resource + action-mode edits can't run inside this patch's own execute block
    // (different context types), so they ride along as dependencies instead. Enabling
    // this one toggle now also strips Add to Playlist / MX Share / Private Folder from
    // the per-file "more" sheet, the multi-select overflow menu, and its split toolbar.
    dependsOn(cleanMeTabMenusPatch, cleanMeTabActionModeMenuPatch)

    execute {
        val matches = LocalMeTilesFingerprint.stringMatches
        val mxShareIndex = matches[0].index
        val privateFolderIndex = matches[1].index
        val musicPlayerIndex = matches[4].index
        val cloudDriveIndex = matches[5].index
        val method = LocalMeTilesFingerprint.method

        method.removeInstructions(cloudDriveIndex - 3, 6)
        method.removeInstructions(musicPlayerIndex - 3, 6)

        method.replaceInstructions(privateFolderIndex - 6, "const/4 v1, 0x0\nnop")
        method.replaceInstructions(mxShareIndex - 6, "const/4 v1, 0x0\nnop")
    }
}
