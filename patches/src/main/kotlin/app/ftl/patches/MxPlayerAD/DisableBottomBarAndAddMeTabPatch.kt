package app.ftl.patches.mxplayerad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.ftl.util.getFreeRegisterProvider
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

// Confirmed by manual smali diff against a working build: the toolbar item's onClick
// must call this zero-arg method on the mj/nob-style delegate to actually navigate to
// the Me tab. It's obfuscated and WILL change on future rebuilds - if the button stops
// navigating, re-diff a working build (compare stock vs the last known-good patched
// smali for this method) and update this one constant.
private const val NAVIGATE_METHOD_NAME = "R"

/**
 * Resolves the bottom bar's show/hide method (`X0(ZZ)V` in the sample build) purely
 * structurally, app-wide: takes exactly two booleans, returns void, reads a ViewGroup
 * field, branches on the first boolean, and sets that view's visibility to GONE (8) on
 * one branch. Never pins the method name or the field name, both of which are
 * obfuscated and reshuffle every build.
 *
 * This intentionally does NOT resolve the host class via the `@id/online_bottom_layout`
 * findViewById() site first (e.g. via a classFingerprint) - that id is also read by
 * other generated code for the same layout (a ViewBinding bind() method, for one),
 * and a plain "first class that touches this id" lookup can land on one of those
 * instead of the actual bottom-bar host, which then has no (Z,Z)V method at all and
 * fails this fingerprint. The (Z,Z)V signature plus this exact body shape is
 * discriminating enough on its own without that detour.
 */
private object ToggleBottomBarFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z", "Z"),
    filters = listOf(
        fieldAccess(
            definingClass = "this",
            type = "Landroid/view/ViewGroup;",
            opcode = Opcode.IGET_OBJECT,
        ),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        literal(8, location = MatchAfterImmediately()),
        methodCall(
            smali = "Landroid/view/View;->setVisibility(I)V",
            location = MatchAfterWithin(2),
        ),
    ),
)

/**
 * Resolves the options-menu preparation method (`c0(Menu)Z` in the sample build).
 * Anchored on `Apps.j(Menu, int, boolean)` - a real, unobfuscated app utility used to
 * toggle menu item visibility, called repeatedly at the top of this specific method -
 * plus the method's own tail idiom of delegating to another method on itself before
 * returning. Assumption: this combination is unique app-wide. That held for the
 * analyzed build (MX Player v3.1.4 / 2001003524); if a future version throws a
 * no-match error here, re-check with a compare tool and narrow further.
 */
private object MjMenuPrepareFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/view/Menu;"),
    filters = listOf(
        methodCall(
            smali = "Lcom/mxtech/app/Apps;->j(Landroid/view/Menu;IZ)V",
        ),
        methodCall(
            definingClass = "this",
            parameters = listOf("Landroid/view/Menu;"),
            returnType = "V",
        ),
        opcode(Opcode.RETURN, location = MatchAfterImmediately()),
    ),
)

val disableBottomBarAndAddMeTabPatch = bytecodePatch(
    name = "Disable Bottom Bar And Add Me Tab To Top",
    description = "Hides the bottom navigation bar and adds a Me tab button to the toolbar.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(addMeTabMenuResourcePatch)

    extendWith("extensions/mxplayerad.mpe")

    execute {
        // Always take the "hide" branch, regardless of what the caller passes.
        ToggleBottomBarFingerprint.method.addInstruction(0, "const/4 p1, 0x1")

        MjMenuPrepareFingerprint.let { fingerprint ->
            val method = fingerprint.method

            // The field typed ActivityWelcomeMX (real, manifest-declared class, so this
            // type string is stable) is read earlier in this same method - grab its
            // current defining class + name instead of pinning either, since both are
            // obfuscated and reshuffle every build.
            val activityField = method.instructions
                .filterIsInstance<ReferenceInstruction>()
                .first { it.opcode == Opcode.IGET_OBJECT &&
                    (it.reference as? FieldReference)?.type == ACTIVITY_WELCOME_MX_CLASS
                }
                .reference as FieldReference
            val activityFieldSmali =
                "${activityField.definingClass}->${activityField.name}:${activityField.type}"

            val insertionIndex = fingerprint.instructionMatches[1].index

            // v0-v4 are all live/used elsewhere in this method by the time control
            // reaches this point from different branches, so ask for a register that's
            // verified free right here rather than guessing one.
            val registers = method.getFreeRegisterProvider(insertionIndex, 2, emptyList())
            val activityRegister = registers.getFreeRegister()
            val stringRegister = registers.getFreeRegister()

            // Confirmed against a manually verified working build: the toolbar item's
            // click must invoke this zero-arg method on the delegate (p0) itself - not
            // forward a click to the (now-hidden) bottom-nav view, which has no
            // listener of its own. The method name is obfuscated and will need
            // re-checking against a fresh smali diff on future app versions if this
            // patch stops navigating.
            method.addInstructions(
                insertionIndex,
                """
                    iget-object v$activityRegister, p0, $activityFieldSmali
                    const-string v$stringRegister, "$NAVIGATE_METHOD_NAME"
                    invoke-static {p1, v$activityRegister, p0, v$stringRegister}, Lapp/ftl/extension/mxplayerad/MeTabToolbarPatch;->wireMeTabMenuItem(Landroid/view/Menu;Landroid/app/Activity;Ljava/lang/Object;Ljava/lang/String;)V
                """.trimIndent(),
            )
        }
    }
}
