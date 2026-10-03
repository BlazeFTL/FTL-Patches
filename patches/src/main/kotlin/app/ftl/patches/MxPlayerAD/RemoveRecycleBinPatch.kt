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
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

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
        val tilesMethod = RecycleBinTileFingerprint.method
        val blockStart = RecycleBinTileFingerprint.instructionMatches[0].index
        val stringIndex = RecycleBinTileFingerprint.instructionMatches[3].index
        val hideTarget = tilesMethod.getInstruction(stringIndex + 3)

        tilesMethod.addInstructionsWithLabels(
            blockStart + 1,
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

        val collectionField = dialogClass.fields.first { it.type == "Ljava/util/Collection;" }

        var callbackMethodName = ""
        val callbackField = dialogClass.fields.single { field ->
            val type = classDefByOrNull(field.type) ?: return@single false
            if (!AccessFlags.INTERFACE.isSet(type.accessFlags)) return@single false
            val method = type.methods.firstOrNull { m ->
                m.returnType == "V" &&
                    m.parameterTypes.map { it.toString() } == listOf("Ljava/util/Collection;", "Z")
            } ?: return@single false
            callbackMethodName = method.name
            true
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
                iget-object v0, p0, ${dialogClass.type}->${collectionField.name}:Ljava/util/Collection;
                if-eqz v0, :cond_0

                iget-object v1, p0, ${dialogClass.type}->${callbackField.name}:${callbackField.type}
                if-eqz v1, :cond_0

                const/4 v2, 0x1
                invoke-interface {v1, v0, v2}, ${callbackField.type}->$callbackMethodName(Ljava/util/Collection;Z)V

                :cond_0
                return-void
            """.trimIndent(),
        )

        dialogClass.methods.add(onClickMethod)
    }
}
