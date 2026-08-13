package app.ftl.patches.dpi

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.ftl.util.getFreeRegisterProvider
import app.ftl.util.registersUsed
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

    val dpiOption by stringOption(
        key = "dpi",
        default = "100",
        title = "Display scale",
        description = "Scales this app's display relative to the device's own setting. " +
            "100% = no change, 150% = 1.5x larger, 50% = half size. Range 25-300%.",
        required = true,
        validator = { it?.toIntOrNull()?.let { v -> v in 25..300 } ?: false },
    )

    execute {
        val dpi = dpiOption?.toIntOrNull() ?: 100

        val applicationClass = AppEntryPoint.applicationClassName
            ?.toClassType()
            ?.let { mutableClassDefByOrNull(it) }

        if (applicationClass != null && injectApplicationInit(applicationClass, dpi)) {
            return@execute
        }

        // No usable Application.onCreate() found. Fall back to injecting into one
        // activity per distinct process declared in the manifest (falling back further
        // to just the launcher activity if manifest parsing found nothing), so apps that
        // isolate any component (e.g. a splash/ad screen) into its own process still get
        // DensityPatch initialized in every process it runs in, not just whichever one
        // happens to host the launcher activity.
        val entryActivities = AppEntryPoint.processEntryActivities.values.toMutableSet()
        AppEntryPoint.launcherActivityClassName?.let { entryActivities.add(it) }

        if (entryActivities.isEmpty()) return@execute

        entryActivities.forEach { className ->
            val activityClass = className.toClassType().let { mutableClassDefByOrNull(it) } ?: return@forEach
            injectActivityInit(activityClass, dpi)
        }
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
            onCreate.getFreeRegisterProvider(1, 1, onCreate.getInstruction(0).registersUsed).getFreeRegister()
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
            onCreate.getFreeRegisterProvider(1, 1, onCreate.getInstruction(0).registersUsed).getFreeRegister()
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
