package app.ftl.patches.snaptube

import app.ftl.util.getFreeRegisterProvider
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_HIDE_CATEGORIES =
    "Lapp/ftl/extension/snaptube/SnaptubeSettingsHider;->hideCategories(Landroidx/preference/PreferenceGroup;)V"

private const val EXTENSION_HIDE_PREFERENCES =
    "Lapp/ftl/extension/snaptube/SnaptubeSettingsHider;->hidePreferences(Landroidx/preference/PreferenceFragmentCompat;)V"

/** Matches the no-argument PreferenceScreen getter without hard-coding its obfuscated name.
 *  For the supplied builds this resolves to E2 on the old build and F2 on the new build.
 */
internal object PreferenceScreenGetterFingerprint : Fingerprint(
    definingClass = "Landroidx/preference/PreferenceFragmentCompat;",
    returnType = "Landroidx/preference/PreferenceScreen;",
    parameters = emptyList(),
)

internal object SettingsOnCreatePreferencesFingerprint : Fingerprint(
    definingClass = "Lcom/snaptube/premium/settings/SettingsPreferenceFragment;",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;", "Ljava/lang/String;"),
    filters = listOf(
        methodCall(
            definingClass = "Landroidx/preference/PreferenceFragmentCompat;",
            parameters = listOf("I"),
            returnType = "V",
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
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
            val screenGetterName = PreferenceScreenGetterFingerprint.method.name

            it.method.addInstructions(
                anchor + 1,
                """
                invoke-virtual {p0}, Landroidx/preference/PreferenceFragmentCompat;->$screenGetterName()Landroidx/preference/PreferenceScreen;

                move-result-object v$register

                invoke-static {v$register}, $EXTENSION_HIDE_CATEGORIES
                """.trimIndent(),
            )
        }

        SettingsOnViewCreatedFingerprint.let {
            val superCallIndex = it.instructionMatches[0].index
            val e3CallIndex = it.instructionMatches[1].index

            // Run after super and again after the normal setup so both early and late
            // preference inflation paths are covered without touching p0/register types.
            it.method.addInstructions(superCallIndex + 1, "invoke-static {p0}, $EXTENSION_HIDE_PREFERENCES")
            it.method.addInstructions(e3CallIndex + 1, "invoke-static {p0}, $EXTENSION_HIDE_PREFERENCES")
        }
    }
}
