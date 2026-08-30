package app.ftl.patches.videodownloader

import app.ftl.patches.spoofsignature.spoofSignatureVerificationPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// Purchase-verification helper. It lives in an obfuscated top-level class (Lp60-style,
// single letter + digits) under an equally obfuscated method name ("g") - both change
// every build, so neither is pinned. The match relies only on the unobfuscated Play
// Billing Library + java.util calls this method makes (TextUtils.isEmpty, then walking
// an ArrayList<Purchase> checking getProducts()/getPurchaseState()), a combination rare
// enough to stay unique without a class or method name anchor.
internal object IsPurchaseValidFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;", "Ljava/util/ArrayList;"),
    filters = listOf(
        methodCall(smali = "Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        // Default "not found" / "list empty" return value - flipping this 0->1
        // makes the check return true (purchased) whenever no matching active
        // purchase is found, which is what the loop below falls through to.
        opcode(Opcode.CONST_4, MatchAfterImmediately()),
        methodCall(smali = "Lcom/android/billingclient/api/Purchase;->getProducts()Ljava/util/List;"),
        methodCall(smali = "Lcom/android/billingclient/api/Purchase;->getPurchaseState()I"),
    ),
)

val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Only Use In V2.7.2. Signature verification is spoofed automatically " +
        "so the purchase check passes without manually applying Spoof app signature.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_VIDEO_DOWNLOADER_UNLOCK_PRO)

    dependsOn(spoofSignatureVerificationPatch)

    execute {
        IsPurchaseValidFingerprint.let {
            val constIndex = it.instructionMatches[2].index
            val register = (it.method.implementation!!.instructions[constIndex] as OneRegisterInstruction).registerA
            it.method.replaceInstruction(constIndex, "const/4 v$register, 0x1")
        }
    }
}
