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
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

// The "Me icon does nothing" regression survived three straight fixes (reflection,
// then two different action-view lookups) that all checked out correct on decompile,
// because none of them were the actual bug. c0(Menu)Z has several early-exit branches
// (feature-flag checks, null checks - see the full body) that all jump straight to
// :cond_4/:goto_1, which sit immediately before the M0(Menu) call - and every prior
// version of this patch inserted its wiring code AT that same position
// (instructionMatches[1].index, i.e. right before M0). Dexlib2 keeps a label bound to
// the instruction it originally pointed at (M0) rather than moving it onto newly-
// inserted code placed before that instruction, so anything reaching M0 via one of
// those jumps landed past the insert and skipped it outright - while looking completely
// correct in a top-to-bottom decompile, since the label still renders directly above M0
// either way. Confirmed by the user moving the insert by hand (raw smali text naturally
// rebinds a label to whatever line comes first when reassembled) and testing it working.
// Fix: insert right after M0 instead of right before it (instructionMatches[2].index -
// the already-captured RETURN match, since M0 falls straight through to it with no
// label in between). Every path through this method reaches that point unconditionally
// after M0 runs, so nothing can jump around it.

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

/**
 * Resolves the "navigate to Me tab" method (`R()V` in the sample build). Restricted to
 * the same class as [MjMenuPrepareFingerprint] (via classFingerprint) and matched by
 * shape alone: a zero-argument void method containing the literal string "me_local".
 * That string is real app data - a tab-selection key the app itself passes to a Bundle
 * and to MXLocalMePageActivity - never renamed by R8, unlike the method's own name and
 * defining class, which are obfuscated and reshuffle every build and so are never pinned.
 */
private object NavigateToMeFingerprint : Fingerprint(
    classFingerprint = MjMenuPrepareFingerprint,
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("me_local"),
)

val disableBottomBarAndAddMeTabPatch = bytecodePatch(
    name = "Disable Bottom Bar And Add Me Tab To Top",
    description = "Hides the bottom navigation bar and adds a Me tab button to the toolbar.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_AD)

    dependsOn(addMeTabMenuResourcePatch)

    execute {
        // Always take the "hide" branch, regardless of what the caller passes.
        ToggleBottomBarFingerprint.method.addInstruction(0, "const/4 p1, 0x1")

        val mjClass = MjMenuPrepareFingerprint.classDef
        val navigateMethod = NavigateToMeFingerprint.method

        // Give the delegate class a real OnClickListener implementation instead of
        // reaching the navigate method through reflection: the interface and the
        // method below are added directly onto the same class R() already lives on,
        // so calling it is a plain same-class invoke-virtual against whatever name
        // this build's fingerprint resolved - never a hardcoded/obfuscated identifier,
        // and never a runtime reflective lookup that can silently fail.
        val onClickListenerType = "Landroid/view/View\$OnClickListener;"
        if (onClickListenerType !in mjClass.interfaces) {
            mjClass.interfaces.add(onClickListenerType)
        }

        val onClickMethod = ImmutableMethod(
            mjClass.type,
            "onClick",
            listOf(ImmutableMethodParameter("Landroid/view/View;", null, null)),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(2),
        ).toMutable()

        onClickMethod.addInstructions(
            0,
            """
                invoke-virtual {p0}, ${mjClass.type}->${navigateMethod.name}()V
                return-void
            """.trimIndent(),
        )

        mjClass.methods.add(onClickMethod)

        MjMenuPrepareFingerprint.let { fingerprint ->
            val method = fingerprint.method

            // instructionMatches[2] is the RETURN right after M0 (filter[2] above,
            // MatchAfterImmediately). Inserting here - not at instructionMatches[1],
            // M0's own position - is the actual fix: nothing branches to this point,
            // so every path through the method hits it unconditionally after M0 runs.
            val insertionIndex = fingerprint.instructionMatches[2].index

            // Confirmed by manual smali diff against a working build (MX Player
            // v3.1.4 / versionCode 24011893): find the item by its build-assigned
            // numeric id, get its action view, wire it directly - no extension call.
            // p0 already implements View.OnClickListener (added above), so it's
            // passed straight through as its own listener.
            method.addInstructions(
                insertionIndex,
                """
                    const v4, 0x7f0b1d9a
                    invoke-interface {p1, v4}, Landroid/view/Menu;->findItem(I)Landroid/view/MenuItem;
                    move-result-object v3
                    invoke-interface {v3}, Landroid/view/MenuItem;->getActionView()Landroid/view/View;
                    move-result-object v3
                    invoke-virtual {v3, p0}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
                """.trimIndent(),
            )
        }
    }
}
