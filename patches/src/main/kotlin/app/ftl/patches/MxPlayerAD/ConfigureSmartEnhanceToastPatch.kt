package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// ActivityScreen is the app's own manifest-declared Activity - real, stable,
// safe to pin. Everything else here is opcode/literal shape only:
//
//   [enable branch]
//   invoke-virtual/range ...->Sa()V      (toggle+apply, obfuscated name)
//   sget-boolean ...->q:Z                (re-read the toggled flag, obfuscated field)
//   const/4 v5, -1                       (sentinel, unique literal)
//   if-eqz  ...                          (branch on the flag)
//   iget-object ...->I:...               (player-controls field, obfuscated)
//   invoke-virtual ...->E0(I)V           (obfuscated)
//   invoke-virtual ...->e8(I)V           <- enable-branch insertion point is right after this
//   ... (everything between is the animation/analytics block we leave dead) ...
//   [disable branch tail]
//   sget    ...->smart_enhance_disabled:I  (R-class field NAME - never obfuscated)
//   invoke-static ...->c(Landroid/content/Context;IZ)V  (toast helper, obfuscated)
//   return-void                          (method's real end - array-data payloads
//                                          from the dead middle block follow after
//                                          this, so the true end of the method's
//                                          instruction list is NOT a safe anchor)
//
// The dead middle block (analytics + EnhanceEffectView construction) also
// contains its own iget-object/invoke-virtual pairs on other fields, so
// anchoring the second half on a bare iget-object opcode is not safe - the
// filter chain locks onto the first one it finds (whichever field it belongs
// to) and, when nothing sget immediately follows the invoke-virtual after
// it, the whole match fails outright: the enable branch's Sa()V call is the
// only INVOKE_VIRTUAL_RANGE in the method, so there's no later occurrence
// left to retry against. The R-class field name is the one thing in that
// stretch that's both real and unique, so it anchors the second half
// directly instead.
internal object SmartEnhanceToggleFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ActivityScreen;",
    filters = listOf(
        opcode(Opcode.INVOKE_VIRTUAL_RANGE),
        opcode(Opcode.SGET_BOOLEAN, location = MatchAfterImmediately()),
        literal(-1, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()), // e8(I)V - insertion point follows
        fieldAccess(
            name = "smart_enhance_disabled",
            opcode = Opcode.SGET,
            location = MatchAfterWithin(300),
        ),
        opcode(Opcode.INVOKE_STATIC, location = MatchAfterImmediately()), // disable toast call
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
    ),
)

val configureSmartEnhanceToastPatch = bytecodePatch(
    name = "Configure Smart Enhance",
    description = "Configures the Smart Enhance intro popup and enable/disable toast.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)
    dependsOn(disableSmartEnhancePopupPatch)

    skipPopupOption()
    val showToast by booleanOption(
        key = "showToast",
        default = true,
        title = "Show toast on enable",
        description = "On: toast when Smart Enhance turns on. Off: fully silent toggle.",
    )

    execute {
        val matches = SmartEnhanceToggleFingerprint.instructionMatches
        val method = SmartEnhanceToggleFingerprint.method

        val insertIndex = matches[6].index + 1
        val disabledIdRef = matches[7].getInstruction<ReferenceInstruction>().reference as FieldReference
        val disabledToastRef = matches[8].getInstruction<ReferenceInstruction>().reference as MethodReference

        val toastMethod = "${disabledToastRef.definingClass}->${disabledToastRef.name}(" +
            disabledToastRef.parameterTypes.joinToString("") { it.toString() } +
            ")${disabledToastRef.returnType}"
        // Same R-class as the disable id; R field names are never obfuscated,
        // only the sibling field differs (enabled vs disabled).
        val enabledIdField = "${disabledIdRef.definingClass}->smart_enhance_enabled:${disabledIdRef.type}"

        // Disable-branch toast is always dropped (matches the reference build).
        method.removeInstructions(matches[7].index, 2)

        method.addInstructions(
            insertIndex,
            if (showToast == false) {
                "return-void"
            } else {
                """
                    sget v4, $enabledIdField
                    invoke-static {v0, v4, v1}, $toastMethod
                    return-void
                """.trimIndent()
            },
        )
    }
}
