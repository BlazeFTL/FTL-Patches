package app.ftl.patches.snaptube

import app.ftl.patches.snaptube.compat.COMPATIBILITY_SNAPTUBE
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.fingerprint.MethodFingerprint.Companion.methodCall
import app.revanced.patcher.fingerprint.MethodFingerprint.Companion.string
import app.revanced.patcher.patch.bytecodePatch

// 1. Hides "Download tools" and "Phone clean" categories
internal object SettingsSetPreferencesFingerprint : MethodFingerprint(
    definingClass = "Lcom/snaptube/premium/settings/SettingsPreferenceFragment;",
    filters = listOf(methodCall(smali = "Landroidx/preference/PreferenceFragmentCompat;->A2(I)V"))
)

// 2. Hides specific items by key in onViewCreated
internal object SettingsOnViewCreatedFingerprint : MethodFingerprint(
    definingClass = "Lcom/snaptube/premium/settings/SettingsPreferenceFragment;",
    filters = listOf(methodCall(smali = "Landroidx/preference/PreferenceFragmentCompat;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V"))
)

// 3. Disables default push notification channels
internal object NotificationChannelSFingerprint : MethodFingerprint(
    definingClass = "Lo/vj7;",
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(methodCall(smali = "Lo/vj7;->r(Landroid/content/Context;Ljava/lang/String;Z)Z"))
)

// 4. Disables Toolbar Notification defaults
internal object ToolbarNotificationDefaultShowFingerprint : MethodFingerprint(
    definingClass = "Lo/vj7;",
    filters = listOf(
        methodCall(smali = "Lcom/wandoujia/base/config/GlobalConfig;->isToolbarNotificationDefaultShow()Z"),
        string("Channel_Id_Tools_Bar")
    )
)

