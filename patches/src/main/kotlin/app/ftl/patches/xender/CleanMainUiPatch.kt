package app.ftl.patches.xender

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_APPLY_UI =
    "Lapp/ftl/extension/xender/XenderUiCleaner;->applyUiCustomizations(Landroid/app/Activity;)V"

/**
 * Matches MainActivity.onCreate(Bundle). Anchored on the call to initNavigation(),
 * MainActivity's own real (unobfuscated) private method, so the drawer/nav views
 * it sets up already exist by the time the extension call below runs.
 */
private object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(smali = "Lcn/xender/ui/activity/MainActivity;->initNavigation()V"),
    ),
)

/**
 * Matches MainActivity.onResume(). Anchored on the super call to
 * FragmentActivity.onResume() — a real AndroidX API — since nothing else in
 * MainActivity's own onResume is distinctive enough to pin.
 */
private object MainActivityOnResumeFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "onResume",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(smali = "Landroidx/fragment/app/FragmentActivity;->onResume()V"),
    ),
)

/**
 * Matches MainActivity.drawerEnterClick(), MainActivity's own real (unobfuscated)
 * public method. Class + name + signature alone are unique, no filters needed.
 * The drawer's own items are inflated lazily, so this is reapplied here too —
 * this is what fixed the "only works after opening the sidebar" behavior.
 */
private object MainActivityDrawerEnterClickFingerprint : Fingerprint(
    definingClass = "Lcn/xender/ui/activity/MainActivity;",
    name = "drawerEnterClick",
    returnType = "V",
    parameters = emptyList(),
)

val cleanMainUiPatch = bytecodePatch(
    name = "Clean main UI",
    description = "Hides the bottom navigation bar, the top-right guide icon, and the Rate/Help/About drawer items, and brings the connect/create/join buttons to front. Reapplied on create, resume, and drawer open (and retried for ~1.8s after each) since some of these views are inflated lazily.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    extendWith("extensions/xender.mpe")

    execute {
        MainActivityOnCreateFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches[0].index
            fingerprint.method.addInstructions(index + 1, "invoke-static {p0}, $EXTENSION_APPLY_UI")
        }

        MainActivityOnResumeFingerprint.let { fingerprint ->
            val index = fingerprint.instructionMatches[0].index
            fingerprint.method.addInstructions(index + 1, "invoke-static {p0}, $EXTENSION_APPLY_UI")
        }

        MainActivityDrawerEnterClickFingerprint.let { fingerprint ->
            fingerprint.method.addInstructions(0, "invoke-static {p0}, $EXTENSION_APPLY_UI")
        }
    }
}
