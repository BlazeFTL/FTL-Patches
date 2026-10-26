package app.ftl.patches.mobilism

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val HIDE_DISCUSSIONS = "$EXT_CLASS_PREFIX/HideDiscussions;"
private const val FEATURES = "$EXT_CLASS_PREFIX/ModFeatures;"

internal object HideDiscussionsCategoriesFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/Main/HomeFragment;",
    name = "getCategories",
    returnType = "V",
    parameters = listOf()
)

internal object HideDiscussionsMarkFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)

@Suppress("unused")
val hideDiscussionsPatch = bytecodePatch(
    name = "Hide discussions",
    description = "Hides Recent discussions, Reviews and Contests sections on Home. Configurable in Mod settings."
) {
    compatibleWith(COMPATIBILITY_MOBILISM)

    dependsOn(modSettingsPatch)

    extendWith("extensions/mobilism.mpe")

    execute {
        HideDiscussionsMarkFingerprint.method.insertAfterSuperCall(
            "invoke-static {}, $FEATURES->markDiscussions()V"
        )

        HideDiscussionsCategoriesFingerprint.method.let { method ->
            val index = method.indexOfFieldPut("categories")
            val reg = method.implementation!!.instructions[index].firstRegister()
            method.addInstructions(
                index + 1,
                "invoke-static/range {v$reg .. v$reg}, $HIDE_DISCUSSIONS->filterCategories(Ljava/util/List;)V"
            )
        }
    }
}
