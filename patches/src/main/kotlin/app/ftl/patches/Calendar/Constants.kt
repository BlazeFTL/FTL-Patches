package app.ftl.patches.calendar

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val COMPATIBILITY_CALENDAR = Compatibility(
    packageName = "calendar.agenda.schedule.event.advance.calendar.planner",
    name = "Calendar",
    targets = listOf(AppTarget(version = "1.0.35")),
)

internal const val CAL_PKG = "Lcalendar/agenda/schedule/event/advance/calendar/planner"
internal const val BASE_ACT = "$CAL_PKG/BaseAct;"
internal const val SPLASH_ACTIVITY = "$CAL_PKG/activity/SplashActivity;"
internal const val LANGUAGE_ACTIVITY = "$CAL_PKG/activity/goal/LanguageActivity;"
internal const val PERMISSION_ACTIVITY = "$CAL_PKG/activity/PermissionActivity;"
internal const val AFTER_CALL_ACTIVITY = "$CAL_PKG/activity/AfterCallFeatureActivity;"
internal const val SETTING_ACTIVITY = "$CAL_PKG/activity/SettingActivity;"
internal const val SETTING_BINDING = "$CAL_PKG/databinding/ActivitySettingBinding;"
internal const val DASHBOARD_ACTIVITY = "$CAL_PKG/activity/CalendarDashboardActivity;"
internal const val RATE_US_FEEDBACK_ACTIVITY = "$CAL_PKG/activity/RateUsFeedbackActivity;"
internal const val RATE_US_MANAGER = "$CAL_PKG/utils/RateUsManager;"
internal const val ADS_UTILITY = "Lplugin/adsdk/service/AdsUtility;"
internal const val ANDROID_CONTEXT = "Landroid/content/Context;"
internal const val ANDROID_BUNDLE = "Landroid/os/Bundle;"
internal const val ANDROID_INTENT = "Landroid/content/Intent;"
