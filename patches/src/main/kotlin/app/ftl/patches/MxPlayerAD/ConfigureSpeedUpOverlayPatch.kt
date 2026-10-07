package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import org.w3c.dom.Element

private const val SPEEDUP_NO_UI_KEY = "speedup_no_ui"

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
        "Fixes the stock leftover-visible-view bug in the long-press SpeedUp overlay. " +
        "\"No UI\" is toggled in Me tab > Mod Settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)
    dependsOn(fixSpeedUpTipStringPatch, modSettingsPatch, modSettingFlagPatch(SPEEDUP_NO_UI_KEY))

    execute {
        val method = SpeedUpOverlayFingerprint.method
        val matches = SpeedUpOverlayFingerprint.instructionMatches

        val firstFieldRef = matches[0].getInstruction<ReferenceInstruction>().reference as FieldReference
        val secondFieldRef = matches[2].getInstruction<ReferenceInstruction>().reference as FieldReference
        val firstField = "${firstFieldRef.definingClass}->${firstFieldRef.name}:${firstFieldRef.type}"
        val secondField = "${secondFieldRef.definingClass}->${secondFieldRef.name}:${secondFieldRef.type}"

        val dSetVisibility = matches[1]
        val eSetVisibility = matches[3]

        val dVisReg = dSetVisibility.getInstruction<FiveRegisterInstruction>().registerD
        val eVisReg = eSetVisibility.getInstruction<FiveRegisterInstruction>().registerD

        method.addInstructions(eSetVisibility.index, "const/4 v$eVisReg, 0x4")
        method.addInstructions(dSetVisibility.index, "const/4 v$dVisReg, 0x0")

        val stockStart = method.getInstruction(0)

        method.addInstructionsWithLabels(
            0,
            """
                const-string v0, "$SPEEDUP_NO_UI_KEY"
                invoke-static {v0}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :stock
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
            ExternalLabel("stock", stockStart),
        )
    }
}
