package app.ftl.patches.calendar

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

// The MT2 diff (info.json) carried versionCode 24011893 - the exact same
// value already used for an unrelated app elsewhere in this repo, so it's a
// stale/reused field from the compare tool session, not this app's real
// manifest versionCode. Using the versionCode given directly (34) instead.
internal val COMPATIBILITY_CALENDAR_PLANNER = Compatibility(
    packageName = "calendar.agenda.schedule.event.advance.calendar.planner",
    name = "Calendar",
    targets = listOf(
        AppTarget(version = "1.0.34", versionCode = 34),
    ),
)
