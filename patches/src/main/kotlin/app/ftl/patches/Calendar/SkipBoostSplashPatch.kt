package app.ftl.patches.calendar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption

private const val SPLASH_ACTIVITY =
    "Lcalendar/agenda/schedule/event/advance/calendar/planner/activity/SplashActivity;"
private const val SPLASH_ACTIVITY_RUNNABLE =
    "Lcalendar/agenda/schedule/event/advance/calendar/planner/activity/SplashActivity\$1;"

val skipBoostSplashPatch = bytecodePatch(
    name = "Skip/Boost splash screen",
    description = "By default, skips the splash screen entirely on launch. Turn on \"Boost splash\" " +
        "to instead keep it briefly on screen (configurable delay) rather than skipping it outright. " +
        "Useless To Boost Splash Screen If You Select Unlock Premium Patch Too " +
        "Use If You Want To Skip Splash Screen Entirely",
    default = true,
) {
    compatibleWith(COMPATIBILITY_CALENDAR_PLANNER)

    val boostSplash by booleanOption(
        key = "boostSplash",
        default = false,
        title = "Boost splash instead of skipping",
        description = "Off: splash is skipped entirely. On: splash briefly shows, then proceeds " +
            "after the configured delay.",
    )

    val boostDelayMs by intOption(
        key = "boostDelayMs",
        default = 200,
        title = "Boost delay (ms)",
        description = "Only used when \"Boost splash\" is on. How long to show the splash before " +
            "proceeding.",
        validator = { it != null && it in 0..10000 },
    )

    execute {
        // Neutralize the remote-config-driven ad delay wherever it's fired from, so it can
        // never reintroduce a multi-second wait behind our own onCreate() edit below.
        SplashActivityAdDelayFingerprint.method.also { method ->
            val delayIndex = SplashActivityAdDelayFingerprint.instructionMatches.first().index
            method.removeInstructions(delayIndex, 4)
            method.addInstructions(delayIndex, "const-wide/16 v0, 0x0")
        }

        if (boostSplash == true) {
            // Keep the splash content view on screen, then proceed after the configured delay
            // using the app's own existing splash Runnable (already wired to call proceed()).
            // v0/v1 are this method's only non-parameter locals; p0/p1 (Bundle) are reused as
            // the wide delay register pair only after p0 has already been read below - safe
            // since everything past our own return-void becomes unreachable dead code.
            val insertIndex = SplashActivitySetContentViewFingerprint.instructionMatches.last().index + 1
            val delay = boostDelayMs ?: 200

            SplashActivitySetContentViewFingerprint.method.addInstructions(
                insertIndex,
                """
                    new-instance v0, Landroid/os/Handler;
                    invoke-direct {v0}, Landroid/os/Handler;-><init>()V
                    new-instance v1, $SPLASH_ACTIVITY_RUNNABLE
                    invoke-direct {v1, p0}, $SPLASH_ACTIVITY_RUNNABLE-><init>($SPLASH_ACTIVITY)V
                    const-wide/16 p0, $delay
                    invoke-virtual {v0, v1, p0, p1}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z
                    return-void
                """.trimIndent(),
            )
        } else {
            // Skip the splash entirely: call proceed() immediately after the real
            // superclass onCreate() runs, before any splash-specific setup happens.
            val insertIndex = SplashActivityOnCreateSuperFingerprint.instructionMatches.last().index + 1

            SplashActivityOnCreateSuperFingerprint.method.addInstructions(
                insertIndex,
                """
                    invoke-direct {p0}, $SPLASH_ACTIVITY->proceed()V
                    return-void
                """.trimIndent(),
            )
        }
    }
}
