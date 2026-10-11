package app.ftl.patches.firefox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private val COMPATIBILITY_FIREFOX_FULLSCREEN = Compatibility(
    packageName = "org.mozilla.fenix",
    name = "Firefox Nightly",
    targets = listOf(
        AppTarget(version = "159.0a1"),
        AppTarget(version = "160.0a1", versionCode = 2016189527),
    ),
)

private object FullscreenToastFingerprint : Fingerprint(
    definingClass = "Lorg/mozilla/fenix/browser/BaseBrowserFragment;",
    returnType = "V",
    parameters = listOf("Z"),
    custom = { method, _ -> method.name.startsWith("fullScreenChanged") },
    filters = listOf(
        methodCall(
            smali = "Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)" +
                "Landroid/widget/Toast;",
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        methodCall(smali = "Landroid/widget/Toast;->show()V", location = MatchAfterImmediately()),
    ),
)

@Suppress("unused")
val hideFullscreenToastPatch = bytecodePatch(
    name = "Hide fullscreen toast",
    description = "Removes the toast shown when a video goes fullscreen. Toggle in Mod Settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FIREFOX_FULLSCREEN)

    extendWith("extensions/firefox.mpe")

    execute {
        val fingerprint = FullscreenToastFingerprint
        val method = fingerprint.method
        val call = fingerprint.instructionMatches[0].index
        val show = fingerprint.instructionMatches[2].index
        val scratch = Liveness(method).takeLow(1, call)[0]

        method.applyEdits(
            insert(
                call,
                """
                    invoke-static {}, $MOD_SETTINGS->hideFullscreenToast()Z
                    move-result v$scratch
                    if-nez v$scratch, :ftl_skip_toast
                """,
                mapOf("ftl_skip_toast" to { show + 1 }),
            ),
        )
    }
}
