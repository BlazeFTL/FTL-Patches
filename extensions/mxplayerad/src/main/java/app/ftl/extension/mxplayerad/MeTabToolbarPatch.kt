package app.ftl.extension.mxplayerad

import android.app.Activity
import android.util.Log
import android.view.Menu
import android.widget.Toast

// TEMPORARY diagnostics: every exit point below toasts why it stopped, so we can see
// exactly where this bails at runtime instead of guessing again. Remove the DEBUG/toast
// lines once the click is confirmed navigating end to end.
private const val DEBUG = true

object MeTabToolbarPatch {
    private const val TAG = "MorpheMeTabToolbar"

    private fun debug(activity: Activity, message: String) {
        Log.d(TAG, message)
        if (DEBUG) Toast.makeText(activity, "MeTab: $message", Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun wireMeTabMenuItem(menu: Menu, activity: Activity, delegate: Any, navigateMethodName: String) {
        try {
            val itemId = activity.resources.getIdentifier("me_toolbar_action", "id", activity.packageName)
            if (itemId == 0) {
                debug(activity, "id 'me_toolbar_action' not found")
                return
            }

            val item = menu.findItem(itemId)
            if (item == null) {
                debug(activity, "menu.findItem returned null")
                return
            }

            val actionView = item.actionView
            if (actionView == null) {
                debug(activity, "actionView is null")
                return
            }

            actionView.setOnClickListener {
                try {
                    val method = delegate.javaClass.getMethod(navigateMethodName)
                    method.invoke(delegate)
                    Toast.makeText(activity, "MeTab: clicked, invoked $navigateMethodName OK", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    Log.e(TAG, "navigate invoke failed", t)
                    Toast.makeText(activity, "MeTab: click invoke failed: ${t.javaClass.simpleName}: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }

            debug(activity, "wired OK (delegate=${delegate.javaClass.name})")
        } catch (t: Throwable) {
            Log.e(TAG, "wireMeTabMenuItem failed", t)
            if (DEBUG) Toast.makeText(activity, "MeTab: wire failed: ${t.javaClass.simpleName}: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }
}
