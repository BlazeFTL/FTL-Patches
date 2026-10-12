package app.ftl.patches.calendar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Suppress("unused")
val skipInitialSetupPagesPatch = bytecodePatch(
    name = "Skip initial setup pages",
    description = "Skips the permission and call setup pages shown after the language selection.",
) {
    compatibleWith(COMPATIBILITY_CALENDAR)

    execute {
        LanguageNextIntentFingerprint.method.addInstructions(
            0,
            """
                invoke-direct {p0}, $LANGUAGE_ACTIVITY->callIntent()$ANDROID_INTENT
                move-result-object p0
                return-object p0
            """
        )

        PermissionOnCreateFingerprint.method.addAfterSuperCall(
            """
                invoke-virtual {p0}, $PERMISSION_ACTIVITY->nextCall()V
                return-void
            """
        )

        SplashDestinationIntentFingerprint.method.apply {
            val list = implementation!!.instructions
            val callEndInstance = list.indexOfFirst {
                if (it.opcode != Opcode.SGET_OBJECT) return@indexOfFirst false
                val ref = (it as? ReferenceInstruction)?.reference as? FieldReference
                    ?: return@indexOfFirst false
                ref.name == "INSTANCE" && ref.definingClass.endsWith("/callend/CommonClass;")
            }
            if (callEndInstance < 0) throw PatchException("destinationIntent: CommonClass.INSTANCE not found")

            replaceInstruction(
                callEndInstance,
                "invoke-direct {p0}, $SPLASH_ACTIVITY->getNextScreenIntent()$ANDROID_INTENT"
            )
            addInstructions(
                callEndInstance + 1,
                """
                    move-result-object p0
                    return-object p0
                """
            )
        }
    }
}
