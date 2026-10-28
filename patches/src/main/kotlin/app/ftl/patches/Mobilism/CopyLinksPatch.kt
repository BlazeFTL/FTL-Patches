package app.ftl.patches.mobilism

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val COPY_LINKS = "$EXT_CLASS_PREFIX/CopyLinks;"
private const val FEATURES = "$EXT_CLASS_PREFIX/ModFeatures;"

internal object CopyLinksAfterViewFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Release/ReadMoreActivity;",
    name = "AfterView",
    returnType = "V",
    parameters = listOf()
)

internal object CopyLinksMarkFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)

@Suppress("unused")
val copyLinksPatch = bytecodePatch(
    name = "Copy link button",
    description = "Adds a copy icon after every link in release details. Configurable in Mod settings."
) {
    compatibleWith(COMPATIBILITY_MOBILISM)

    dependsOn(modSettingsPatch)

    extendWith("extensions/mobilism.mpe")

    execute {
        CopyLinksMarkFingerprint.method.insertAfterSuperCall(
            "invoke-static {}, $FEATURES->markLinks()V"
        )

        CopyLinksAfterViewFingerprint.method.let { method ->
            val index = method.indexOfVirtualCall("setTextFromHtml")
            val reg = method.implementation!!.instructions[index].firstRegister()
            method.addInstructions(
                index + 1,
                "invoke-static/range {v$reg .. v$reg}, $COPY_LINKS->attach(Landroid/widget/TextView;)V"
            )
        }
    }
}
