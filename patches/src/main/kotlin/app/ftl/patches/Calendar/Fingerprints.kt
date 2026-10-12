package app.ftl.patches.calendar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

internal object IsPremiumUserFingerprint : Fingerprint(
    definingClass = ADS_UTILITY,
    name = "isPremiumUser",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf(ANDROID_CONTEXT),
)

internal object GetActiveSubscriptionFingerprint : Fingerprint(
    definingClass = ADS_UTILITY,
    name = "getActiveSubscription",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf(ANDROID_CONTEXT),
)

internal object SettingOnResumeFingerprint : Fingerprint(
    definingClass = SETTING_ACTIVITY,
    name = "onResume",
    returnType = "V",
    parameters = listOf(),
)

internal object SettingInitClickFingerprint : Fingerprint(
    definingClass = SETTING_ACTIVITY,
    name = "initClick",
    returnType = "V",
    parameters = listOf(),
)

internal object SplashConsentCallbackFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY,
    returnType = "V",
    parameters = listOf("Ljava/lang/Boolean;"),
    filters = listOf(
        string("yyyy-MM-dd"),
    ),
)

internal object SplashOnCreateFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY,
    name = "onCreate",
    returnType = "V",
    parameters = listOf(ANDROID_BUNDLE),
)

internal object SplashDestinationIntentFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY,
    name = "destinationIntent",
    returnType = ANDROID_INTENT,
    parameters = listOf(),
    filters = listOf(
        string("shownlanguage"),
    ),
)

internal object LanguageNextIntentFingerprint : Fingerprint(
    definingClass = LANGUAGE_ACTIVITY,
    name = "getNextIntent",
    returnType = ANDROID_INTENT,
    parameters = listOf(),
)

internal object PermissionOnCreateFingerprint : Fingerprint(
    definingClass = PERMISSION_ACTIVITY,
    name = "onCreate",
    returnType = "V",
    parameters = listOf(ANDROID_BUNDLE),
)

internal object AfterCallOnCreateFingerprint : Fingerprint(
    definingClass = AFTER_CALL_ACTIVITY,
    name = "onCreate",
    returnType = "V",
    parameters = listOf(ANDROID_BUNDLE),
)

internal object AfterCallNetworkStateChangedFingerprint : Fingerprint(
    definingClass = AFTER_CALL_ACTIVITY,
    name = "networkStateChanged",
    returnType = "V",
    parameters = listOf("Lplugin/adsdk/extras/NetworkChangeReceiver\$NetworkState;"),
)

internal object HideNavBarOnlyFingerprint : Fingerprint(
    definingClass = BASE_ACT,
    name = "hideNavBarOnly",
    returnType = "V",
    parameters = listOf(),
)

internal object RateUsFeedbackOnCreateFingerprint : Fingerprint(
    definingClass = RATE_US_FEEDBACK_ACTIVITY,
    name = "onCreate",
    returnType = "V",
    parameters = listOf(ANDROID_BUNDLE),
)

internal object ShouldShowRateUsFingerprint : Fingerprint(
    definingClass = RATE_US_MANAGER,
    name = "shouldShowRateUs",
    returnType = "Z",
    parameters = listOf(),
)

internal object CheckBatteryOptimizationFingerprint : Fingerprint(
    definingClass = DASHBOARD_ACTIVITY,
    name = "checkBatteryOptimization",
    returnType = "V",
    parameters = listOf(),
)
