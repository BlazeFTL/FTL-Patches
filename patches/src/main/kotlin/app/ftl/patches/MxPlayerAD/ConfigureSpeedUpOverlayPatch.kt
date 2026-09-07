package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

// No definingClass/name: the enclosing class and method are both fully
// obfuscated (3-char class, single-letter method) with nothing real to pin.
// Anchored purely on the real Android SDK call (setVisibility) and opcode
// shape of the method's tail:
//   iget-object, setVisibility(I)V, iget-object, setVisibility(I)V,
//   iput-boolean, return-void
// - the two consecutive iget-object+setVisibility pairs immediately
// followed by iput-boolean+return-void (the method's end) is a distinctive
// enough shape to be unique in the app. This is the release/deactivate
// branch of the long-press SpeedUp overlay; the entrance/activate branch
// (the scale-up animation) comes earlier in the same method and isn't
// otherwise touched by the "2x UI" option - only "no UI" bypasses it.
internal object SpeedUpOverlayFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.IGET_OBJECT),
        methodCall(smali = "Landroid/view/View;->setVisibility(I)V", location = MatchAfterImmediately()),
        opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        methodCall(smali = "Landroid/view/View;->setVisibility(I)V", location = MatchAfterImmediately()),
        opcode(Opcode.IPUT_BOOLEAN, location = MatchAfterImmediately()),
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
    ),
)

val configureSpeedUpOverlayPatch = bytecodePatch(
    name = "Configure SpeedUp overlay",
    description = "\"2x UI\": keeps the long-press SpeedUp overlay/animation, with the stock " +
        "leftover-visible-view bug fixed. \"No UI\": the overlay never shows at all - the " +
        "speed change itself still applies, since that's handled elsewhere.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    val noUi by booleanOption(
        key = "noUi",
        default = false,
        title = "No UI",
        description = "On: the SpeedUp overlay never shows. Off: 2x UI, with the leftover-view bug fixed.",
    )

    execute {
        val method = SpeedUpOverlayFingerprint.method

        if (noUi == true) {
            // Skip the whole method: no measuring, no scale animation, no
            // visibility changes, no auto-hide Handler scheduling.
            method.addInstructions(0, "return-void")
            return@execute
        }

        // Second setVisibility call in the release branch - force its
        // argument register to View.INVISIBLE (4) right before the call,
        // whatever it held before (stock leaves it View.VISIBLE, a bug).
        val secondCall = SpeedUpOverlayFingerprint.instructionMatches[3]
        val paramReg = secondCall.getInstruction<FiveRegisterInstruction>().registerD

        method.addInstructions(secondCall.index, "const/4 v$paramReg, 0x4")
    }
}
