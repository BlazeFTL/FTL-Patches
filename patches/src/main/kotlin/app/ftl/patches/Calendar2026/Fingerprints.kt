package app.ftl.patches.calendar2026

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal const val MAIN_ACTIVITY = "Lcom/agendaplanner/taskmangereventmangercalendar/act/Main_Activity;"
internal const val SPLASH_ACTIVITY = "Lcom/agendaplanner/taskmangereventmangercalendar/act/Splash_Activity;"
internal const val SETTING_ACTIVITY = "Lcom/agendaplanner/taskmangereventmangercalendar/act/Setting_Activity;"
internal const val CALLER_PREFS = "Lcom/ogmediaapps/callerid/helpersp/SharedPreferencesHelper;"
internal const val CALLER_UTILS = "Lcom/ogmediaapps/callerid/helpersp/CallerIdUtils;"

internal object SplashStartTimerFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY,
    name = "starttimer",
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        opcode(Opcode.INT_TO_LONG)
    )
)

internal object SplashOnResumeFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY,
    name = "onResume",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        literal(0xbb8)
    )
)

internal object SplashRequestConsentFormFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY,
    name = "requestConsentForm",
    returnType = "V",
    parameters = listOf()
)

internal object SplashIntentMainActFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY,
    name = "Intent_MainAct",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(smali = "Landroid/provider/Settings;->canDrawOverlays(Landroid/content/Context;)Z"),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately())
    )
)

internal object SplashCallPermissionCheckFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY,
    name = "callPrmCheck",
    returnType = "Z",
    parameters = listOf()
)

internal object SplashHideSystemUiFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY,
    name = "hideSystemUI",
    returnType = "V",
    parameters = listOf()
)

internal object MainCallPermissionCheckFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY,
    name = "callPrmCheck",
    returnType = "Z",
    parameters = listOf()
)

internal object MainCheckOverlayPermissionFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY,
    name = "checkScreenOverlayPermission",
    returnType = "V",
    parameters = listOf()
)

internal object MainPermissionResultFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY,
    name = "onRequestPermissionsResult",
    returnType = "V",
    parameters = listOf("I", "[Ljava/lang/String;", "[I"),
    filters = listOf(
        methodCall(definingClass = "this", name = "resetCalendarDenyCount"),
        fieldAccess(
            definingClass = "Landroid/os/Build\$VERSION;",
            name = "SDK_INT",
            opcode = Opcode.SGET,
            location = InstructionLocation.MatchAfterImmediately()
        ),
        methodCall(
            definingClass = "this",
            name = "checkpermission",
            location = InstructionLocation.MatchAfterWithin(10)
        )
    )
)

internal object MainHideNavigationBarFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY,
    name = "hideNavigationBar",
    returnType = "V",
    parameters = listOf()
)

internal object SettingOnCreateFingerprint : Fingerprint(
    definingClass = SETTING_ACTIVITY,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        fieldAccess(definingClass = "this", name = "per_sett", opcode = Opcode.IPUT_OBJECT)
    )
)

internal object CallerPhoneStateReceiverFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        fieldAccess(definingClass = CALLER_UTILS, name = "INSTANCE", opcode = Opcode.SGET_OBJECT),
        methodCall(definingClass = CALLER_UTILS, name = "isPhoneStatePermissionGranted"),
        string("android.intent.action.PHONE_STATE"),
        methodCall(
            smali = "Landroid/content/Context;->registerReceiver(" +
                "Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;"
        )
    )
)

internal object CompleteCallFeatureFingerprint : Fingerprint(
    definingClass = CALLER_PREFS,
    name = "isCompleteCallFeatureEnable",
    returnType = "Z",
    parameters = listOf()
)

internal object MissedCallFeatureFingerprint : Fingerprint(
    definingClass = CALLER_PREFS,
    name = "isMissedCallFeatureEnable",
    returnType = "Z",
    parameters = listOf()
)

internal object NoAnswerFeatureFingerprint : Fingerprint(
    definingClass = CALLER_PREFS,
    name = "isNoAnswerFeatureEnable",
    returnType = "Z",
    parameters = listOf()
)

internal object IsPurchasedFingerprint : Fingerprint(
    definingClass = CALLER_PREFS,
    name = "isPurchased",
    returnType = "Z",
    parameters = listOf()
)
