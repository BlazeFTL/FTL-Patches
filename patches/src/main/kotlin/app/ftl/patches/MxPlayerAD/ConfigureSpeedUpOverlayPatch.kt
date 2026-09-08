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

// Anchored on the release/instant tail of SpeedViewManager.a(Z)V:
//   iget-object d, setVisibility, iget-object e, setVisibility,
//   iput-boolean c, return-void
// Class/method are fully obfuscated, so we pin on the SDK call + opcode shape.
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
    description =
        "\"2x UI\": keeps the long-press SpeedUp overlay/animation, with the stock " +
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

        // Both view fields, read off the fingerprint match (d/e are obfuscated
        // and reshuffle every build, so never hardcode them).
        val firstFieldRef = matches[0].getInstruction<ReferenceInstruction>().reference as FieldReference
        val secondFieldRef = matches[2].getInstruction<ReferenceInstruction>().reference as FieldReference
        val firstField = "${firstFieldRef.definingClass}->${firstFieldRef.name}:${firstFieldRef.type}"
        val secondField = "${secondFieldRef.definingClass}->${secondFieldRef.name}:${secondFieldRef.type}"

        if (noUi == true) {
            // No UI: stub out the whole show/hide method. Force BOTH views
            // (d = small chip, e = big overlay) INVISIBLE and return. Because
            // this sits at the top and returns, the stock postDelayed(...)
            // auto-show runnable is never scheduled - so nothing can pop the
            // overlay back in a couple of seconds.
            method.addInstructions(
                0,
                """
                const/4 v0, 0x4
                iget-object v1, p0, $firstField
                if-eqz v1, :cond_a
                invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
                :cond_a
                iget-object v1, p0, $secondField
                if-eqz v1, :cond_14
                invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
                :cond_14
                return-void
                """.trimIndent(),
            )
            return@execute
        }

        // 2x UI: fix the leftover-visible bug in the instant branch (cond_91).
        // Stock cond_91 hides d (v3=4) AND shows e (v2=0) - same net result as
        // the animated branch - leaving a stale big overlay on screen. Swap it:
        // show the small chip (d -> 0 VISIBLE), hide the big overlay (e -> 4
        // INVISIBLE). We set the visibility argument registers directly rather
        // than rewrite the invoke operands, which is equivalent to swapping
        // v2<->v3 on those two calls.
        val dSetVisibility = matches[1] // invoke-virtual {p1, v3}, setVisibility (view d)
        val eSetVisibility = matches[3] // invoke-virtual {p1, v2}, setVisibility (view e)

        val dVisReg = dSetVisibility.getInstruction<FiveRegisterInstruction>().registerD
        val eVisReg = eSetVisibility.getInstruction<FiveRegisterInstruction>().registerD

        // Insert the higher index first so the lower index stays valid.
        method.addInstructions(eSetVisibility.index, "const/4 v$eVisReg, 0x4") // e -> INVISIBLE
        method.addInstructions(dSetVisibility.index, "const/4 v$dVisReg, 0x0") // d -> VISIBLE
    }
}
