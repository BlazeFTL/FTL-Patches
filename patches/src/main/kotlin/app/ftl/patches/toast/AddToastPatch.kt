package app.ftl.patches.toast

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.ftl.patches.dpi.AppEntryPoint
import app.ftl.patches.dpi.findAppEntryPointPatch
import app.ftl.util.getFreeRegisterProvider
import app.ftl.util.traverseClassHierarchy

private const val EXTENSION_SET_MESSAGE =
    "Lapp/ftl/extension/toast/ToastPatch;->setMessage(Ljava/lang/String;)V"
private const val EXTENSION_SET_SHOW_ONCE =
    "Lapp/ftl/extension/toast/ToastPatch;->setShowOnce(Z)V"
private const val EXTENSION_SHOW =
    "Lapp/ftl/extension/toast/ToastPatch;->show(Landroid/content/Context;)V"

private fun String.toClassType() = "L${replace('.', '/')};"

private fun String.smaliEscape() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

// Universal patch (no compatibleWith), so `default` must be false.
@Suppress("unused")
val addToastPatch = bytecodePatch(
    "Add Toast",
    "Shows a custom toast message when the app starts. Works on any app.",
    false,
) {
    dependsOn(findAppEntryPointPatch)

    extendWith("extensions/toast.mpe")

    val message by stringOption(
        key = "message",
        default = "Mod By BlazeFTL",
        title = "Toast message",
        description = "Text shown in the toast.",
        required = false,
    )

    val showOnce by booleanOption(
        key = "showOnce",
        default = true,
        title = "Show once",
        description = "On: the toast only shows on the first launch. Off: it shows on every launch.",
    )

    execute {
        val text = (message ?: "Mod By BlazeFTL").smaliEscape()
        val once = showOnce ?: true

        val applicationClass = AppEntryPoint.applicationClassName
            ?.toClassType()
            ?.let { mutableClassDefByOrNull(it) }

        if (applicationClass != null && injectApplicationToast(applicationClass, text, once)) {
            return@execute
        }

        // No usable Application.onCreate() found, fall back to the launcher activity.
        val launcherClass = AppEntryPoint.launcherActivityClassName
            ?.toClassType()
            ?.let { mutableClassDefByOrNull(it) }
            ?: return@execute

        injectActivityToast(launcherClass, text, once)
    }
}

/**
 * @return true if injection succeeded.
 */
private fun BytecodePatchContext.injectApplicationToast(
    applicationClass: MutableClass,
    message: String,
    once: Boolean,
): Boolean {
    var injected = false

    traverseClassHierarchy(applicationClass) {
        if (injected) return@traverseClassHierarchy

        // Strictly no-arg: Application.onCreate() never takes a Bundle. A looser filter
        // here can latch onto an unrelated onCreate(Bundle) higher in the hierarchy
        // (e.g. from some SDK's lifecycle interface) before reaching the real one.
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
                const-string v$register, "$message"
                invoke-static/range { v$register .. v$register }, $EXTENSION_SET_MESSAGE
                const v$register, ${if (once) "0x1" else "0x0"}
                invoke-static/range { v$register .. v$register }, $EXTENSION_SET_SHOW_ONCE
                invoke-static/range { p0 .. p0 }, $EXTENSION_SHOW
            """,
        )
        injected = true
    }

    return injected
}

/**
 * @return true if injection succeeded.
 */
private fun BytecodePatchContext.injectActivityToast(
    activityClass: MutableClass,
    message: String,
    once: Boolean,
): Boolean {
    var injected = false

    traverseClassHierarchy(activityClass) {
        if (injected) return@traverseClassHierarchy

        val onCreate = methods.firstOrNull {
            it.name == "onCreate" &&
                it.parameters == listOf("Landroid/os/Bundle;") &&
                it.returnType == "V"
        } ?: return@traverseClassHierarchy

        val register = try {
            onCreate.getFreeRegisterProvider(1, 1).getFreeRegister()
        } catch (e: IllegalArgumentException) {
            return@traverseClassHierarchy
        }

        onCreate.addInstructions(
            0,
            """
                const-string v$register, "$message"
                invoke-static/range { v$register .. v$register }, $EXTENSION_SET_MESSAGE
                const v$register, ${if (once) "0x1" else "0x0"}
                invoke-static/range { v$register .. v$register }, $EXTENSION_SET_SHOW_ONCE
                invoke-static/range { p0 .. p0 }, $EXTENSION_SHOW
            """,
        )
        injected = true
    }

    return injected
}
