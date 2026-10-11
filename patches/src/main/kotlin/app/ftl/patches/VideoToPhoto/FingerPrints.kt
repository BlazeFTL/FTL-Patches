package app.ftl.patches.videotophoto

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private const val COROUTINE_RESUME_ERROR = "call to 'resume' before 'invoke' with coroutine"

internal object AccessLevelIsActiveFingerprint : Fingerprint(
    definingClass = "Lcom/adapty/models/AdaptyProfile\$AccessLevel;",
    name = "isActive",
    returnType = "Z",
    parameters = listOf()
)

internal object AccessLevelIsLifetimeFingerprint : Fingerprint(
    definingClass = "Lcom/adapty/models/AdaptyProfile\$AccessLevel;",
    name = "isLifetime",
    returnType = "Z",
    parameters = listOf()
)

internal object AdaptyProfileUpdateFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("J", "Lcom/adapty/models/AdaptyProfile;"),
    filters = listOf(
        string("premium")
    )
)

internal object AdaptyRepositoryClassFingerprint : Fingerprint(
    filters = listOf(
        string("Adapty profile listener unavailable (")
    )
)

internal object IsProFingerprint : Fingerprint(
    classFingerprint = AdaptyRepositoryClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Boolean;->booleanValue()Z"),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately())
    )
)

internal object SkipOnboardingPaywallFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        string("skipOnboardingPaywall")
    )
)

internal object OnboardingCompleteFlagFingerprint : Fingerprint(
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string("onboarding_complete"),
        methodCall(
            smali = "Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z",
            location = InstructionLocation.MatchAfterWithin(5)
        ),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately())
    )
)

internal object ProLockedUiFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
    filters = listOf(
        literal(0x84),
        literal(0xd2),
        literal(0x6000)
    )
)

internal object SplashTimeoutFingerprint : Fingerprint(
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        literal(4000L),
        literal(2000L),
        string("onboarding_complete")
    )
)

internal object SplashJobDelayFingerprint : Fingerprint(
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string(COROUTINE_RESUME_ERROR),
        literal(1800L),
        methodCall(
            parameters = listOf("J", "L"),
            returnType = "Ljava/lang/Object;",
            opcode = Opcode.INVOKE_STATIC,
            location = InstructionLocation.MatchAfterImmediately()
        )
    )
)

internal object SplashInitTimeoutFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L", "L"),
    filters = listOf(
        string(COROUTINE_RESUME_ERROR),
        literal(1500L),
        methodCall(smali = "Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;")
    )
)
