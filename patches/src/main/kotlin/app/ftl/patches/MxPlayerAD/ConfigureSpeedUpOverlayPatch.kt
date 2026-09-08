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
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

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
        val matches = SpeedUpOverlayFingerprint.instructionMatches

        // Both view fields, read off the same fingerprint match used below for
        // the 2x-UI branch - not hardcoded, since "d"/"e" are obfuscated and
        // reshuffle every build same as everything else on this class.
        val firstFieldRef = matches[0].getInstruction<ReferenceInstruction>().reference as FieldReference
        val secondFieldRef = matches[2].getInstruction<ReferenceInstruction>().reference as FieldReference
        val firstField = "${firstFieldRef.definingClass}->${firstFieldRef.name}:${firstFieldRef.type}"
        val secondField = "${secondFieldRef.definingClass}->${secondFieldRef.name}:${secondFieldRef.type}"

        if (noUi == true) {
            // A bare return-void here was wrong: it only skips whatever THIS
            // specific call would have done, it doesn't guarantee the views
            // are actually hidden - something else can still leave/set them
            // visible, which is why the overlay was showing up ~2-3s late
            // instead of never. Force both views INVISIBLE unconditionally
            // (regardless of p1/caller), null-checked since either can be
            // null depending on which layout variant got inflated.
            method.addInstructions(
                0,
                """
                    const/4 v0, 0x4
                    iget-object v1, p0, $firstField
                    if-eqz v1, :skip_first
                    invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
                    :skip_first
                    iget-object v1, p0, $secondField
                    if-eqz v1, :skip_second
                    invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
                    :skip_second
                    return-void
                """.trimIndent(),
            )
            return@execute
        }

        // Second setVisibility call in the release branch - force its
        // argument register to View.INVISIBLE (4) right before the call,
        // whatever it held before (stock leaves it View.VISIBLE, a bug).
        // Verified correct against a real build compare - unchanged.
        val secondCall = matches[3]
        val paramReg = secondCall.getInstruction<FiveRegisterInstruction>().registerD

        method.addInstructions(secondCall.index, "const/4 v$paramReg, 0x4")
    }
}
