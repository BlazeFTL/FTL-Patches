package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val ENHANCE_SLIDER_CLASS = "Lapp/ftl/extension/mxplayerad/EnhanceSlider;"
private const val ENHANCE_SLIDER_HOST = "Lapp/ftl/extension/mxplayerad/EnhanceSlider\$Host;"
private const val ENHANCE_SLIDER_FIELD = "patch_enhanceSlider"
private const val ENHANCE_ON_PERCENT_METHOD = "onEnhancePercent"
private const val ENHANCE_SHOW_METHOD = "patch_showEnhancePercentMenu"

// Stock toggle (originally "Ha"): the only method in ActivityScreen using this real string.
internal object SmartEnhanceHaFingerprint : Fingerprint(
    definingClass = ACTIVITY_SCREEN_CLASS,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(string("enhanceEnabled")),
)

// Toolbar MenuItem field (originally "m1"): R.id.enhance -> Menu.findItem -> iput-object.
// "enhance" is a resource entry name, stable across builds.
internal object SmartEnhanceMenuItemFieldFingerprint : Fingerprint(
    definingClass = ACTIVITY_SCREEN_CLASS,
    filters = listOf(
        fieldAccess(name = "enhance", type = "I", opcode = Opcode.SGET),
        opcode(Opcode.INVOKE_INTERFACE, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
    ),
)

// PlaybackController field (originally "Z") + its auto-hide reset (originally "c()V"),
// read off the one back-to-back use inside the SDK override onGenericMotionEvent.
internal object PlaybackControllerResetHideFingerprint : Fingerprint(
    definingClass = ACTIVITY_SCREEN_CLASS,
    name = "onGenericMotionEvent",
    returnType = "Z",
    parameters = listOf("Landroid/view/MotionEvent;"),
    filters = listOf(
        fieldAccess(
            type = "Lcom/mxtech/videoplayer/widget/PlaybackController;",
            opcode = Opcode.IGET_OBJECT,
        ),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()),
    ),
)

// PlaybackController.b callback (originally "O0"): real parameter types, unique in ActivityScreen.
internal object PlaybackControllerCallbackFingerprint : Fingerprint(
    definingClass = ACTIVITY_SCREEN_CLASS,
    returnType = "V",
    parameters = listOf(
        "Lcom/mxtech/videoplayer/widget/PlaybackController;",
        "I",
        "I",
        "Z",
    ),
)

/**
 * Compare-build edits (stock -> Slider), plus the lock/unlock fix:
 *  - Ha() opens a 0-100% popup slider (Mod Settings switch) instead of toggling                    [B: showEnhancePercentMenu]
 *  - seek listener class -> extension EnhanceSlider (B: menu/EnhanceSeekListener)
 *  - V8(I) -> patch_onEnhancePercent(I): hide-timer nudge, q flag + Sa(), level, apply [B: V8]
 *  - E0(IF) -> patch_applyEnhance(IF) on the player class (shared core; stock E0(I) left intact)
 *  - level field                                                              [B: m9f]
 *  - popup dismissed when the playback controls hide                          [B: O0 prologue]
 *  - fix: H6 (state change on lock/unlock) no longer calls M9() (which switches the effect off);
 *    it refreshes the icon with Sa() and restarts the retrying re-applier. "Next video" callers
 *    of M9 are untouched, so the effect still resets per video.
 */
