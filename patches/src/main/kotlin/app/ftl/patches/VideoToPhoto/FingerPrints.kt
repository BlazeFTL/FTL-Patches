package app.ftl.patches.videotophoto

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object AdaptyRepositoryClassFingerprint : Fingerprint(
    filters = listOf(
        string("Adapty profile listener unavailable (")
    )
)

internal object IsProStateFingerprint : Fingerprint(
    classFingerprint = AdaptyRepositoryClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this"),
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        methodCall(name = "getValue", returnType = "Ljava/lang/Object;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, InstructionLocation.MatchAfterImmediately()),
        checkCast("Ljava/lang/Boolean;", InstructionLocation.MatchAfterImmediately()),
        methodCall(smali = "Ljava/lang/Boolean;->booleanValue()Z"),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately())
    )
)

internal object AccessLevelIsActiveFingerprint : Fingerprint(
    definingClass = "Lcom/adapty/models/AdaptyProfile\$AccessLevel;",
    name = "isActive",
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_BOOLEAN, definingClass = "this", name = "isActive")
    )
)

internal object AccessLevelIsLifetimeFingerprint : Fingerprint(
    definingClass = "Lcom/adapty/models/AdaptyProfile\$AccessLevel;",
    name = "isLifetime",
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_BOOLEAN, definingClass = "this", name = "isLifetime")
    )
)
