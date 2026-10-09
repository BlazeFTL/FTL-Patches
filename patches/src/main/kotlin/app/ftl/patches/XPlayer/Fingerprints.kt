package app.ftl.patches.xplayer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal const val ADD_WIDGET_ACTIVITY = "Lcom/inshot/xplayer/activities/AddWidgetActivity;"

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

internal object WidgetMenuClickFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/view/MenuItem;"),
    custom = { method, _ ->
        method.implementation?.instructions?.any {
            it.opcode == Opcode.CONST_CLASS &&
                ((it as ReferenceInstruction).reference as? TypeReference)?.type == ADD_WIDGET_ACTIVITY
        } == true
    }
)
