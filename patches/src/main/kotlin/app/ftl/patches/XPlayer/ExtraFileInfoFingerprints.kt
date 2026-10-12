package app.ftl.patches.xplayer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal const val MEDIA_FILE_INFO = "Lcom/inshot/xplayer/content/MediaFileInfo;"

internal object VideoRowBindFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE),
    returnType = "V",
    parameters = listOf("L", MEDIA_FILE_INFO, "Ljava/util/List;", "I"),
    strings = listOf("%s%%"),
    filters = listOf(
        methodCall(
            definingClass = MEDIA_FILE_INFO,
            parameters = listOf(),
            returnType = "J",
            opcode = Opcode.INVOKE_VIRTUAL
        ),
        opcode(Opcode.MOVE_RESULT_WIDE, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.CONST_WIDE_16, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.CMP_LONG, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.IF_LEZ, InstructionLocation.MatchAfterImmediately()),
        fieldAccess(
            type = "Landroid/widget/ProgressBar;",
            opcode = Opcode.IGET_OBJECT,
            location = InstructionLocation.MatchAfterImmediately()
        )
    )
)

internal object FolderRowBindFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("L", "I"),
    strings = listOf("%s "),
    filters = listOf(
        fieldAccess(type = "Landroid/view/View;", opcode = Opcode.IGET_OBJECT),
        fieldAccess(
            definingClass = "this",
            type = "J",
            opcode = Opcode.IGET_WIDE,
            location = InstructionLocation.MatchAfterImmediately()
        ),
        methodCall(
            parameters = listOf(),
            returnType = "J",
            opcode = Opcode.INVOKE_VIRTUAL,
            location = InstructionLocation.MatchAfterImmediately()
        ),
        opcode(Opcode.MOVE_RESULT_WIDE, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.SUB_LONG_2ADDR, InstructionLocation.MatchAfterImmediately()),
        literal(86400000L, location = InstructionLocation.MatchAfterImmediately())
    )
)
