package app.ftl.extension.firefox;

import android.app.Application;
import android.util.Log;

@SuppressWarnings("unused")
public final class TabFab {

    private static final float STEP_DP = 72.0f;

    private static int endIcon;

    private TabFab() {
    }

    public static boolean endButton() {
        return ModSettings.tabNext() && ModSettings.tabEnd();
    }

    // Distance from the stock + button, in dp (FAB 56 + 16 gap). Added to Modifier.padding(end = ...).
    public static float endPadding() {
        return STEP_DP;
    }

    public static int endIcon() {
        if (endIcon != 0) return endIcon;
        try {
            Application app = (Application) Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication").invoke(null);
            String pkg = app.getPackageName();
            int id = app.getResources().getIdentifier("ftl_ic_tab_add_end", "drawable", pkg);
            if (id == 0) id = app.getResources().getIdentifier("mozac_ic_forward_24", "drawable", pkg);
            endIcon = id;
        } catch (Throwable t) {
            Log.e("MorpheFirefox", "end icon failed", t);
        }
        return endIcon;
    }
}
