package app.ftl.patches.videodownloader

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

val skipTipsScreenPatch = bytecodePatch(
    name = "Skip tips screen",
    description = "Closes the onboarding tips screen as soon as it opens."
) {
    compatibleWith(VIDEO_DOWNLOADER_PACKAGE)

    execute {
        TipsActivityOnCreateFingerprint.let { fingerprint ->
            fingerprint.method.addInstructions(
                fingerprint.instructionMatches.first().index + 1,
                """
                    invoke-super {p0}, Landroid/app/Activity;->finish()V
                    return-void
                """
            )
        }
    }
}
