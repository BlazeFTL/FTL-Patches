package app.ftl.patches.snaptube

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private fun Instruction.callsMethod(definingClass: String, methodName: String): Boolean {
    if (opcode != Opcode.INVOKE_VIRTUAL &&
        opcode != Opcode.INVOKE_SUPER &&
        opcode != Opcode.INVOKE_STATIC
    ) return false

    val reference = (this as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return reference.definingClass == definingClass && reference.name == methodName
}

@Suppress("unused")
val hideSettingsAndNotificationsPatch = bytecodePatch(
    name = "Hide Settings & Turn Off Default Notifications",
    description = "Hides specific settings (Download tools, Phone clean items) and disables default push notifications for recommended contents, tool notifications, and toolbar.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_SNAPTUBE)

    execute {
        classDefForEach { classDef ->
            when (classDef.type) {

                // ======================================================
                // SettingsPreferenceFragment: hide settings items/groups
                // ======================================================
                "Lcom/snaptube/premium/settings/SettingsPreferenceFragment;" -> {
                    mutableClassDefBy(classDef).methods.forEach { method ->
                        var instructions = method.instructionsOrNull ?: return@forEach

                        // ------------------------------------------------------------
                        // 1. Remove whole "Download tools" / "Phone clean" categories
                        //
                        // This is injected after:
                        // invoke-virtual {p0, p1}, Landroidx/preference/PreferenceFragmentCompat;->A2(I)V
                        // and only if the next instruction is return-void, matching your diff.
                        // ------------------------------------------------------------
                        val a2Index = instructions.indexOfFirst {
                            it.callsMethod(
                                "Landroidx/preference/PreferenceFragmentCompat;",
                                "A2"
                            )
                        }

                        if (a2Index != -1) {
                            val nextInstruction = instructions.getOrNull(a2Index + 1)
                            if (nextInstruction?.opcode == Opcode.RETURN_VOID) {
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

                                // Refresh after modification
                                instructions = method.instructionsOrNull ?: return@forEach
                            }
                        }

                        // ------------------------------------------------------------
                        // 2. Hide individual preference items in onViewCreated
                        //
                        // Injected after:
                        // invoke-super {p0, p1, p2}, Landroidx/preference/PreferenceFragmentCompat;->onViewCreated(...)
                        // ------------------------------------------------------------
                        val onViewCreatedIndex = instructions.indexOfFirst {
                            it.callsMethod(
                                "Landroidx/preference/PreferenceFragmentCompat;",
                                "onViewCreated"
                            )
                        }

                        if (onViewCreatedIndex != -1) {
                            method.addInstructions(
                                onViewCreatedIndex + 1,
                                """
                                    const-string v0, "recover_deleted_files_settings"
                                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                    move-result-object v0
                                    if-eqz v0, :hide_1
                                    const/4 v1, 0x0
                                    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                    :hide_1

                                    const-string v0, "whatsapp_status_saver"
                                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                    move-result-object v0
                                    if-eqz v0, :hide_2
                                    const/4 v1, 0x0
                                    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                    :hide_2

                                    const-string v0, "vault_settings"
                                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                    move-result-object v0
                                    if-eqz v0, :hide_3
                                    const/4 v1, 0x0
                                    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                    :hide_3

                                    const-string v0, "clean_junk"
                                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                    move-result-object v0
                                    if-eqz v0, :hide_4
                                    const/4 v1, 0x0
                                    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                    :hide_4

                                    const-string v0, "clean_boost"
                                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                    move-result-object v0
                                    if-eqz v0, :hide_5
                                    const/4 v1, 0x0
                                    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                    :hide_5

                                    const-string v0, "clean_battery_saver"
                                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                    move-result-object v0
                                    if-eqz v0, :hide_6
                                    const/4 v1, 0x0
                                    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                    :hide_6

                                    const-string v0, "clean_large_files"
                                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                    move-result-object v0
                                    if-eqz v0, :hide_7
                                    const/4 v1, 0x0
                                    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                    :hide_7

                                    const-string v0, "clean_trash"
                                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                    move-result-object v0
                                    if-eqz v0, :hide_8
                                    const/4 v1, 0x0
                                    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                    :hide_8

                                    const-string v0, "clean_whatsapp"
                                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                    move-result-object v0
                                    if-eqz v0, :hide_9
                                    const/4 v1, 0x0
                                    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                    :hide_9

                                    const-string v0, "photo_clean"
                                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                    move-result-object v0
                                    if-eqz v0, :hide_10
                                    const/4 v1, 0x0
                                    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                    :hide_10

                                    const-string v0, "clean_app_uninstaller"
                                    invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                    move-result-object v0
                                    if-eqz v0, :hide_11
                                    const/4 v1, 0x0
                                    invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                    :hide_11
                                """.trimIndent()
                            )

                            // ------------------------------------------------------------
                            // 3. Your diff also hides clean_trash again before return-void
                            // ------------------------------------------------------------
                            val returnIndex = method.instructionsOrNull?.indexOfLast {
                                it.opcode == Opcode.RETURN_VOID
                            } ?: -1

                            if (returnIndex != -1) {
                                method.addInstructions(
                                    returnIndex,
                                    """
                                        const-string v0, "clean_trash"
                                        invoke-virtual {p0, v0}, Landroidx/preference/PreferenceFragmentCompat;->w1(Ljava/lang/CharSequence;)Landroidx/preference/Preference;
                                        move-result-object v0
                                        if-eqz v0, :hide_trash_final
                                        const/4 v1, 0x0
                                        invoke-virtual {v0, v1}, Landroidx/preference/Preference;->x0(Z)V
                                        :hide_trash_final
                                    """.trimIndent()
                                )
                            }

                            // Refresh after modification
                            instructions = method.instructionsOrNull ?: return@forEach
                        }
                    }
                }

                // ======================================================
                // o/vj7: notification defaults
                // ======================================================
                "Lo/vj7;" -> {
                    mutableClassDefBy(classDef).methods.forEach { method ->
                        var instructions = method.instructionsOrNull ?: return@forEach

                        // ------------------------------------------------------------
                        // 4. Force Toolbar notification default to false
                        //
                        // Replaces:
                        // invoke-static {}, Lcom/wandoujia/base/config/GlobalConfig;->isToolbarNotificationDefaultShow()Z
                        // move-result v0
                        //
                        // With:
                        // const/4 v0, 0x0
                        // ------------------------------------------------------------
                        val toolbarDefaultIndex = instructions.indexOfFirst {
                            it.callsMethod(
                                "Lcom/wandoujia/base/config/GlobalConfig;",
                                "isToolbarNotificationDefaultShow"
                            )
                        }

                        if (toolbarDefaultIndex != -1) {
                            val moveResult = instructions.getOrNull(toolbarDefaultIndex + 1)
                            val targetRegister = (moveResult as? OneRegisterInstruction)?.registerA ?: 0

                            method.replaceInstruction(
                                toolbarDefaultIndex,
                                "const/4 v$targetRegister, 0x0"
                            )

                            if (moveResult != null && moveResult.opcode.name.startsWith("MOVE_RESULT")) {
                                method.removeInstruction(toolbarDefaultIndex + 1)
                            }

                            // Refresh
                            instructions = method.instructionsOrNull ?: return@forEach

                            // ------------------------------------------------------------
                            // 5. Also replace the nearby:
                            // const/4 v0, 0x1
                            // goto ...
                            //
                            // with:
                            // const/4 v0, 0x0
                            // goto ...
                            // ------------------------------------------------------------
                            for (i in 0 until instructions.size) {
                                val instruction = instructions[i]

                                if (instruction.opcode == Opcode.CONST_4 &&
                                    instruction is OneRegisterInstruction &&
                                    instruction.registerA == targetRegister &&
                                    instruction.toString().contains("0x1")
                                ) {
                                    val nextInstruction = instructions.getOrNull(i + 1)
                                    if (nextInstruction?.opcode == Opcode.GOTO) {
                                        method.replaceInstruction(
                                            i,
                                            "const/4 v$targetRegister, 0x0"
                                        )
                                        break
                                    }
                                }
                            }

                            // Refresh
                            instructions = method.instructionsOrNull ?: return@forEach
                        }

                        // ------------------------------------------------------------
                        // 6. Channel defaults:
                        // Channel_Id_Push
                        // Channel_Id_Cleaner
                        // Channel_Id_Tools_Bar
                        //
                        // Injected at start of:
                        // .method public static s(Ljava/lang/String;)Z
                        // ------------------------------------------------------------
                        if (method.name == "s" && method.returnType == "Z") {
                            val alreadyPatched = instructions.any {
                                it.toString().contains("Channel_Id_Push")
                            }

                            val hasChannelCall = instructions.any {
                                it.callsMethod("Lo/vj7;", "r")
                            }

                            if (!alreadyPatched && hasChannelCall) {
                                method.addInstructions(
                                    0,
                                    """
                                        const-string v0, "Channel_Id_Push"
                                        invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                                        move-result v0
                                        if-eqz v0, :channel_check_cleaner

                                        const/4 v1, 0x0
                                        goto :channel_done

                                        :channel_check_cleaner
                                        const-string v0, "Channel_Id_Cleaner"
                                        invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                                        move-result v0
                                        if-eqz v0, :channel_check_toolbar

                                        const/4 v1, 0x0
                                        goto :channel_done

                                        :channel_check_toolbar
                                        const-string v0, "Channel_Id_Tools_Bar"
                                        invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                                        move-result v0
                                        if-eqz v0, :channel_default_on

                                        const/4 v1, 0x0
                                        goto :channel_done

                                        :channel_default_on
                                        const/4 v1, 0x1

                                        :channel_done
                                    """.trimIndent()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
