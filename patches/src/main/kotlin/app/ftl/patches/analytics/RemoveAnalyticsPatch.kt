package app.ftl.patches.analytics

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.w3c.dom.Element

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

internal val ANALYTICS_STRING_BLACKLIST = listOf(
    "akamaitechnologies.com",
    "amazonaws.com",
    "amplitude.com",
    "api.branch.io",
    "api.crittercism.com",
    "app.adjust.com",
    "appboy.com",
    "appmetrica.yandex.ru",
    "appsflyer.com",
    "audience_network",
    "azure.com",
    "chartboost.com",
    "cloudfront.net",
    "com.google.analytics",
    "com.google.android.gms.analytics",
    "com.google.firebase.analytics",
    "com.google.firebase.firebase_analytics",
    "com.yandex.metrica.IMetricaService",
    "crashlytics.com",
    "data.flurry.com",
    "firebaseapp.com",
    "google-analytics.com",
    "googletagmanager.com",
    "hockeyapp.net",
    "lsdsl.ml",
    "measurement.com",
    "microsoft.applications.telemetry",
    "my.target.com",
    "scorecardresearch.com",
    "skype.android.analytics.com",
    "skype.android.crash.com",
    "skype.telemetry.com",
    "smaato.com",
    "startappexchange.com",
    "startappservice.com",
    "umeng.com",
    "wzrkt.com",
    "YandexMetricaNativeModule",
)

private const val ANALYTICS_STRING_REPLACEMENT = ""

val stripFirebaseManifestComponentsPatch = resourcePatch(
    name = "Strip Firebase manifest components",
    description = "Removes Firebase Analytics/Crashlytics receiver and service declarations from AndroidManifest.xml.",
) {
    execute {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as? Element ?: return@use
            val children = application.childNodes
            val toRemove = mutableListOf<Element>()

            for (i in 0 until children.length) {
                val node = children.item(i) as? Element ?: continue
                when (node.tagName) {
                    "receiver" -> {
                        if (node.getAttribute("android:name").startsWith("com.google.firebase")) {
                            toRemove += node
                        }
                    }
                    "service" -> {
                        val name = node.getAttribute("android:name")
                        val actions = node.getElementsByTagName("action")
                        val hasFirebaseAction = (0 until actions.length).any { j ->
                            (actions.item(j) as Element).getAttribute("android:name").startsWith("com.google.firebase")
                        }
                        if (name.startsWith("com.google.firebase") || hasFirebaseAction) {
                            toRemove += node
                        }
                    }
                }
            }

            toRemove.forEach { application.removeChild(it) }
        }
    }
}

val removeAnalyticsPatch = bytecodePatch(
    name = "Remove analytics",
    description = "Neuters logging entry points for major analytics/crash-reporting SDKs, poisons const-string analytics hosts and component names across all bytecode, and strips Firebase receiver/service declarations from the manifest.",
) {
    dependsOn(stripFirebaseManifestComponentsPatch)

    execute {
        FirebaseAnalyticsLogEventFingerprint.methodOrNull?.addInstructions(0, "return-void")
        CrashlyticsRecordExceptionFingerprint.methodOrNull?.addInstructions(0, "return-void")
        FlurryAgentLogEventFingerprint.methodOrNull?.addInstructions(0, "return-void")
        GoogleAnalyticsTrackerSendFingerprint.methodOrNull?.addInstructions(0, "return-void")
        YandexMetricaReportEventFingerprint.methodOrNull?.addInstructions(0, "return-void")
        AppsFlyerLogEventFingerprint.methodOrNull?.addInstructions(0, "return-void")
        AdjustTrackEventFingerprint.methodOrNull?.addInstructions(0, "return-void")

        classDefForEach { classDef ->
            val hasMatch = classDef.methods.any { method ->
                (method.instructionsOrNull ?: emptyList()).any { instruction ->
                    instruction.opcode == Opcode.CONST_STRING &&
                        ANALYTICS_STRING_BLACKLIST.any { term ->
                            ((instruction as ReferenceInstruction).reference as StringReference)
                                .string.contains(term, ignoreCase = true)
                        }
                }
            }
            if (!hasMatch) return@classDefForEach

            mutableClassDefBy(classDef).methods.forEach { method ->
                (method.instructionsOrNull ?: emptyList()).forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.CONST_STRING) return@forEachIndexed
                    val value = ((instruction as ReferenceInstruction).reference as StringReference).string
                    if (ANALYTICS_STRING_BLACKLIST.none { value.contains(it, ignoreCase = true) }) return@forEachIndexed
                    val register = (instruction as OneRegisterInstruction).registerA
                    method.replaceInstruction(index, "const-string v$register, \"$ANALYTICS_STRING_REPLACEMENT\"")
                }
            }
        }
    }
}
