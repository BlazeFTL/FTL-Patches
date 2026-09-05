package app.ftl.extension.mxplayerad

import android.app.Activity
import android.util.Log
import android.view.Menu
import android.view.View

object MeTabToolbarPatch {
    private const val TAG = "MorpheMeTabToolbar"

    @JvmStatic
    fun wireMeTabMenuItem(menu: Menu, activity: Activity, listener: View.OnClickListener) {
        try {
            val itemId = activity.resources.getIdentifier("me_toolbar_action", "id", activity.packageName)
            if (itemId == 0) return

            val actionView = menu.findItem(itemId)?.actionView ?: return
            actionView.setOnClickListener(listener)
        } catch (t: Throwable) {
            Log.e(TAG, "wireMeTabMenuItem failed", t)
        }
    }
}
