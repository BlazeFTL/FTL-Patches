package app.ftl.patches.xplayer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val MIN_LOCALS = 2

private fun MutableMethod.localCount() =
    implementation!!.registerCount - parameterTypes.size -
        if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1

@Suppress("unused")
val hideCastButtonPatch = bytecodePatch(
    name = "Hide Cast Button",
    description = "Hides the cast button in the toolbar menus. Toggle it in Mod Settings."
) {
    compatibleWith(
        Compatibility(
            name = "XPlayer - Video Player",
            packageName = "video.player.videoplayer",
            targets = listOf(AppTarget(version = "2.9.2"))
        )
    )

    dependsOn(modSettingsPatch)

    execute {
        CastMenuFragmentFingerprint.matchAll().forEach { match ->
            val method = match.method
            if (method.localCount() < MIN_LOCALS) {
                throw PatchException("Not enough registers in ${method.definingClass}->${method.name}")
            }

            val lastIndex = method.implementation!!.instructions.size - 1
            val last = method.implementation!!.instructions[lastIndex]
            if (last.opcode != Opcode.RETURN_VOID) {
                throw PatchException("Unexpected end of ${method.definingClass}->${method.name}")
            }

            val castId = (match.instructionMatches[0].instruction as NarrowLiteralInstruction).narrowLiteral

            method.addInstructionsWithLabels(
                lastIndex,
                """
                    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getContext()Landroid/content/Context;
                    move-result-object v0
                    invoke-static {v0}, Lapp/ftl/extension/xplayer/ModPrefs;->hideCast(Landroid/content/Context;)Z
                    move-result v0
                    if-eqz v0, :skip
                    const v0, 0x${Integer.toHexString(castId)}
                    invoke-interface {p1, v0}, Landroid/view/Menu;->findItem(I)Landroid/view/MenuItem;
                    move-result-object v0
                    if-eqz v0, :skip
                    const/4 v1, 0x0
                    invoke-interface {v0, v1}, Landroid/view/MenuItem;->setVisible(Z)Landroid/view/MenuItem;
                """,
                ExternalLabel("skip", last)
            )
        }

        val controlMatch = CastMenuControlActivityFingerprint.match()
        val controlMethod = controlMatch.method
        if (controlMethod.localCount() < MIN_LOCALS) {
            throw PatchException("Not enough registers in ControlActivity.onPrepareOptionsMenu")
        }

        val itemRegister = (controlMatch.instructionMatches[2].instruction as OneRegisterInstruction).registerA
        if (itemRegister != 0) {
            throw PatchException("Unexpected item register in ControlActivity.onPrepareOptionsMenu")
        }

        val insertIndex = controlMatch.instructionMatches[2].index + 1
        controlMethod.addInstructionsWithLabels(
            insertIndex,
            """
                invoke-static {p0}, Lapp/ftl/extension/xplayer/ModPrefs;->hideCast(Landroid/content/Context;)Z
                move-result v1
                if-eqz v1, :skip
                const/4 v1, 0x0
                invoke-interface {v0, v1}, Landroid/view/MenuItem;->setVisible(Z)Landroid/view/MenuItem;
            """,
            ExternalLabel("skip", controlMethod.implementation!!.instructions[insertIndex])
        )
    }
}
