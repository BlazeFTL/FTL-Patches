package app.ftl.extension.mobilism;

public final class ModFeatures {
    public static final String HIDE_PREMIUM_HEADER = "hide_premium_header";
    public static final String HIDE_PREMIUM_DOWNLOAD = "hide_premium_download";
    public static final String HIDE_RECENT_DISCUSSIONS = "hide_recent_discussions";
    public static final String HIDE_REVIEWS = "hide_reviews";
    public static final String HIDE_CONTESTS = "hide_contests";
    public static final String OPEN_LATEST = "open_latest";
    public static final String COPY_LINKS = "copy_links";

    public static final String PREMIUM = "premium";
    public static final String DISCUSSIONS = "discussions";
    public static final String LATEST = "latest";
    public static final String LINKS = "links";

    private static final String PRESENT = "present_";

    private ModFeatures() {
    }

    public static boolean present(String feature) {
        return ModPrefs.getBoolean(PRESENT + feature, false);
    }

    private static void mark(String feature) {
        if (!ModPrefs.getBoolean(PRESENT + feature, false)) {
            ModPrefs.setBoolean(PRESENT + feature, true);
        }
    }

    public static void markPremium() {
        mark(PREMIUM);
    }

    public static void markDiscussions() {
        mark(DISCUSSIONS);
    }

    public static void markLatest() {
        mark(LATEST);
    }

    public static void markLinks() {
        mark(LINKS);
    }

    public static boolean isOn(String key) {
        return ModPrefs.getBoolean(key, true);
    }
}
