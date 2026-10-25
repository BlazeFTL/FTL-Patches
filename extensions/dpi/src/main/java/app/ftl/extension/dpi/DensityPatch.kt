package app.ftl.extension.dpi

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

object DensityPatch {
    private const val TAG = "MorpheDpi"
    private const val DEFAULT_PERCENT = 100
    private const val MIN_PERCENT = 25
    private const val MAX_PERCENT = 300
    private const val MIN_DPI = 96
    private const val MAX_DPI = 640
    private const val BASELINE_DPI = 160f
    private const val SCAN_INTERVAL_MS = 120L
    private const val HEAL_DELAY_MS = 60L
    private const val MAX_FIXES_PER_SECOND = 6

    @Volatile
    private var targetDpi = 0

    @Volatile private var percent = DEFAULT_PERCENT

    @Volatile
    private var initialized = false

    private val activeActivities =
        Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    private val watchers = WeakHashMap<Activity, Watch>()

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var dumpBudget = 200

    private val pendingRefresh =
        Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    @JvmStatic
    fun setPercent(value: Int) { percent = value }

    @JvmStatic
    fun init(application: Application) {
        if (initialized) return
        try {
            register(application, percent)
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
        }
    }

    @JvmStatic
    fun init(activity: Activity) {
        init(activity.application)
        try {
            forceDensity(activity, "init-activity")
            activeActivities.add(activity)
        } catch (t: Throwable) {
            Log.e(TAG, "init(activity) failed", t)
        }
    }

    /**
     * Injected at the head of every manifest activity's onConfigurationChanged().
     * By the time this runs the system has already swapped/reset the activity's
     * Resources to the real density, so correcting here beats whatever the app's own
     * handler does (e.g. rebuilding toolbars) after it.
     */
    @JvmStatic
    fun onConfigChanged(activity: Activity) {
        if (!initialized || targetDpi == 0) return
        try {
            forceDensity(activity, "activity-config")
            scheduleRefresh(activity, HEAL_DELAY_MS)
        } catch (t: Throwable) {
            Log.e(TAG, "onConfigChanged failed", t)
        }
    }

