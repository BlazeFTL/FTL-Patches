package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val SMART_ENHANCE_SKIP_POPUP_KEY = "smart_enhance_skip_popup"

// Anchored on the two real string literals (prefs key + dialog fragment tag)
// and the real SharedPreferences.getBoolean() SDK call between them. The
// enclosing OnClickListener class and its outer-activity field/toggle method
// are all obfuscated and rename every build, so none of them are pinned -
// they're read off the matched instructions instead (see execute {} below).
internal object SmartEnhanceMenuClickFingerprint : Fingerprint(
    name = "onClick",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        string("KEY_PLAYER_MENU_ENHANCE_NEW"),
        methodCall(
            smali = "Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z",
            location = MatchAfterImmediately(),
        ),
        string("LocalPlayerEnhanceIntroDialog"),
    ),
)

internal val disableSmartEnhancePopupPatch = bytecodePatch(
    name = null,
    description = "Lets Mod Settings skip the \"Smart Enhance\" intro dialog on the player menu.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)
    dependsOn(modSettingsPatch, modSettingFlagPatch(SMART_ENHANCE_SKIP_POPUP_KEY))

    execute {
        val method = SmartEnhanceMenuClickFingerprint.method
        val matches = SmartEnhanceMenuClickFingerprint.instructionMatches

        // matches[0] = const-string "KEY_PLAYER_MENU_ENHANCE_NEW" (7 instructions into the case)
        // matches[2] = const-string "LocalPlayerEnhanceIntroDialog" (11 instructions before return-void)
        val caseStart = matches[0].index - 7
        val caseEnd = matches[2].index + 11
        val instructions = method.implementation!!.instructions

        // Outer ActivityScreen field on this OnClickListener (originally "Lql;->c").
        val outerFieldRef = (instructions[caseStart + 1] as ReferenceInstruction).reference as FieldReference
        // ActivityScreen's real toggle+apply method (originally "Ha").
        val toggleMethodRef = (instructions[caseEnd - 1] as ReferenceInstruction).reference as MethodReference

        val outerField = "${outerFieldRef.definingClass}->${outerFieldRef.name}:${outerFieldRef.type}"
        val toggleParams = toggleMethodRef.parameterTypes.joinToString("") { it.toString() }
        val toggleMethod =
            "${toggleMethodRef.definingClass}->${toggleMethodRef.name}($toggleParams)${toggleMethodRef.returnType}"

        val scratch = matches[0].getInstruction<OneRegisterInstruction>().registerA
        val thisRegister = method.implementation!!.registerCount - method.parameters.size - 1
        if (scratch == thisRegister) throw PatchException("Scratch register collides with this in the Smart Enhance click handler")

        val stockStart = method.getInstruction(matches[0].index)

        // "New" badge: stock persists the seen state through this same pref key after the intro
        // dialog. Skipping the case skipped that write, so the badge came back on every new
        // player session. Read the SharedPreferences register and the getBoolean default off
        // the stock call; first-run state is the default, so seen = !default. Semantic-free.
        val getBoolean = matches[1].getInstruction<Instruction>()
        var prefsReg = -1
        var defReg = -1
        when (getBoolean) {
            is FiveRegisterInstruction -> {
                prefsReg = getBoolean.registerC
                defReg = getBoolean.registerE
            }
            is RegisterRangeInstruction -> {
                prefsReg = getBoolean.startRegister
                defReg = getBoolean.startRegister + 2
            }
        }
        var defaultValue: Boolean? = null
        if (defReg >= 0) {
            for (i in matches[0].index - 1 downTo caseStart) {
                val insn = instructions[i]
                if (insn is OneRegisterInstruction && insn.registerA == defReg) {
                    if ((insn.opcode == Opcode.CONST_4 || insn.opcode == Opcode.CONST_16) && insn is NarrowLiteralInstruction) {
                        defaultValue = insn.narrowLiteral != 0
                    }
                    break
                }
            }
        }
        val markSeen = if (prefsReg >= 0 && prefsReg != scratch && defaultValue != null) {
            val fn = if (defaultValue) "markNewSeenFalse" else "markNewSeenTrue"
            """
                move-object/from16 p1, v$prefsReg
                invoke-static/range {p1 .. p1}, $ENHANCE_CONFIG_CLASS->$fn(Ljava/lang/Object;)V
            """.trimIndent()
        } else {
            throw PatchException("Smart Enhance click handler: could not resolve the prefs register / default for the New badge")
        }

        method.addInstructionsWithLabels(
            matches[0].index,
            """
                const-string v$scratch, "$SMART_ENHANCE_SKIP_POPUP_KEY"
                invoke-static/range {v$scratch .. v$scratch}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                move-result v$scratch
                if-eqz v$scratch, :stock
                $markSeen
                iget-object p1, p0, $outerField
                check-cast p1, ${toggleMethodRef.definingClass}
                invoke-virtual {p1}, $toggleMethod
                return-void
            """.trimIndent(),
            ExternalLabel("stock", stockStart),
        )
    }
}
