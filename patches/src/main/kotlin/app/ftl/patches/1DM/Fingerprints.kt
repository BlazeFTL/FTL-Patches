package app.ftl.patches.`1dm`

import app.morphe.patcher.Fingerprint

object ShouldShowNotificationFingerprint : Fingerprint(
    definingClass = "Lidm/internet/download/manager/ServerNotification;",
    name = "shouldShowNotification",
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;")
)
