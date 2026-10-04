package app.ftl.patches.alldownloader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private object AppOpenAdDialogShowFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/app/Activity;"),
    filters = listOf(
        methodCall(smali = "Landroid/app/Activity;->isDestroyed()Z"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_NEZ, MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.SGET_OBJECT, type = "L", location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        methodCall(smali = "Landroid/os/CountDownTimer;->start()Landroid/os/CountDownTimer;"),
    ),
    custom = { _, classDef ->
        classDef.type == APP_OPEN_MANAGER_CLASS ||
            classDef.superclass == APP_OPEN_MANAGER_BASE_CLASS
    },
)

val disableOpenAdOnResumePatch = bytecodePatch(
    name = "Disable ad dialog when reopening app",
    description = "Prevents the full-screen \"loading ad\" dialog from appearing when the app is " +
        "reopened after being minimized.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_ALL_VIDEO_DOWNLOADER)

    execute {
        AppOpenAdDialogShowFingerprint.let { fingerprint ->
            val timerGuardIndex = fingerprint.instructionMatches[3].index
            fingerprint.method.addInstructions(timerGuardIndex, "return-void")
        }
    }
}
