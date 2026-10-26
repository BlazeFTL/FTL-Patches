package app.ftl.patches.mobilism

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val HIDE_PREMIUM = "$EXT_CLASS_PREFIX/HidePremium;"
private const val FEATURES = "$EXT_CLASS_PREFIX/ModFeatures;"

internal object HidePremiumNavDrawerFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/MainActivity;",
    name = "setNavigationDrawer",
    returnType = "V",
    parameters = listOf()
)

internal object HidePremiumDownloadFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Release/ReleaseActivtiy_;",
    name = "onViewChanged",
    returnType = "V",
    parameters = listOf("Lorg/androidannotations/api/view/HasViews;")
)

internal object HidePremiumMarkFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)

@Suppress("unused")
val hidePremiumPatch = bytecodePatch(
    name = "Hide premium",
    description = "Hides the Go premium button and the premium download button. Configurable in Mod settings."
) {
    compatibleWith(COMPATIBILITY_MOBILISM)

    dependsOn(modSettingsPatch)

    extendWith("extensions/mobilism.mpe")

    execute {
        HidePremiumMarkFingerprint.method.insertAfterSuperCall(
            "invoke-static {}, $FEATURES->markPremium()V"
        )

        HidePremiumNavDrawerFingerprint.method.let { method ->
            method.indexesOfReturnVoid().asReversed().forEach { index ->
                method.addInstructions(
                    index,
                    "invoke-static/range {p0 .. p0}, $HIDE_PREMIUM->onNavDrawer(Ljava/lang/Object;)V"
                )
            }
        }

        HidePremiumDownloadFingerprint.method.let { method ->
            val index = method.indexOfFieldPut("premiumDownloads")
            val reg = method.implementation!!.instructions[index].firstRegister()
            method.addInstructions(
                index + 1,
                "invoke-static/range {v$reg .. v$reg}, $HIDE_PREMIUM->onPremiumDownload(Ljava/lang/Object;)V"
            )
        }
    }
}
