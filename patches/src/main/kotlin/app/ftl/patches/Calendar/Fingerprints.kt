package app.ftl.patches.calendar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

// Lplugin/adsdk/service/AdsUtility; is the app's own shared ad/billing helper
// class. This build ships with almost no identifier obfuscation - class,
// method and field names throughout are real words, not Lo/xx-style symbols
// - so pinning definingClass + name here is safe: there is nothing obfuscated
// to avoid pinning.

internal object IsPremiumUserFingerprint : Fingerprint(
    definingClass = "Lplugin/adsdk/service/AdsUtility;",
    name = "isPremiumUser",
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;"),
)

internal object GetActiveSubscriptionFingerprint : Fingerprint(
    definingClass = "Lplugin/adsdk/service/AdsUtility;",
    name = "getActiveSubscription",
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;"),
)

// onResume() already hides llAdContainer once isPremiumUser() is true (a
// real, unobfuscated call/field chain). Anchored on that call and the branch
// it feeds, plus the View.setVisibility(I)V call it makes, so the insertion
// point survives unrelated code shifting around it.
internal object SettingActivityOnResumeFingerprint : Fingerprint(
    definingClass = "Lcalendar/agenda/schedule/event/advance/calendar/planner/activity/SettingActivity;",
    name = "onResume",
    returnType = "V",
    filters = listOf(
        methodCall(smali = "Lplugin/adsdk/service/AdsUtility;->isPremiumUser(Landroid/content/Context;)Z"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        methodCall(smali = "Landroid/view/View;->setVisibility(I)V"),
    ),
)
