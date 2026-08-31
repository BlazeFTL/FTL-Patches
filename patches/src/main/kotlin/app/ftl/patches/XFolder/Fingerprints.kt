package app.ftl.patches.xfolder

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * The static getter that reports whether ads/Pro are already unlocked. Its own
 * class (seen as a single obfuscated letter, e.g. "Ll7/k;") and the preference
 * getter it calls (e.g. seen as "Lu7/A0;->b(...)") are both obfuscated and
 * reshuffle every build, so neither is pinned - the "Lo/mg"->"Lo/sg" break
 * rule. Matched structurally instead: the only public static no-arg
 * boolean method that reads the real, unobfuscated preference key
 * "REMOVE_AD" with a false default, then calls an obfuscated
 * (String, boolean) -> boolean getter and returns its result. Both the
 * "REMOVE_AD" string and that call shape are stable business-logic anchors
 * independent of the obfuscated names around them.
 */
internal object IsAdRemovedFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        string("REMOVE_AD"),
        literal(0),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Z"),
            returnType = "Z",
            opcode = Opcode.INVOKE_STATIC,
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
    ),
)
