package app.ftl.patches.xplayer

import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val removeTutorialPatch = bytecodePatch(
    name = "Remove Tutorial",
    description = "Removes the player tutorial."
) {
    compatibleWith(
        Compatibility(
            name = "XPlayer - Video Player",
            packageName = "video.player.videoplayer",
            targets = listOf(AppTarget(version = "2.9.2"))
        )
    )

    execute {
        val match = VideoGuideFingerprint.match()

        match.method.removeInstruction(match.instructionMatches[7].index)
        match.method.removeInstruction(match.instructionMatches[3].index)
    }
}