    private fun register(application: Application, percent: Int) {
        initialized = true

        val clampedPercent = if (percent in MIN_PERCENT..MAX_PERCENT) percent else DEFAULT_PERCENT
        val originalDpi = application.resources.displayMetrics.densityDpi
        val scaled = Math.round(originalDpi * clampedPercent / 100f)
        targetDpi = Math.max(MIN_DPI, Math.min(MAX_DPI, scaled))

        Log.d(TAG, "register original=$originalDpi target=$targetDpi percent=$clampedPercent")

        if (targetDpi == originalDpi) return

        applyTo(application.resources, "register")

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                forceDensity(activity, "pre-created")
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                forceDensity(activity, "created")
                activeActivities.add(activity)
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityPreStarted(activity: Activity) {
                correct(activity, "pre-started")
            }

            override fun onActivityPreResumed(activity: Activity) {
                correct(activity, "pre-resumed")
            }

            override fun onActivityResumed(activity: Activity) {
                correct(activity, "resumed")
                attachWatchdog(activity)
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                activeActivities.remove(activity)
                pendingRefresh.remove(activity)
                detachWatchdog(activity)
            }
        })

        application.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                applyTo(application.resources, "app-config")
                for (a in ArrayList(activeActivities)) {
                    forceDensity(a, "app-config")
                    scheduleRefresh(a, HEAL_DELAY_MS)
                }
            }

            override fun onLowMemory() {}
            override fun onTrimMemory(level: Int) {}
        })
    }

    private class Watch(activity: Activity) : ViewTreeObserver.OnPreDrawListener {
        private val ref = WeakReference(activity)
        private var windowStart = 0L
        private var fixes = 0
        private var lastScan = 0L

        override fun onPreDraw(): Boolean {
            val activity = ref.get() ?: return true
            return try {
                evaluate(activity)
            } catch (t: Throwable) {
                Log.e(TAG, "watchdog failed", t)
                true
            }
        }

        // Runs after layout, before draw. Returns false to cancel the frame so a wrong-density
        // frame is never shown; refresh() schedules the re-measure that replaces it.
        private fun evaluate(activity: Activity): Boolean {
            val now = SystemClock.uptimeMillis()
            var fixed = false
            var relayout = false

            if (!isDensityOk(activity.resources)) {
                fixed = forceDensity(activity, "predraw")
            } else if (now - lastScan >= SCAN_INTERVAL_MS) {
                lastScan = now
                val decor = activity.window?.peekDecorView()
                if (decor != null && scanTree(decor, IdentityHashMap())) {
                    fixed = true
                    Log.d(TAG, "predraw: stray resources fixed in ${activity.javaClass.simpleName}")
                }
            }

            if (!fixed) {
                val decor = activity.window?.peekDecorView()
                if (decor != null && safeFixToolbars(activity, decor)) relayout = true
            }

            if (!fixed && !relayout) return true

            if (now - windowStart > 1000L) {
                windowStart = now
                fixes = 0
            }
            if (fixes >= MAX_FIXES_PER_SECOND) return true
            fixes++

            if (fixed) refresh(activity)
            return false
        }
    }

    private fun actionBarSizePx(activity: Activity): Int {
        val metrics = activity.resources.displayMetrics
        val value = TypedValue()
        if (activity.theme.resolveAttribute(android.R.attr.actionBarSize, value, true)) {
            val px = TypedValue.complexToDimensionPixelSize(value.data, metrics)
            if (px > 0) return px
        }
        return Math.round(56f * metrics.density)
    }

    // No ==/!= on objects, endsWith() etc. in here: the host app's shrunk Kotlin runtime can
    // lack kotlin.jvm.internal.Intrinsics.areEqual (NoSuchMethodError seen on MX Player).
    private fun isToolbar(view: View): Boolean {
        var c: Class<*>? = view.javaClass
        while (c != null && c !== View::class.java) {
            if (nameEndsWithToolbar(c.name)) return true
            c = c.superclass
        }
        return false
    }

    private fun nameEndsWithToolbar(name: String): Boolean {
        val suffix = ".Toolbar"
        val offset = name.length - suffix.length
        if (offset < 0) return false
        for (i in 0 until suffix.length) {
            if (name[offset + i] != suffix[i]) return false
        }
        return true
    }

    @Volatile
    private var toolbarFixBroken = false

    private fun safeFixToolbars(activity: Activity, decor: View): Boolean {
        if (toolbarFixBroken) return false
        return try {
            fixToolbars(decor, actionBarSizePx(activity), 0, 0)
        } catch (t: Throwable) {
            toolbarFixBroken = true
            Log.e(TAG, "toolbar fix disabled", t)
            false
        }
    }

    // Window insets (status bar / cutout) are real pixels and do not follow the patched
    // density, while a toolbar's fixed height is dp-based. Toolbar = top inset padding + dp
    // height, so lowering density squeezes the content area to nothing and the bar clips its
    // own icons. Guarantee room for one action bar below the inset padding. Uses the measured
    // height, not LayoutParams: custom toolbars (MX's MXImmersiveToolbar) add the status bar
    // height themselves during measure, so lp.height alone over-corrects and pushes content down.
    private fun fixToolbars(view: View, need: Int, y: Int, depth: Int): Boolean {
        if (depth > 14 || y > 400 || view.visibility == View.GONE || view !is ViewGroup) return false
        var fixed = false
        val lp = view.layoutParams
        if (lp != null && lp.height > 0 && view.paddingTop > 0 && !view.isLayoutRequested && isToolbar(view)) {
            val minHeight = view.paddingTop + need
            val actual = view.measuredHeight
            if (actual > 0 && actual < minHeight - 1) {
                val newHeight = lp.height + (minHeight - actual)
                Log.d(TAG, "toolbar height ${lp.height} -> $newHeight (measured=$actual pad=${view.paddingTop} bar=$need)")
                lp.height = newHeight
                view.layoutParams = lp
                fixed = true
            }
        }
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (fixToolbars(child, need, y + child.top - view.scrollY, depth + 1)) fixed = true
        }
        return fixed
    }

    private fun attachWatchdog(activity: Activity) {
        if (watchers.containsKey(activity)) return
        val decor = try { activity.window?.decorView } catch (t: Throwable) { null } ?: return
        val watch = Watch(activity)
        try {
            decor.viewTreeObserver.addOnPreDrawListener(watch)
            watchers[activity] = watch
            decor.postDelayed({ dumpTop(activity, decor, "attach+500") }, 500L)
        } catch (t: Throwable) {
            Log.e(TAG, "attachWatchdog failed", t)
        }
    }

    private fun detachWatchdog(activity: Activity) {
        val watch = watchers.remove(activity) ?: return
        try {
            val observer = activity.window?.peekDecorView()?.viewTreeObserver
            if (observer != null && observer.isAlive) observer.removeOnPreDrawListener(watch)
        } catch (t: Throwable) {
            Log.e(TAG, "detachWatchdog failed", t)
        }
    }

    private fun isDensityOk(resources: Resources): Boolean =
        resources.displayMetrics.densityDpi == targetDpi && resources.configuration.densityDpi == targetDpi

    private fun forceDensity(activity: Activity, reason: String): Boolean {
        var changed = applyTo(activity.resources, reason)
        val base: Context? = activity.baseContext
        if (base != null && base.resources !== activity.resources) {
            if (applyTo(base.resources, reason)) changed = true
        }
        val app = activity.application
        if (app != null && applyTo(app.resources, reason)) changed = true
        return changed
    }

    private fun correct(activity: Activity, reason: String) {
        if (forceDensity(activity, reason)) scheduleRefresh(activity, 0L)
    }

    private fun scheduleRefresh(activity: Activity, delayMs: Long) {
        if (!pendingRefresh.add(activity)) return
        mainHandler.postDelayed({
            pendingRefresh.remove(activity)
            try {
                if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                forceDensity(activity, "refresh")
                refresh(activity)
            } catch (t: Throwable) {
                Log.e(TAG, "refresh failed", t)
            }
        }, delayMs)
    }

    // View.measure() reuses its cached result when the MeasureSpec is unchanged, and a
    // plain decor.requestLayout() only flags the decor and its ancestors, so dp-sized
    // children (toolbars, rows) kept the sizes measured at the wrong density. Flagging
    // every view forces the whole tree to re-measure.
    private fun refresh(activity: Activity) {
        val decor = activity.window?.peekDecorView() ?: return
        try {
            decor.dispatchConfigurationChanged(activity.resources.configuration)
        } catch (t: Throwable) {
            Log.e(TAG, "dispatchConfigurationChanged failed", t)
        }
        forceLayoutTree(decor)
        decor.requestApplyInsets()
        decor.requestLayout()
        decor.invalidate()
        Log.d(TAG, "refresh ${activity.javaClass.simpleName}")
        decor.postDelayed({ dumpTop(activity, decor, "refresh+400") }, 400L)
    }

    // Debug aid: logs the views that intersect the top strip of the screen, with their
    // bounds in window coordinates, so a clipped header can be matched to the view doing it.
    private fun dumpTop(activity: Activity, decor: View, reason: String) {
        if (dumpBudget <= 0) return
        dumpBudget--
        try {
            val lines = ArrayList<String>()
            val res = activity.resources
            Log.d(TAG, "dump[$reason] ${activity.javaClass.simpleName} dpi=${res.displayMetrics.densityDpi}/${res.configuration.densityDpi} " +
                "wdp=${res.configuration.screenWidthDp} hdp=${res.configuration.screenHeightDp} decor=${decor.width}x${decor.height}")
            dumpView(decor, 0, lines)
            for (l in lines) Log.d(TAG, l)
        } catch (t: Throwable) {
            Log.e(TAG, "dump failed", t)
        }
    }

    private fun dumpView(view: View, depth: Int, out: MutableList<String>) {
        if (out.size >= 60 || depth > 12) return
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        if (loc[1] > 330 || view.visibility != View.VISIBLE) return
        val id = try {
            if (view.id != View.NO_ID) view.resources.getResourceEntryName(view.id) else "-"
        } catch (t: Throwable) { "?" }
        val lp = view.layoutParams
        val clip = if (view is ViewGroup) " clipC=${view.clipChildren} clipP=${view.clipToPadding}" else ""
        val clipB = view.clipBounds?.let { " clipBounds=$it" } ?: ""
        out.add(
            "  ".repeat(depth) + "${view.javaClass.simpleName}#$id " +
                "[${loc[0]},${loc[1]} ${view.width}x${view.height}] mH=${view.measuredHeight} " +
                "lp=${lp?.width}x${lp?.height} pad=${view.paddingTop}/${view.paddingBottom} ty=${view.translationY} minH=${view.minimumHeight} sy=${view.scrollY}$clip$clipB"
        )
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) dumpView(view.getChildAt(i), depth + 1, out)
        }
    }

    private fun forceLayoutTree(view: View) {
        view.forceLayout()
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) forceLayoutTree(view.getChildAt(i))
        }
    }

    // Views keep the Resources instance they were inflated with. Anything that built its own
    // (createConfigurationContext, dialogs, override-config wrappers) is not reachable from
    // Activity.getResources(), so walk the tree and correct those too.
    private fun scanTree(view: View, seen: IdentityHashMap<Resources, Boolean>): Boolean {
        var fixed = false
        val res = view.resources
        if (res != null && seen.put(res, true) == null && applyTo(res, "view")) fixed = true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (scanTree(view.getChildAt(i), seen)) fixed = true
            }
        }
        return fixed
    }

    private fun scaleDp(value: Int, fromDpi: Int): Int =
        if (value <= 0 || fromDpi <= 0) value else Math.round(value * fromDpi.toFloat() / targetDpi)

    private fun applyTo(resources: Resources, reason: String): Boolean {
        if (isDensityOk(resources)) return false

        val current = resources.configuration
        val fromDpi = if (current.densityDpi > 0) current.densityDpi else resources.displayMetrics.densityDpi

        // Must be a copy: handing the live Configuration back to updateConfiguration() makes
        // updateFrom() see "no change", so drawable/dimen caches built at the old density
        // are never flushed.
        val configuration = Configuration(current)
        configuration.densityDpi = targetDpi
        if (fromDpi != targetDpi) {
            configuration.screenWidthDp = scaleDp(current.screenWidthDp, fromDpi)
            configuration.screenHeightDp = scaleDp(current.screenHeightDp, fromDpi)
            configuration.smallestScreenWidthDp = scaleDp(current.smallestScreenWidthDp, fromDpi)
        }

        val metrics = DisplayMetrics()
        metrics.setTo(resources.displayMetrics)
        val scale = targetDpi / BASELINE_DPI
        val fontScale = if (configuration.fontScale == 0f) 1f else configuration.fontScale
        metrics.densityDpi = targetDpi
        metrics.density = scale
        @Suppress("DEPRECATION")
        run { metrics.scaledDensity = scale * fontScale }

        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, metrics)

        val ok = isDensityOk(resources)
        Log.d(TAG, "applyTo $reason dpi $fromDpi -> $targetDpi res=${System.identityHashCode(resources)} ok=$ok")
        return ok
    }
}
