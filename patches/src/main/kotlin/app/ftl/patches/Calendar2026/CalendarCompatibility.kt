package app.ftl.patches.calendar2026

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_CALENDAR = Compatibility(
    name = "Calendar 2026",
    packageName = "com.agendaplanner.taskmangereventmangercalendar",
    targets = listOf(AppTarget(version = "2.1"))
)
