package app.ftl.patches.analytics

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

internal object FirebaseAnalyticsLogEventFingerprint : Fingerprint(
    name = "logEvent",
    definingClass = "Lcom/google/firebase/analytics/FirebaseAnalytics;",
    returnType = "V",
)

internal object CrashlyticsRecordExceptionFingerprint : Fingerprint(
    name = "recordException",
    definingClass = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;",
    returnType = "V",
)

internal object FlurryAgentLogEventFingerprint : Fingerprint(
    name = "logEvent",
    definingClass = "Lcom/flurry/android/FlurryAgent;",
    returnType = "V",
)

internal object GoogleAnalyticsTrackerSendFingerprint : Fingerprint(
    name = "send",
    definingClass = "Lcom/google/android/gms/analytics/Tracker;",
    returnType = "V",
)

internal object YandexMetricaReportEventFingerprint : Fingerprint(
    name = "reportEvent",
    definingClass = "Lcom/yandex/metrica/YandexMetrica;",
    returnType = "V",
)

internal object AppsFlyerLogEventFingerprint : Fingerprint(
    name = "logEvent",
    definingClass = "Lcom/appsflyer/AppsFlyerLib;",
    returnType = "V",
)

internal object AdjustTrackEventFingerprint : Fingerprint(
    name = "trackEvent",
    definingClass = "Lcom/adjust/sdk/Adjust;",
    returnType = "V",
)

val removeAnalyticsPatch = bytecodePatch(
    name = "Remove analytics",
    description = "Neuters logging entry points for Firebase Analytics, Crashlytics, Flurry, legacy Google Analytics, Yandex Metrica, AppsFlyer and Adjust.",
) {
    execute {
        FirebaseAnalyticsLogEventFingerprint.method.addInstructions(0, "return-void")
        CrashlyticsRecordExceptionFingerprint.method.addInstructions(0, "return-void")
        FlurryAgentLogEventFingerprint.method.addInstructions(0, "return-void")
        GoogleAnalyticsTrackerSendFingerprint.method.addInstructions(0, "return-void")
        YandexMetricaReportEventFingerprint.method.addInstructions(0, "return-void")
        AppsFlyerLogEventFingerprint.method.addInstructions(0, "return-void")
        AdjustTrackEventFingerprint.method.addInstructions(0, "return-void")
    }
}
