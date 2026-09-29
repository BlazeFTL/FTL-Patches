package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

// Names Slider itself defines (not obfuscated - safe to share as literals with
// SmartEnhanceAlwaysOnPatch.kt, which calls into these).
internal const val ENHANCE_SET_FILTER_METHOD = "patch_setEnhanceFilterValue"
internal const val ENHANCE_APPLY_PERCENT_METHOD = "patch_applySmartEnhancePercent"
internal const val ENHANCE_LEVEL_FIELD = "patch_smartEnhanceLevel"
internal const val ENHANCE_SEEKBAR_FIELD = "patch_smartEnhanceSeekBar"
internal const val ENHANCE_LABEL_FIELD = "patch_smartEnhancePercentLabel"
internal const val ENHANCE_POPUP_FIELD = "patch_smartEnhancePercentPopup"
internal const val ENHANCE_RESTART_FORCER_METHOD = "patch_restartSmartEnhanceForcer"
internal const val ENHANCE_FORCER_HANDLER_FIELD = "patch_smartEnhanceForcerHandler"
internal const val ENHANCE_FORCER_TICKS_FIELD = "patch_smartEnhanceForcerTicks"

/**
 * Locates the field write for the toolbar's "Smart Enhance" MenuItem (originally
 * "m1" on ActivityScreen). Anchored on the real, stable resource-entry name
 * "enhance" (Lxxx;->enhance:I - resource ID holder classes get reobfuscated per
 * build, but resource *entry names* are tied to resources.arsc/aapt2 linkage and
 * don't rename the way code identifiers do) followed by the real Menu.findItem(I)
 * SDK call. The obfuscated holder class is never pinned, only the field's own
 * name/type via fieldAccess(name = ...).
 */
internal object SmartEnhanceMenuItemFieldFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ActivityScreen;",
    filters = listOf(
        fieldAccess(name = "enhance", type = "I", opcode = Opcode.SGET),
        opcode(Opcode.INVOKE_INTERFACE, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
    ),
)

// Real, stable SDK interface override names - Android's OnSeekBarChangeListener
// dispatch means R8 cannot rename these without breaking the interface, in any build.
internal object OnProgressChangedFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ActivityScreen;",
    name = "onProgressChanged",
    returnType = "V",
    parameters = listOf("Landroid/widget/SeekBar;", "I", "Z"),
)

internal object OnStartTrackingTouchFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ActivityScreen;",
    name = "onStartTrackingTouch",
    returnType = "V",
    parameters = listOf("Landroid/widget/SeekBar;"),
)

internal object OnStopTrackingTouchFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ActivityScreen;",
    name = "onStopTrackingTouch",
    returnType = "V",
    parameters = listOf("Landroid/widget/SeekBar;"),
)

/**
 * Extracts ActivityScreen's PlaybackController field (originally "Z") and its
 * auto-hide-timer reset method (originally "c()V") from the one place in stock where
 * they're used back-to-back inside a real, unobfuscated SDK override:
 * onGenericMotionEvent(MotionEvent)Z has exactly one read of that field, immediately
 * followed by the reset call. Nothing is inserted here - only the two references are
 * read, and neither obfuscated name is ever pinned.
 */
internal object PlaybackControllerResetHideFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ActivityScreen;",
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

/**
 * Locates ActivityScreen's PlaybackController state-change callback (originally
 * "O0" - the app's own listener interface, not an Android SDK one, so unlike the
 * SeekBar callbacks above the name itself isn't safe to pin). Matched purely by
 * defining class + real parameter/return types - the only obfuscated thing about
 * this signature is the method's own name, which is never referenced.
 * Verify uniqueness against a live dex before shipping (rule 6): this exact
 * (PlaybackController, I, I, Z)V shape is plausible-but-unconfirmed to be
 * singular within ActivityScreen.
 */
internal object PlaybackControllerCallbackFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ActivityScreen;",
    returnType = "V",
    parameters = listOf(
        "Lcom/mxtech/videoplayer/widget/PlaybackController;",
        "I",
        "I",
        "Z",
    ),
)

