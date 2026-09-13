package app.ftl.patches.impostack

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * SecurityTracker.isPremium(Context)Z - the single gate every premium feature
 * checks. The class and method names are unobfuscated here, but per the repo's
 * "pin nothing that can move" rule the match is structural instead: the only
 * public final (Context) -> boolean method that reads the real, unobfuscated
 * preference key "sL_01" with a false default, then calls the SDK-shaped
 * (String, boolean) -> boolean SharedPreferences getter and returns its result
 * immediately. The sibling setter setPremiumStatus(Context, Z)V shares the
 * "sL_01" key but returns void and its putBoolean call yields an Editor object
 * (move-result-object), so it can never match this shape.
 */
internal object IsPremiumFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        string("sL_01"),
        literal(0),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Z"),
            returnType = "Z",
            opcode = Opcode.INVOKE_INTERFACE,
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
    ),
)
