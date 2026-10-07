package app.ftl.patches.videodownloader

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch

val disableRateDialogsPatch = bytecodePatch(
    name = "Disable rate dialogs",
    description = "Prevents the rate and star rating dialogs from showing."
) {
    compatibleWith(VIDEO_DOWNLOADER_PACKAGE)

    execute {
        RateDialogShowFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """
        )

        StarRatingDialogBuilderFingerprint.let { fingerprint ->
            fingerprint.method.replaceInstruction(
                fingerprint.instructionMatches.last().index,
                "nop"
            )
        }
    }
}
