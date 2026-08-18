package app.ftl.extension.xender

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View

object CleanUiPatch {
    private const val TAG = "MorpheXenderCleanUi"
    private const val MAX_RETRIES = 12
    private const val RETRY_DELAY_MS = 150L

    private val HIDE_IDS = arrayOf(
        "x_main_navigation_view",
        "action_guide",
        "x_drawer_rate_item",
        "x_drawer_help_item",
        "x_drawer_about_item",
    )

    private val FRONT_IDS = arrayOf(
        "connect_button",
        "create_btn",
        "join_btn",
    )

    @JvmStatic
    fun applyOnce(activity: Activity) {
        try {
            val res = activity.resources
            val pkg = activity.packageName

            for (name in HIDE_IDS) {
                val id = res.getIdentifier(name, "id", pkg)
                if (id != 0) activity.findViewById<View>(id)?.visibility = View.GONE
            }

            for (name in FRONT_IDS) {
                val id = res.getIdentifier(name, "id", pkg)
                if (id != 0) activity.findViewById<View>(id)?.bringToFront()
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
