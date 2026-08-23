package app.ftl.patches.removeanalytics

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
import kotlin.random.Random
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

// Ad-SDK keyword list, ported verbatim from Mpatch's AntiAnalytics blocklist.
// In the original tool this list is only ever checked against strings that
// start with "http://"/"https://" (its source regex is `https?://.*(term).*`).
// Bare terms like "admob"/"branch"/"azure"/"adsdk"/"analytics"/"measurement"
// also collide with internal SDK config keys, adapter class names, and plain
// English words (e.g. "appenda" ⊂ "appendable") when matched outside that
// URL context — see isBlockedAnalyticsLiteral below, which enforces it.
internal val AD_SDK_STRING_BLACKLIST = listOf(
    "61.145.124.238",
    "ad.api.kaffnet",
    "ad.mail.ru",
    "ad.myinstashot.com",
    "adc3-launch",
    "adbuddiz",
    "adcolony",
    "addapptr",
    "adincube",
    "adjust",
    "adkmob",
    "adknowledge",
    "admarvel",
    "admob",
    "admost",
    "adnw_logging",
    "adsafeprotected",
    "adsdk",
    "adsert",
    "adserver",
    "adservice",
    "advertising",
    "adview",
    "adz.wattpad",
    "aerserv",
    "airpush",
    "altamob",
    "alta.eqmob",
    "amazon-adsystem",
    "amazonaws",
    "analytics",
    "appAdForce",
    "appboy",
    "appbrain",
    "appenda",
    "appia",
    "applifier.com",
    "applovin",
    "applvn",
    "appnext",
    "appnexus",
    "appodeal",
    "apprupt",
    "apsalar",
    "appsdt",
    "appsflyer",
    "avocarrot",
    "azure",
    "boxdigital/sdk/ad",
    "branch",
    "ca-app-pub",
    "certificate.mobile.yandex.net",
    "chartboost",
    "cloudfront",
    "code.google.com/p/android/issues/detail",
    "crashlytics",
    "csi.gstatic.com",
    "doubleclick.net",
    "dsp.batmobil",
    "duapps",
    "firebaseapp",
    "flurry",
    "fyber",
    "g.doubleclick",
    "google/android/gms/internal",
    "google.com/safebrowsing/clientreport",
    "googleapis.com/auth/games",
    "googleads",
    "googlesyndication",
    "graph.facebook",
    "greystripe",
    "heyzap",
    "hockeyapp",
    "hyprmx",
    "InlineAd",
    "inmobi",
    "inneractive",
    "instreamatic",
    "integralads",
    "ironsource",
    "jirbo",
    "jumptap",
    "kochava",
    "Leadbolt",
    "localytics",
    "loopme",
    "madnet.ru",
    "mdotm",
    "measurement",
    "mediabrix",
    "metrica",
    "millennialmedia",
    "mngads",
    "moat",
    "mobclix",
    "mobfox",
    "mobvista",
    "montexi",
    "moolah",
    "mopub",
    "mp.mydas.mobi",
    "my/target",
    "NativeInterstitial",
    "net.rayjump",
    "network_ads_common",
    "nexage",
    "onelouder/adlib",
    "openx",
    "pagead/ads",
    "plus1.wapstart.ru",
    "pubmatic",
    "pubnative",
    "r.my.com/mobile",
    "revmob",
    "sb.scorecardresearch",
    "smaato/SOMA",
    "startapp",
    "startup.mobile.yandex.net",
    "supersonicads",
    "tagmanager",
    "tapas",
    "tapjoy",
    "udm.scorecardresearch",
    "unity3d/ads",
    "unityads",
    "vdopia",
    "vungle",
    "www.dummy",
    "wzrkt",
    "xtify",
    "yandexadexchange",
    "zestadz",
)

private val URL_SCHEME_PREFIXES = listOf("http://", "https://")

private fun String.isBlockedAnalyticsLiteral(): Boolean =
    ANALYTICS_STRING_BLACKLIST.any { contains(it, ignoreCase = true) } ||
        (URL_SCHEME_PREFIXES.any { startsWith(it, ignoreCase = true) } &&
            AD_SDK_STRING_BLACKLIST.any { contains(it, ignoreCase = true) })

private const val RANDOM_STRING_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
private const val RANDOM_STRING_LENGTH = 7

private fun randomAnalyticsReplacement(): String =
    (1..RANDOM_STRING_LENGTH).map { RANDOM_STRING_CHARSET[Random.nextInt(RANDOM_STRING_CHARSET.length)] }.joinToString("")

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

// Provider entries that must survive the strip below even though their name
// starts with "com.google.firebase" — removing FirebaseInitProvider prevents
// FirebaseApp.initializeApp() from ever running, which crashes any code path
// that calls FirebaseApp.getInstance() / FirebaseKt.getApp(), regardless of
// whether logEvent()/recordException() are stubbed out.
private val FIREBASE_MANIFEST_KEEP = setOf(
    "com.google.firebase.provider.FirebaseInitProvider",
)

// name = null keeps this out of PatchLoader's top-level list (removeAnalyticsPatch
// pulls it in via dependsOn), so it doesn't show as its own toggle in the UI.
val stripFirebaseManifestComponentsPatch = resourcePatch(
    name = null,
    description = "Removes Firebase Analytics/Crashlytics provider, receiver, and service declarations from AndroidManifest.xml.",
) {
    execute {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as? Element ?: return@use
            val children = application.childNodes
            val toRemove = mutableListOf<Element>()

            for (i in 0 until children.length) {
                val node = children.item(i) as? Element ?: continue
                when (node.tagName) {
                    "provider" -> {
                        val name = node.getAttribute("android:name")
                        if (name.startsWith("com.google.firebase") && name !in FIREBASE_MANIFEST_KEEP) {
                            toRemove += node
                        }
                    }
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
            val hasStringMatch = classDef.methods.any { method ->
                (method.instructionsOrNull ?: emptyList()).any { instruction ->
                    isConstString(instruction.opcode) &&
                        ((instruction as ReferenceInstruction).reference as StringReference)
                            .string.isBlockedAnalyticsLiteral()
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
                if (hasStringMatch) {
                    (method.instructionsOrNull ?: emptyList()).forEachIndexed { index, instruction ->
                        if (!isConstString(instruction.opcode)) return@forEachIndexed
                        val value = ((instruction as ReferenceInstruction).reference as StringReference).string
                        if (!value.isBlockedAnalyticsLiteral()) return@forEachIndexed
                        val register = (instruction as OneRegisterInstruction).registerA
                        method.replaceInstruction(index, "const-string v$register, \"${randomAnalyticsReplacement()}\"")
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
