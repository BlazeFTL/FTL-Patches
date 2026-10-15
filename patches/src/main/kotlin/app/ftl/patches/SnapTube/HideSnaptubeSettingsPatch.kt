package app.ftl.patches.snaptube

import app.ftl.util.getFreeRegisterProvider
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_HIDE_CATEGORIES =
    "Lapp/ftl/extension/snaptube/SnaptubeSettingsHider;->hideCategories(Landroidx/preference/PreferenceGroup;Ljava/lang/String;)V"

private const val EXTENSION_HIDE_PREFERENCES =
    "Lapp/ftl/extension/snaptube/SnaptubeSettingsHider;->hidePreferences(Landroidx/preference/PreferenceFragmentCompat;Ljava/lang/String;)V"

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
        opcode(Opcode.RETURN_VOID),
    ),
)

/** Reads the obfuscated name of Preference.setVisible(Z) from the app's own call site:
 *  findPreference("clean_trash") followed by Preference.<setVisible>(Z)V in onViewCreated.
 */
internal object SettingsSetVisibleCallFingerprint : Fingerprint(
    definingClass = "Lcom/snaptube/premium/settings/SettingsPreferenceFragment;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
    filters = listOf(
        string("clean_trash"),
        methodCall(
            definingClass = "Landroidx/preference/Preference;",
            parameters = listOf("Z"),
            returnType = "V",
            opcodes = listOf(Opcode.INVOKE_VIRTUAL),
        ),
    ),
)

@Suppress("unused")
val hideSnaptubeSettingsPatch = bytecodePatch(
    name = "Clean SnapTube Settings Page",
    description = "Hides the Download tools and Phone clean categories, and their sub-items, from Settings.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_SNAPTUBE)

    extendWith("extensions/snaptube.mpe")

    execute {
        val setVisibleName = runCatching {
            val call = SettingsSetVisibleCallFingerprint.instructionMatches[1]
            (call.getInstruction<ReferenceInstruction>().reference as MethodReference).name
        }.getOrDefault("")

        SettingsOnCreatePreferencesFingerprint.let {
            val anchor = it.instructionMatches[0].index
            val registers = it.method.getFreeRegisterProvider(anchor + 1, 2)
            val screenRegister = registers.getFreeRegister()
            val nameRegister = registers.getFreeRegister()
            val screenGetterName = PreferenceScreenGetterFingerprint.method.name

            it.method.addInstructions(
                anchor + 1,
                """
                invoke-virtual {p0}, Landroidx/preference/PreferenceFragmentCompat;->$screenGetterName()Landroidx/preference/PreferenceScreen;

                move-result-object v$screenRegister

                const-string v$nameRegister, "$setVisibleName"

                invoke-static {v$screenRegister, v$nameRegister}, $EXTENSION_HIDE_CATEGORIES
                """.trimIndent(),
            )
        }

        SettingsOnViewCreatedFingerprint.let {
            val method = it.method
            val superCallIndex = it.instructionMatches[0].index
            val returnIndex = it.instructionMatches[1].index
            val thisRegister = method.implementation!!.registerCount - method.parameterTypes.size - 1

            fun hook(index: Int) {
                val register = method.getFreeRegisterProvider(index, 1, thisRegister).getFreeRegister()
                method.addInstructions(
                    index,
                    """
                    const-string v$register, "$setVisibleName"

                    invoke-static {p0, v$register}, $EXTENSION_HIDE_PREFERENCES
                    """.trimIndent(),
                )
            }

            hook(returnIndex)
            hook(superCallIndex + 1)
        }
    }
}
