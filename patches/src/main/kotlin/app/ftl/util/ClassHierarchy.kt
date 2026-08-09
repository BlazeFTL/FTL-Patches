package app.ftl.util

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass

/**
 * Traverse the class hierarchy starting from the given root class.
 *
 * @param targetClass the class to start traversing the class hierarchy from.
 * @param callback function that is called for every class in the hierarchy.
 */
fun BytecodePatchContext.traverseClassHierarchy(targetClass: MutableClass, callback: MutableClass.() -> Unit) {
    callback(targetClass)

    targetClass.superclass ?: return

    mutableClassDefByOrNull(targetClass.superclass!!)?.let {
        traverseClassHierarchy(it, callback)
    
    }
}
