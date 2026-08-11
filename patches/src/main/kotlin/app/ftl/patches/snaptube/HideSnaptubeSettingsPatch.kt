package app.ftl.patches.snaptube

import app.ftl.util.getFreeRegisterProvider
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_HIDE_CATEGORIES =
    "Lapp/ftl/extension/snaptube/SnaptubeSettingsHider;->hideCategories(Landroidx/preference/PreferenceGroup;)V"

private const val EXTENSION_HIDE_PREFERENCES =
    "Lapp/ftl/extension/snaptube/SnaptubeSettingsHider;->hidePreferences(Landroidx/preference/PreferenceFragmentCompat;)V"

internal object SettingsOnCreatePreferencesFingerprint : Fingerprint(
    definingClass = "Lcom/snaptube/premium/settings/SettingsPreferenceFragment;",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;", "Ljava/lang/String;"),
    filters = listOf(
        methodCall(smali = "Landroidx/preference/PreferenceFragmentCompat;->A2(I)V"),
    ),
)

internal object SettingsOnViewCreatedFingerprint : Fingerprint(
    definingClass = "Lcom/snaptube/premium/settings/SettingsPreferenceFragment;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(smali = "Landroidx/preference/PreferenceFragmentCompat;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V"),
        methodCall(smali = "Lcom/snaptube/premium/settings/SettingsPreferenceFragment;->E3()V"),
    ),
)

@Suppress("unused")
val hideSnaptubeSettingsPatch = bytecodePatch(
    name = "Hide Settings Items",
    description = "Hides the Download tools and Phone clean categories, and their sub-items, from Settings.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_SNAPTUBE)

    extendWith("extensions/snaptube.mpe")

    execute {
        SettingsOnCreatePreferencesFingerprint.let {
            val anchor = it.instructionMatches[0].index
            val register = it.method.getFreeRegisterProvider(anchor + 1, 1).getFreeRegister()

            it.method.addInstructions(
                anchor + 1,
                """
                invoke-virtual {p0}, Landroidx/preference/PreferenceFragmentCompat;->E2()Landroidx/preference/PreferenceScreen;

                move-result-object v$register

                invoke-static {v$register}, $EXTENSION_HIDE_CATEGORIES
                """.trimIndent(),
            )
        }

        SettingsOnViewCreatedFingerprint.let {
            val superCallIndex = it.instructionMatches[0].index
            val e3CallIndex = it.instructionMatches[1].index

            it.method.addInstructions(e3CallIndex + 1, "invoke-static {p0}, $EXTENSION_HIDE_PREFERENCES")
            it.method.addInstructions(superCallIndex + 1, "invoke-static {p0}, $EXTENSION_HIDE_PREFERENCES")
        }
    }
}
