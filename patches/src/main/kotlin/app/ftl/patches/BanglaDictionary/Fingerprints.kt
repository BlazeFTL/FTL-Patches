package app.ftl.patches.bangladictionary

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

internal object IsPurchasedFingerprint : Fingerprint(
    isStatic = true,
    returnType = "Z",
    parameters = listOf("Landroid/content/SharedPreferences;"),
    filters = listOf(
        fieldAccess(opcode = Opcode.SGET_OBJECT, type = "Ljava/lang/String;"),
        opcode(Opcode.CONST_4, InstructionLocation.MatchAfterImmediately()),
        methodCall(
            smali = "Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z",
            opcode = Opcode.INVOKE_INTERFACE,
            location = InstructionLocation.MatchAfterImmediately(),
        ),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.RETURN, InstructionLocation.MatchAfterImmediately()),
    ),
)
