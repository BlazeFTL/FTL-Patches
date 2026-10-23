package app.ftl.patches.customdpi

import app.ftl.util.getFreeRegisterProvider
import app.ftl.util.registersUsed
import app.ftl.util.traverseClassHierarchy
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val CONTEXT_TYPE = "Landroid/content/Context;"
private const val EXTENSION_SET_PERCENT =
    "Lapp/ftl/extension/dpi/DensityPatch;->setPercent(I)V"
private const val EXTENSION_WRAP =
    "Lapp/ftl/extension/dpi/DensityPatch;->wrap(Landroid/content/Context;)Landroid/content/Context;"

private fun String.toClassType() = "L${replace('.', '/')};"

@Suppress("unused")
val universalDpiPatch = bytecodePatch(
    "Change Display Size",
    "Change any app's display size without touching your phone's system settings. " +
        "Make it bigger if things look too small, or smaller to fit more on screen. " +
        "You Need To Configure 100(No Change), 90(10% Smaller App Ui), 110(10% Bigger App Ui).",
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
        if (dpi == 100) return@execute

        val targets = LinkedHashSet<String>()
        AppEntryPoint.applicationClassName?.let { targets.add(it) }
        targets.addAll(AppEntryPoint.activityClassNames)

        val patched = HashSet<String>()

        targets.forEach { className ->
            val target = mutableClassDefByOrNull(className.toClassType()) ?: return@forEach
            hookAttachBaseContext(target, dpi, patched)
        }
    }
}

private fun BytecodePatchContext.hookAttachBaseContext(
    target: MutableClass,
    dpi: Int,
    patched: MutableSet<String>,
) {
    var declared = false
    var injected = false

    traverseClassHierarchy(target) {
        if (injected) return@traverseClassHierarchy

        val method = methods.firstOrNull {
            it.name == "attachBaseContext" &&
                it.returnType == "V" &&
                it.parameterTypes.map { p -> p.toString() } == listOf(CONTEXT_TYPE)
        } ?: return@traverseClassHierarchy

        declared = true

        if (!patched.add(type)) {
            injected = true
            return@traverseClassHierarchy
        }

        val register = try {
            method.getFreeRegisterProvider(1, 1, method.getInstruction(0).registersUsed).getFreeRegister()
        } catch (e: IllegalArgumentException) {
            patched.remove(type)
            return@traverseClassHierarchy
        }

        method.addInstructions(
            0,
            """
                const v$register, $dpi
                invoke-static/range { v$register .. v$register }, $EXTENSION_SET_PERCENT
                invoke-static/range { p1 .. p1 }, $EXTENSION_WRAP
                move-result-object p1
            """,
        )
        injected = true
    }

    if (declared) return

    val superType = target.superclass ?: return

    val method = ImmutableMethod(
        target.type,
        "attachBaseContext",
        listOf(ImmutableMethodParameter(CONTEXT_TYPE, null, null)),
        "V",
        AccessFlags.PROTECTED.value,
        null,
        null,
        MutableMethodImplementation(3),
    ).toMutable()

    method.addInstructions(
        0,
        """
            const v0, $dpi
            invoke-static/range { v0 .. v0 }, $EXTENSION_SET_PERCENT
            invoke-static/range { p1 .. p1 }, $EXTENSION_WRAP
            move-result-object p1
            invoke-super { p0, p1 }, $superType->attachBaseContext(Landroid/content/Context;)V
            return-void
        """,
    )

    target.methods.add(method)
}
