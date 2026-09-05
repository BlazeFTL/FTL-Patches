package app.ftl.patches.alldownloader

import app.ftl.patches.spoofsignature.spoofSignatureVerificationPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * Matches SplashActivity's onCreate(Bundle) by its real signature. The insertion
 * point is anchored on the app's own Play Store review-prompt URL literal (contains
 * the app's package name), which sits right after invoke-super in every build seen
 * so far - a stable, app-specific string beats pinning an obfuscated instruction
 * index.
 */
private object SplashOnCreateFingerprint : Fingerprint(
    definingClass = SPLASH_ACTIVITY_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        string("https://play.google.com/store/apps/details?id=videoplayer.videodownloader.downloader"),
    ),
)

/**
 * SplashActivity guards its "already navigated away" state with a private boolean
 * field whose name is a single obfuscated letter that reshuffles every build (it
 * was "c" in the sample provided). Rather than pinning that name, this fingerprint
 * finds the field structurally: it is the boolean field read at the very start of
 * a void method, immediately guarding a `return-void` (an "if already done, bail"
 * idiom used twice in this class, both times on the same field). Whichever build
 * this field is named, this pattern finds it and the exact reference is read back
 * off the match instead of being hardcoded.
 */
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
        "animation, the language-selection screen, and any ad/app-open dialog normally shown first.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ALL_VIDEO_DOWNLOADER)

    dependsOn(spoofSignatureVerificationPatch)

    execute {
        // Resolve the obfuscated guard field's current name before touching onCreate.
        val guardField = AlreadyNavigatedGuardFingerprint.instructionMatches.first()
            .getInstruction<ReferenceInstruction>().reference as FieldReference
        val guardFieldSmali = "${guardField.definingClass}->${guardField.name}:${guardField.type}"

        SplashOnCreateFingerprint.let { fingerprint ->
            val urlStringIndex = fingerprint.instructionMatches.first().index

            // Registers v0/v1 are still live here (v0 holds Build.VERSION.SDK_INT,
            // read again later in the method; v1 is about to be reloaded by the
            // original const-string right after this insert) - v4/v5/v6 are free
            // local registers within the method's existing register count.
            fingerprint.method.addInstructions(
                urlStringIndex,
                """
                    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                    move-result-object v4
                    new-instance v5, Landroid/graphics/drawable/ColorDrawable;
                    const/4 v6, 0x0
                    invoke-direct {v5, v6}, Landroid/graphics/drawable/ColorDrawable;-><init>(I)V
                    invoke-virtual {v4, v5}, Landroid/view/Window;->setBackgroundDrawable(Landroid/graphics/drawable/Drawable;)V
                    new-instance v4, Landroid/content/Intent;
                    const-class v5, $MAIN_ACTIVITY_CLASS
                    invoke-direct {v4, p0, v5}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
                    const v5, 0x34400000
                    invoke-virtual {v4, v5}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;
                    invoke-virtual {p0, v4}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
                    const/4 v5, 0x0
                    invoke-virtual {p0, v5, v5}, Landroid/app/Activity;->overridePendingTransition(II)V
                    const/4 v5, 0x1
                    iput-boolean v5, p0, $guardFieldSmali
                    invoke-virtual {p0}, Landroid/app/Activity;->finishAffinity()V
                """.trimIndent(),
            )
        }
    }
}

