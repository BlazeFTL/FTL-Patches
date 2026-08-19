package app.ftl.extension.xender

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View

/**
 * Ids are registered from the patch side via sget on the app's own R$id fields (compile-time
 * stable, real resource field names) - never via Resources.getIdentifier(name, ...), which can
 * return 0 if the resource name string gets stripped by resource shrinking even though the
 * numeric id itself still works fine via direct field access.
 */
object CleanUiPatch {
    private const val TAG = "MorpheXenderCleanUi"
    private const val MAX_RETRIES = 12
    private const val RETRY_DELAY_MS = 150L

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
        retry(activity, 0)
    }

    private fun retry(activity: Activity, attempt: Int) {
        if (attempt >= MAX_RETRIES) return
        Handler(Looper.getMainLooper()).postDelayed({
            applyOnce(activity)
            retry(activity, attempt + 1)
        }, RETRY_DELAY_MS)
    }
}
