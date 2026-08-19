package app.ftl.extension.xender;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ids are registered from the patch side via sget on the app's own R$id fields (compile-time
 * stable, real resource field names) - never via Resources.getIdentifier(name, ...), which can
 * return 0 if the resource name string gets stripped by resource shrinking even though the
 * numeric id itself still works fine via direct field access.
 *
 * Re-apply is driven by a ViewTreeObserver.OnGlobalLayoutListener attached once to the decor
 * view, rather than a fixed-count/fixed-delay retry loop. Switching tabs (Apps/Photos/Files/...)
 * re-inflates fragment content well after any bounded retry window would have closed, and each
 * of those inflations is itself a layout pass - so reacting to every layout pass for the life of
 * the activity is what actually keeps the hide/front state correct, no matter what triggered the
 * change or when.
 */
public final class CleanUiPatch {

    private static final String TAG = "MorpheXenderCleanUi";

    private static final Set<Integer> hideIds = new LinkedHashSet<>();
    private static final Set<Integer> frontIds = new LinkedHashSet<>();

    private CleanUiPatch() {
    }

    public static void registerHideId(int id) {
        hideIds.add(id);
    }

    public static void registerFrontId(int id) {
        frontIds.add(id);
    }

    // Strips any CoordinatorLayout scroll-hide Behavior (e.g. a hide-on-scroll
    // bottom-bar behavior) from a view, so nothing can re-hide it once we've made
    // it visible/brought it to front. No-op if the view isn't hosted in a
    // CoordinatorLayout or has no Behavior attached.
    private static void disableScrollBehavior(View view) {
        ViewGroupLayoutParamsHolder holder = new ViewGroupLayoutParamsHolder(view.getLayoutParams());
        if (holder.params instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) holder.params;
            if (params.getBehavior() != null) {
                params.setBehavior(null);
                view.setLayoutParams(params);
            }
        }
    }

    // Tiny holder just to keep the instanceof/cast readable above.
    private static final class ViewGroupLayoutParamsHolder {
        final android.view.ViewGroup.LayoutParams params;
        ViewGroupLayoutParamsHolder(android.view.ViewGroup.LayoutParams params) {
            this.params = params;
        }
    }

    public static void applyOnce(Activity activity) {
        try {
            for (int id : hideIds) {
                View v = activity.findViewById(id);
                if (v != null) {
                    v.setVisibility(View.GONE);
                    disableScrollBehavior(v);
                }
            }
            for (int id : frontIds) {
                View v = activity.findViewById(id);
                if (v != null) {
                    v.bringToFront();
                    disableScrollBehavior(v);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "applyOnce failed", t);
        }
    }

    public static void scheduleReapply(final Activity activity) {
        applyOnce(activity);
        try {
            if (activity.getWindow() == null) return;
            View decorView = activity.getWindow().getDecorView();
            final ViewTreeObserver vto = decorView.getViewTreeObserver();
            if (vto != null && vto.isAlive()) {
                vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        applyOnce(activity);
                    }
                });
            }
        } catch (Throwable t) {
            Log.e(TAG, "scheduleReapply failed", t);
        }
    }
}
