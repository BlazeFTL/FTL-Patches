package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

/**
 * Matches the constructor of the "rate us" dialog. Both the class and the
 * constructor's own name are obfuscated and reshuffle every build, so neither
 * is pinned. Instead this matches on 2 real, unobfuscated Android SDK calls the
 * constructor makes: `Dialog.setContentView(int)` and
 * `RatingBar.setOnRatingBarChangeListener(...)`. No other dialog in the app both
 * extends Dialog and wires up a RatingBar, so this combination is unique app-wide.
 */
private object RateDialogConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/app/Dialog;",
            name = "setContentView",
            parameters = listOf("I"),
            returnType = "V",
        ),
        methodCall(
            definingClass = "Landroid/widget/RatingBar;",
            name = "setOnRatingBarChangeListener",
            parameters = listOf("Landroid/widget/RatingBar\$OnRatingBarChangeListener;"),
            returnType = "V",
        ),
    ),
)

val disableRateDialogPatch = bytecodePatch(
    name = "Disable rate us dialog",
    description = "Overrides show() on the in-app \"rate us\" dialog so it's still built but never displayed.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)

    execute {
        // The class itself is obfuscated, so it's read back off the match rather
        // than hardcoded.
        val dialogClass = RateDialogConstructorFingerprint.classDef

        // Dialog.show() is public, non-final and virtual, so adding an override
        // here intercepts every call site without needing to find or touch them -
        // the same fix as the reference diff, just applied to whatever the
        // obfuscated class name is in a given build.
        val showMethod = ImmutableMethod(
            dialogClass.type,
            "show",
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value,
            emptySet(),
            emptySet(),
            MutableMethodImplementation(1),
        ).toMutable()

        showMethod.addInstruction("return-void")

        dialogClass.methods.add(showMethod)
    }
}
