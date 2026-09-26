package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/**
 * Matches the branch guarding whether the Recycle Bin tile is added to the Me tab's
 * local-tiles list, in `LocalMePageViewModel` (real, unobfuscated class name - already
 * pinned the same way by [app.ftl.patches.mxplayerad] siblings). Anchored on the real
 * `RecycleBinManager` class type before the flag read and the real `"recycleBin"` tile-key
 * string after it; the flag field itself (`Ljb5;->g:Z` in the sample build) is never
 * pinned since its defining class/name are obfuscated and reshuffle every build - only
 * its primitive `Z` type + opcode shape + position between the two real anchors identify it.
 */
private object RecycleBinTileFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ad/subscriptions/ui/metab/viewmodels/LocalMePageViewModel;",
    filters = listOf(
        fieldAccess(
            type = "Lcom/mxtech/bin/RecycleBinManager;",
            opcode = Opcode.SGET_OBJECT,
        ),
        fieldAccess(
            type = "Z",
            opcode = Opcode.SGET_BOOLEAN,
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        string("recycleBin", location = MatchAfterWithin(3)),
    ),
)

val removeRecycleBinPatch = bytecodePatch(
    name = "Remove Recycle Bin",
    description = "Deleted files are always removed permanently, whenever this patch is applied - " +
        "there's no safe way to make that half a runtime switch without the stock (unpatched) " +
        "delete-dialog code to fall back to. The Me tab tile itself is a Mod Settings switch: " +
        "off just brings the tile back, it doesn't restore recycling.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(modSettingsPatch, modSettingFlagPatch(KEY_ME_HIDE_RECYCLE_BIN))

    execute {
        // --- Me tab: Mod Settings switch for the Recycle Bin tile -----------------
        // Same method LocalMeTilesFingerprint/cleanMeTabTilesPatch instruments (y(), on
        // LocalMePageViewModel) - v1 is that method's "item under construction" register,
        // reused and fully drained right before every tile's own block starts, so it's
        // safe scratch here too (confirmed against the real y() smali, not assumed).
        // Registers its own onTilesOwner so the tile updates live even if Clean Me Tab
        // isn't applied in the same build; redundant (harmless) if it is.
        // :cond_b6 found the same way the original (unconditional) version of this
        // patch found it - string -> invoke-direct -> invoke-virtual(add) -> :cond_b6 -
        // rather than my own Label-based target() helper, since that's unproven for
        // this specific fingerprint and this offset is already known-correct.
        val tilesMethod = RecycleBinTileFingerprint.method
        val blockStart = RecycleBinTileFingerprint.instructionMatches[0].index
        val stringIndex = RecycleBinTileFingerprint.instructionMatches[3].index
        val hideTarget = tilesMethod.getInstruction(stringIndex + 3)

        tilesMethod.addInstructionsWithLabels(
    blockStart + 1,   // insert AFTER the RecycleBinManager sget-object, not before it
    """
        const-string v1, "$KEY_ME_HIDE_RECYCLE_BIN"
        invoke-static {v1}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
        move-result v1
        if-nez v1, :hide
    """.trimIndent(),
    ExternalLabel("hide", hideTarget),
)

        tilesMethod.addInstructions(
            0,
            """
                const-string v0, "${tilesMethod.name}"
                invoke-static {p0, v0}, $MOD_SETTINGS_CLASS->onTilesOwner(Ljava/lang/Object;Ljava/lang/String;)V
            """.trimIndent(),
        )

        // --- Delete dialog: always delete permanently ------------------------------
        // The patcher has no API to add a brand-new standalone class outside the
        // extension/.mpe pipeline, so this folds the reference build's separate
        // DeleteConfirmOk listener into the dialog class itself (self as listener)
        // instead of instantiating a second object - same runtime behavior.
        // Field names `j`/`p` and the androidx `d`/`d$a`/`AlertController$b` names are
        // this specific build's obfuscated/renamed identifiers (matches the validated
        // compare build, versionCode 2001003531) - re-check against a fresh compare
        // zip if this patch ever needs to target a different build.
        // Real class name is obfuscated ("a", reshuffles every build). sourceFile
        // ("MediaDeleteConfirmDialog.kt") used to anchor this, but this build also
        // applies Remove Debug Info, which clears every class's source-file
        // attribute along with line numbers/local names - so by the time this
        // patch's execute runs, sourceFile is null on every class, debug info or
        // not. Rebuilt on anchors untouched by either R8 renaming or debug-info
        // stripping, none of them obfuscated leaves:
        //   - superclass under the real "androidx/appcompat/app/" package (some
        //     AppCompatDialog-family type; the exact leaf letter, e.g. "d", still
        //     reshuffles per build so only the package prefix is pinned)
        //   - not a nested class ('$' in the type), which is what previously
        //     needed the now-gone sourceFile to rule out the a$a/a$b/a$c siblings
        //   - has a field of the real (never-obfuscated) stdlib type
        //     Ljava/util/Collection; (the pending-delete file list)
        //   - has a 2-arg constructor (name "<init>" is a fixed dex intrinsic,
        //     never renamed) whose first param is under the real
        //     "androidx/fragment/app/" package (FragmentActivity) and second is Z
        // Re-verify this combination is still unique in the class pool if this
        // ever needs to target a different build.
        val dialogClass = mutableClassDefBy { classDef ->
            classDef.superclass?.startsWith("Landroidx/appcompat/app/") == true &&
                '$' !in classDef.type &&
                classDef.fields.any { it.type == "Ljava/util/Collection;" } &&
                classDef.methods.any { method ->
                    method.name == "<init>" &&
                        method.parameters.size == 2 &&
                        method.parameters[0].type.startsWith("Landroidx/fragment/app/") &&
                        method.parameters[1].type == "Z"
                }
        }

        val onClickListenerType = "Landroid/content/DialogInterface\$OnClickListener;"
        if (onClickListenerType !in dialogClass.interfaces) {
            dialogClass.interfaces.add(onClickListenerType)
        }

        val showMethod = ImmutableMethod(
            dialogClass.type,
            "show",
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(6),
        ).toMutable()

        showMethod.addInstructions(
    0,
    """
        const-string v0, "$KEY_ME_HIDE_RECYCLE_BIN"
        invoke-static {v0}, $MOD_SETTINGS_CLASS->get(Ljava/lang/String;)Z
        move-result v0
        if-eqz v0, :stock

        invoke-virtual {p0}, Landroid/app/Dialog;->getContext()Landroid/content/Context;
        move-result-object v0

        new-instance v1, Landroidx/appcompat/app/d${'$'}a;
        invoke-direct {v1, v0}, Landroidx/appcompat/app/d${'$'}a;-><init>(Landroid/content/Context;)V

        iget-object v2, v1, Landroidx/appcompat/app/d${'$'}a;->b:Landroidx/appcompat/app/AlertController${'$'}b;

        const-string v3, "Delete"
        iput-object v3, v2, Landroidx/appcompat/app/AlertController${'$'}b;->e:Ljava/lang/CharSequence;

        const-string v3, "The following file will be deleted permanently."
        iput-object v3, v2, Landroidx/appcompat/app/AlertController${'$'}b;->g:Ljava/lang/CharSequence;

        const-string v3, "OK"
        invoke-virtual {v1, v3, p0}, Landroidx/appcompat/app/d${'$'}a;->h(Ljava/lang/CharSequence;Landroid/content/DialogInterface${'$'}OnClickListener;)V

        const-string v2, "Cancel"
        const/4 v3, 0x0
        invoke-virtual {v1, v2, v3}, Landroidx/appcompat/app/d${'$'}a;->e(Ljava/lang/CharSequence;Landroid/content/DialogInterface${'$'}OnClickListener;)V

        invoke-virtual {v1}, Landroidx/appcompat/app/d${'$'}a;->n()Landroidx/appcompat/app/d;
        move-result-object v1

        sget v4, Landroid/R${'$'}id;->button1:I
        invoke-virtual {v1, v4}, Landroid/app/Dialog;->findViewById(I)Landroid/view/View;
        move-result-object v4

        if-eqz v4, :cond_end

        invoke-virtual {v4}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;
        move-result-object v0
        check-cast v0, Landroid/view/ViewGroup${'$'}MarginLayoutParams;
        iget v3, v0, Landroid/view/ViewGroup${'$'}MarginLayoutParams;->leftMargin:I
        add-int/lit8 v3, v3, 0x3c
        iput v3, v0, Landroid/view/ViewGroup${'$'}MarginLayoutParams;->leftMargin:I
        invoke-virtual {v4, v0}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V

        :cond_end
        return-void

        :stock
        invoke-super {p0}, Landroidx/appcompat/app/d;->show()V
        return-void
    """.trimIndent(),
)

        dialogClass.methods.add(showMethod)

        val onClickMethod = ImmutableMethod(
            dialogClass.type,
            "onClick",
            listOf(
                ImmutableMethodParameter("Landroid/content/DialogInterface;", null, null),
                ImmutableMethodParameter("I", null, null),
            ),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(6),
        ).toMutable()

        onClickMethod.addInstructions(
            0,
            """
                iget-object v0, p0, ${dialogClass.type}->j:Ljava/util/Collection;
                if-eqz v0, :cond_0

                iget-object v1, p0, ${dialogClass.type}->p:Lut8;
                if-eqz v1, :cond_0

                const/4 v2, 0x1
                invoke-interface {v1, v0, v2}, Lut8;->a(Ljava/util/Collection;Z)V

                :cond_0
                return-void
            """.trimIndent(),
        )

        dialogClass.methods.add(onClickMethod)
    }
}
