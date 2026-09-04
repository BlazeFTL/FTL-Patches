package app.ftl.extension.mxplayerad

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

object MeTabToolbarPatch {
    private const val TAG = "MorpheMeTabToolbar"

    @JvmStatic
    fun wireMeTabMenuItem(menu: Menu, activity: Activity) {
        try {
            val res = activity.resources
            val pkg = activity.packageName

            val itemId = res.getIdentifier("me_toolbar_action", "id", pkg)
            if (itemId == 0) return

            val item = menu.findItem(itemId) ?: return

            // This app builds its own action-view menu items by inflating and
            // attaching the view in code rather than via app:actionLayout, so do
            // the same here. Guard on an already-set action view so repeated
            // onPrepareOptionsMenu passes don't re-inflate/re-wire every time.
            if (item.actionView != null) return

            val layoutId = res.getIdentifier("me_toolbar_action", "layout", pkg)
            if (layoutId == 0) return

            val actionView = LayoutInflater.from(activity).inflate(layoutId, null) ?: return

            val tabId = res.getIdentifier("local_tab", "id", pkg)
            val tabView = if (tabId != 0) activity.findViewById<View>(tabId) else null

            // Reuse whatever icon the existing Me tab already shows, rather than
            // shipping a guessed drawable name that may not exist in this build.
            val iconId = res.getIdentifier("iv_me_toolbar", "id", pkg)
            val icon = if (iconId != 0) actionView.findViewById<ImageView>(iconId) else null
            val source = tabView?.let { findFirstImageView(it) }
            if (icon != null && source?.drawable != null) {
                icon.setImageDrawable(source.drawable)
            }

            actionView.setOnClickListener {
                tabView?.performClick()
            }

            item.actionView = actionView
        } catch (t: Throwable) {
            Log.e(TAG, "wireMeTabMenuItem failed", t)
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
