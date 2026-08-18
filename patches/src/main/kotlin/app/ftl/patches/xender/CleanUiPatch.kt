package app.ftl.patches.xender

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchFirst
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val MAIN_ACTIVITY_CLASS = "Lcn/xender/ui/activity/MainActivity;"

private const val EXTENSION_SCHEDULE_REAPPLY =
    "Lapp/ftl/extension/xender/CleanUiPatch;->scheduleReapply(Landroid/app/Activity;)V"
private const val EXTENSION_APPLY_ONCE =
    "Lapp/ftl/extension/xender/CleanUiPatch;->applyOnce(Landroid/app/Activity;)V"

/**
 * Matches MainActivity.onCreate(Bundle) right after its content view is inflated.
 * Anchored on the real, un-renamed activity_main layout resource id read that always
 * precedes the setContentView call, followed structurally by INVOKE_STATIC and its
 * MOVE_RESULT_OBJECT. The databinding cast that immediately follows uses an obfuscated
 * class name that reshuffles every build, so this stops one instruction short of it.
 */
private object MainOnCreateContentViewFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        fieldAccess(
            smali = "Lcn/xender/R\$layout;->activity_main:I",
        ),
        opcode(Opcode.INVOKE_STATIC, MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
    ),
)

/**
 * Matches MainActivity.onResume() by its real signature, anchored on the invoke-super
 * call to FragmentActivity.onResume() - a real AndroidX class kept by the library's own
 * consumer proguard rules, and the method's first instruction.
 */
private object MainOnResumeFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    name = "onResume",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            smali = "Landroidx/fragment/app/FragmentActivity;->onResume()V",
            location = MatchFirst(),
        ),
    ),
)

/**
 * Matches MainActivity.onWindowFocusChanged(boolean) by its real signature, anchored on
 * the invoke-super call to the framework Activity method - the method's first instruction.
 */
private object MainOnWindowFocusChangedFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    name = "onWindowFocusChanged",
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        methodCall(
            smali = "Landroid/app/Activity;->onWindowFocusChanged(Z)V",
            location = MatchFirst(),
        ),
    ),
)

/**
 * Matches MainActivity's drawer-open click handler purely by its real, un-renamed name.
 * The name must stay fixed since it's wired from a databinding click listener, so no
 * instruction filters are needed - the signature alone is a unique, stable anchor.
 */
private object DrawerEnterClickFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY_CLASS,
    name = "drawerEnterClick",
    returnType = "V",
    parameters = emptyList(),
)

/**
 * Hides the nav drawer's promo/rate/help/about items and the bottom navigation view,
 * and keeps the Connect/Create/Join buttons on top. All the actual view-id lookups and
 * the re-apply retry loop live in the extension (extensions/xender/CleanUiPatch.kt),
 * resolved at runtime via resources.getIdentifier() - so nothing here needs to touch
 * a resource id integer, which changes every build even though the names don't.
 */
val cleanUiPatch = bytecodePatch(
    name = "Clean up main UI",
    description = "Hides the nav drawer's Guide/Rate/Help/About items and the bottom navigation view, and keeps the Connect/Create/Join buttons on top.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_XENDER)

    extendWith("extensions/xender.mpe")

    execute {
        MainOnCreateContentViewFingerprint.let { fingerprint ->
            val insertIndex = fingerprint.instructionMatches.last().index + 1
            fingerprint.method.addInstructions(
                insertIndex,
                "invoke-static {p0}, $EXTENSION_SCHEDULE_REAPPLY",
            )
        }

        MainOnResumeFingerprint.let { fingerprint ->
            val superCallIndex = fingerprint.instructionMatches.first().index
            fingerprint.method.addInstructions(
                superCallIndex + 1,
                "invoke-static {p0}, $EXTENSION_SCHEDULE_REAPPLY",
            )
        }

        MainOnWindowFocusChangedFingerprint.let { fingerprint ->
            val superCallIndex = fingerprint.instructionMatches.first().index
            fingerprint.method.addInstructions(
                superCallIndex + 1,
                "invoke-static {p0}, $EXTENSION_APPLY_ONCE",
            )
        }

        DrawerEnterClickFingerprint.let { fingerprint ->
            fingerprint.method.addInstructions(
                0,
                "invoke-static {p0}, $EXTENSION_SCHEDULE_REAPPLY",
            )
        }
    }
}
