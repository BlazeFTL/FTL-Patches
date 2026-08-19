package app.ftl.extension.xender

import android.app.Activity
import android.util.Log
import android.view.View

/**
 * Ids are registered from the patch side via sget on the app's own R$id fields (compile-time
 * stable, real resource field names) - never via Resources.getIdentifier(name, ...), which can
 * return 0 if the resource name string gets stripped by resource shrinking even though the
 * numeric id itself still works fine via direct field access.
 *
 * Re-apply is driven by a ViewTreeObserver.OnGlobalLayoutListener attached once to the decor
 * view, rather than a fixed-count/fixed-delay retry loop. Switching tabs (Apps/Photos/Files/...)
 * re-inflates fragment content well after any bounded retry window would have closed, and each
 * of those inflations is itself a layout pass - so reacting to every layout pass for the life of
 * the activity is what actually keeps the hide/front state correct, no matter what triggered the
 * change or when.
 */
object CleanUiPatch {
    private const val TAG = "MorpheXenderCleanUi"

    private val hideIds = LinkedHashSet<Int>()
    private val frontIds = LinkedHashSet<Int>()

    @JvmStatic
    fun registerHideId(id: Int) {
        hideIds.add(id)
    }

    @JvmStatic
    fun registerFrontId(id: Int) {
        frontIds.add(id)
    }

    @JvmStatic
    fun applyOnce(activity: Activity) {
        try {
            for (id in hideIds) {
                activity.findViewById<View>(id)?.visibility = View.GONE
            }
            for (id in frontIds) {
                activity.findViewById<View>(id)?.bringToFront()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "applyOnce failed", t)
        }
    }

    @JvmStatic
    fun scheduleReapply(activity: Activity) {
        applyOnce(activity)
        try {
            val decorView = activity.window?.decorView ?: return
            val vto = decorView.viewTreeObserver
            if (vto != null && vto.isAlive) {
                vto.addOnGlobalLayoutListener { applyOnce(activity) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "scheduleReapply failed", t)
        }
    }
}
