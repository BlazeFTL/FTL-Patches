package app.ftl.patches.`1dm`

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly

@Suppress("unused")
val disableSaleNotificationPatch = bytecodePatch(
    name = "Disable sale notification",
    description = "Disables server pushed sale notifications.",
    default = true
) {
    compatibleWith(
        Compatibility(
            packageName = "idm.internet.download.manager.plus",
            name = "1DM+",
            targets = listOf(AppTarget(version = "20.0"))
        )
    )

    execute {
        ShouldShowNotificationFingerprint.method.returnEarly(false)
    }
}
