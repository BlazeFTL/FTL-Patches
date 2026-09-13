package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import org.w3c.dom.Element

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

// name = null - configureSpeedUpOverlayPatch pulls this in via dependsOn as part of the same toggle.
internal val fixSpeedUpTipStringPatch = resourcePatch(
    name = null,
    description = "Shortens the SpeedUp long-press tip from \"%1\$s Speed Playing\" to \"%1\$s\".",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        document("res/values/strings.xml").use { document ->
            val strings = document.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val element = strings.item(i) as Element
                if (element.getAttribute("name") == "speed_ff_2x_tip") {
                    element.textContent = "%1\$s"
                    break
                }
            }
        }
    }
}

val configureSpeedUpOverlayPatch = bytecodePatch(
    name = "Configure SpeedUp overlay",
    description =
        "\"2x UI\": keeps the long-press SpeedUp overlay/animation, with the stock " +
        "leftover-visible-view bug fixed. \"No UI\": the overlay never shows at all - the " +
        "speed change itself still applies, since that's handled elsewhere.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)
    dependsOn(fixSpeedUpTipStringPatch)

    val noUi by booleanOption(
        key = "noUi",
        default = false,
        title = "No UI",
        description = "On: the SpeedUp overlay never shows. Off: 2x UI, with the leftover-view bug fixed.",
    )

    execute {
        val method = SpeedUpOverlayFingerprint.method
        val matches = SpeedUpOverlayFingerprint.instructionMatches

        val firstFieldRef = matches[0].getInstruction<ReferenceInstruction>().reference as FieldReference
        val secondFieldRef = matches[2].getInstruction<ReferenceInstruction>().reference as FieldReference
        val firstField = "${firstFieldRef.definingClass}->${firstFieldRef.name}:${firstFieldRef.type}"
        val secondField = "${secondFieldRef.definingClass}->${secondFieldRef.name}:${secondFieldRef.type}"

        if (noUi == true) {
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

        val dSetVisibility = matches[1]
        val eSetVisibility = matches[3]

        val dVisReg = dSetVisibility.getInstruction<FiveRegisterInstruction>().registerD
        val eVisReg = eSetVisibility.getInstruction<FiveRegisterInstruction>().registerD

        method.addInstructions(eSetVisibility.index, "const/4 v$eVisReg, 0x4")
        method.addInstructions(dSetVisibility.index, "const/4 v$dVisReg, 0x0")
    }
}
