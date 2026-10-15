package app.ftl.patches.bangladictionary

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.instanceOf
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal object IsPurchasedFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/content/SharedPreferences;"),
    filters = listOf(
        fieldAccess(opcode = Opcode.SGET_OBJECT, type = "Ljava/lang/String;"),
        opcode(Opcode.CONST_4, InstructionLocation.MatchAfterImmediately()),
        methodCall(
            smali = "Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z",
            opcode = Opcode.INVOKE_INTERFACE,
            location = InstructionLocation.MatchAfterImmediately(),
        ),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.RETURN, InstructionLocation.MatchAfterImmediately()),
    ),
)

internal object TtsInitFingerprint : Fingerprint(
    name = "onInit",
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN),
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, location = InstructionLocation.MatchAfterWithin(6)),
        string("KEY_TTS_INIT_TIME"),
    ),
)

internal object TtsEngineCheckFingerprint : Fingerprint(
    classFingerprint = TtsInitFingerprint,
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("android.speech.tts.engine.CHECK_TTS_DATA"),
        instanceOf("Landroid/content/ActivityNotFoundException;"),
        methodCall(
            smali = "Landroid/content/Context;->getString(I)Ljava/lang/String;",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, InstructionLocation.MatchAfterImmediately()),
        methodCall(
            parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
            returnType = "V",
            opcode = Opcode.INVOKE_STATIC,
            location = InstructionLocation.MatchAfterImmediately(),
        ),
    ),
)

private fun installDialogCall() = methodCall(
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Landroid/content/DialogInterface\$OnClickListener;",
    ),
    returnType = "V",
    opcode = Opcode.INVOKE_STATIC,
)

internal object TtsSpeakFingerprint : Fingerprint(
    classFingerprint = TtsInitFingerprint,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Z"),
    filters = listOf(
        installDialogCall(),
        installDialogCall(),
        string("NULL. Please check code!"),
    ),
)

internal object PronunciationSetupFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(name = "rl_add_to_studyplan", opcode = Opcode.SGET),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/view/View;",
            location = InstructionLocation.MatchAfterImmediately(),
        ),
        fieldAccess(name = "rl_pronunce", opcode = Opcode.SGET),
        fieldAccess(name = "rl_clear", opcode = Opcode.SGET),
    ),
)

internal object PronunciationButtonBindFingerprint : Fingerprint(
    classFingerprint = PronunciationSetupFingerprint,
    returnType = "V",
    filters = listOf(
        fieldAccess(name = "button_pronunce", opcode = Opcode.SGET),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            type = "Landroid/widget/Button;",
            location = InstructionLocation.MatchAfterWithin(6),
        ),
    ),
)

internal object TtsInitTimePrefChangedFingerprint : Fingerprint(
    classFingerprint = PronunciationSetupFingerprint,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        string("KEY_TTS_INIT_TIME"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Lcom/hdictionary/bn/DictionaryActivity;"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "Lcom/hdictionary/bn/DictionaryActivity;"),
        methodCall(
            smali = "Landroid/view/View;->setVisibility(I)V",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
    ),
)
