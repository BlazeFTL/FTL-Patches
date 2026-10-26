package app.ftl.extension.mobilism;

import java.util.Iterator;
import java.util.List;

public final class HideDiscussions {
    private HideDiscussions() {
    }

    public static void filterCategories(List<?> list) {
        if (list == null) {
            return;
        }
        boolean discussions = ModFeatures.isOn(ModFeatures.HIDE_RECENT_DISCUSSIONS);
        boolean reviews = ModFeatures.isOn(ModFeatures.HIDE_REVIEWS);
        boolean contests = ModFeatures.isOn(ModFeatures.HIDE_CONTESTS);
        if (!discussions && !reviews && !contests) {
            return;
        }
        try {
            Iterator<?> it = list.iterator();
            while (it.hasNext()) {
                String name = nameOf(it.next());
                if (name == null) {
                    continue;
                }
                name = name.trim();
                if ((discussions && name.equalsIgnoreCase("Recent discussions"))
                        || (reviews && name.equalsIgnoreCase("Reviews"))
                        || (contests && name.equalsIgnoreCase("Contests"))) {
                    it.remove();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String nameOf(Object category) {
        try {
            Object n = category.getClass().getMethod("getName").invoke(category);
            return n instanceof String ? (String) n : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
