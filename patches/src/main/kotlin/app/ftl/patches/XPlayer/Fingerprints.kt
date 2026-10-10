package app.ftl.patches.xplayer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object AdRemovedReadFingerprint : Fingerprint(
    filters = listOf(
        string("adRemoved"),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Z"),
            returnType = "Z",
            opcode = Opcode.INVOKE_STATIC,
            location = InstructionLocation.MatchAfterWithin(3)
        ),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately())
    )
)

internal object PurchasedProductsCheckFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Ljava/util/List;"),
    filters = listOf(
        string("com.camerasideas.xplayer.removead"),
        string("xplayer.vip.month")
    )
)

internal object CastMenuFragmentFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/Menu;"),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "cast"),
        methodCall(
            smali = "Landroid/view/Menu;->findItem(I)Landroid/view/MenuItem;",
            location = InstructionLocation.MatchAfterImmediately()
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, InstructionLocation.MatchAfterImmediately()),
        methodCall(smali = "Landroid/view/MenuItem;->setIcon(I)Landroid/view/MenuItem;"),
        methodCall(
            definingClass = "Landroidx/fragment/app/Fragment;",
            parameters = listOf("Landroid/view/Menu;"),
            returnType = "V",
            opcode = Opcode.INVOKE_SUPER
        )
    )
)

internal object CastMenuControlActivityFingerprint : Fingerprint(
    definingClass = "Lcom/inshot/cast/xcast/ControlActivity;",
    name = "onPrepareOptionsMenu",
    returnType = "Z",
    parameters = listOf("Landroid/view/Menu;"),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "cast"),
        methodCall(
            smali = "Landroid/view/Menu;->findItem(I)Landroid/view/MenuItem;",
            location = InstructionLocation.MatchAfterImmediately()
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, InstructionLocation.MatchAfterImmediately())
    )
)

internal object VideoGuideFingerprint : Fingerprint(
    definingClass = "Lcom/inshot/xplayer/activities/PlayerActivity;",
    filters = listOf(
        string("videoGuide"),
        opcode(Opcode.INVOKE_STATIC, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.CONST_STRING, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.INVOKE_STATIC, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, InstructionLocation.MatchAfterImmediately())
    )
)

internal object RateCountFingerprint : Fingerprint(
    definingClass = "Lcom/inshot/xplayer/activities/PlayerActivity;",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("showRateCount"),
        string("showRateWatchTimeCount")
    )
)

internal object VolumeBarInitFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Lcom/inshot/xplayer/activities/PlayerActivity;", "L"),
    filters = listOf(
        string("sKrMspmkr"),
        opcode(Opcode.MUL_INT_LIT8, InstructionLocation.MatchAfterWithin(6)),
        methodCall(
            smali = "Landroid/widget/ProgressBar;->setMax(I)V",
            location = InstructionLocation.MatchAfterImmediately()
        ),
        literal(0x3e8, location = InstructionLocation.MatchAfterImmediately()),
        methodCall(
            smali = "Lcom/inshot/xplayer/utils/widget/BookMarkCircleSeekBar;->setMax(I)V",
            location = InstructionLocation.MatchAfterImmediately()
        )
    )
)

internal object VolumeSwipeFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        opcode(Opcode.FLOAT_TO_INT),
        fieldAccess(
            definingClass = "this",
            type = "I",
            opcode = Opcode.IGET,
            location = InstructionLocation.MatchAfterImmediately()
        ),
        opcode(Opcode.ADD_INT_2ADDR, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.SHL_INT_LIT8, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.CONST_4, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.IF_LE, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.SHL_INT_LIT8, InstructionLocation.MatchAfterImmediately())
    )
)

internal object VolumeBarMaxFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        methodCall(smali = "Landroid/widget/PopupWindow;-><init>(Landroid/view/View;IIZ)V"),
        opcode(Opcode.SHL_INT_2ADDR),
        methodCall(
            smali = "Landroid/widget/ProgressBar;->setMax(I)V",
            location = InstructionLocation.MatchAfterImmediately()
        )
    )
)

internal object VolumeKeyFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("I", "Landroid/view/KeyEvent;"),
    filters = listOf(
        literal(0x19),
        opcode(Opcode.SHL_INT_LIT8),
        opcode(Opcode.IF_LE, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.SHL_INT_LIT8, InstructionLocation.MatchAfterImmediately())
    )
)

internal object MaxVolumeFingerprint : Fingerprint(
    returnType = "I",
    parameters = listOf(),
    filters = listOf(
        methodCall(smali = "Landroid/media/AudioManager;->getStreamMaxVolume(I)I"),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.MUL_INT_LIT8, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.RETURN, InstructionLocation.MatchAfterImmediately())
    )
)

internal object VolumeSetFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        fieldAccess(definingClass = "this", type = "I", opcode = Opcode.IGET),
        opcode(Opcode.IF_LE, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.MOVE, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.IF_GEZ, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.CONST_4, InstructionLocation.MatchAfterImmediately()),
        fieldAccess(
            definingClass = "this",
            type = "I",
            opcode = Opcode.IGET,
            location = InstructionLocation.MatchAfterImmediately()
        ),
        opcode(Opcode.IF_EQ, InstructionLocation.MatchAfterImmediately()),
        fieldAccess(
            definingClass = "this",
            type = "I",
            opcode = Opcode.IPUT,
            location = InstructionLocation.MatchAfterImmediately()
        )
    )
)

internal object ServiceVolumeBoostFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        string("vboost"),
        methodCall(
            smali = "Landroid/content/SharedPreferences;->getInt(Ljava/lang/String;I)I",
            location = InstructionLocation.MatchAfterImmediately()
        ),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.IF_LEZ, InstructionLocation.MatchAfterImmediately()),
        methodCall(
            smali = "Landroid/media/AudioManager;->getStreamMaxVolume(I)I",
            location = InstructionLocation.MatchAfterWithin(20)
        ),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        methodCall(
            smali = "Landroid/media/AudioManager;->getStreamVolume(I)I",
            location = InstructionLocation.MatchAfterWithin(6)
        ),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.IF_NE, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.INT_TO_FLOAT, InstructionLocation.MatchAfterWithin(3))
    )
)
