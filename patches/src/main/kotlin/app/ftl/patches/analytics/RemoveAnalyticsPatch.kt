package app.ftl.patches.analytics

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
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

// SDK's own bytecode: entry points already stubbed via fingerprints above.
// Poisoning strings inside the SDK's own internals can break its init-time
// validation and crash the app before any tracking call is even made.
private val ANALYTICS_SDK_PACKAGE_PREFIXES = listOf(
    "Lcom/google/firebase/",
    "Lcom/google/android/gms/analytics/",
    "Lcom/google/android/gms/measurement/",
    "Lcom/google/android/gms/internal/measurement/",
    "Lcom/flurry/android/",
    "Lcom/yandex/metrica/",
    "Lcom/appsflyer/",
    "Lcom/adjust/sdk/",
)

private fun isConstString(opcode: Opcode) =
    opcode == Opcode.CONST_STRING || opcode == Opcode.CONST_STRING_JUMBO

// Some analytics/crash SDKs verify their own signing cert (anti-repackage
// check) and disable themselves if it doesn't match. Force verify() to
// always report success so the stubbed-out SDK above doesn't get flagged.
// Real, unobfuscated SDK method — safe to pin directly, no fingerprint needed.
private fun isSignatureVerifyCall(reference: MethodReference) =
    reference.definingClass == "Ljava/security/Signature;" &&
        reference.name == "verify" &&
        reference.parameterTypes.singleOrNull() == "[B" &&
        reference.returnType == "Z"

// name = null keeps this out of PatchLoader's top-level list (removeAnalyticsPatch
// pulls it in via dependsOn), so it doesn't show as its own toggle in the UI.
val stripFirebaseManifestComponentsPatch = resourcePatch(
    name = null,
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
    name = "Remove Analytics",
    description = "Disables tracking and crash-reporting tools, corrupts analytics web links inside the code, and removes background tracking services.",
    default = false,
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
            val isSdkInternal = ANALYTICS_SDK_PACKAGE_PREFIXES.any { classDef.type.startsWith(it) }

            val hasStringMatch = !isSdkInternal && classDef.methods.any { method ->
                (method.instructionsOrNull ?: emptyList()).any { instruction ->
                    isConstString(instruction.opcode) &&
                        ANALYTICS_STRING_BLACKLIST.any { term ->
                            ((instruction as ReferenceInstruction).reference as StringReference)
                                .string.contains(term, ignoreCase = true)
                        }
                }
            }

            val hasSignatureVerifyCall = classDef.methods.any { method ->
                val instructions = (method.instructionsOrNull ?: emptyList()).toList()
                instructions.indices.any { i ->
                    val instruction = instructions[i]
                    (instruction.opcode == Opcode.INVOKE_VIRTUAL || instruction.opcode == Opcode.INVOKE_VIRTUAL_RANGE) &&
                        (instruction as? ReferenceInstruction)?.reference is MethodReference &&
                        isSignatureVerifyCall(instruction.reference as MethodReference) &&
                        instructions.getOrNull(i + 1)?.opcode == Opcode.MOVE_RESULT
                }
            }

            if (!hasStringMatch && !hasSignatureVerifyCall) return@classDefForEach

            mutableClassDefBy(classDef).methods.forEach { method ->
                if (!isSdkInternal) {
                    (method.instructionsOrNull ?: emptyList()).forEachIndexed { index, instruction ->
                        if (!isConstString(instruction.opcode)) return@forEachIndexed
                        val value = ((instruction as ReferenceInstruction).reference as StringReference).string
                        if (ANALYTICS_STRING_BLACKLIST.none { value.contains(it, ignoreCase = true) }) return@forEachIndexed
                        val register = (instruction as OneRegisterInstruction).registerA
                        method.replaceInstruction(index, "const-string v$register, \"$ANALYTICS_STRING_REPLACEMENT\"")
                    }
                }

                val instructions = method.instructionsOrNull ?: return@forEach
                val insertions = mutableListOf<Pair<Int, Int>>() // insertAt to destRegister

                instructions.indices.forEach { i ->
                    val instruction = instructions[i]
                    if (instruction.opcode != Opcode.INVOKE_VIRTUAL && instruction.opcode != Opcode.INVOKE_VIRTUAL_RANGE) return@forEach
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return@forEach
                    if (!isSignatureVerifyCall(reference)) return@forEach

                    val moveResult = instructions.getOrNull(i + 1) ?: return@forEach
                    if (moveResult.opcode != Opcode.MOVE_RESULT) return@forEach

                    val destRegister = (moveResult as OneRegisterInstruction).registerA
                    insertions += (i + 2) to destRegister
                }

                insertions.sortedByDescending { it.first }.forEach { (insertAt, register) ->
                    method.addInstruction(insertAt, "const/4 v$register, 0x1")
                }
            }
        }
    }
}
