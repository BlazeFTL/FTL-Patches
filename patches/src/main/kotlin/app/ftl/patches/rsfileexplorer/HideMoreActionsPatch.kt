package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * The action ids to strip out of the "More actions" menu: Hide, Add to desktop,
 * Encrypt, Decrypt, Add bookmark, Web Search, Copy to, Move to, Transfer, Playing.
 */
private val HIDDEN_ACTION_IDS = setOf(
    "hide",
    "shortcut",
    "encrypt",
    "decrypt",
    "add_to_favorite",
    "web_search",
    "copy_to",
    "move_to",
    "transfer",
    "playing",
)

/**
 * Matches the "More actions" menu-builder method. Its own name is obfuscated,
 * and the enclosing class has a second, unrelated menu-builder method (for the
 * quick-actions bar) that references these same action-id strings once each
 * too — so "image_edit", a string unique to the "More actions" builder, is
 * included to disambiguate the two.
 */
private object MoreActionsMenuFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    strings = HIDDEN_ACTION_IDS.toList() + "image_edit",
)

val hideMoreActionsPatch = bytecodePatch(
    name = "Hide more actions",
    description = "Hides Hide, Add to desktop, Encrypt, Decrypt, Add bookmark, Web Search, Copy to, Move to, Transfer, and Playing from the \"More actions\" menu.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)

    execute {
        val method = MoreActionsMenuFingerprint.method

        // Renaming each id's const-string value breaks whatever exact-match
        // lookup the menu builder uses to place these entries, so they're still
        // built internally but never end up in the rendered menu — without
        // having to locate or touch that (unobfuscated-agnostic) lookup logic.
        method.implementation!!.instructions.forEachIndexed { index, instruction ->
            if (instruction.opcode != Opcode.CONST_STRING) return@forEachIndexed

            val value = ((instruction as ReferenceInstruction).reference as StringReference).string
            if (value !in HIDDEN_ACTION_IDS) return@forEachIndexed

            val register = (instruction as OneRegisterInstruction).registerA
            method.replaceInstruction(index, "const-string v$register, \"${value}_x\"")
        }
    }
}
