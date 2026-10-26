package app.ftl.patches.mobilism

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch

private const val OPEN_LATEST = "$EXT_CLASS_PREFIX/OpenLatest;"

internal object OpenInLatestMainCreateFingerprint : Fingerprint(
    definingClass = "Ldemigos/com/mobilism/UI/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)

@Suppress("unused")
val openInLatestPatch = bytecodePatch(
    name = "Open in latest",
    description = "Opens the Latest list for the current content type on launch. Configurable in Mod settings."
) {
    compatibleWith(COMPATIBILITY_MOBILISM)

    dependsOn(modSettingsPatch)

    extendWith("extensions/mobilism.mpe")

    execute {
        OpenInLatestMainCreateFingerprint.method.insertAfterSuperCall(
            "invoke-static/range {p0 .. p1}, $OPEN_LATEST->onMainCreate(Ljava/lang/Object;Landroid/os/Bundle;)V"
        )
    }
}
