package app.ftl.util

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass

fun BytecodePatchContext.traverseClassHierarchy(targetClass: MutableClass, callback: MutableClass.() -> Unit) {
    callback(targetClass)

    targetClass.superclass ?: return

    mutableClassDefByOrNull(targetClass.superclass!!)?.let {
        traverseClassHierarchy(it, callback)
    }
}
