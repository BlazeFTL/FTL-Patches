package app.ftl.extension.mxplayerad

import android.app.Activity
import android.util.Log
import android.view.Menu

object MeTabToolbarPatch {
    private const val TAG = "MorpheMeTabToolbar"

    @JvmStatic
    fun wireMeTabMenuItem(menu: Menu, activity: Activity, delegate: Any, navigateMethodName: String) {
        try {
            val itemId = activity.resources.getIdentifier("me_toolbar_action", "id", activity.packageName)
            if (itemId == 0) return

            val actionView = menu.findItem(itemId)?.actionView ?: return

            actionView.setOnClickListener {
                try {
                    delegate.javaClass.getMethod(navigateMethodName).invoke(delegate)
                } catch (t: Throwable) {
                    Log.e(TAG, "navigate invoke failed", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "wireMeTabMenuItem failed", t)
        }
    }
}
