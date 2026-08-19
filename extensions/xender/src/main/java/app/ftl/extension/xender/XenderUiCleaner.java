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

    // Resolved once via getIdentifier() (slow, does a resource-table lookup) and
    // reused on every retry, instead of re-resolving all 8 names up to 13x per
    // trigger. Left null until first successful resolution.
    private static volatile int[] hiddenIds;
    private static volatile int[] frontIds;

    private XenderUiCleaner() {
    }

    // Never runs on the caller's stack: onCreate/onResume/drawerEnterClick only
    // ever post a message and return immediately, so this can't add to whatever
    // else those methods do synchronously (including the app's own ad-preload
    // work, which is what the ad-source crash traces through).
    public static void applyUiCustomizations(final Activity activity) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                applyUiCustomizationsInternal(activity, 0);
            }
        });
    }

    private static void applyUiCustomizationsInternal(final Activity activity, final int attempt) {
        if (activity == null || activity.isFinishing()) return;

        try {
            Resources res = activity.getResources();
            String pkg = activity.getPackageName();

            if (hiddenIds == null) hiddenIds = resolveIds(res, pkg, HIDDEN_VIEW_NAMES);
            if (frontIds == null) frontIds = resolveIds(res, pkg, FRONT_VIEW_NAMES);

            for (int id : hiddenIds) {
                if (id == 0) continue;
                View v = activity.findViewById(id);
                if (v != null) v.setVisibility(View.GONE);
            }
            for (int id : frontIds) {
                if (id == 0) continue;
                View v = activity.findViewById(id);
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
                    applyUiCustomizationsInternal(activity, attempt + 1);
                }
            }, RETRY_DELAY_MS);
        }
    }

    private static int[] resolveIds(Resources res, String pkg, String[] names) {
        int[] ids = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            ids[i] = res.getIdentifier(names[i], "id", pkg);
        }
        return ids;
    }
}
