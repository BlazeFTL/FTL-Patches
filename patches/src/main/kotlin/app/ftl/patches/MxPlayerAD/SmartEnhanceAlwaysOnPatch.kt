package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

// Real Activity lifecycle override - never renamed.
internal object ActivityScreenOnCreateFingerprint : Fingerprint(
    definingClass = ACTIVITY_SCREEN_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

/**
 * Compare-build edits (stock -> Always On), plus the lock/unlock fix:
 *  1. M9 (reset): q = true instead of false, E0(1) instead of E0(-1)      [B]
 *  2. onCreate: initial q = true instead of false                          [B]
 *  3. onCreate: right after the player is stored in I, restart the forcer  [B: EnhanceForcer.run(),
 *     now the retrying patch_enhanceKick()]
 *  4. fix: M9 also restarts the retrying re-applier after Sa(), because lock/unlock calls M9 while
 *     the surface and native renderer are being rebuilt, so the single E0(1) is lost.
 * Every edit replaces an instruction in place (never inserts before a branch target).
 */
internal val smartEnhanceAlwaysOnPatch = bytecodePatch(
    name = "Smart Enhance Always On",
    description = "Turns Smart Enhance on for every video and keeps it on after lock/unlock.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)
    dependsOn(smartEnhanceCorePatch)

    execute {
        val activity = EnhanceRefs.activityScreen.type
        val qField = EnhanceRefs.qField.smali()
        val pFieldSmali = EnhanceRefs.playerField.smali()

        // --- 1 + 4: M9 ---------------------------------------------------------
        val m9 = EnhanceRefs.m9
        val m = SmartEnhanceForceMethodFingerprint.instructionMatches
        val qReg = m[0].getInstruction<OneRegisterInstruction>().registerA
        val oneReg = m[3].getInstruction<OneRegisterInstruction>().registerA
        val returnIndex = m[6].index

        m9.replaceInstruction(m[0].index, "const/4 v$qReg, 0x1")
        m9.replaceInstruction(m[3].index, "const/4 v$oneReg, 0x1")
        m9.addInstructions(
            returnIndex,
            """
                const/4 v$oneReg, 0x0
                iput v$oneReg, p0, $activity->$ENHANCE_LEVEL_FIELD:F
                invoke-virtual {p0}, $activity->$ENHANCE_KICK_METHOD()V
            """.trimIndent(),
        )

        // --- 2 + 3: onCreate ---------------------------------------------------
        val onCreate = ActivityScreenOnCreateFingerprint.method
        val insns = onCreate.implementation!!.instructions

        val qIndex = insns.indexOfFirst {
            it.opcode == Opcode.SPUT_BOOLEAN && ((it as ReferenceInstruction).reference as FieldReference).smali() == qField
        }
        if (qIndex < 0) throw PatchException("onCreate: initial q write not found")

        var oneRegister = -1
        val scanStart = maxOf(0, qIndex - 12)
        for (i in qIndex - 1 downTo scanStart) {
            val insn = insns[i]
            if (insn.opcode == Opcode.CONST_4 && (insn as NarrowLiteralInstruction).narrowLiteral == 1) {
                val reg = (insn as OneRegisterInstruction).registerA
                val clobbered = (i + 1 until qIndex).any {
                    val later = insns[it]
                    later is OneRegisterInstruction && later.registerA == reg
                }
                if (!clobbered) {
                    oneRegister = reg
                    break
                }
            }
        }
        if (oneRegister < 0) throw PatchException("onCreate: no register holding 1 before the q write")
        onCreate.replaceInstruction(qIndex, "sput-boolean v$oneRegister, $qField")

        val playerStoreIndex = insns.indexOfFirst {
            it.opcode == Opcode.IPUT_OBJECT && ((it as ReferenceInstruction).reference as FieldReference).smali() == pFieldSmali
        }
        if (playerStoreIndex < 0) throw PatchException("onCreate: player field store not found")
        onCreate.addInstructions(
            playerStoreIndex + 1,
            "invoke-virtual/range {p0 .. p0}, $activity->$ENHANCE_KICK_METHOD()V",
        )
    }
}
