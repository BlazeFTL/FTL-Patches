package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import org.w3c.dom.Element

private const val PERMISSION_ACTIVITY_CLASS = "Lcom/edili/filemanager/base/perm/FeaturedPermissionActivity;"
private const val SPLASH_ACTIVITY = "com.edili.filemanager.module.activity.FirstActivity"
private const val MAIN_ACTIVITY = "com.edili.filemanager.MainActivity"

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

/**
 * Moves the MAIN/LAUNCHER intent-filter from the splash activity to the main
 * activity, so the splash activity is never shown on cold start. No `name`, so it
 * isn't independently toggleable — it only runs as a dependency of skipSplashScreenPatch.
 */
internal val moveLauncherToMainActivityPatch = resourcePatch(
    description = "Moves the launcher intent filter from the splash activity to the main activity.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)

    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            var splash: Element? = null
            var main: Element? = null

            for (i in 0 until activities.length) {
                val activity = activities.item(i) as? Element ?: continue
                when (activity.getAttribute("android:name")) {
                    SPLASH_ACTIVITY -> splash = activity
                    MAIN_ACTIVITY -> main = activity
                }
            }

            val splashActivity = splash ?: return@use
            val mainActivity = main ?: return@use

            val intentFilters = splashActivity.getElementsByTagName("intent-filter")
            var launcherFilter: Element? = null

            for (i in 0 until intentFilters.length) {
                val filter = intentFilters.item(i) as? Element ?: continue
                val actions = filter.getElementsByTagName("action")
                val hasMainAction = (0 until actions.length).any { idx ->
                    (actions.item(idx) as? Element)?.getAttribute("android:name") == "android.intent.action.MAIN"
                }
                if (hasMainAction) {
                    launcherFilter = filter
                    break
                }
            }

            // Only the MAIN/LAUNCHER intent-filter moves; the splash activity keeps
            // its other intent-filter (com.rs.action.permission.require) untouched,
            // matching the reference diff.
            val filterToMove = launcherFilter ?: return@use
            splashActivity.removeChild(filterToMove)
            mainActivity.insertBefore(filterToMove, mainActivity.firstChild)
        }
    }
}

val skipSplashScreenPatch = bytecodePatch(
    name = "Skip splash screen",
    description = "Moves the launcher intent filter to the main activity and calls the all-files-access permission request directly, instead of showing the splash activity and its full-screen 'grant storage access' dialog.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)
    dependsOn(moveLauncherToMainActivityPatch)

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
