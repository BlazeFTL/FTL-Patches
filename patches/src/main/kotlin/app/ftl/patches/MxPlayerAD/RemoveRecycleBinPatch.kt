package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
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
    description = "Disables the Recycle Bin and removes it from the Me tab; deleted files are removed permanently.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    execute {
        // --- Me tab: never add the Recycle Bin tile -------------------------------
        // Literal mirror of the validated compare diff: only the if-nez -> goto edit,
        // the dead flag read above it is left untouched, same as the reference build.
        val tilesMethod = RecycleBinTileFingerprint.method
        val ifNezIndex = RecycleBinTileFingerprint.instructionMatches[2].index
        val stringIndex = RecycleBinTileFingerprint.instructionMatches[3].index
        // string -> invoke-direct -> invoke-virtual(add) -> :cond_3 target.
        val cond3Target = tilesMethod.getInstruction(stringIndex + 3)

        tilesMethod.removeInstruction(ifNezIndex)
        tilesMethod.addInstructionsWithLabels(
            ifNezIndex,
            "goto :cond_3",
            ExternalLabel("cond_3", cond3Target),
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
        // Real class name is obfuscated ("a" in the sample build, reshuffles every
        // build) and its only real strings turned out non-unique/wrong-method, so this
        // resolves it by the Kotlin source file name instead - R8 keeps original
        // source-file attributes even when it renames the class/members, and combined
        // with the real androidx superclass it uniquely picks this class out from its
        // sibling nested classes (a$a/a$b/a$c) that share the same source file.
        val dialogClass = mutableClassDefBy { classDef ->
            classDef.sourceFile == "MediaDeleteConfirmDialog.kt" &&
                classDef.superclass == "Landroidx/appcompat/app/d;"
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
