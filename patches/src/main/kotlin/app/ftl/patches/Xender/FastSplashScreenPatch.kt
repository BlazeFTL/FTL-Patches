package app.ftl.patches.xender

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch

/**
 * Matches the permission-result handler in Xender's SplashActivity.
 * The method is deliberately identified by its class, name, parameter, and return type
 * because the supplied v18.8.0.prime build uses a stable semantic method name here.
 */
private object SplashPermissionResultFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/SplashActivity;",
    name = "handleCheckPermissionGrantCode",
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(
            smali = "Lcn/xender/ui/activity/SplashActivity;->delayCreateData()V",
        ),
    ),
)

/**
 * Removes the extra external-storage check from the splash permission-success path
 * and enters the main activity directly.
 */
val fastSplashScreenPatch = bytecodePatch(
    name = "Speed up splash screen",
    description = "Enters the main activity directly after the splash permission check instead of delaying for the additional external-storage check.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    execute {
        SplashPermissionResultFingerprint.let { fingerprint ->
            val delayCallIndex = fingerprint.instructionMatches.first().index
            fingerprint.method.removeInstruction(delayCallIndex)
            fingerprint.method.addInstructions(
                delayCallIndex,
                """
                    const/4 v0, 0x0
                    invoke-virtual {p0, v0}, Lcn/xender/ui/activity/SplashActivity;->toMainActivity(Landroid/os/Bundle;)V
                """.trimIndent(),
            )
        }
    }
}
