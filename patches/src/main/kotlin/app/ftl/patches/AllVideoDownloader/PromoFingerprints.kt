package app.ftl.patches.videodownloader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal const val VIDEO_DOWNLOADER_PACKAGE = "videoplayer.videodownloader.downloader"

internal object TipsActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lvideoplayer/videodownloader/downloader/twelve/activity/TipsActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        opcode(Opcode.INVOKE_SUPER, InstructionLocation.MatchFirst())
    )
)

internal object RateDialogShowFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("Landroid/app/Activity;"),
    filters = listOf(
        methodCall(smali = "Landroid/app/Activity;->isFinishing()Z"),
        methodCall(smali = "Landroid/app/Activity;->isDestroyed()Z"),
        fieldAccess(smali = "Landroid/os/Build\$VERSION;->SDK_INT:I"),
        methodCall(smali = "Landroid/app/Dialog;->setOnDismissListener(Landroid/content/DialogInterface\$OnDismissListener;)V"),
        methodCall(smali = "Landroid/app/Dialog;->show()V")
    )
)

internal object StarRatingDialogBuilderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("Landroid/content/Context;", "L", "L", "L"),
    filters = listOf(
        methodCall(smali = "Landroidx/appcompat/widget/StarCheckView;->setInitStarDrawable(I)V"),
        methodCall(smali = "Landroid/app/Dialog;->show()V")
    )
)

internal object HowToDownloadPromptGateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;", "Z"),
    filters = listOf(
        fieldAccess(
            definingClass = "this",
            type = "I",
            opcode = Opcode.IGET,
            location = InstructionLocation.MatchFirst()
        ),
        opcode(Opcode.PACKED_SWITCH, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.INVOKE_SUPER, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.RETURN, InstructionLocation.MatchAfterImmediately()),
        methodCall(smali = "Ljava/lang/System;->currentTimeMillis()J")
    )
)

internal object DefaultBrowserSheetOnStartFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        opcode(Opcode.INVOKE_SUPER, InstructionLocation.MatchFirst()),
        fieldAccess(type = "Landroid/app/Dialog;", opcode = Opcode.IGET_OBJECT),
        methodCall(smali = "Landroid/app/Dialog;->getWindow()Landroid/view/Window;"),
        methodCall(smali = "Landroid/view/Window;->setWindowAnimations(I)V"),
        string(" does not have a Dialog.")
    )
)

internal object ScanAdMenuBuilderFingerprint : Fingerprint(
    name = "onOptionsItemSelected",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/view/MenuItem;"),
    filters = listOf(
        string("ZSgRRCk=")
    )
)
