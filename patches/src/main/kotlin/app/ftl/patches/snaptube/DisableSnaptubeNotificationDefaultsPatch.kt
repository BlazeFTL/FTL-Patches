package app.ftl.patches.snaptube

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal object ToolbarNotificationDefaultFingerprint : Fingerprint(
    definingClass = "Lo/vj7;",
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(smali = "Lo/nqa;->R(Landroid/content/Context;)I"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQ, location = MatchAfterImmediately()),
        opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
        methodCall(smali = "Lcom/wandoujia/base/config/GlobalConfig;->isToolbarNotificationDefaultShow()Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
    ),
)

internal object DefaultNotificationChannelFingerprint : Fingerprint(
    definingClass = "Lo/vj7;",
    name = "s",
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        opcode(Opcode.CONST_4),
        methodCall(smali = "Lo/vj7;->r(Landroid/content/Context;Ljava/lang/String;Z)Z"),
    ),
)

@Suppress("unused")
val disableSnaptubeNotificationDefaultsPatch = bytecodePatch(
    name = "Disable Notification Defaults",
    description = "Turns off the Toolbar, Recommended contents, and Tool notifications channels by default.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_SNAPTUBE)

    extendWith("extensions/snaptube.mpe")

    execute {
        ToolbarNotificationDefaultFingerprint.let {
            val constTrueIndex = it.instructionMatches[3].index
            val moveResultIndex = it.instructionMatches[5].index

            it.method.addInstructions(moveResultIndex + 1, "const/4 v0, 0x0")
            it.method.replaceInstruction(constTrueIndex, "const/4 v0, 0x0")
        }

        DefaultNotificationChannelFingerprint.let {
            val defaultTrueIndex = it.instructionMatches[0].index

            it.method.replaceInstruction(
                defaultTrueIndex,
                "invoke-static {p0}, Lapp/ftl/extension/snaptube/SnaptubeSettingsHider;->defaultChannelEnabled(Ljava/lang/String;)Z",
            )
            it.method.addInstructions(defaultTrueIndex + 1, "move-result v1")
        }
    }
}