// Stock M9(): the "next video" reset - force-disables Smart Enhance (q=false,
// E0(-1)). Moved here (from SmartEnhanceAlwaysOnPatch.kt) because Slider itself now
// also needs M9's identity, independent of whether AlwaysOn is installed: locking
// and unlocking the screen tears down and rebuilds the SurfaceView (confirmed by
// reading H9()'s real body - it nulls the SurfaceView/SurfaceHolder), and the one
// caller of M9 that runs on that path (found below by content, not by name) force-
// disables the effect regardless of what the user set the slider to. Anchored
// purely on opcode/literal shape, no obfuscated names read. Verify uniqueness
// against a live dex before shipping (rule 6).
internal object SmartEnhanceForceMethodFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        opcode(Opcode.CONST_4),
        opcode(Opcode.SPUT_BOOLEAN, location = MatchAfterImmediately()),
        opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        literal(-1, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()), // E0(I)V
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()), // icon refresh
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
    ),
)

// Real, stable SDK interface override - SurfaceHolder.Callback.surfaceCreated is
// never renamed, same reasoning as the SeekBar callbacks above. Fires whenever a
// surface is (re)built: a fresh video open, and a lock/unlock rebuild alike.
internal object SurfaceCreatedFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ActivityScreen;",
    name = "surfaceCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/SurfaceHolder;"),
)