internal val smartEnhanceControlSliderPatch = bytecodePatch(
    name = "Smart Enhance Slider",
    description = "Replaces the Smart Enhance toggle with a 0-100% popup slider and keeps the level after lock/unlock.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)
    dependsOn(smartEnhanceCorePatch, modSettingFlagPatch(KEY_ENHANCE_SLIDER))

    execute {
        val activityScreen = EnhanceRefs.activityScreen
        val activity = activityScreen.type
        val qField = EnhanceRefs.qField.smali()
        val pField = EnhanceRefs.playerField.smali()
        val pType = EnhanceRefs.playerType
        val saMethod = EnhanceRefs.saRef.smali()

        val menuMatches = SmartEnhanceMenuItemFieldFingerprint.instructionMatches
        val m1Ref = menuMatches[3].getInstruction<ReferenceInstruction>().reference as FieldReference
        val m1Field = m1Ref.smali()

        val hideMatches = PlaybackControllerResetHideFingerprint.instructionMatches
        val zRef = hideMatches[0].getInstruction<ReferenceInstruction>().reference as FieldReference
        val cRef = hideMatches[1].getInstruction<ReferenceInstruction>().reference as MethodReference

        activityScreen.addPatchField(ENHANCE_SLIDER_FIELD, ENHANCE_SLIDER_CLASS)
        if (ENHANCE_SLIDER_HOST !in activityScreen.interfaces) activityScreen.interfaces.add(ENHANCE_SLIDER_HOST)

        // EnhanceSlider.Host implementation (originally V8(I)V).
        activityScreen.addPatchMethod(
            ENHANCE_ON_PERCENT_METHOD,
            listOf("I"),
            "V",
            6,
            """
                iget-object v0, p0, ${zRef.smali()}
                if-eqz v0, :no_hide
                invoke-virtual {v0}, ${cRef.smali()}
                :no_hide
                sget-boolean v0, $qField
                if-eqz p1, :flag_off
                const/4 v1, 0x1
                goto :flag_set
                :flag_off
                const/4 v1, 0x0
                :flag_set
                if-eq v0, v1, :flag_same
                sput-boolean v1, $qField
                invoke-virtual {p0}, $saMethod
                :flag_same
                iget-object v0, p0, $pField
                if-eqz p1, :off
                int-to-float v2, p1
                const/high16 v3, 0x42c80000
                div-float/2addr v2, v3
                const v3, $ENHANCE_DEFAULT_LEVEL
                mul-float/2addr v2, v3
                iput v2, p0, $activity->$ENHANCE_LEVEL_FIELD:F
                if-eqz v0, :done
                const/4 v3, 0x1
                invoke-virtual {v0, v3, v2}, $pType->$ENHANCE_APPLY_METHOD(IF)V
                return-void
                :off
                const/4 v2, 0x0
                iput v2, p0, $activity->$ENHANCE_LEVEL_FIELD:F
                if-eqz v0, :done
                const/4 v3, -0x1
                invoke-virtual {v0, v3, v2}, $pType->$ENHANCE_APPLY_METHOD(IF)V
                :done
                return-void
            """,
        )

        // Popup builder call (originally showEnhancePercentMenu()V).
        activityScreen.addPatchMethod(
            ENHANCE_SHOW_METHOD,
            emptyList(),
            "V",
            5,
            """
                iget-object v0, p0, $m1Field
                if-eqz v0, :out
                invoke-interface {v0}, Landroid/view/MenuItem;->getActionView()Landroid/view/View;
                move-result-object v1
                if-eqz v1, :out
                iget-object v2, p0, $activity->$ENHANCE_SLIDER_FIELD:$ENHANCE_SLIDER_CLASS
                if-nez v2, :have
                new-instance v2, $ENHANCE_SLIDER_CLASS
                invoke-direct {v2, p0}, $ENHANCE_SLIDER_CLASS-><init>($ENHANCE_SLIDER_HOST)V
                iput-object v2, p0, $activity->$ENHANCE_SLIDER_FIELD:$ENHANCE_SLIDER_CLASS
                :have
                iget v3, p0, $activity->$ENHANCE_LEVEL_FIELD:F
                invoke-virtual {v2, v1, v3}, $ENHANCE_SLIDER_CLASS->show(Landroid/view/View;F)V
                :out
                return-void
            """,
        )

        // Ha(): open the slider when the Mod Settings switch is on, else stock toggle.
        val ha = SmartEnhanceHaFingerprint.method
        val haStart = ha.getInstruction(0)
        ha.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $ENHANCE_CONFIG_CLASS->sliderOn()Z
                move-result v0
                if-eqz v0, :stock
                move-object/from16 v0, p0
                invoke-virtual {v0}, $activity->$ENHANCE_SHOW_METHOD()V
                return-void
            """.trimIndent(),
            ExternalLabel("stock", haStart),
        )

        // Dismiss the popup when the controls hide (p2 == 0).
        val callback = PlaybackControllerCallbackFingerprint.method
        val callbackStart = callback.getInstruction(0)
        callback.addInstructionsWithLabels(
            0,
            """
                if-nez p2, :skip
                iget-object v0, p0, $activity->$ENHANCE_SLIDER_FIELD:$ENHANCE_SLIDER_CLASS
                if-eqz v0, :skip
                invoke-virtual {v0}, $ENHANCE_SLIDER_CLASS->dismiss()V
            """.trimIndent(),
            ExternalLabel("skip", callbackStart),
        )

        // --- lock/unlock fix: H6(B)V ------------------------------------------------
        // Found by content: the single (B)V method whose M9() call directly follows two
        // zero-arg void invoke-virtual calls (originally K9(), L9()).
        val m9Ref = EnhanceRefs.m9
        fun isZeroArgVoidVirtual(insn: Instruction): Boolean {
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: return false
            return insn.opcode == Opcode.INVOKE_VIRTUAL && ref.parameterTypes.isEmpty() && ref.returnType.toString() == "V"
        }

        fun isM9Call(insn: Instruction): Boolean {
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: return false
            return insn.opcode == Opcode.INVOKE_VIRTUAL &&
                ref.name == m9Ref.name &&
                ref.parameterTypes.isEmpty() &&
                ref.returnType.toString() == "V" &&
                ref.definingClass == m9Ref.definingClass
        }

        val candidates = activityScreen.methods.mapNotNull { candidate ->
            if (candidate.parameterTypes.map { it.toString() } != listOf("B") || candidate.returnType != "V") {
                return@mapNotNull null
            }
            val insns = candidate.implementation?.instructions ?: return@mapNotNull null
            for (i in 2 until insns.size) {
                if (isM9Call(insns[i]) && isZeroArgVoidVirtual(insns[i - 1]) && isZeroArgVoidVirtual(insns[i - 2])) {
                    return@mapNotNull candidate to i
                }
            }
            null
        }
        if (candidates.size != 1) {
            throw PatchException("Expected exactly one state-change caller of M9(), found ${candidates.size}")
        }
        val (stateChange, callIndex) = candidates.single()
        stateChange.replaceInstruction(callIndex, "invoke-virtual {p0}, $saMethod")
        stateChange.addInstructions(
            callIndex + 1,
            "invoke-virtual {p0}, $activity->$ENHANCE_KICK_METHOD()V",
        )
    }
}
