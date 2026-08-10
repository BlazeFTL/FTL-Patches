package app.ftl.util

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod

fun MutableMethod.returnEarly(value: Boolean) = addInstructions(
    0,
    "const/4 v0, ${if (value) "0x1" else "0x0"}\nreturn v0",
)

fun MutableMethod.returnEarly(value: Int) = addInstructions(
    0,
    "const/16 v0, $value\nreturn v0",
)

fun MutableMethod.returnEarly() = addInstructions(
    0,
    when (returnType) {
        "V" -> "return-void"
        "Z", "B", "C", "S", "I", "F" -> "const/4 v0, 0x0\nreturn v0"
        "J", "D" -> "const-wide/16 v0, 0x0\nreturn-wide v0"
        else -> "const/4 v0, 0x0\nreturn-object v0"
    }
  ,
)
