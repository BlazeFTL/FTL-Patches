package app.ftl.patches.dpi

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.longOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.ftl.util.getFreeRegisterProvider
import app.ftl.util.traverseClassHierarchy

private const val EXTENSION_INIT =
    "Lapp/ftl/extension/dpi/DensityPatch;->init(Landroid/app/Application;I)V"

private fun String.toClassType() = "L${replace('.', '/')};"

// Universal patch (no compatibleWith) -> compatible with any package. `default` must stay false,
// per PatchBuilder.resolveDefaultValue(), since a universal patch cannot default to enabled.
@Suppress("unused")
val universalDpiPatch = bytecodePatch(
    "Custom DPI (Universal)",
    "Forces a higher display density for this app only, so the whole UI scales up " +
        "without changing the system density. Works on any app that has an onCreate() " +
        "in its Application class or launcher activity.",
    false,
) {
    dependsOn(findAppEntryPointPatch)

    extendWith("extensions/dpi.mpe")

    val dpiOption = longOption(
        key = "dpi",
        default = 240L,
        title = "Custom DPI",
        description = "Forced display density in dots-per-inch for this app only. " +
            "160 = system default (mdpi), 240 is roughly 1.5x larger. Range 96-640.",
        required = false,
        validator = { it == null || it in 96L..640L },
    )

    execute {
        val dpi = (dpiOption.value ?: 240L).toInt()

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

        val register = onCreate.getFreeRegisterProvider(0, 1).getFreeRegister()
        onCreate.addInstructions(
            0,
            """
                const v$register, $dpi
                invoke-static { p0, v$register }, $EXTENSION_INIT
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

        val provider = onCreate.getFreeRegisterProvider(0, 2)
        val appRegister = provider.getFreeRegister()
        val dpiRegister = provider.getFreeRegister()

        onCreate.addInstructions(
            0,
            """
                invoke-virtual { p0 }, Landroid/app/Activity;->getApplication()Landroid/app/Application;
                move-result-object v$appRegister
                const v$dpiRegister, $dpi
                invoke-static { v$appRegister, v$dpiRegister }, $EXTENSION_INIT
            """,
        )
        injected = true
    }

    return injected
}
