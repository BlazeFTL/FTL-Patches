package app.ftl.patches.xplayer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal object AdRemovedReadFingerprint : Fingerprint(
    filters = listOf(
        string("adRemoved"),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Z"),
            returnType = "Z",
            opcode = Opcode.INVOKE_STATIC,
            location = InstructionLocation.MatchAfterWithin(3)
        ),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately())
    )
)

internal object PurchasedProductsCheckFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Ljava/util/List;"),
    filters = listOf(
        string("com.camerasideas.xplayer.removead"),
        string("xplayer.vip.month")
    )
)
