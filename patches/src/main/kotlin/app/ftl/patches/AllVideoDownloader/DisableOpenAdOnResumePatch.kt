package app.ftl.patches.alldownloader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

/**
 * The helper class that shows this dialog is fully obfuscated: a short, shuffled
 * package and a one/two-character class name (e.g. seen as one build's "hc.b0"),
 * not just an obfuscated method - so there is no class name safe to pin here at
 * all, per the "never pin obfuscated identifiers" rule.
 *
 * Instead the method itself is found structurally, with no class name declared:
 * a void method taking a single Activity parameter, which reads a same-class
 * CountDownTimer field near the top before deciding whether to show the dialog.
 * CountDownTimer is a real Android SDK type (not obfuscated) and is otherwise
 * unused in this app, so it works as a reliable, name-independent anchor for
 * this exact method regardless of which class it currently lives in.
 */
private object AppOpenAdDialogShowFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/app/Activity;"),
    filters = listOf(
        string("activity"),
        fieldAccess(
            definingClass = "this",
            type = "Landroid/os/CountDownTimer;",
            opcode = Opcode.SGET_OBJECT,
        ),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
    ),
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
            val guardIndex = fingerprint.instructionMatches[1].index
            fingerprint.method.addInstructions(guardIndex, "return-void")
        }
    }
}
