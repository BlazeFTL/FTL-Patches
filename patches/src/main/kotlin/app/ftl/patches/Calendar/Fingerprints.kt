package app.ftl.patches.calendar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
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

// --- Skip/Boost Splash Screen ---

// onCreate() calls the real superclass onCreate() before anything splash-specific runs.
// This is an unobfuscated call to the ad SDK's own base class, so it's a safe, stable anchor
// for the very first instruction of the method.
internal object SplashActivityOnCreateSuperFingerprint : Fingerprint(
    definingClass = "Lcalendar/agenda/schedule/event/advance/calendar/planner/activity/SplashActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(smali = "Lplugin/adsdk/service/BaseActivity;->onCreate(Landroid/os/Bundle;)V"),
    ),
)

// setContentView() is a real unobfuscated AndroidX call that inflates the splash layout.
// Anchoring right after it lets "boost" mode leave the splash briefly visible instead of
// skipping past it entirely like the default "skip totally" mode does.
internal object SplashActivitySetContentViewFingerprint : Fingerprint(
    definingClass = "Lcalendar/agenda/schedule/event/advance/calendar/planner/activity/SplashActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(smali = "Landroidx/appcompat/app/AppCompatActivity;->setContentView(Landroid/view/View;)V"),
    ),
)

// The remote-config-driven ad splash delay lives in a separate lambda (String, boolean),
// wired up independently of onCreate()'s own control flow (likely registered inside
// BaseActivity's own onCreate(), which runs via invoke-super before our edits take effect).
// Structural anchor only: real unobfuscated field name + a literal + real math/branch
// opcodes - no obfuscated class, method or field name is pinned anywhere in this filter
// chain, so it survives the method's own synthetic name changing across rebuilds.
internal object SplashActivityAdDelayFingerprint : Fingerprint(
    definingClass = "Lcalendar/agenda/schedule/event/advance/calendar/planner/activity/SplashActivity;",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Z"),
    filters = listOf(
        fieldAccess(
            smali = "Lplugin/adsdk/service/api/ListModel;->splashScreenShowAdTime:I",
            opcode = Opcode.IGET,
        ),
        opcode(Opcode.INT_TO_LONG, MatchAfterImmediately()),
        literal(0x3e8L, opcodes = listOf(Opcode.CONST_WIDE_16), location = MatchAfterImmediately()),
        opcode(Opcode.MUL_LONG_2ADDR, MatchAfterImmediately()),
    ),
)
