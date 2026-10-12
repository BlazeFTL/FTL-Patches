package app.ftl.patches.calendar2026

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val skipInitialSetupPagesPatch = bytecodePatch(
    name = "Skip initial setup pages",
    description = "Skips the phone, overlay and notification permission pages in the initial setup."
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        SplashCallPermissionCheckFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )

        SplashIntentMainActFingerprint.let {
            val match = it.instructionMatches[1]
            val register = match.getInstruction<OneRegisterInstruction>().registerA
            it.method.replaceInstruction(match.index, "const/4 v$register, 0x1")
        }

        MainCallPermissionCheckFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )

        MainCheckOverlayPermissionFingerprint.method.addInstructions(
            0,
            """
                invoke-direct {p0}, $MAIN_ACTIVITY->homeinterShowe()V
                return-void
            """
        )

        MainPermissionResultFingerprint.let {
            val checkPermission = it.instructionMatches[2]
            it.method.replaceInstruction(
                checkPermission.index,
                "invoke-direct {p0}, $MAIN_ACTIVITY->refreshCalendarDataFF()V"
            )

            val sdkInt = it.instructionMatches[1]
            val register = sdkInt.getInstruction<OneRegisterInstruction>().registerA
            it.method.replaceInstruction(sdkInt.index, "const/4 v$register, 0x0")
        }
    }
}
