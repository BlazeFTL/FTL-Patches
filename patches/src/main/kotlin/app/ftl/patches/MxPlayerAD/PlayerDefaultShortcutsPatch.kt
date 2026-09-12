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
        "Default 0x780F.",
    validator = { it != null && it in 0..0xFFFF },
)

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
        // Boolean register from contains("shortcuts_flag").
        val checkRegister = matches[4].getInstruction<OneRegisterInstruction>().registerA
        val ifEqzIndex = matches[5].index
        // Captured as an instruction object, not an index, so the external label still resolves
        // correctly after the insertion below shifts every later index forward.
        val ifEqzInstruction = method.implementation!!.instructions[ifEqzIndex]

        val maskHex = "0x%x".format(defaultShortcutsMaskOption.value ?: 0x780F)

        // Inserted immediately before the original if-eqz, which is left completely untouched:
        // when the key exists (v_check != 0, the "customized before" case) this new check falls
        // straight through to it unchanged. Only the never-customized case is short-circuited.
        method.addInstructionsWithLabels(
            ifEqzIndex,
            """
                if-nez v$checkRegister, :original_check
                const v$accumulatorRegister, $maskHex
                return v$accumulatorRegister
            """.trimIndent(),
            ExternalLabel("original_check", ifEqzInstruction),
        )
    }
}
