package app.ftl.patches.snaptube

import app.ftl.util.returnEarly
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal object ChooseFormatAdRewardH0Fingerprint : Fingerprint(
    definingClass = "Lcom/snaptube/plugin/extension/nonlifecycle/ad/ChooseFormatAdRewardViewModel;",
    returnType = "Z",
    filters = listOf(
        methodCall(smali = "Lo/re4;->b0()Lcom/snaptube/player_guide/IPlayerGuide;"),
    ),
)

internal object ChooseFormatAdRewardMgCheckFingerprint : Fingerprint(
    definingClass = "Lcom/snaptube/plugin/extension/nonlifecycle/ad/ChooseFormatAdRewardViewModel;",
    filters = listOf(
        methodCall(smali = "Lo/mg\$a;->a(Lo/mg;)Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
    ),
)

internal object ChooseFormatAdRewardNeedStatusFingerprint : Fingerprint(
    definingClass = "Lcom/snaptube/plugin/extension/nonlifecycle/ad/ChooseFormatAdRewardViewBinder\$observeNeedRewardStatus\$1\$1\$1\$a;",
    filters = listOf(
        opcode(Opcode.IF_EQZ),
    ),
)

internal val COMPATIBILITY_SNAPTUBE = Compatibility(
    name = "SnapTube",
    packageName = "com.snaptube.premium",
    targets = listOf(
        AppTarget(version = "7.64.0.76450210", versionCode = 76450210),
    ),
)

@Suppress("unused")
val removeSnaptubeWatchAdToDownloadPatch = bytecodePatch(
    name = "Remove Watch Ad To Download",
    description = "Removes the requirement to watch a rewarded ad before a download starts.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_SNAPTUBE)

    execute {
        ChooseFormatAdRewardMgCheckFingerprint.let {
            val ifEqz = it.instructionMatches[2]
            val register = ifEqz.getInstruction<OneRegisterInstruction>().registerA
            it.method.addInstructions(ifEqz.index, "const/4 v$register, 0x0")
        }

        ChooseFormatAdRewardNeedStatusFingerprint.let {
            val ifEqz = it.instructionMatches[0]
            val register = ifEqz.getInstruction<OneRegisterInstruction>().registerA
            it.method.addInstructions(ifEqz.index, "const/4 v$register, 0x0")
        }

        ChooseFormatAdRewardH0Fingerprint.method.returnEarly(false)
    }
}
