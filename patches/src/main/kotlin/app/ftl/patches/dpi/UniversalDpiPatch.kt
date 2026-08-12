package app.ftl.patches.dpi

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.longOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.ftl.util.getFreeRegisterProvider
import app.ftl.util.traverseClassHierarchy

private const val EXTENSION_SET_PERCENT =
    "Lapp/ftl/extension/dpi/DensityPatch;->setPercent(I)V"
private const val EXTENSION_INIT =
    "Lapp/ftl/extension/dpi/DensityPatch;->init(Landroid/app/Application;)V"
private const val EXTENSION_INIT_ACTIVITY =
    "Lapp/ftl/extension/dpi/DensityPatch;->init(Landroid/app/Activity;)V"

private fun String.toClassType() = "L${replace('.', '/')};"

// Universal patch (no compatibleWith) -> compatible with any package. `default` must stay false,
// per PatchBuilder.resolveDefaultValue(), since a universal patch cannot default to enabled.
@Suppress("unused")
val universalDpiPatch = bytecodePatch(
    "Custom DPI (Universal)",
    "Change this app's display size without touching your phone's system settings. " +
        "Make it bigger if things look too small, or smaller to fit more on screen. " +
        "Works on any app.",
    false,
) {
    dependsOn(findAppEntryPointPatch)

    extendWith("extensions/dpi.mpe")

    val dpiOption = longOption(
        key = "dpi",
        default = 100L,
        title = "Display scale",
        description = "Scales this app's display relative to the device's own setting. " +
            "100% = no change, 150% = 1.5x larger, 50% = half size. Range 25-300%.",
        required = false,
        validator = { it == null || it in 25L..300L },
    )

    execute {
        val dpi = (dpiOption.value ?: 100L).toInt()

        val applicationClass = AppEntryPoint.applicationClassName
            ?.toClassType()
            ?.let { mutableClassDefByOrNull(it) }

        if (applicationClass != null && injectApplicationInit(applicationClass, dpi)) {
            return@execute
        }

        // No usable Application.onCreate() found (either no custom Application subclass
        // is declared, or it doesn't override onCreate() anywhere in its hierarchy that's
        // part of this APK). Fall back to the launcher activity, the earliest point that's
        // guaranteed to run and is reliably findable from the manifest.
        val launcherClass = AppEntryPoint.launcherActivityClassName
            ?.toClassType()
            ?.let { mutableClassDefByOrNull(it) }
            ?: return@execute

        injectActivityInit(launcherClass, dpi)
    }
}

/**
 * @return true if injection succeeded.
 */
private fun BytecodePatchContext.injectApplicationInit(applicationClass: MutableClass, dpi: Int): Boolean {
    var injected = false

    traverseClassHierarchy(applicationClass) {
        if (injected) return@traverseClassHierarchy

        val onCreate = methods.firstOrNull {
            it.name == "onCreate" && it.parameters.isEmpty() && it.returnType == "V"
        } ?: return@traverseClassHierarchy

        val register = try {
            onCreate.getFreeRegisterProvider(1, 1).getFreeRegister()
        } catch (e: IllegalArgumentException) {
            return@traverseClassHierarchy
        }

        onCreate.addInstructions(
            0,
            """
                const v$register, $dpi
                invoke-static/range { v$register .. v$register }, $EXTENSION_SET_PERCENT
                invoke-static/range { p0 .. p0 }, $EXTENSION_INIT
            """,
        )
        injected = true
    }

    return injected
}

/**
 * @return true if injection succeeded.
 */
private fun BytecodePatchContext.injectActivityInit(activityClass: MutableClass, dpi: Int): Boolean {
    var injected = false

    traverseClassHierarchy(activityClass) {
        if (injected) return@traverseClassHierarchy

        val onCreate = methods.firstOrNull {
            it.name == "onCreate" &&
                it.parameters == listOf("Landroid/os/Bundle;") &&
                it.returnType == "V"
        } ?: return@traverseClassHierarchy

        // Only 1 register needed: init(Activity) resolves the Application itself,
        // and also applies density to this activity directly (register()'s
        // ActivityLifecycleCallbacks only cover activities created afterward).
        val register = try {
            onCreate.getFreeRegisterProvider(1, 1).getFreeRegister()
        } catch (e: IllegalArgumentException) {
            return@traverseClassHierarchy
        }

        onCreate.addInstructions(
            0,
            """
                const v$register, $dpi
                invoke-static/range { v$register .. v$register }, $EXTENSION_SET_PERCENT
                invoke-static/range { p0 .. p0 }, $EXTENSION_INIT_ACTIVITY
            """,
        )
        injected = true
    }

    return injected
}
