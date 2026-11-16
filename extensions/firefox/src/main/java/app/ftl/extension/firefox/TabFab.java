package app.ftl.extension.firefox;

import android.app.Application;
import android.util.Log;

@SuppressWarnings("unused")
public final class TabFab {

    private static final float STEP_DP = 72.0f;
    private static final int END = 0;
    private static final int DOWN = 1;
    private static final int UP = 2;

    private static int endIcon;

    private TabFab() {
    }

    public static boolean endButton() {
        return ModSettings.tabNext() && ModSettings.tabEnd();
    }

    private static boolean isPage(Object page, String name) {
        return page instanceof Enum && name.equals(((Enum<?>) page).name());
    }

    // Bit per button: 1 = end, 2 = scroll down, 4 = scroll up. Buttons sit left of the stock + button.
    public static int plan(Object page) {
        boolean normal = isPage(page, "NormalTabs");
        boolean priv = isPage(page, "PrivateTabs");
        int mask = 0;
        if (normal && endButton()) mask |= 1;
        if ((normal || priv) && TabScroll.enabled()) mask |= 6;
        return mask;
    }

    public static boolean shown(int mask, int kind) {
        return (mask & (1 << kind)) != 0;
    }

    // Distance from the stock + button in dp (FAB 56 + 16 gap), used as Modifier.padding(end = ...).
    // Order left to right: up, down, end, +.
    public static float padding(int mask, int kind) {
        int end = (mask & 1) != 0 ? 1 : 0;
        if (kind == END) return STEP_DP;
        if (kind == DOWN) return STEP_DP * (1 + end);
        return STEP_DP * (2 + end);
    }

    public static Object click(int kind, Object base) {
        if (kind == END) return TabEndClick.of(base);
        return kind == UP ? TabScroll.up() : TabScroll.down();
    }

    public static int icon(int kind) {
        if (kind == UP) return TabScroll.upIcon();
        if (kind == DOWN) return TabScroll.downIcon();
        return endIcon();
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
