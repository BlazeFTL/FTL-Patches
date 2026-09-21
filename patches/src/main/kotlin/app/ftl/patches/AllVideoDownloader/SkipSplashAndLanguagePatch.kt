package app.ftl.patches.alldownloader

import app.ftl.patches.spoofsignature.spoofSignatureVerificationPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private object SplashOnCreateFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            name = "onCreate",
            parameters = listOf("Landroid/os/Bundle;"),
            returnType = "V",
        ),
    ),
)

private object AlreadyNavigatedGuardFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    returnType = "V",
    filters = listOf(
        fieldAccess(
            definingClass = "this",
            type = "Z",
            opcode = Opcode.IGET_BOOLEAN,
        ),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        opcode(Opcode.RETURN_VOID, MatchAfterImmediately()),
    ),
)

val skipSplashAndLanguagePatch = bytecodePatch(
    name = "Skip splash and language screens",
    description = "Jumps straight to the main activity from the splash screen, skipping the splash " +
        "animation, the language-selection screen, DONT UNSELECT THIS,IT HAS SIGNATURE BYPASS INCLUDED.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ALL_VIDEO_DOWNLOADER)

    dependsOn(spoofSignatureVerificationPatch)

    execute {
        val guardField = AlreadyNavigatedGuardFingerprint.instructionMatches.first()
            .getInstruction<ReferenceInstruction>().reference as FieldReference
        val guardFieldSmali = "${guardField.definingClass}->${guardField.name}:${guardField.type}"

        SplashOnCreateFingerprint.let { fingerprint ->
            val afterSuperIndex = fingerprint.instructionMatches.first().index + 1

            fingerprint.method.addInstructions(
                afterSuperIndex,
                """
                    move-object/from16 v0, p0
                    invoke-virtual {v0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                    move-result-object v2
                    new-instance v6, Landroid/graphics/drawable/ColorDrawable;
                    const/4 v7, 0x0
                    invoke-direct {v6, v7}, Landroid/graphics/drawable/ColorDrawable;-><init>(I)V
                    invoke-virtual {v2, v6}, Landroid/view/Window;->setBackgroundDrawable(Landroid/graphics/drawable/Drawable;)V
                    new-instance v2, Landroid/content/Intent;
                    const-class v6, $MAIN_ACTIVITY_CLASS
                    invoke-direct {v2, v0, v6}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
                    const v6, 0x34400000
                    invoke-virtual {v2, v6}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;
                    invoke-virtual {v0, v2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
                    const/4 v6, 0x0
                    invoke-virtual {v0, v6, v6}, Landroid/app/Activity;->overridePendingTransition(II)V
                    const/4 v6, 0x1
                    iput-boolean v6, v0, $guardFieldSmali
                    invoke-virtual {v0}, Landroid/app/Activity;->finishAffinity()V
                """.trimIndent(),
            )
        }
    }
}
