package app.ftl.patches.spoofsignature

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.AccessFlags
import org.w3c.dom.Element

private const val EXTENSION_CLASS = "Lapp/ftl/extension/signature/SignatureHookApp;"

// Shared state populated by sub-patches ────────────────────────────────────

internal var extractedPackageName: String = ""
    private set

// Fingerprint ───────────────────────────────────────────────────────────────

/**
 * Targets the static initializer of [SignatureHookApp] in the extension, which contains
 * two placeholder const-string instructions we replace with the real package name and
 * Base64 certificate signature.
 */
internal val SignatureHookAppStaticInitFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC, AccessFlags.CONSTRUCTOR),
    parameters = emptyList(),
    custom = { method, classDef ->
        classDef.type == EXTENSION_CLASS &&
            method.implementation?.instructions?.filterIsInstance<
                com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
            >()?.count { instr ->
                (instr.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference)
                    ?.string in setOf("<package-name>", "<signature>")
            } == 2
    },
)

// Sub-patches (internal) ────────────────────────────────────────────────────

/**
 * Reads [extractedPackageName] from AndroidManifest.xml.
 */
internal val extractPackageNamePatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { doc ->
            extractedPackageName = (doc.getElementsByTagName("manifest").item(0) as Element)
                .getAttribute("package")
        }
    }
}

/**
 * Sets `android:name` on `<application>` to [SignatureHookApp] if not already set.
 * This replaces the app's Application subclass with our hooked one.
 * If the app already has a custom Application, we handle that in bytecode via setSuperClass.
 */
internal val injectSignatureHookAppPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as Element
            val existing = application.getAttribute("android:name")
            if (existing.isEmpty()) {
                application.setAttribute(
                    "android:name",
                    "app.ftl.extension.signature.SignatureHookApp",
                )
            }
            // If existing is non-empty, bytecode setSuperClass handles it instead.
        }
    }
}

// Main patch ────────────────────────────────────────────────────────────────

/**
 * Universal "Spoof signature verification" patch — shows in Morphe for **any** app.
 *
 * Ported from jkennethcarino/adobo (SpoofSignatureVerificationPatch + SignatureHookApp),
 * originally based on https://github.com/L-JINBIN/ApkSignatureKillerEx.
 *
 * **What it does:**
 * 1. Extracts the original APK's signing certificate and Base64-encodes it (auto).
 * 2. Injects [SignatureHookApp] as the Application superclass — it replaces
 *    `PackageInfo.CREATOR` (a Parcel deserializer) with a proxy that swaps the
 *    re-signed APK's certificate back to the original one at runtime.
 * 3. This intercepts ALL signature reads (via PackageManager, direct Parcel unpacking,
 *    Play Integrity reflection) at the Parcelable layer — deeper than DEX call-site patching.
 *
 * **Options:**
 * - `packageName` — leave blank to auto-read from AndroidManifest. Override if you changed it.
 *
 * Signature itself comes from [extractApkCertificatePatch] (auto-extracted from the input APK,
 * or supplied manually via its own options).
 */
@Suppress("unused")
val spoofSignatureVerificationPatch = bytecodePatch(
    name = "Spoof app signature",
    description = """
        Makes the app think its signing certificate is unchanged after Morphe re-signs it.

        Useful when an app crashes or shows a tamper warning because it checks its own certificate.

        Does not bypass Play Integrity / SafetyNet hardware attestation.
    """.trimIndent(),
    default = false,
) {
    val packageNameOption by stringOption(
        key = "packageName",
        default = null,
        title = "Package name override (optional)",
        description = "Leave blank — read automatically from the app. " +
            "Only set this if you changed the app's package name and the patch is using the wrong one.",
        required = false,
    ) { it == null || it!!.matches(Regex("^[a-z]\\w*(\\.[a-z]\\w*)+\$")) }

    dependsOn(extractPackageNamePatch, extractApkCertificatePatch, injectSignatureHookAppPatch)

    extendWith("extensions/signature.mpe")

    execute {
        // Resolve package name.
        val packageName = packageNameOption?.takeIf { it.isNotBlank() } ?: extractedPackageName

        // Resolve signature.
        val signature = autoBase64Der
            ?: throw PatchException(
                "No certificate found in META-INF and no signature supplied. " +
                    "Provide the Base64-encoded certificate via the 'Provide Original app certificate' patch options."
            )

        // Patch placeholder const-strings in SignatureHookApp's static initializer.
        SignatureHookAppStaticInitFingerprint.method.apply {
            val instructions = implementation?.instructions?.toList() ?: return@apply
            var packageNameIndex = -1
            var signatureIndex = -1
            instructions.forEachIndexed { i, instr ->
                val ref = (instr as? com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c)
                    ?.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference
                when (ref?.string) {
                    "<package-name>" -> packageNameIndex = i
                    "<signature>"    -> signatureIndex = i
                }
            }
            if (packageNameIndex >= 0)
                replaceInstruction(packageNameIndex, "const-string v0, \"$packageName\"")
            if (signatureIndex >= 0)
                replaceInstruction(signatureIndex, "const-string v1, \"${signature.trim()}\"")
        }

        val hookClass = SignatureHookAppStaticInitFingerprint.originalClassDef

        // For apps that already have a custom Application subclass, redirect their superclass
        // to SignatureHookApp so the static initializer fires without replacing app logic.
        classDefForEach { classDef ->
            if (classDef != hookClass && classDef.superclass == "Landroid/app/Application;") {
                mutableClassDefBy(classDef).setSuperClass(hookClass.type)
            }
        }
    }
}
