package app.ftl.patches.videodownloader

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val BROWSER_ACTIVITY = "Lvideo/downloader/videodownloader/activity/BrowserActivity;"
private const val EXT = "Lapp/ftl/extension/videodownloader"

internal object PopupBlockerShouldOverrideUrlLoadingFingerprint : Fingerprint(
    name = "shouldOverrideUrlLoading",
    returnType = "Z",
    parameters = listOf("Landroid/webkit/WebView;", "Landroid/webkit/WebResourceRequest;"),
    filters = listOf(
        methodCall(
            smali = "Landroid/webkit/WebResourceRequest;->getUrl()Landroid/net/Uri;",
            opcode = Opcode.INVOKE_INTERFACE,
        ),
        methodCall(
            definingClass = "this",
            parameters = listOf("Landroid/webkit/WebView;", "Ljava/lang/String;"),
            returnType = "Z",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
        methodCall(
            smali = "Landroid/webkit/WebViewClient;->shouldOverrideUrlLoading(" +
                "Landroid/webkit/WebView;Landroid/webkit/WebResourceRequest;)Z",
            opcode = Opcode.INVOKE_SUPER,
        ),
    ),
)

internal object PopupBlockerOnCreateWindowFingerprint : Fingerprint(
    name = "onCreateWindow",
    returnType = "Z",
    parameters = listOf("Landroid/webkit/WebView;", "Z", "Z", "Landroid/os/Message;"),
    filters = listOf(
        methodCall(
            smali = "Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
        opcode(Opcode.CONST_4),
        opcode(Opcode.IF_NEZ, MatchAfterImmediately()),
        methodCall(
            smali = "Landroid/webkit/WebView\$WebViewTransport;->setWebView(Landroid/webkit/WebView;)V",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
    ),
)

internal object PopupBlockerOnCreateOptionsMenuFingerprint : Fingerprint(
    definingClass = BROWSER_ACTIVITY,
    name = "onCreateOptionsMenu",
    returnType = "Z",
    parameters = listOf("Landroid/view/Menu;"),
)

internal object PopupBlockerOnNewIntentFingerprint : Fingerprint(
    definingClass = BROWSER_ACTIVITY,
    name = "onNewIntent",
    returnType = "V",
    parameters = listOf("Landroid/content/Intent;"),
    filters = listOf(
        fieldAccess(
            definingClass = "this",
            opcode = Opcode.IGET_OBJECT,
        ),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Z"),
            returnType = "Z",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
    ),
)

@Suppress("unused")
val popupBlockerPatch = bytecodePatch(
    name = "Popup blocker",
    description = "Blocks popups and popunder redirects with Allow / Block / Always Block prompts. " +
        "Adds \"Manage popup rules\" as the last item of the 3-dot menu.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_VIDEO_DOWNLOADER)

    extendWith("extensions/videodownloader.mpe")

    execute {
        PopupBlockerShouldOverrideUrlLoadingFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p1, p2 }, $EXT/PopupGate;->intercept(Landroid/webkit/WebView;Landroid/webkit/WebResourceRequest;)Z
                    move-result v0
                    if-eqz v0, :ftl_continue
                    return v0
                """,
                ExternalLabel("ftl_continue", getInstruction(0)),
            )
        }

        PopupBlockerOnCreateWindowFingerprint.let { fingerprint ->
            val method = fingerprint.method
            val registerCount = method.implementation!!.registerCount
            val p1 = registerCount - 4
            val p4 = registerCount - 1
            val constMatch = fingerprint.instructionMatches[1]
            val ifMatch = fingerprint.instructionMatches[2]

            if (constMatch.getInstruction<OneRegisterInstruction>().registerA != p1 ||
                ifMatch.getInstruction<OneRegisterInstruction>().registerA != p4
            ) {
                throw PatchException("onCreateWindow layout changed: expected const/4 p1 followed by if-nez p4")
            }

            val ifIndex = ifMatch.index
            method.addInstructionsWithLabels(
                ifIndex,
                """
                    invoke-static { p4 }, $EXT/PopupWindowGate;->shouldBlock(Landroid/os/Message;)Z
                    move-result p3
                    if-eqz p3, :ftl_continue
                    return p1
                """,
                ExternalLabel("ftl_continue", method.getInstruction(ifIndex)),
            )

            method.addInstructions(
                0,
                "invoke-static { p1 }, $EXT/PopupWindowGate;->captureOpener(Landroid/webkit/WebView;)V",
            )
        }

        PopupBlockerOnCreateOptionsMenuFingerprint.method.addInstructions(
            0,
            "invoke-static { p0, p1 }, $EXT/PopupMenuHook;->addPopupRulesItem(Landroid/app/Activity;Landroid/view/Menu;)V",
        )

        PopupBlockerOnNewIntentFingerprint.let { fingerprint ->
            val tabsField = (fingerprint.instructionMatches[0].instruction as ReferenceInstruction)
                .reference as FieldReference
            val openTab = (fingerprint.instructionMatches[1].instruction as ReferenceInstruction)
                .reference as MethodReference

            if (openTab.definingClass != tabsField.type) {
                throw PatchException("onNewIntent layout changed: tab opener is not called on the tabs field")
            }

            val classDef = fingerprint.classDef
            val bridge = ImmutableMethod(
                classDef.type,
                "openPopupTab",
                listOf(ImmutableMethodParameter("Ljava/lang/String;", null, null)),
                "V",
                AccessFlags.PUBLIC.value,
                null,
                null,
                MutableMethodImplementation(4),
            ).toMutable()
            classDef.methods.add(bridge)

            bridge.addInstructions(
                0,
                """
                    iget-object v0, p0, ${tabsField.definingClass}->${tabsField.name}:${tabsField.type}
                    if-eqz v0, :ftl_done
                    const/4 v1, 0x1
                    invoke-virtual { v0, p1, v1 }, ${openTab.definingClass}->${openTab.name}(${openTab.parameterTypes.joinToString("")})${openTab.returnType}
                    :ftl_done
                    return-void
                """,
            )

            classDef.interfaces.add("$EXT/PopupTabOpener;")
        }
    }
}
