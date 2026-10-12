package app.ftl.patches.xplayer

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val disableRateDialogPatch = bytecodePatch(
    name = "Disable Rate Dialog",
    description = "Disables the rate dialog counters so it never appears."
) {
    compatibleWith(
        Compatibility(
            name = "XPlayer - Video Player",
            packageName = "video.player.videoplayer",
            targets = listOf(AppTarget(version = "2.9.2"))
        )
    )

    execute {
        RateCountFingerprint.method.addInstruction(0, "return-void")
    }
}
