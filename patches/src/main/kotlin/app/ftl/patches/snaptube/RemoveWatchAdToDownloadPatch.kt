package app.ftl.patches.snaptube

import app.ftl.util.returnEarly
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal object ChooseFormatAdRewardH0Fingerprint : Fingerprint(
    definingClass = "Lcom/snaptube/plugin/extension/nonlifecycle/ad/ChooseFormatAdRewardViewModel;",
    returnType = "Z",
    filters = listOf(
        methodCall(
            parameters = emptyList(),
            returnType = "Lcom/snaptube/player_guide/IPlayerGuide;",
            opcodes = listOf(Opcode.INVOKE_STATIC),
        ),
    ),
)

// The old version of this fingerprint pinned the call as
// `methodCall(smali = "Lo/mg\$a;->a(Lo/mg;)Z")` - Lo/mg is a per-build obfuscated
// class name (it became Lo/sg in a later build) so that filter broke on update.
// Matched structurally instead: it's the only Z-returning virtual call in this
// class that takes exactly one (obfuscated) object argument, immediately
// followed by move-result + if-eqz. That shape is stable across builds even
// as the obfuscated class/method names churn.
internal object ChooseFormatAdRewardMgCheckFingerprint : Fingerprint(
    definingClass = "Lcom/snaptube/plugin/extension/nonlifecycle/ad/ChooseFormatAdRewardViewModel;",
    filters = listOf(
        methodCall(
            parameters = listOf("L"),
            returnType = "Z",
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
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
