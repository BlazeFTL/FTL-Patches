package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Matches the method that resolves the enabled-shortcuts bitmask, via the 3 real (unobfuscated)
 * SharedPreferences key literals it migrates across app versions ("shortcuts_flag.3" -> current,
 * ".2" and the unsuffixed key -> legacy). This 3-key combo is unique app-wide. Neither the class
 * nor the method name are pinned - both are obfuscated and reshuffle every build.
 *
 * Instruction shape (stock, all versions since the 3-key migration was introduced):
 *   const-string "shortcuts_flag.3" -> getInt(default=-1) -> move-result v_acc   [accumulator]
 *   ... contains("shortcuts_flag.3") check, returns early if true ...
 *   ... contains("shortcuts_flag.2") check, migrates+returns if true ...
 *   const-string "shortcuts_flag" -> contains(...) -> move-result v_check       [boolean]
 *   if-eqz v_check, :cond   <- true means "never customized in any app version" -> return v_acc
 *                              (v_acc is still exactly -1 here, the getInt default from the top)
 *
 * Bit 0x80 (the gap between equalizer 0x40 and sleep timer 0x100) is missing from every
 * inspectable consumer of this int - confirmed byte-identical, registers included, between MX
 * Player 3.1.4 and 3.2.2 (both the patched method itself and its only other reader, an unrelated
 * "shortcutsShown" analytics logger). It's the leftover slot from the oldest legacy format - the
 * pre-".2" migration loop below still walks a mask up through 0x80, so the bit is real, not dead.
 * Reported symptom: enabling "Change default shortcuts" hides the player's PiP button on 3.2.2,
 * but not on 3.1.4; disabling the toggle fixes it. Working theory: MX Player reused this exact
 * slot for a newer shortcut (PiP suspected) sometime after 3.1.4, without updating the analytics
 * label array. This can't be confirmed from a patched-classes diff alone - the icon-rendering
 * consumer isn't one of the classes any patch touches, so it never shows up there. Given that,
 * the fix below doesn't guess what 0x80 is: it just stops touching it, so whatever MX Player
 * defaults it to (enabled, on the fresh-install path this fingerprint targets) survives - whether
 * that turns out to be PiP or something else, now or in a future build that reuses some other
 * unlisted bit the same way.
 */
private object DefaultShortcutsFingerprint : Fingerprint(
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(
        string("shortcuts_flag.3"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterWithin(5)),
        string("shortcuts_flag.2", location = MatchAfterWithin(40)),
        string("shortcuts_flag", location = MatchAfterWithin(40)),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterWithin(5)),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
    ),
)

// Unregistered here - cleanSidebarShortcutsPatch registers both, so they're configured from there.
internal val enableDefaultShortcutsOption = booleanOption(
    key = "enableDefaultShortcuts",
    default = true,
    title = "Change default shortcuts",
    description = "Settings > Player > Customize Shortcuts still works and always wins once touched.",
)
internal val defaultShortcutsMaskOption = intOption(
    key = "defaultShortcutsMask",
    default = 0x780F,
    title = "Default shortcuts bitmask (hex)",
    description = "Bit sum of enabled shortcuts, decimal or 0x-prefixed hex. Bits: 0x1 rotation, " +
        "0x2 speed, 0x4 background play, 0x8 loop, 0x10 mute, 0x20 shuffle, 0x40 equalizer, " +
        "0x100 sleep timer, 0x200 repeat A-B, 0x400 night mode, 0x800 customise items, " +
        "0x1000 screenshot, 0x2000 mirror mode, 0x4000 vertical flip, 0x8000 audio effect. " +
        "Bit 0x80 isn't in this list (unused pre-3.2 legacy slot, suspected PiP shortcut on " +
        "newer MX Player builds) and is never touched no matter what you set here - it always " +
        "keeps MX Player's own default. Default 0x780F.",
    validator = { it != null && it in 0..0xFFFF },
)

// Every bit this patch is allowed to force. 0x80 is deliberately excluded - see fingerprint doc.
private const val MANAGED_BITS_MASK = 0xFF7F

// ~MANAGED_BITS_MASK as a 32-bit AND-clear literal (0xFFFF0080): clears only the managed bits,
// leaves 0x80 and every bit above 0xFFFF exactly as SharedPreferences.getInt's -1 default left it.
private const val CLEAR_MANAGED_BITS_LITERAL = "-0xff80"

// name = null - only reached via cleanSidebarShortcutsPatch's dependsOn below.
internal val defaultShortcutsPatch = bytecodePatch(
    name = null,
    description = "Changes which shortcuts are enabled by default for installs that never " +
        "customized them.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        if (enableDefaultShortcutsOption.value != true) return@execute

        val method = DefaultShortcutsFingerprint.method
        val matches = DefaultShortcutsFingerprint.instructionMatches

        // Accumulator register (holds -1 unconditionally at this point - see fingerprint doc above).
        val accumulatorRegister = matches[1].getInstruction<OneRegisterInstruction>().registerA
        // Boolean register from contains("shortcuts_flag") - dead right after the branch below on
        // both paths (original code immediately reassigns it, or we return before it's read again),
        // so it doubles as our scratch register.
        val checkRegister = matches[4].getInstruction<OneRegisterInstruction>().registerA
        val ifEqzIndex = matches[5].index
        // Captured as an instruction object, not an index, so the external label still resolves
        // correctly after the insertion below shifts every later index forward.
        val ifEqzInstruction = method.implementation!!.instructions[ifEqzIndex]

        val maskHex = "0x%x".format((defaultShortcutsMaskOption.value ?: 0x780F) and MANAGED_BITS_MASK)

        // Inserted immediately before the original if-eqz, which is left completely untouched:
        // when the key exists (v_check != 0, the "customized before" case) this new check falls
        // straight through to it unchanged. Only the never-customized case is short-circuited.
        //
        // The accumulator is merged, not overwritten: clear only the managed bits (leaving 0x80
        // and bits 16-31 exactly as -1 left them), then OR in the user's chosen managed bits. A
        // plain "const v_acc, $maskHex; return v_acc" would force every bit outside the 15 named
        // ones off too - including whatever MX Player uses 0x80 for today, or adds next.
        method.addInstructionsWithLabels(
            ifEqzIndex,
            """
                if-nez v$checkRegister, :original_check
                const v$checkRegister, $CLEAR_MANAGED_BITS_LITERAL
                and-int/2addr v$accumulatorRegister, v$checkRegister
                const v$checkRegister, $maskHex
                or-int/2addr v$accumulatorRegister, v$checkRegister
                return v$accumulatorRegister
            """.trimIndent(),
            ExternalLabel("original_check", ifEqzInstruction),
        )
    }
}
