package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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

// Unregistered here - configureSmartEnhanceToastPatch registers it, so it's configured from there.
internal val skipPopupOption = booleanOption(
    key = "skipPopup",
    default = false,
    title = "Skip intro popup",
    description = "Tapping the menu item toggles Smart Enhance directly instead of showing the popup first.",
)

// name = null - only reached via configureSmartEnhanceToastPatch's dependsOn below.
internal val disableSmartEnhancePopupPatch = bytecodePatch(
    name = null,
    description = "Skips the \"Smart Enhance\" intro dialog on the player menu.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        if (skipPopupOption.value != true) return@execute

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

        // caseStart is the packed-switch's case-0 ENTRY POINT (the instruction the
        // switch table's :pswitch label physically points at). Removing it outright
        // (as a plain removeInstructions over the whole range used to do) strands
        // that label: dexlib2 walks it forward past every instruction removed at
        // that same index, so it ends up bound to whatever survives right after the
        // deleted range - here, the packed-switch-payload block itself, right past
        // this method's return-void. Tapping this menu item then jumps into that
        // data table instead of real code: instant class-verification failure for
        // the WHOLE onClick class (it's a shared multi-purpose listener used all
        // over the app, so the crash surfaces anywhere else that reuses it, not
        // just here). Fix: replaceInstruction() the case's first instruction
        // in-place so the label rides along with it, then remove/insert everything
        // else around that untouched first slot.
        method.removeInstructions(caseStart + 1, caseEnd - caseStart)
        method.replaceInstruction(caseStart, "iget-object p1, p0, $outerField")
        method.addInstructions(
            caseStart + 1,
            """
                check-cast p1, ${toggleMethodRef.definingClass}
                invoke-virtual {p1}, $toggleMethod
                return-void
            """.trimIndent(),
        )
    }
}
