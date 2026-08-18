package app.ftl.extension.xender;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

@SuppressWarnings("unused")
public final class XenderCleanUi {
    private XenderCleanUi() {}

    public static void schedule(Activity activity) {
        if (activity == null) return;

        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] task = new Runnable[1];

        task[0] = new Runnable() {
            int count;

            @Override
            public void run() {
                apply(activity);
                if (count++ < 12) {
                    handler.postDelayed(this, 150L);
                }
            }
        };

        task[0].run();
    }

    private static void apply(Activity activity) {
        hide(activity, "x_main_navigation_view");
        hide(activity, "action_guide");
        hide(activity, "x_drawer_rate_item");
        hide(activity, "x_drawer_help_item");
        hide(activity, "x_drawer_about_item");

        bringToFront(activity, "connect_button");
        bringToFront(activity, "create_btn");
        bringToFront(activity, "join_btn");
    }

    private static void hide(Activity activity, String name) {
        View view = find(activity, name);
        if (view != null) view.setVisibility(View.GONE);
    }

    private static void bringToFront(Activity activity, String name) {
        View view = find(activity, name);
        if (view != null) view.bringToFront();
    }

    private static View find(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(
            name, "id", activity.getPackageName()
        );
        return id != 0 ? activity.findViewById(id) : null;
    }
}
