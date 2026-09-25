package app.ftl.patches.rsfileexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import org.w3c.dom.Element

private const val PERMISSION_ACTIVITY_CLASS = "Lcom/edili/filemanager/base/perm/FeaturedPermissionActivity;"
private const val SPLASH_ACTIVITY = "com.edili.filemanager.module.activity.FirstActivity"
private const val MAIN_ACTIVITY = "com.edili.filemanager.MainActivity"

/**
 * As of 2.3.1.1 the dialog-builder is no longer its own small method - the dev
 * merged it into the same method that also gates the legacy (pre-API 30)
 * permission flow and the POST_NOTIFICATIONS flow (`v()`). The dialog's own
 * resource-field anchor is also gone: the theme is now an inlined resource-ID
 * literal instead of a named `R$style` sget, so it can no longer be pinned by
 * name either.
 *
 * Matched instead by a chain of only real, unobfuscated landmarks, in the
 * order they appear in `v()`:
 *  1. the real `Build.VERSION.SDK_INT` field read that gates this dialog to
 *     API 30+
 *  2. the literal `30` it's compared against
 *  3. the app's own SharedPreferences key that can suppress this dialog
 *     ("key_not_support_storage_perm") - a string the devs chose, far more
 *     durable than a resource ID or obfuscated symbol
 *  4. the branch on that pref's value - this IF_NEZ must stay: when the pref
 *     is true it skips straight to the legacy request-perm flow, and ripping
 *     it out (as an earlier version of this patch did) makes that skip
 *     unconditional, so the replacement code fires even when this device
 *     already granted the permission - an ActivityNotFoundException on ROMs
 *     without the All-Files-Access settings screen. Only its index is used,
 *     as the boundary right before the dialog body being replaced
 *  5. the `return-void` that ends the block - the block's own dialog-reuse
 *     branch (`:cond_8c`/`:goto_94`) merges back into linear order before it,
 *     so this is still the first return-void reachable after filter 4
 *
 * `accessFlags = PUBLIC, FINAL` + 0 params disambiguates `v()` from the
 * class's other public 0-arg void methods (`onDestroy`, `w()`), neither of
 * which is both public and final.
 */
private object StorageOnboardingDialogFingerprint : Fingerprint(
    definingClass = PERMISSION_ACTIVITY_CLASS,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(smali = "Landroid/os/Build\$VERSION;->SDK_INT:I", opcode = Opcode.SGET),
        literal(30L),
        string("key_not_support_storage_perm"),
        opcode(Opcode.IF_NEZ, MatchAfterWithin(6)),
        opcode(Opcode.RETURN_VOID, MatchAfterWithin(50)),
    ),
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
    description = "Skips Splash Screen From 2nd App Opening",
    default = false,
) {
    compatibleWith(COMPATIBILITY_RS_FILE_EXPLORER)
    dependsOn(moveLauncherToMainActivityPatch)

    execute {
        val fingerprint = StorageOnboardingDialogFingerprint
        val matches = fingerprint.instructionMatches
        val startIndex = matches[3].index + 1 // instruction after IF_NEZ - keep the guard itself intact
        val endIndex = matches[4].index // RETURN_VOID - last instruction of the dialog block
        val method = fingerprint.method

        // Replace "build/show the full-screen dialog and wire its button to the
        // app's onClick dispatcher" with a direct launch of the all-files-access
        // settings screen, WITHOUT touching the IF_NEZ guard in front of it - that
        // guard is what makes this code only run when the pref says the device
        // doesn't support the storage-permission flow. Deliberately does NOT reuse
        // the app's own click-handler (Ledili/wg-style lambda dispatcher, reached
        // via a synthetic switch-case index): both the dispatcher's class name and
        // its case index are R8-merge artifacts that reshuffle every build, and the
        // dispatcher itself is shared by dozens of unrelated features. Every
        // instruction below is a real, unobfuscated Android API instead.
        method.removeInstructions(startIndex, endIndex - startIndex + 1)
        method.addInstructions(
            startIndex,
            """
                new-instance v0, Landroid/content/Intent;
                const-string v1, "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"
                invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V
                const-string v1, "package"
                invoke-virtual {p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
                move-result-object v2
                const/4 v3, 0x0
                invoke-static {v1, v2, v3}, Landroid/net/Uri;->fromParts(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Landroid/net/Uri;
                move-result-object v1
                invoke-virtual {v0, v1}, Landroid/content/Intent;->setData(Landroid/net/Uri;)Landroid/content/Intent;
                invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
                return-void
            """.trimIndent(),
        )
    }
}
