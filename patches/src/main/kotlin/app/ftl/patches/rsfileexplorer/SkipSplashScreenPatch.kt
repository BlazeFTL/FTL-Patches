package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private val COMPATIBILITY_RS_FILE_EXPLORER = Compatibility(
    packageName = "com.rs.explorer.filemanager",
    name = "RS File Manager",
    targets = listOf(
        AppTarget(version = "2.3.0.4", versionCode = 239),
    ),
)

private const val PERMISSION_ACTIVITY_CLASS = "Lcom/edili/filemanager/base/perm/FeaturedPermissionActivity;"

/**
 * Matches the private method that builds and shows the full-screen "grant storage
 * access" splash dialog. The method name itself is obfuscated and reshuffles every
 * build, so it's identified instead by the sget of the app's own unobfuscated
 * resource field for the dialog's theme, which only appears in this one method.
 */
private object FullScreenAskStorageDialogFingerprint : Fingerprint(
    definingClass = PERMISSION_ACTIVITY_CLASS,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(
            smali = "Lcom/edili/filemanager/common/R\$style;->RS_FullScreen_Dialog:I",
            opcode = Opcode.SGET,
        ),
    ),
)

/**
 * Matches the private click-handler that the dialog's "Grant" button calls, which
 * launches the all-files-access settings screen. Also obfuscated, so it's found by
 * the real Android settings action string it fires instead of its method name — the
 * exact reference is read back off the match rather than hardcoded.
 */
private object GrantAllFilesAccessFingerprint : Fingerprint(
    definingClass = PERMISSION_ACTIVITY_CLASS,
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    strings = listOf("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"),
)

val skipSplashScreenPatch = bytecodePatch(
    name = "Skip splash screen",
    description = "Calls the all-files-access permission request directly instead of first showing the full-screen 'grant storage access' splash dialog.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)

    execute {
        val grantMethod = GrantAllFilesAccessFingerprint.method
        val paramsSmali = grantMethod.parameterTypes.joinToString("")
        val grantMethodSmali = "${grantMethod.definingClass}->${grantMethod.name}(${paramsSmali})${grantMethod.returnType}"

        FullScreenAskStorageDialogFingerprint.method.let { method ->
            val instructionCount = method.implementation!!.instructions.size
            method.removeInstructions(0, instructionCount)
            method.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    invoke-direct {p0, v0}, $grantMethodSmali
                    return-void
                """.trimIndent(),
            )
        }
    }
}
