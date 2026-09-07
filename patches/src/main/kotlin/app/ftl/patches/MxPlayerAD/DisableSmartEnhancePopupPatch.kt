package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
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

val disableSmartEnhancePopupPatch = bytecodePatch(
    name = "Disable Smart Enhance popup",
    description = "Skips the \"Smart Enhance\" intro dialog on the player menu - tapping the " +
        "menu item toggles Smart Enhance directly instead of showing the popup first.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

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

        // caseStart is the packed-switch case's own label target (":pswitch_281"
        // in the original dump). removeInstructions() there would delete the
        // labeled instruction outright, and dexlib2 re-homes the orphaned label
        // onto whatever instruction next occupies that slot once every edit below
        // is done - which, since this case sits right before the method's
        // packed-switch-data table, ends up being the data table itself. That
        // makes the switch jump straight into the raw jump table instead of into
        // this case's code (VerifyError: "encountered data table in instruction
        // stream"). replaceInstruction() on just this first slot keeps the label
        // anchored to real code; only the rest of the old body is removed/added.
        method.replaceInstruction(caseStart, "iget-object p1, p0, $outerField")
        method.removeInstructions(caseStart + 1, caseEnd - caseStart)
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
