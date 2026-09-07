package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// invoke-virtual vs. invoke-virtual/range (and invoke-static vs. .../range) is
// picked by the compiler purely from register pressure in the method, not by
// the source code - the same call can compile either way across builds/splits
// of the same app version. Matching both forms avoids a spurious fingerprint
// failure over a build detail that carries no semantic difference.
private class AnyInvokeVirtualFilter(location: InstructionLocation = InstructionLocation.MatchAfterAnywhere()) :
    OpcodesFilter(listOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE), location)

private class AnyInvokeStaticFilter(location: InstructionLocation = InstructionLocation.MatchAfterAnywhere()) :
    OpcodesFilter(listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE), location)

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
//   iget-object ...->I:...               (same field, disable branch)
//   invoke-virtual ...->E0(I)V
//   sget    ...->smart_enhance_disabled:I  (R-class field name - never obfuscated)
//   invoke-static ...->c(Landroid/content/Context;IZ)V  (toast helper, obfuscated)
//   return-void                          (method's real end - array-data payloads
//                                          from the dead middle block follow after
//                                          this, so the true end of the method's
//                                          instruction list is NOT a safe anchor)
//
// Chaining both halves into one fingerprint (with a bounded gap over the dead
// middle block) guarantees the two anchor points resolve to the same method,
// without ever pinning an obfuscated name.
internal object SmartEnhanceToggleFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ActivityScreen;",
    filters = listOf(
        AnyInvokeVirtualFilter(),
        opcode(Opcode.SGET_BOOLEAN, location = MatchAfterImmediately()),
        literal(-1, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        AnyInvokeVirtualFilter(location = MatchAfterImmediately()),
        AnyInvokeVirtualFilter(location = MatchAfterImmediately()), // e8(I)V - insertion point follows
        opcode(Opcode.IGET_OBJECT, location = MatchAfterWithin(250)),     // disable branch starts
        AnyInvokeVirtualFilter(location = MatchAfterImmediately()),
        opcode(Opcode.SGET, location = MatchAfterImmediately()),          // smart_enhance_disabled id
        AnyInvokeStaticFilter(location = MatchAfterImmediately()),        // disable toast call
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
    ),
)

val configureSmartEnhanceToastPatch = bytecodePatch(
    name = "Configure Smart Enhance toast",
    description = "Always skips the Smart Enhance popup animation and always drops the " +
        "disable-side toast. \"Show toast\" on: fires a toast when Smart Enhance is enabled. " +
        "Off: enabling is silent too.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

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
        val disabledIdRef = matches[9].getInstruction<ReferenceInstruction>().reference as FieldReference
        val disabledToastRef = matches[10].getInstruction<ReferenceInstruction>().reference as MethodReference

        val toastMethod = "${disabledToastRef.definingClass}->${disabledToastRef.name}(" +
            disabledToastRef.parameterTypes.joinToString("") { it.toString() } +
            ")${disabledToastRef.returnType}"
        // Same R-class as the disable id; R field names are never obfuscated,
        // only the sibling field differs (enabled vs disabled).
        val enabledIdField = "${disabledIdRef.definingClass}->smart_enhance_enabled:${disabledIdRef.type}"

        // Disable-branch toast is always dropped (matches the reference build).
        method.removeInstructions(matches[9].index, 2)

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
