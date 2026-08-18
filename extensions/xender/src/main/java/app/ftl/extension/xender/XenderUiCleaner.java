package app.ftl.extension.xender;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

@SuppressWarnings("unused")
public final class XenderUiCleaner {

    private static final String TAG = "MorpheXender";

    // Resolved via Resources.getIdentifier() instead of R$id field refs, so this
    // survives even if R$id's field layout or the app's obfuscation changes —
    // only the resource entry names (from the app's own layout XML) have to hold.
    private static final String[] HIDDEN_VIEW_NAMES = {
        "x_main_navigation_view",
        "action_guide",
        "x_drawer_rate_item",
        "x_drawer_help_item",
        "x_drawer_about_item",
    };

    private static final String[] FRONT_VIEW_NAMES = {
        "connect_button",
        "create_btn",
        "join_btn",
    };

    private static final int MAX_RETRIES = 12;
    private static final long RETRY_DELAY_MS = 150L;

    private XenderUiCleaner() {
    }

    public static void applyUiCustomizations(Activity activity) {
        applyUiCustomizations(activity, 0);
    }

    private static void applyUiCustomizations(final Activity activity, final int attempt) {
        if (activity == null || activity.isFinishing()) return;

        try {
            Resources res = activity.getResources();
            String pkg = activity.getPackageName();

            for (String name : HIDDEN_VIEW_NAMES) {
                View v = findViewByName(activity, res, pkg, name);
                if (v != null) v.setVisibility(View.GONE);
            }
            for (String name : FRONT_VIEW_NAMES) {
                View v = findViewByName(activity, res, pkg, name);
                if (v != null) v.bringToFront();
            }
        } catch (Throwable t) {
            Log.e(TAG, "applyUiCustomizations failed", t);
        }

        // Some of these views are inflated lazily (e.g. drawer contents), so this
        // keeps retrying for ~1.8s after each trigger instead of running once.
        if (attempt < MAX_RETRIES) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    applyUiCustomizations(activity, attempt + 1);
                }
            }, RETRY_DELAY_MS);
        }
    }

    private static View findViewByName(Activity activity, Resources res, String pkg, String name) {
        int id = res.getIdentifier(name, "id", pkg);
        if (id == 0) return null;
        return activity.findViewById(id);
    }
}
