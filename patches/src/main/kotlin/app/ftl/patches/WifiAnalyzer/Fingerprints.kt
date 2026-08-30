package app.ftl.patches.wifianalyzer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

// --- Unlock Pro ---

/**
 * The billing/premium-state manager's canonical constructor. Its own class
 * name is a single obfuscated token (Lo/xx-style, reshuffles every build), so
 * it's never pinned - instead this matches the constructor via 2 real,
 * app-specific, unobfuscated anchors in the order they occur: the
 * SharedPreferences file name "billing_cache" (file opened) followed by the
 * cached-entitlement key "cachedIsPremium" (flag pulled out of it via
 * SharedPreferences.getBoolean). Neither string occurs anywhere else in the
 * app, so this uniquely locates the class without a name anchor. The 2nd,
 * obfuscated constructor parameter (an interface type) is declared only as
 * "L" since its own type name reshuffles every build too.
 */
private object BillingManagerConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("Landroid/app/Activity;", "L"),
    filters = listOf(
        string("billing_cache"),
        methodCall(
            smali = "Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)" +
                "Landroid/content/SharedPreferences;",
        ),
        string("cachedIsPremium"),
        methodCall(smali = "Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z"),
    ),
)

/**
 * The "is premium purchased" getter, called from 15+ places across
 * MainActivity to gate every Pro feature. Both its own name and its enclosing
 * class are single obfuscated letters that reshuffle every build, so neither
 * is pinned. The class is instead resolved via
 * [BillingManagerConstructorFingerprint] above, and within that class this is
 * the only public, no-argument, boolean-returning method - a shape unique to
 * it (the class's other boolean accessors are all package-private synthetic
 * bridge methods that additionally take a class-instance parameter).
 */
internal object IsPremiumCheckFingerprint : Fingerprint(
    classFingerprint = BillingManagerConstructorFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Z",
    parameters = listOf(),
)

// --- Disable Rate Dialog ---

/**
 * MainActivity's "maybe trigger the in-app rate dialog" method. Its own name
 * is a single obfuscated letter (coincidentally the same "J0" token as the
 * billing class above, in an unrelated class), so it's not pinned -
 * MainActivity itself is the app's real declared Activity class and is safe
 * to pin directly. The match instead anchors on the method's one conditional
 * branch (the one-shot "already triggered this session" gate) followed by 2
 * real, app-specific, unobfuscated strings: the "neverShowRateDialogAgain"
 * SharedPreferences key and the "newRateDialog_Shown" Firebase event name,
 * both unique to this method app-wide.
 */
internal object RateDialogTriggerFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    returnType = "V",
    parameters = listOf(MAIN_ACTIVITY_CLASS),
    filters = listOf(
        opcode(Opcode.IF_EQZ),
        string("neverShowRateDialogAgain"),
        string("newRateDialog_Shown"),
    ),
)
