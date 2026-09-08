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
import org.w3c.dom.Element

// No definingClass/name: the enclosing class and method are both fully
// obfuscated (3-char class, single-letter method) with nothing real to pin.
// Anchored purely on the real Android SDK call (setVisibility) and opcode
// shape of the method's tail:
//   iget-object, setVisibility(I)V, iget-object, setVisibility(I)V,
//   iput-boolean, return-void
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
        "leftover-visible-view bug fixed and the overlay text trimmed to just the speed. " +
        "\"No UI\": the overlay never shows at all - the speed change itself still applies, " +
        "since that's handled elsewhere.",
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
        // --- Resource half: trim "%1$s Speed Playing" -> "%1$s" ---
        document("res/values/strings.xml").use { document ->
            val strings = document.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val element = strings.item(i) as? Element ?: continue
                if (element.getAttribute("name") == "speed_ff_2x_tip") {
                    element.textContent = "%1\$s"
                }
            }
        }

        // --- Bytecode half: unchanged, already verified on device ---
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
            // (d = small chip, e = big overlay) INVISIBLE and return, so the
            // stock postDelayed(...) auto-show runnable is never scheduled.
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

        // 2x UI: swap the instant branch (cond_91) so the small chip (d) stays
        // VISIBLE and the big overlay (e) is INVISIBLE - fixes the stock
        // leftover-visible-view bug.
        val dSetVisibility = matches[1] // invoke-virtual {p1, v3}, setVisibility (view d)
        val eSetVisibility = matches[3] // invoke-virtual {p1, v2}, setVisibility (view e)

        val dVisReg = dSetVisibility.getInstruction<FiveRegisterInstruction>().registerD
        val eVisReg = eSetVisibility.getInstruction<FiveRegisterInstruction>().registerD

        // Insert the higher index first so the lower index stays valid.
        method.addInstructions(eSetVisibility.index, "const/4 v$eVisReg, 0x4") // e -> INVISIBLE
        method.addInstructions(dSetVisibility.index, "const/4 v$dVisReg, 0x0") // d -> VISIBLE
    }
}
