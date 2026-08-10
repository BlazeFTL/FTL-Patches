package app.ftl.patches.snaptube

import app.ftl.util.ensureRegisters
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

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

private val HIDDEN_PREFERENCE_KEYS = listOf(
    "recover_deleted_files_settings",
    "whatsapp_status_saver",
    "vault_settings",
    "clean_junk",
    "clean_boost",
    "clean_battery_saver",
    "clean_large_files",
    "clean_trash",
    "clean_whatsapp",
    "photo_clean",
    "clean_app_uninstaller",
)

private fun hidePreferenceBlock(key: String, labelSuffix: String) = """
    const-string v0, "$key"

    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;

    move-result-object v1

    if-eqz v1, :cond_$labelSuffix

    const/4 v2, 0x0

    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V

    :cond_$labelSuffix
""".trimIndent()

@Suppress("unused")
val hideSnaptubeSettingsPatch = bytecodePatch(
    name = "Hide Settings Items",
    description = "Hides the Download tools and Phone clean categories, and their sub-items, from Settings.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_SNAPTUBE)

    execute {
        SettingsOnCreatePreferencesFingerprint.let {
            val anchor = it.instructionMatches[0].index
            it.method.ensureRegisters(10)
            it.method.addInstructions(
                anchor + 1,
                """
                invoke-virtual {p0}, Landroidx/preference/PreferenceFragmentCompat;->E2()Landroidx/preference/PreferenceScreen;

                move-result-object v0

                if-eqz v0, :cond_end

                const/4 v1, 0x0

                :loop_start
                invoke-virtual {v0}, Landroidx/preference/PreferenceGroup;->J0()I

                move-result v2

                if-ge v1, v2, :cond_end

                invoke-virtual {v0, v1}, Landroidx/preference/PreferenceGroup;->I0(I)Landroidx/preference/Preference;

                move-result-object v3

                if-eqz v3, :cond_next

                invoke-virtual {v3}, Landroidx/preference/Preference;->C()Ljava/lang/CharSequence;

                move-result-object v4

                if-eqz v4, :cond_next

                invoke-virtual {v4}, Ljava/lang/Object;->toString()Ljava/lang/String;

                move-result-object v5

                const-string v6, "Download tools"

                invoke-virtual {v5, v6}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

                move-result p0

                if-nez p0, :cond_remove

                const-string v6, "Phone clean"

                invoke-virtual {v5, v6}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

                move-result p0

                if-eqz p0, :cond_next

                :cond_remove
                invoke-virtual {v0, v3}, Landroidx/preference/PreferenceGroup;->M0(Landroidx/preference/Preference;)Z

                goto :loop_start

                :cond_next
                add-int/lit8 v1, v1, 0x1

                goto :loop_start

                :cond_end
                """.trimIndent(),
            )
        }

        SettingsOnViewCreatedFingerprint.let {
            val superCallIndex = it.instructionMatches[0].index
            val e3CallIndex = it.instructionMatches[1].index

            it.method.ensureRegisters(6)

            it.method.addInstructions(e3CallIndex + 1, hidePreferenceBlock("clean_trash", "clean_trash_2"))

            it.method.addInstructions(
                superCallIndex + 1,
                HIDDEN_PREFERENCE_KEYS.joinToString("\n\n") { key -> hidePreferenceBlock(key, key) },
            )
        }
    }
}
