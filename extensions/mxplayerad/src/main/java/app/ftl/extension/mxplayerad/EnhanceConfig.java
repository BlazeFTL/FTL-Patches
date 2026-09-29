package app.ftl.extension.mxplayerad;

import android.content.SharedPreferences;

public final class EnhanceConfig {
    private static final float MAX_LEVEL = 0.18f;
    private static final String NEW_BADGE_KEY = "KEY_PLAYER_MENU_ENHANCE_NEW";

    private EnhanceConfig() {}

    public static boolean sliderOn() {
        return ModSettings.get("smart_enhance_slider");
    }

    public static float alwaysLevel() {
        if (!ModSettings.get("smart_enhance_always_on")) return 0f;
        int pct = ModSettings.getInt("smart_enhance_default_pct");
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;
        return pct / 100f * MAX_LEVEL;
    }

    public static void markNewSeenTrue(Object prefs) {
        mark(prefs, true);
    }

    public static void markNewSeenFalse(Object prefs) {
        mark(prefs, false);
    }

    private static void mark(Object prefs, boolean value) {
        if (prefs instanceof SharedPreferences) {
            ((SharedPreferences) prefs).edit().putBoolean(NEW_BADGE_KEY, value).apply();
        }
    }
}
