package app.ftl.patches.snaptube

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// 1. Fingerprint for the Toolbar Notification Default method in o/vj7
internal object ToolbarNotificationFingerprint : Fingerprint(
    definingClass = "Lo/vj7;",
    filters = listOf(
        methodCall(smali = "Lcom/wandoujia/base/config/GlobalConfig;->isToolbarNotificationDefaultShow()Z"),
        string("Channel_Id_Tools_Bar")
    )
)

// 2. Fingerprint for the Channel Defaults method s(Ljava/lang/String;)Z in o/vj7
internal object ChannelDefaultsFingerprint : Fingerprint(
    definingClass = "Lo/vj7;",
    name = "s",
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        methodCall(smali = "Lcom/wandoujia/base/config/GlobalConfig;->getAppContext()Landroid/content/Context;")
    )
)

// 3. Fingerprint for SettingsPreferenceFragment A2 method (categories)
internal object SettingsCategoriesFingerprint : Fingerprint(
    definingClass = "Lcom/snaptube/premium/settings/SettingsPreferenceFragment;",
    filters = listOf(
        methodCall(smali = "Landroidx/preference/PreferenceFragmentCompat;->A2(I)V")
    )
)

// 4. Fingerprint for SettingsPreferenceFragment onViewCreated method (items)
internal object SettingsItemsFingerprint : Fingerprint(
    definingClass = "Lcom/snaptube/premium/settings/SettingsPreferenceFragment;",
    filters = listOf(
        methodCall(smali = "Landroidx/preference/PreferenceFragmentCompat;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V")
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
        // 1. Patch Toolbar Notification Default in o/vj7
        ToolbarNotificationFingerprint.methodOrNull?.let { method ->
            var instructions = method.instructionsOrNull ?: return@let
            
            val invokeIndex = instructions.indexOfFirst { 
                it.toString().contains("isToolbarNotificationDefaultShow()Z") 
            }
            
            if (invokeIndex != -1) {
                val moveResult = instructions.getOrNull(invokeIndex + 1)
                val reg = (moveResult as? OneRegisterInstruction)?.registerA ?: 0
                
                method.replaceInstruction(invokeIndex, "const/4 v$reg, 0x0")
                
                if (moveResult != null && moveResult.opcode.name.startsWith("MOVE_RESULT")) {
                    method.removeInstruction(invokeIndex + 1)
                    instructions = method.instructionsOrNull ?: return@let
                }
            }
            
            for (i in 0 until instructions.size) {
                val inst = instructions[i]
                if (inst.opcode == Opcode.CONST_4 && inst.toString().contains("0x1")) {
                    val nextInst = instructions.getOrNull(i + 1)
                    if (nextInst?.opcode == Opcode.GOTO) {
                        val reg = (inst as? OneRegisterInstruction)?.registerA ?: 0
                        method.replaceInstruction(i, "const/4 v$reg, 0x0")
                        break
                    }
                }
            }
        }

        // 2. Patch Channel Defaults in method s of o/vj7
        ChannelDefaultsFingerprint.methodOrNull?.let { method ->
            val instructions = method.instructionsOrNull ?: return@let
            val alreadyPatched = instructions.any { it.toString().contains("Channel_Id_Push") }
            
            if (!alreadyPatched) {
                method.addInstructions(
                    0,
                    """
                        const-string v0, "Channel_Id_Push"
                        invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :label_5
                        const/4 v1, 0x0
                        goto :label_20
                        :label_5
                        const-string v0, "Channel_Id_Cleaner"
                        invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :label_0
                        const/4 v1, 0x0
                        goto :label_20
                        :label_0
                        const-string v0, "Channel_Id_Tools_Bar"
                        invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :label_6
                        const/4 v1, 0x0
                        goto :label_20
                        :label_6
                        const/4 v1, 0x1
                        :label_20
                    """.trimIndent()
                )
            }
        }

        // 3. Hide Categories in SettingsPreferenceFragment
        SettingsCategoriesFingerprint.methodOrNull?.let { method ->
            val instructions = method.instructionsOrNull ?: return@let
            val a2Index = instructions.indexOfFirst { 
                it.toString().contains("Landroidx/preference/PreferenceFragmentCompat;->A2(I)V") 
            }
            
            if (a2Index != -1) {
                val nextInst = instructions.getOrNull(a2Index + 1)
                if (nextInst?.opcode == Opcode.RETURN_VOID) {
                    method.addInstructions(
                        a2Index + 1,
                        """
                            invoke-virtual {p0}, Landroidx/preference/PreferenceFragmentCompat;->E2()Landroidx/preference/PreferenceScreen;
                            move-result-object v0
                            if-eqz v0, :cat_end
                            const/4 v1, 0x0
                            :cat_loop
                            invoke-virtual {v0}, Landroidx/preference/PreferenceGroup;->J0()I
                            move-result v2
                            if-ge v1, v2, :cat_end
                            invoke-virtual {v0, v1}, Landroidx/preference/PreferenceGroup;->I0(I)Landroidx/preference/Preference;
                            move-result-object v3
                            if-eqz v3, :cat_continue
                            invoke-virtual {v3}, Landroidx/preference/Preference;->C()Ljava/lang/CharSequence;
                            move-result-object v4
                            if-eqz v4, :cat_continue
                            invoke-virtual {v4}, Ljava/lang/Object;->toString()Ljava/lang/String;
                            move-result-object v4
                            const-string p0, "Download tools"
                            invoke-virtual {v4, p0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
                            move-result p0
                            if-nez p0, :cat_remove
                            const-string p0, "Phone clean"
                            invoke-virtual {v4, p0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
                            move-result p0
                            if-eqz p0, :cat_continue
                            :cat_remove
                            invoke-virtual {v0, v3}, Landroidx/preference/PreferenceGroup;->M0(Landroidx/preference/Preference;)Z
                            goto :cat_loop
                            :cat_continue
                            add-int/lit8 v1, v1, 0x1
                            goto :cat_loop
                            :cat_end
                        """.trimIndent()
                    )
                }
            }
        }

        // 4. Hide Items in SettingsPreferenceFragment onViewCreated
        SettingsItemsFingerprint.methodOrNull?.let { method ->
            val instructions = method.instructionsOrNull ?: return@let
            val onViewCreatedIndex = instructions.indexOfFirst { 
                it.toString().contains("Landroidx/preference/PreferenceFragmentCompat;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V") 
            }
            
            if (onViewCreatedIndex != -1) {
                method.addInstructions(
                    onViewCreatedIndex + 1,
                    """
                        const-string v0, "recover_deleted_files_settings"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :hide_1
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :hide_1
                        const-string v0, "whatsapp_status_saver"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :hide_2
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :hide_2
                        const-string v0, "vault_settings"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :hide_3
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :hide_3
                        const-string v0, "clean_junk"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :hide_4
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :hide_4
                        const-string v0, "clean_boost"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :hide_5
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :hide_5
                        const-string v0, "clean_battery_saver"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :hide_6
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :hide_6
                        const-string v0, "clean_large_files"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :hide_7
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :hide_7
                        const-string v0, "clean_trash"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :hide_8
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :hide_8
                        const-string v0, "clean_whatsapp"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :hide_9
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :hide_9
                        const-string v0, "photo_clean"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :hide_10
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :hide_10
                        const-string v0, "clean_app_uninstaller"
                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                        move-result-object v1
                        if-eqz v1, :hide_11
                        const/4 v2, 0x0
                        invoke-virtual {v1, v2}, Landroidx/preference/Preference;->x0(Z)V
                        :hide_11
                    """.trimIndent()
                )
            }
        }
    }
}
