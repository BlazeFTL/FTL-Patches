package app.ftl.patches.alldownloader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch

private const val NOTIFY_SERVICE_CLASS =
    "Lvideoplayer/videodownloader/downloader/service/NotifyService;"

private object NotifyServiceStartForegroundFingerprint : Fingerprint(
    definingClass = NOTIFY_SERVICE_CLASS,
    name = "onStartCommand",
    returnType = "I",
    parameters = listOf("Landroid/content/Intent;", "I", "I"),
    filters = listOf(
        methodCall(smali = "Landroid/app/Service;->startForeground(ILandroid/app/Notification;)V"),
    ),
)

val disableNotifyServiceForegroundPatch = bytecodePatch(
    name = "Disable persistent foreground service",
    description = "Stops the notification shortcut service from running as a foreground service " +
        "so the app no longer stays alive in the background after being removed from recents.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ALL_VIDEO_DOWNLOADER)

    execute {
        NotifyServiceStartForegroundFingerprint.let { fingerprint ->
            fingerprint.method.replaceInstruction(
                fingerprint.instructionMatches.first().index,
                "invoke-virtual {p0}, Landroid/app/Service;->stopSelf()V",
            )
        }
    }
}