@Suppress("unused")
val hideSettingsAndNotificationsPatch = bytecodePatch(
    name = "Hide Settings & Turn Off Default Notifications",
    description = "Hides specific settings (Download tools, Phone clean items) and disables default push notifications for recommended contents, tool notifications, and toolbar.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_SNAPTUBE)

    execute {
        // 1. Hide Categories (Download tools, Phone clean)
        SettingsSetPreferencesFingerprint.result?.let { result ->
            val method = result.mutableMethod
            val a2Index = method.indexOfFirst { it.toString().contains("Landroidx/preference/PreferenceFragmentCompat;->A2(I)V") }
            if (a2Index != -1) {
                method.addInstructions(a2Index + 1, """
                    invoke-virtual {p0}, Landroidx/preference/PreferenceFragmentCompat;->E2()Landroidx/preference/PreferenceScreen;
                    move-result-object v0
                    if-eqz v0, :label_16
                    const/4 v1, 0x0
                    :label_5
                    invoke-virtual {v0}, Landroidx/preference/PreferenceGroup;->J0()I
                    move-result v2
                    if-ge v1, v2, :label_16
                    invoke-virtual {v0, v1}, Landroidx/preference/PreferenceGroup;->I0(I)Landroidx/preference/Preference;
                    move-result-object v3
                    if-eqz v3, :label_18
                    invoke-virtual {v3}, Landroidx/preference/Preference;->C()Ljava/lang/CharSequence;
                    move-result-object v4
                    if-eqz v4, :label_18
                    invoke-virtual {v4}, Ljava/lang/Object;->toString()Ljava/lang/String;
                    move-result-object v5
                    const-string v6, "Download tools"
                    invoke-virtual {v5, v6}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
                    move-result p0
                    if-nez p0, :label_17
                    const-string v6, "Phone clean"
                    invoke-virtual {v5, v6}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
                    move-result p0
                    if-eqz p0, :label_18
                    :label_17
                    invoke-virtual {v0, v3}, Landroidx/preference/PreferenceGroup;->M0(Landroidx/preference/Preference;)Z
                    goto :label_5
                    :label_18
                    add-int/lit8 v1, v1, 0x1
                    goto :label_5
                    :label_16
                """.trimIndent())
            }
        }

        // 2. Hide Specific Items (Junk clean, Vault, Recover deleted files, etc.)
        SettingsOnViewCreatedFingerprint.result?.let { result ->
            val method = result.mutableMethod
            val superIndex = method.indexOfFirst { it.toString().contains("Landroidx/preference/PreferenceFragmentCompat;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V") }
            if (superIndex != -1) {
                method.addInstructions(superIndex + 1, """
                    const-string v0, "recover_deleted_files_settings"
                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                    move-result-object v1
                    if-eqz v1, :label_37
                    const/4 v2, 0x0
                    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                    :label_37
                    const-string v0, "whatsapp_status_saver"
                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                    move-result-object v1
                    if-eqz v1, :label_8
                    const/4 v2, 0x0
                    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                    :label_8
                    const-string v0, "vault_settings"
                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                    move-result-object v1
                    if-eqz v1, :label_54
                    const/4 v2, 0x0
                    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                    :label_54
                    const-string v0, "clean_junk"
                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                    move-result-object v1
                    if-eqz v1, :label_55
                    const/4 v2, 0x0
                    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                    :label_55
                    const-string v0, "clean_boost"
                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                    move-result-object v1
                    if-eqz v1, :label_56
                    const/4 v2, 0x0
                    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                    :label_56
                    const-string v0, "clean_battery_saver"
                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                    move-result-object v1
                    if-eqz v1, :label_46
                    const/4 v2, 0x0
                    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                    :label_46
                    const-string v0, "clean_large_files"
                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                    move-result-object v1
                    if-eqz v1, :label_57
                    const/4 v2, 0x0
                    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                    :label_57
                    const-string v0, "clean_whatsapp"
                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                    move-result-object v1
                    if-eqz v1, :label_59
                    const/4 v2, 0x0
                    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                    :label_59
                    const-string v0, "photo_clean"
                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                    move-result-object v1
                    if-eqz v1, :label_60
                    const/4 v2, 0x0
                    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                    :label_60
                    const-string v0, "clean_app_uninstaller"
                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                    move-result-object v1
                    if-eqz v1, :label_61
                    const/4 v2, 0x0
                    invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                    :label_61
                """.trimIndent())
                
                // Hide "clean_trash" right before return-void as seen in your diff
                val returnIndex = method.indexOfFirst { it.opcode.name == "RETURN_VOID" }
                if (returnIndex != -1) {
                    method.addInstructions(returnIndex, """
                        const-string v0, "clean_trash"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :label_63
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :label_63
                    """.trimIndent())
                }
            }
        }

        // 3. Turn Off Default Push Notifications (Channels)
        NotificationChannelSFingerprint.result?.let { result ->
            val method = result.mutableMethod
            val rIndex = method.indexOfFirst { it.toString().contains("Lo/vj7;->r(Landroid/content/Context;Ljava/lang/String;Z)Z") }
            if (rIndex != -1) {
                val constV1Index = rIndex - 1
                method.addInstructions(constV1Index + 1, """
                    const-string v1, "Channel_Id_Push"
                    invoke-virtual {v1, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :match_1
                    const-string v1, "Channel_Id_Cleaner"
                    invoke-virtual {v1, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :match_1
                    const-string v1, "Channel_Id_Tools_Bar"
                    invoke-virtual {v1, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :match_1
                    const/4 v1, 0x1
                    goto :end_check
                    :match_1
                    const/4 v1, 0x0
                    :end_check
                """.trimIndent())
                method.removeInstruction(constV1Index)
            }
        }

        // 4. Toolbar Notification Default Show
        ToolbarNotificationDefaultShowFingerprint.result?.let { result ->
            val method = result.mutableMethod
            var i = 0
            while (i < method.instructions.size) {
                val inst = method.instructions[i]
                if (inst.toString().contains("isToolbarNotificationDefaultShow()Z")) {
                    method.addInstructions(i, "const/4 v0, 0x0")
                    method.removeInstructions(i + 1, 2)
                    break
                }
                i++
            }
            for (j in 0 until method.instructions.size) {
                val inst = method.instructions[j]
                if (inst.toString().contains("const/4 v0, 0x1")) {
                    val nextInst = method.instructions.getOrNull(j + 1)
                    if (nextInst != null && nextInst.opcode.name == "GOTO") {
                        method.replaceInstruction(j, "const/4 v0, 0x0")
                        break
                    }
                }
            }
        }
    }
}
