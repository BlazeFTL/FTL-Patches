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

/**
 * The rate-us dialog trigger method. Its own class (seen as a single
 * obfuscated letter, e.g. "Lp7/h;") and instance method name ("e") are both
 * obfuscated and reshuffle every build, so neither is pinned. Matched
 * structurally instead: the only method anywhere in the app taking
 * (Context, RateListener, boolean) and returning void.
 * `Landroid/content/Context;` and the real, unobfuscated third-party
 * `Lcom/zjsoft/rate/listeners/RateListener;` interface (a public type of the
 * bundled zjsoft rate-prompt library, kept unobfuscated) are stable anchors
 * independent of the obfuscated names around them.
 */
internal object ShowRateDialogFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Lcom/zjsoft/rate/listeners/RateListener;",
        "Z",
    ),
)

/**
 * The 5 default-value getters below all share the same shape as
 * [IsAdRemovedFingerprint]: a public static no-arg boolean method that reads
 * a real, unobfuscated preference key with a hardcoded default, then calls
 * an obfuscated (String, boolean) -> boolean getter and returns its result.
 * Their own method names (single obfuscated letters) and enclosing class are
 * not pinned. Each is uniquely identified by its own real preference-key
 * string plus the literal-default/move-result shape, which also
 * disambiguates it from the sibling setter method that shares the same key
 * but has no literal default or move-result.
 */
internal object UseImageDefaultFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        string("_set_use_image"),
        literal(1),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Z"),
            returnType = "Z",
            opcode = Opcode.INVOKE_STATIC,
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
    ),
)

internal object UseVideoDefaultFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        string("_set_use_video"),
        literal(1),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Z"),
            returnType = "Z",
            opcode = Opcode.INVOKE_STATIC,
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
    ),
)

internal object UseAudioDefaultFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        string("_set_use_audio"),
        literal(1),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Z"),
            returnType = "Z",
            opcode = Opcode.INVOKE_STATIC,
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
    ),
)

internal object CleanerTabDefaultFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        string("_set_cleaner_tab"),
        literal(1),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Z"),
            returnType = "Z",
            opcode = Opcode.INVOKE_STATIC,
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
    ),
)

internal object ShowHiddenDefaultFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        string("_set_show_hidden"),
        literal(0),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Z"),
            returnType = "Z",
            opcode = Opcode.INVOKE_STATIC,
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
    ),
)