internal val smartEnhanceControlSliderPatch = bytecodePatch(
    name = "Smart Enhance Control Slider",
    description = "Replaces the Smart Enhance on/off toggle with a live 0-100% popup slider " +
        "in the player.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        // --- Gather every real/obfuscated reference we need, all via anchors ------
        val haMethod = SmartEnhanceToggleFingerprint.method
        val haMatches = SmartEnhanceToggleFingerprint.instructionMatches
        val haInstructions = haMethod.implementation!!.instructions
        val activityScreenType = haMethod.definingClass

        // Fixed prefix of Ha(), confirmed byte-identical across both compare builds:
        // move-object/from16 v0,p0 ; sget-boolean(Llle;->q) ; xor-int/2addr ;
        // sput-boolean(Llle;->q) ; invoke-virtual/range(Sa()V) <- haMatches[0]
        val llleQFieldRef =
            (haInstructions[haMatches[0].index - 3] as ReferenceInstruction).reference as FieldReference
        val saMethodRef =
            (haInstructions[haMatches[0].index] as ReferenceInstruction).reference as MethodReference
        // haMatches[4] = iget-object (the "I" field, ActivityScreen->I:Lp;)
        // haMatches[5] = invoke-virtual (original E0(I)V call - opcode-only match,
        // so this still resolves correctly regardless of what else has touched Ha()
        // by the time this patch runs, since nothing else changes this call's
        // *opcode*, only what's unreachable beneath it)
        val pFieldRef =
            (haInstructions[haMatches[4].index] as ReferenceInstruction).reference as FieldReference
        val originalE0Ref =
            (haInstructions[haMatches[5].index] as ReferenceInstruction).reference as MethodReference

        val activityScreenClass = mutableClassDefBy(activityScreenType)
            ?: throw PatchException("Could not resolve ActivityScreen class")

        val forceMethod = SmartEnhanceForceMethodFingerprint.method

        val pClass = mutableClassDefBy(pFieldRef.type)
            ?: throw PatchException("Could not resolve class ${pFieldRef.type}")

        val originalE0Method = pClass.methods.firstOrNull { candidate ->
            candidate.name == originalE0Ref.name &&
                candidate.parameterTypes.map { it.toString() } == originalE0Ref.parameterTypes.map { it.toString() }
        } ?: throw PatchException("Could not find original E0(I)V-equivalent method on ${pFieldRef.type}")

        // Fixed prefix of the original filter-apply method, confirmed identical in
        // both stock and the real Slider build's own copy of it:
        // iget-object v0,p0,H:Lb; ; if-nez ; return-void ; invoke-virtual(W()) <- index 3
        val originalE0Instructions = originalE0Method.implementation!!.instructions
        val hFieldRef = (originalE0Instructions[0] as ReferenceInstruction).reference as FieldReference
        val wMethodRef = (originalE0Instructions[3] as ReferenceInstruction).reference as MethodReference
        val hField = "${hFieldRef.definingClass}->${hFieldRef.name}:${hFieldRef.type}"
        val wMethod = "${wMethodRef.definingClass}->${wMethodRef.name}()${wMethodRef.returnType}"
        val ffPlayerType = wMethodRef.returnType.toString()

        val menuItemMatches = SmartEnhanceMenuItemFieldFingerprint.instructionMatches
        val menuItemInstructions = SmartEnhanceMenuItemFieldFingerprint.method.implementation!!.instructions
        val m1FieldRef =
            (menuItemInstructions[menuItemMatches[3].index] as ReferenceInstruction).reference as FieldReference
        val m1Field = "${m1FieldRef.definingClass}->${m1FieldRef.name}:${m1FieldRef.type}"

        val resetHideMatches = PlaybackControllerResetHideFingerprint.instructionMatches
        val resetHideInstructions =
            PlaybackControllerResetHideFingerprint.method.implementation!!.instructions
        val zFieldRef =
            (resetHideInstructions[resetHideMatches[0].index] as ReferenceInstruction).reference as FieldReference
        val cMethodRef =
            (resetHideInstructions[resetHideMatches[1].index] as ReferenceInstruction).reference as MethodReference
        val zField = "${zFieldRef.definingClass}->${zFieldRef.name}:${zFieldRef.type}"
        val cMethod = "${cMethodRef.definingClass}->${cMethodRef.name}()${cMethodRef.returnType}"

        val llleQField = "${llleQFieldRef.definingClass}->${llleQFieldRef.name}:${llleQFieldRef.type}"
        val saMethod = "${saMethodRef.definingClass}->${saMethodRef.name}()${saMethodRef.returnType}"
        val pType = pFieldRef.type
        val pField = "${pFieldRef.definingClass}->${pFieldRef.name}:$pType"

        // --- 1. New overload on class p: E0(IF)V, added alongside the untouched --
        // original E0(I)V (same body/behavior otherwise) so every existing 1-arg
        // caller - including Ha()'s dead branches below - keeps working unmodified.
        val setFilterMethod = ImmutableMethod(
            pClass.type,
            ENHANCE_SET_FILTER_METHOD,
            listOf(
                ImmutableMethodParameter("I", null, null),
                ImmutableMethodParameter("F", null, null),
            ),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(5),
        ).toMutable()

        setFilterMethod.addInstructions(
            0,
            """
                iget-object v0, p0, $hField
                if-nez v0, :cond_0
                return-void
                :cond_0
                invoke-virtual {v0}, $wMethod
                move-result-object v0
                if-eqz v0, :cond_1
                invoke-virtual {v0, p1}, $ffPlayerType->setFilter(I)V
                const/4 v1, 0x1
                if-ne p1, v1, :cond_1
                const/4 v1, 0x0
                invoke-virtual {v0, v1}, $ffPlayerType->setFilterSplitPosition(F)V
                invoke-virtual {v0, p2}, $ffPlayerType->setFilterValue(F)V
                :cond_1
                return-void
            """.trimIndent(),
        )
        pClass.methods.add(setFilterMethod)

        // --- 2. New fields on ActivityScreen ---------------------------------------
        // New state fields (all ours - no anchors needed).
        listOf(
            Triple(ENHANCE_LABEL_FIELD, "Landroid/widget/TextView;", AccessFlags.PRIVATE.value),
            Triple(ENHANCE_POPUP_FIELD, "Landroid/widget/PopupWindow;", AccessFlags.PRIVATE.value),
            Triple(ENHANCE_SEEKBAR_FIELD, "Landroid/widget/SeekBar;", AccessFlags.PRIVATE.value),
            Triple(ENHANCE_LEVEL_FIELD, "F", AccessFlags.PUBLIC.value),
        ).forEach { (name, type, flags) ->
            activityScreenClass.fields.add(
                ImmutableField(activityScreenType, name, type, flags, null, null, null).toMutable(),
            )
        }

        // --- 3. New static helper: pctLabel(I)Ljava/lang/String; ------------------
        val pctLabelMethod = ImmutableMethod(
            activityScreenType,
            "patch_smartEnhancePctLabel",
            listOf(ImmutableMethodParameter("I", null, null)),
            "Ljava/lang/String;",
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(3),
        ).toMutable()

        pctLabelMethod.addInstructions(
            0,
            """
                new-instance v0, Ljava/lang/StringBuilder;
                invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
                invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
                const-string v1, "%"
                invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object v0
                return-object v0
            """.trimIndent(),
        )
        activityScreenClass.methods.add(pctLabelMethod)

        // --- 4. New method: applySmartEnhancePercent(I)V --------------------------
        // Same logic as the real V8(I)V minus the dropped auto-hide-timer nudge:
        // update the live label if the popup is open, flip the enabled flag +
        // refresh the toolbar icon only on an actual state change, then apply the
        // level via the new E0(IF)V overload (0% == off, matching the real build).
        val applyPercentMethod = ImmutableMethod(
            activityScreenType,
            ENHANCE_APPLY_PERCENT_METHOD,
            listOf(ImmutableMethodParameter("I", null, null)),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(6),
        ).toMutable()

        applyPercentMethod.addInstructions(
            0,
            """
                iget-object v0, p0, $activityScreenType->$ENHANCE_LABEL_FIELD:Landroid/widget/TextView;
                if-eqz v0, :cond_1
                invoke-static {p1}, $activityScreenType->patch_smartEnhancePctLabel(I)Ljava/lang/String;
                move-result-object v1
                invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                :cond_1
                sget-boolean v0, $llleQField
                if-eqz p1, :cond_2
                const/4 v1, 0x1
                goto :goto_0
                :cond_2
                const/4 v1, 0x0
                :goto_0
                if-eq v0, v1, :cond_3
                sput-boolean v1, $llleQField
                invoke-virtual {p0}, $saMethod
                :cond_3
                if-eqz p1, :cond_off
                int-to-float v2, p1
                const/high16 v3, 0x42c80000
                div-float/2addr v2, v3
                const v3, 0x3e3851ec
                mul-float/2addr v2, v3
                iput v2, p0, $activityScreenType->$ENHANCE_LEVEL_FIELD:F
                goto :cond_have_level
                :cond_off
                const/4 v2, 0x0
                iput v2, p0, $activityScreenType->$ENHANCE_LEVEL_FIELD:F
                :cond_have_level
                iget-object v0, p0, $pField
                if-eqz v0, :cond_no_player
                if-eqz p1, :cond_native_off
                const/4 v3, 0x1
                invoke-virtual {v0, v3, v2}, $pType->$ENHANCE_SET_FILTER_METHOD(IF)V
                goto :cond_no_player
                :cond_native_off
                const/4 v3, -0x1
                invoke-virtual {v0, v3, v2}, $pType->$ENHANCE_SET_FILTER_METHOD(IF)V
                :cond_no_player
                return-void
            """.trimIndent(),
        )
        activityScreenClass.methods.add(applyPercentMethod)

        // --- 5. New method: showSmartEnhancePercentMenu()V ------------------------
        val showMenuMethod = ImmutableMethod(
            activityScreenType,
            "patch_showSmartEnhancePercentMenu",
            emptyList(),
            "V",
            AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(15),
        ).toMutable()

        showMenuMethod.addInstructions(
            0,
            """
                iget-object v0, p0, $m1Field
                if-eqz v0, :cond_0
                invoke-interface {v0}, Landroid/view/MenuItem;->getActionView()Landroid/view/View;
                move-result-object v1
                if-eqz v1, :cond_0
                invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;
                move-result-object v0
                invoke-virtual {v0}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;
                move-result-object v0
                iget v2, v0, Landroid/util/DisplayMetrics;->density:F
                const/high16 v3, 0x41800000
                mul-float/2addr v3, v2
                new-instance v4, Landroid/widget/LinearLayout;
                invoke-direct {v4, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V
                const/4 v0, 0x1
                invoke-virtual {v4, v0}, Landroid/widget/LinearLayout;->setOrientation(I)V
                invoke-virtual {v4, v0}, Landroid/widget/LinearLayout;->setGravity(I)V
                float-to-int v0, v3
                invoke-virtual {v4, v0, v0, v0, v0}, Landroid/view/View;->setPadding(IIII)V
                new-instance v5, Landroid/graphics/drawable/GradientDrawable;
                invoke-direct {v5}, Landroid/graphics/drawable/GradientDrawable;-><init>()V
                const v0, -0x33e5e5e6
                invoke-virtual {v5, v0}, Landroid/graphics/drawable/GradientDrawable;->setColor(I)V
                invoke-virtual {v5, v3}, Landroid/graphics/drawable/GradientDrawable;->setCornerRadius(F)V
                invoke-virtual {v4, v5}, Landroid/view/View;->setBackgroundDrawable(Landroid/graphics/drawable/Drawable;)V
                new-instance v6, Landroid/widget/TextView;
                invoke-direct {v6, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V
                const/4 v0, -0x1
                invoke-virtual {v6, v0}, Landroid/widget/TextView;->setTextColor(I)V
                const/4 v0, 0x1
                invoke-virtual {v6, v0}, Landroid/widget/TextView;->setGravity(I)V
                const/high16 v0, 0x41800000
                invoke-virtual {v6, v0}, Landroid/widget/TextView;->setTextSize(F)V
                new-instance v7, Landroid/widget/SeekBar;
                invoke-direct {v7, p0}, Landroid/widget/SeekBar;-><init>(Landroid/content/Context;)V
                const/16 v0, 0x64
                invoke-virtual {v7, v0}, Landroid/widget/SeekBar;->setMax(I)V
                iget v10, p0, $activityScreenType->$ENHANCE_LEVEL_FIELD:F
                const v11, 0x3e3851ec
                div-float/2addr v10, v11
                const/high16 v11, 0x42c80000
                mul-float/2addr v10, v11
                float-to-int v10, v10
                invoke-virtual {v7, v10}, Landroid/widget/SeekBar;->setProgress(I)V
                iput-object v7, p0, $activityScreenType->$ENHANCE_SEEKBAR_FIELD:Landroid/widget/SeekBar;
                invoke-virtual {v7, p0}, Landroid/widget/SeekBar;->setOnSeekBarChangeListener(Landroid/widget/SeekBar${'$'}OnSeekBarChangeListener;)V
                invoke-static {v10}, $activityScreenType->patch_smartEnhancePctLabel(I)Ljava/lang/String;
                move-result-object v13
                invoke-virtual {v6, v13}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                iput-object v6, p0, $activityScreenType->$ENHANCE_LABEL_FIELD:Landroid/widget/TextView;
                invoke-virtual {v4, v6}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V
                new-instance v13, Landroid/widget/LinearLayout${'$'}LayoutParams;
                const/4 v11, -0x1
                const/4 v12, -0x2
                invoke-direct {v13, v11, v12}, Landroid/widget/LinearLayout${'$'}LayoutParams;-><init>(II)V
                invoke-virtual {v4, v7, v13}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;Landroid/view/ViewGroup${'$'}LayoutParams;)V
                const/high16 v0, 0x43960000
                mul-float/2addr v0, v2
                float-to-int v0, v0
                new-instance v9, Landroid/widget/PopupWindow;
                const/4 v11, -0x2
                invoke-direct {v9, v4, v0, v11}, Landroid/widget/PopupWindow;-><init>(Landroid/view/View;II)V
                const/4 v11, 0x1
                invoke-virtual {v9, v11}, Landroid/widget/PopupWindow;->setOutsideTouchable(Z)V
                const/4 v11, 0x0
                invoke-virtual {v9, v11}, Landroid/widget/PopupWindow;->setFocusable(Z)V
                new-instance v12, Landroid/graphics/drawable/ColorDrawable;
                invoke-direct {v12, v11}, Landroid/graphics/drawable/ColorDrawable;-><init>(I)V
                invoke-virtual {v9, v12}, Landroid/widget/PopupWindow;->setBackgroundDrawable(Landroid/graphics/drawable/Drawable;)V
                iput-object v9, p0, $activityScreenType->$ENHANCE_POPUP_FIELD:Landroid/widget/PopupWindow;
                invoke-virtual {v9, v1, v11, v11}, Landroid/widget/PopupWindow;->showAsDropDown(Landroid/view/View;II)V
                :cond_0
                return-void
            """.trimIndent(),
        )
        activityScreenClass.methods.add(showMenuMethod)

        // --- 6. Static discriminator helpers (own registers - avoids growing the --
        // register count of the three existing SDK-interface methods below, which
        // this framework has no established API for doing safely).
        fun addSkipHelper(name: String, applyPercent: Boolean) {
            val params = listOf(
                ImmutableMethodParameter(activityScreenType, null, null),
                ImmutableMethodParameter("Landroid/widget/SeekBar;", null, null),
            ) + if (applyPercent) listOf(ImmutableMethodParameter("I", null, null)) else emptyList()

            val helper = ImmutableMethod(
                activityScreenType,
                name,
                params,
                "Z",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value,
                null,
                null,
                MutableMethodImplementation(if (applyPercent) 5 else 4),
            ).toMutable()

            val body = if (applyPercent) {
                """
                    iget-object v0, p0, $activityScreenType->$ENHANCE_SEEKBAR_FIELD:Landroid/widget/SeekBar;
                    if-ne v0, p1, :cond_no
                    iget-object v0, p0, $zField
                    if-eqz v0, :cond_skip
                    invoke-virtual {v0}, $cMethod
                    :cond_skip
                    invoke-virtual {p0, p2}, $activityScreenType->$ENHANCE_APPLY_PERCENT_METHOD(I)V
                    const/4 v0, 0x1
                    return v0
                    :cond_no
                    const/4 v0, 0x0
                    return v0
                """.trimIndent()
            } else {
                """
                    iget-object v0, p0, $activityScreenType->$ENHANCE_SEEKBAR_FIELD:Landroid/widget/SeekBar;
                    if-ne v0, p1, :cond_no
                    const/4 v0, 0x1
                    return v0
                    :cond_no
                    const/4 v0, 0x0
                    return v0
                """.trimIndent()
            }
            helper.addInstructions(0, body)
            activityScreenClass.methods.add(helper)
        }

        addSkipHelper("patch_maybeApplySmartEnhance", applyPercent = true)
        addSkipHelper("patch_maybeSkipStartTracking", applyPercent = false)
        addSkipHelper("patch_maybeSkipStopTracking", applyPercent = false)

        // --- 7. Redirect Ha() to the new popup -------------------------------------
        // Everything currently in Ha() becomes dead code below this, exactly as
        // confirmed in the real build's own dex. Left in place rather than deleted:
        // harmless, and any patch still fingerprinting the old toggle logic (the
        // toast patch) keeps matching it as inert dead code instead of failing.
        haMethod.addInstructions(
            0,
            """
                move-object/from16 v0, p0
                invoke-direct {v0}, $activityScreenType->patch_showSmartEnhancePercentMenu()V
                return-void
            """.trimIndent(),
        )

        // --- 8. Guard the three SeekBar callbacks against firing for the real -----
        // scrub bar's own logic when it's actually our popup's SeekBar.
        val progressMethod = OnProgressChangedFingerprint.method
        val progressStart = progressMethod.getInstruction(0)
        progressMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static {p0, p1, p2}, $activityScreenType->patch_maybeApplySmartEnhance(${activityScreenType}Landroid/widget/SeekBar;I)Z
                move-result p1
                if-eqz p1, :stock
                return-void
            """.trimIndent(),
            ExternalLabel("stock", progressStart),
        )

        val startMethod = OnStartTrackingTouchFingerprint.method
        val startStart = startMethod.getInstruction(0)
        startMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static {p0, p1}, $activityScreenType->patch_maybeSkipStartTracking(${activityScreenType}Landroid/widget/SeekBar;)Z
                move-result p1
                if-eqz p1, :stock
                return-void
            """.trimIndent(),
            ExternalLabel("stock", startStart),
        )

        val stopMethod = OnStopTrackingTouchFingerprint.method
        val stopStart = stopMethod.getInstruction(0)
        stopMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static {p0, p1}, $activityScreenType->patch_maybeSkipStopTracking(${activityScreenType}Landroid/widget/SeekBar;)Z
                move-result p1
                if-eqz p1, :stock
                return-void
            """.trimIndent(),
            ExternalLabel("stock", stopStart),
        )

        // --- 9. Dismiss the popup when the player controls themselves hide --------
        // Confirmed via the real build's diff: this callback's only change is these
        // 4 instructions prepended at index 0 (p2 == 0 signals controls hidden) -
        // everything else in the method is untouched, baksmali just renumbers the
        // existing :cond_N labels to make room for this one.
        val callbackMethod = PlaybackControllerCallbackFingerprint.method
        val callbackStart = callbackMethod.getInstruction(0)
        callbackMethod.addInstructionsWithLabels(
            0,
            """
                if-nez p2, :cond_0
                iget-object v0, p0, $activityScreenType->$ENHANCE_POPUP_FIELD:Landroid/widget/PopupWindow;
                if-eqz v0, :cond_0
                invoke-virtual {v0}, Landroid/widget/PopupWindow;->dismiss()V
            """.trimIndent(),
            ExternalLabel("cond_0", callbackStart),
        )

        // --- 10. Shared retry-forcer: survives surface teardown/rebuild -----------
        // Root cause (confirmed by reading the real methods, not assumed): lock/
        // unlock runs a state-change handler that tears the SurfaceView down (H9())
        // then resets Smart Enhance via M9() - and even where the state ISN'T force-
        // reset, the native player/filter pipeline gets rebuilt on that path, so a
        // single re-apply call can lose a race with it. This retries up to 8 times,
        // 300ms apart (mirrors the cadence of a hand-built EnhanceForcer.smali class
        // that already proved this timing works) - but folded into ActivityScreen
        // itself rather than a new class, since the patcher has no API to add a
        // brand-new standalone class (same constraint noted in removeRecycleBinPatch).
        // Reapplies whatever is currently in $ENHANCE_LEVEL_FIELD - so it works
        // whether that level was set by hand via the Control Slider or by AlwaysOn's
        // default, with no knowledge of which.
        val runnableType = "Ljava/lang/Runnable;"
        if (runnableType !in activityScreenClass.interfaces) activityScreenClass.interfaces.add(runnableType)

        listOf(
            Triple(ENHANCE_FORCER_HANDLER_FIELD, "Landroid/os/Handler;", AccessFlags.PRIVATE.value),
            Triple(ENHANCE_FORCER_TICKS_FIELD, "I", AccessFlags.PRIVATE.value),
        ).forEach { (name, type, flags) ->
            activityScreenClass.fields.add(
                ImmutableField(activityScreenType, name, type, flags, null, null, null).toMutable(),
            )
        }

        val forcerRunMethod = ImmutableMethod(
            activityScreenType,
            "run",
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(5),
        ).toMutable()
        forcerRunMethod.addInstructions(
            0,
            """
                sget-boolean v0, $llleQField
                if-eqz v0, :stop
                iget-object v1, p0, $pField
                if-eqz v1, :maybe_reschedule
                iget v2, p0, $activityScreenType->$ENHANCE_LEVEL_FIELD:F
                const/4 v3, 0x1
                invoke-virtual {v1, v3, v2}, $pType->$ENHANCE_SET_FILTER_METHOD(IF)V
                :maybe_reschedule
                iget v0, p0, $activityScreenType->$ENHANCE_FORCER_TICKS_FIELD:I
                add-int/lit8 v1, v0, -0x1
                iput v1, p0, $activityScreenType->$ENHANCE_FORCER_TICKS_FIELD:I
                if-lez v0, :stop
                iget-object v1, p0, $activityScreenType->$ENHANCE_FORCER_HANDLER_FIELD:Landroid/os/Handler;
                const-wide/16 v2, 0x12c
                invoke-virtual {v1, p0, v2, v3}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z
                :stop
                return-void
            """.trimIndent(),
        )
        activityScreenClass.methods.add(forcerRunMethod)

        val restartForcerMethod = ImmutableMethod(
            activityScreenType,
            ENHANCE_RESTART_FORCER_METHOD,
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(3),
        ).toMutable()
        restartForcerMethod.addInstructions(
            0,
            """
                iget-object v0, p0, $activityScreenType->$ENHANCE_FORCER_HANDLER_FIELD:Landroid/os/Handler;
                if-nez v0, :has_handler
                new-instance v0, Landroid/os/Handler;
                invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;
                move-result-object v1
                invoke-direct {v0, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V
                iput-object v0, p0, $activityScreenType->$ENHANCE_FORCER_HANDLER_FIELD:Landroid/os/Handler;
                :has_handler
                invoke-virtual {v0, p0}, Landroid/os/Handler;->removeCallbacks(Ljava/lang/Runnable;)V
                const/16 v1, 0x8
                iput v1, p0, $activityScreenType->$ENHANCE_FORCER_TICKS_FIELD:I
                invoke-virtual {p0}, $activityScreenType->run()V
                return-void
            """.trimIndent(),
        )
        activityScreenClass.methods.add(restartForcerMethod)

        // --- 11. Kick the forcer whenever a surface is (re)built -------------------
        val surfaceCreatedMethod = SurfaceCreatedFingerprint.method
        surfaceCreatedMethod.addInstructions(
            0,
            "invoke-virtual {p0}, $activityScreenType->$ENHANCE_RESTART_FORCER_METHOD()V",
        )

        // --- 12. Stop the lock/unlock state-change path from force-disabling ------
        // Found by content, not name: the one caller of M9() immediately preceded by
        // two other zero-arg-void invoke-virtual calls on p0, INSIDE a method whose
        // own real signature is (B)V - a single raw byte parameter, void return.
        // Confirmed by reading the real method (originally "H6(B)V") - the byte param
        // is a genuinely distinctive shape in this class, needed because the call-
        // triple shape alone isn't unique (multiple M9 callers share it). Nothing
        // after this call in H6 depends on M9's side effects (all void, no results
        // consumed), so swapping it for an icon refresh + forcer-restart is safe.
        // "Next video" and other M9 call sites are untouched, so per-video reset still
        // works normally - this only changes the lock/unlock/state-change path.
        fun isZeroArgVoidVirtualCall(insn: Instruction): Boolean {
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: return false
            return insn.opcode == Opcode.INVOKE_VIRTUAL &&
                ref.parameterTypes.isEmpty() &&
                ref.returnType.toString() == "V"
        }

        fun isForceMethodCall(insn: Instruction): Boolean {
            val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: return false
            return insn.opcode == Opcode.INVOKE_VIRTUAL &&
                ref.name == forceMethod.name &&
                ref.parameterTypes.map { it.toString() } == forceMethod.parameterTypes.map { it.toString() } &&
                ref.returnType.toString() == forceMethod.returnType.toString()
        }

        val stateChangeCandidates = activityScreenClass.methods.mapNotNull { candidate ->
            if (candidate.parameterTypes.map { it.toString() } != listOf("B") ||
                candidate.returnType.toString() != "V"
            ) {
                return@mapNotNull null
            }
            val insns = candidate.implementation?.instructions ?: return@mapNotNull null
            for (i in 2 until insns.size) {
                if (isForceMethodCall(insns[i]) &&
                    isZeroArgVoidVirtualCall(insns[i - 1]) &&
                    isZeroArgVoidVirtualCall(insns[i - 2])
                ) {
                    return@mapNotNull candidate to i
                }
            }
            null
        }
        if (stateChangeCandidates.size != 1) {
            throw PatchException(
                "Expected exactly one K9/L9/M9-shaped caller of M9(), " +
                    "found ${stateChangeCandidates.size} - fingerprint needs re-checking against this build",
            )
        }
        val (stateChangeMethod, m9CallIndex) = stateChangeCandidates.single()
        stateChangeMethod.removeInstructions(m9CallIndex, 1)
        stateChangeMethod.addInstructions(
            m9CallIndex,
            """
                invoke-virtual {p0}, $saMethod
                invoke-virtual {p0}, $activityScreenType->$ENHANCE_RESTART_FORCER_METHOD()V
            """.trimIndent(),
        )
    }
}
