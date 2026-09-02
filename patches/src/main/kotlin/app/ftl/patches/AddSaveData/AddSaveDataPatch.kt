package app.ftl.patches.addsavedata

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.ftl.patches.customdpi.AppEntryPoint
import app.ftl.patches.customdpi.findAppEntryPointPatch
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.ftl.util.getFreeRegisterProvider
import app.ftl.util.registersUsed
import app.ftl.util.traverseClassHierarchy
import java.io.File

private const val EXTENSION_RESTORE =
    "Lapp/ftl/extension/addsave/SaveDataPatch;->restore(Landroid/content/Context;)V"
private const val EXTENSION_SET_MESSAGE =
    "Lapp/ftl/extension/toast/ToastPatch;->setMessage(Ljava/lang/String;)V"
private const val EXTENSION_SET_SHOW_ONCE =
    "Lapp/ftl/extension/toast/ToastPatch;->setShowOnce(Z)V"
private const val EXTENSION_SHOW =
    "Lapp/ftl/extension/toast/ToastPatch;->show(Landroid/content/Context;)V"

private fun String.toClassType() = "L${replace('.', '/')};"

private fun String.smaliEscape() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

/**
 * Not a Swift Backup .dat/.extdat export as-is (those are encrypted 7z containers).
 * Each option here must be a plain zip whose contents ARE what should land in that location,
 * e.g. zip up the extracted /data/data/<pkg> folder and point internalDataZip at it.
 *
 * At least one of the three zips is required. Each is renamed internally to a fixed
 * asset name matching its restore destination, regardless of the original filename:
 *   res0 -> /data/data/<package>    (internalDataZip)
 *   res1 -> Android/data/<package>  (externalDataZip)
 *   res2 -> Android/obb/<package>   (obbZip)
 */
// name omitted (null): internal, pulled in by addSaveDataPatch via dependsOn.
val embedSaveDataPatch = rawResourcePatch(
    description = "Embeds the provided save-data zip(s) into assets/.",
) {
    val internalDataZip by stringOption(
        key = "internalDataZip",
        default = null,
        title = "1. Select data/data zip",
        description = "Zip of the app's internal storage. Restored to /data/data/<package> on first launch.",
        required = false,
    )

    val externalDataZip by stringOption(
        key = "externalDataZip",
        default = null,
        title = "2. Select Android/data zip",
        description = "Zip of the app's external storage folder. Restored to Android/data/<package> on first launch.",
        required = false,
    )

    val obbZip by stringOption(
        key = "obbZip",
        default = null,
        title = "3. Select Android/obb zip",
        description = "Zip of the app's OBB files. Restored to Android/obb/<package> on first launch.",
        required = false,
    )

    execute {
        val internal = internalDataZip?.takeIf { it.isNotBlank() }
        val external = externalDataZip?.takeIf { it.isNotBlank() }
        val obb = obbZip?.takeIf { it.isNotBlank() }

        if (internal == null && external == null && obb == null) {
            throw PatchException(
                "Add Save Data: select at least one zip \u2014 data/data, Android/data, or Android/obb.",
            )
        }

        val assetsDir = get("assets", false)
        assetsDir.mkdirs()

        fun embed(path: String?, assetName: String) {
            if (path == null) return

            val source = File(path)
            if (!source.isFile) {
                throw PatchException("Add Save Data: file not found: $path")
            }

            File(assetsDir, assetName).writeBytes(source.readBytes())
        }

        embed(internal, "res0")
        embed(external, "res1")
        embed(obb, "res2")
    }
}

// Universal patch (no compatibleWith), so `default` must be false.
@Suppress("unused")
val addSaveDataPatch = bytecodePatch(
    "Add Save Data",
    "Unpacks bundled save-data zip(s) into the app's storage on first launch.",
    false,
) {
    dependsOn(embedSaveDataPatch, findAppEntryPointPatch)

    extendWith("extensions/addsave.mpe")
    extendWith("extensions/toast.mpe")

    val addToast by booleanOption(
        key = "addToast",
        default = false,
        title = "Add toast",
        description = "Also show a toast message on first launch.",
    )

    val message by stringOption(
        key = "message",
        default = "Mod By BlazeFTL",
        title = "Toast message",
        description = "Only used if 'Add toast' is on.",
        required = false,
    )

    val showOnce by booleanOption(
        key = "showOnce",
        default = true,
        title = "Show toast once",
        description = "On: the toast only shows on the first launch. Off: it shows on every launch.",
    )

    execute {
        val toast = addToast ?: false
        val text = (message ?: "Mod By BlazeFTL").smaliEscape()
        val once = showOnce ?: true

        val applicationClass = AppEntryPoint.applicationClassName
            ?.toClassType()
            ?.let { mutableClassDefByOrNull(it) }

        if (applicationClass != null && injectApplicationRestore(applicationClass, toast, text, once)) {
            return@execute
        }

        // No usable Application.onCreate() found, fall back to the launcher activity.
        val launcherClass = AppEntryPoint.launcherActivityClassName
            ?.toClassType()
            ?.let { mutableClassDefByOrNull(it) }
            ?: return@execute

        injectActivityRestore(launcherClass, toast, text, once)
    }
}

/**
 * @return true if injection succeeded.
 */
private fun BytecodePatchContext.injectApplicationRestore(
    applicationClass: MutableClass,
    addToast: Boolean,
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

        injected = injectRestore(onCreate, addToast, message, once)
    }

    return injected
}

/**
 * @return true if injection succeeded.
 */
private fun BytecodePatchContext.injectActivityRestore(
    activityClass: MutableClass,
    addToast: Boolean,
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

        injected = injectRestore(onCreate, addToast, message, once)
    }

    return injected
}

/**
 * Restore is unconditional whenever an eligible onCreate is found. The toast is a bonus
 * on top of it: if a free register can't be found for it, restore still gets injected and
 * we just skip the toast rather than failing the whole injection.
 *
 * @return true if injection succeeded (restore was added).
 */
private fun injectRestore(
    onCreate: MutableMethod,
    addToast: Boolean,
    message: String,
    once: Boolean,
): Boolean {
    val instructions = buildString {
        appendLine("invoke-static/range { p0 .. p0 }, $EXTENSION_RESTORE")

        if (addToast) {
            val provider = try {
                onCreate.getFreeRegisterProvider(1, 1, onCreate.getInstruction(0).registersUsed)
            } catch (e: IllegalArgumentException) {
                null
            }

            if (provider != null) {
                val register = provider.getFreeRegister()

                appendLine("const-string v$register, \"$message\"")
                appendLine("invoke-static/range { v$register .. v$register }, $EXTENSION_SET_MESSAGE")
                appendLine("const v$register, ${if (once) "0x1" else "0x0"}")
                appendLine("invoke-static/range { v$register .. v$register }, $EXTENSION_SET_SHOW_ONCE")
                appendLine("invoke-static/range { p0 .. p0 }, $EXTENSION_SHOW")
            }
        }
    }

    onCreate.addInstructions(0, instructions)
    return true
}
