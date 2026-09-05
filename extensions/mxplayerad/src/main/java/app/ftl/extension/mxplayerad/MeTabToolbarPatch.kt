package app.ftl.extension.mxplayerad

import android.util.Log
import android.view.Menu
import android.view.View

object MeTabToolbarPatch {
    private const val TAG = "MorpheMeTabToolbar"

    // Must match ME_TOOLBAR_ACTION_TAG in AddMeTabMenuResourcePatch.kt.
    private const val ACTION_VIEW_TAG = "ftl_me_toolbar_action"

    @JvmStatic
    fun wireMeTabMenuItem(menu: Menu, listener: View.OnClickListener) {
        try {
            for (i in 0 until menu.size()) {
                val actionView = menu.getItem(i)?.actionView ?: continue
                if (actionView.tag == ACTION_VIEW_TAG) {
                    actionView.setOnClickListener(listener)
                    return
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "wireMeTabMenuItem failed", t)
        }
    }
}
