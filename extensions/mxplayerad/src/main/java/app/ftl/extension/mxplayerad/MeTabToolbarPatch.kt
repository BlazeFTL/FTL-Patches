package app.ftl.extension.mxplayerad

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast

// TEMPORARY diagnostics: every exit point below toasts why it stopped, so we can
// see exactly where this bails at runtime instead of guessing again. Remove the
// DEBUG/toast lines once the flow is confirmed working end to end.
private const val DEBUG = true

object MeTabToolbarPatch {
    private const val TAG = "MorpheMeTabToolbar"

    private fun debug(activity: Activity, message: String) {
        Log.d(TAG, message)
        if (DEBUG) Toast.makeText(activity, "MeTab: $message", Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun wireMeTabMenuItem(menu: Menu, activity: Activity) {
        try {
            val res = activity.resources
            val pkg = activity.packageName

            val itemId = res.getIdentifier("me_toolbar_action", "id", pkg)
            if (itemId == 0) {
                debug(activity, "id 'me_toolbar_action' not found")
                return
            }

            val item = menu.findItem(itemId)
            if (item == null) {
                debug(activity, "menu.findItem returned null")
                return
            }

            if (item.actionView != null) {
                debug(activity, "actionView already set, skipping")
                return
            }

            val layoutId = res.getIdentifier("me_toolbar_action", "layout", pkg)
            if (layoutId == 0) {
                debug(activity, "layout 'me_toolbar_action' not found")
                return
            }

            val actionView = LayoutInflater.from(activity).inflate(layoutId, null)
            if (actionView == null) {
                debug(activity, "inflate returned null")
                return
            }

            val tabId = res.getIdentifier("local_tab", "id", pkg)
            val tabView = if (tabId != 0) activity.findViewById<View>(tabId) else null
            debug(activity, "tabId=$tabId tabView=${tabView != null}")

            val iconId = res.getIdentifier("iv_me_toolbar", "id", pkg)
            val icon = if (iconId != 0) actionView.findViewById<ImageView>(iconId) else null
            val source = tabView?.let { findFirstImageView(it) }
            if (icon != null && source?.drawable != null) {
                icon.setImageDrawable(source.drawable)
                debug(activity, "icon copied from local_tab")
            } else if (icon != null) {
                // local_tab isn't a plain ImageView with a usable drawable (e.g. the
                // skin system applies it some other way) - fall back to a known-real
                // drawable name by lookup, never a hardcoded resource id.
                val fallbackId = res.getIdentifier("ic_aurora_tab_me_selected", "drawable", pkg)
                if (fallbackId != 0) {
                    icon.setImageDrawable(res.getDrawable(fallbackId, activity.theme))
                    debug(activity, "icon set from fallback drawable")
                } else {
                    debug(activity, "no icon source found (local_tab or fallback)")
                }
            }

            actionView.setOnClickListener {
                tabView?.performClick()
            }

            item.actionView = actionView
            debug(activity, "wired successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "wireMeTabMenuItem failed", t)
            if (DEBUG) Toast.makeText(activity, "MeTab: exception ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun findFirstImageView(view: View): ImageView? {
        if (view is ImageView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findFirstImageView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
