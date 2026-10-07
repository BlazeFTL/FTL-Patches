package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

// Real Activity lifecycle override - never renamed.
internal object ActivityScreenOnCreateFingerprint : Fingerprint(
    definingClass = ACTIVITY_SCREEN_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

/**
 * Configurable Always On. Level = Mod Settings "Default level" (0-100%) of stock strength;
 * Always On switch off (or level 0) = stock behavior.
 *  1. M9 (per-video reset): if level > 0 -> q = true, level field = level, Sa(), kick; else stock.
 *  2. onCreate: initial q/level from the same setting instead of q = false.
 *  3. onCreate: after the player is stored, kick the retrying re-applier (survives async setup,
 *     and later lock/unlock via surfaceCreated / H6 in the core + slider patches).
 */
internal val smartEnhanceAlwaysOnPatch = bytecodePatch(
    name = "Smart Enhance Always On",
    description = "Applies Smart Enhance to every video at a level set in Mod Settings, and keeps it after lock/unlock.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)
    dependsOn(
        smartEnhanceCorePatch,
        modSettingFlagPatch(KEY_ENHANCE_ALWAYS_ON),
        modSettingFlagPatch(KEY_ENHANCE_DEFAULT_PCT),
    )

    execute {
        val activity = EnhanceRefs.activityScreen.type
        val qField = EnhanceRefs.qField.smali()
        val pFieldSmali = EnhanceRefs.playerField.smali()
        val saMethod = EnhanceRefs.saRef.smali()

        // --- onCreate (indices read first; edits go high -> low) ----------------
        val onCreate = ActivityScreenOnCreateFingerprint.method
        val insns = onCreate.implementation!!.instructions

        val qIndex = insns.indexOfFirst {
            it.opcode == Opcode.SPUT_BOOLEAN && ((it as ReferenceInstruction).reference as FieldReference).smali() == qField
        }
        if (qIndex < 0) throw PatchException("onCreate: initial q write not found")

        val playerStoreIndex = insns.indexOfFirst {
            it.opcode == Opcode.IPUT_OBJECT && ((it as ReferenceInstruction).reference as FieldReference).smali() == pFieldSmali
        }
        if (playerStoreIndex < 0) throw PatchException("onCreate: player field store not found")

        // Low register holding "this" (stock: move-object/from16 v0, p0).
        val p0Index = onCreate.implementation!!.registerCount - 2
        val thisReg = (0 until qIndex).firstNotNullOfOrNull { i ->
            val insn = insns[i]
            if (insn.opcode == Opcode.MOVE_OBJECT_FROM16 && (insn as TwoRegisterInstruction).registerB == p0Index) {
                insn.registerA
            } else {
                null
            }
        } ?: throw PatchException("onCreate: no low register copy of this before the q write")
        if (thisReg > 15) throw PatchException("onCreate: this copy is not in a 4-bit register")

        // Two registers nothing before the q write has touched (stock reassigns them later).
        val free = (9..15).filter { r ->
            (0 until qIndex).none { i ->
                val insn = insns[i]
                insn is OneRegisterInstruction && insn.registerA == r
            }
        }
        if (free.size < 2) throw PatchException("onCreate: no two free low registers at the q write")
        val a = free[0]
        val b = free[1]

        onCreate.addInstructions(
            playerStoreIndex + 1,
            "invoke-virtual/range {p0 .. p0}, $activity->$ENHANCE_KICK_METHOD()V",
        )

        onCreate.replaceInstruction(qIndex, "invoke-static {}, $ENHANCE_CONFIG_CLASS->alwaysLevel()F")
        onCreate.addInstructions(
            qIndex + 1,
            """
                move-result v$a
                iput v$a, v$thisReg, $activity->$ENHANCE_LEVEL_FIELD:F
                const/4 v$b, 0x0
                cmpl-float v$b, v$a, v$b
                sput-boolean v$b, $qField
            """.trimIndent(),
        )

        // --- M9: per-video reset -------------------------------------------------
        val m9 = EnhanceRefs.m9
        val stockStart = m9.getInstruction(0)
        m9.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $ENHANCE_CONFIG_CLASS->alwaysLevel()F
                move-result v0
                const/4 v1, 0x0
                cmpl-float v1, v0, v1
                if-eqz v1, :stock
                const/4 v1, 0x1
                sput-boolean v1, $qField
                iput v0, p0, $activity->$ENHANCE_LEVEL_FIELD:F
                invoke-virtual {p0}, $saMethod
                invoke-virtual {p0}, $activity->$ENHANCE_KICK_METHOD()V
                return-void
            """.trimIndent(),
            ExternalLabel("stock", stockStart),
        )
    }
}
