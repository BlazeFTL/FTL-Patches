package app.ftl.extension.mxplayerad

import android.app.Activity
import android.util.Log
import android.view.Menu
import android.view.View

object MeTabToolbarPatch {
    private const val TAG = "MorpheMeTabToolbar"

    @JvmStatic
    fun wireMeTabMenuItem(menu: Menu, activity: Activity) {
        try {
            val res = activity.resources
            val pkg = activity.packageName

            val itemId = res.getIdentifier("me_toolbar_action", "id", pkg)
            if (itemId == 0) return

            val actionView = menu.findItem(itemId)?.actionView ?: return

            actionView.setOnClickListener {
                val tabId = res.getIdentifier("local_tab", "id", pkg)
                if (tabId != 0) activity.findViewById<View>(tabId)?.performClick()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "wireMeTabMenuItem failed", t)
        }
    }
}
