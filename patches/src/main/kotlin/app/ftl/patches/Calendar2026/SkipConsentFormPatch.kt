package app.ftl.patches.calendar2026

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val skipConsentFormPatch = bytecodePatch(
    name = "Skip consent form",
    description = "Skips the consent form request on launch and continues the splash flow directly."
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        SplashRequestConsentFormFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                invoke-direct {p0, v0}, $SPLASH_ACTIVITY->starttimer(I)V
                return-void
            """
        )
    }
}
