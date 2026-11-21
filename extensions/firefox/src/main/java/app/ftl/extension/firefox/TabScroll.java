package app.ftl.extension.firefox;

import android.app.Application;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class TabScroll {

    private static final String TAG = "MorpheFirefox";
    private static final int HISTORY = 6;
    private static final float FAR = 1.0e7f;

    private static final List<WeakReference<Object>> STATES = new ArrayList<>();
    private static volatile Object tray;
    private static int upIcon;
    private static int downIcon;
    private static Boolean installed;

    private TabScroll() {
    }

    public static synchronized void track(Object state) {
        if (state == null) return;
        Iterator<WeakReference<Object>> it = STATES.iterator();
        while (it.hasNext()) {
            Object known = it.next().get();
            if (known == null || known == state) it.remove();
        }
        STATES.add(new WeakReference<Object>(state));
        while (STATES.size() > HISTORY) STATES.remove(0);
    }

    public static void tray(Object state) {
        tray = state;
    }

    // ftlInstalled is generated into this class only when the Tab scroll button patch is applied.
    private static boolean installed() {
        Boolean cached = installed;
        if (cached == null) {
            try {
                TabScroll.class.getMethod("ftlInstalled");
                cached = Boolean.TRUE;
            } catch (Throwable t) {
                cached = Boolean.FALSE;
            }
            installed = cached;
        }
        return cached;
    }

    public static boolean enabled() {
        return installed() && ModSettings.tabScroll();
    }

    public static TabScrollClick up() {
        return TabScrollClick.UP;
    }

    public static TabScrollClick down() {
        return TabScrollClick.DOWN;
    }

    public static int upIcon() {
        return resolveIcon(true);
    }

    public static int downIcon() {
        return resolveIcon(false);
    }

    static void go(boolean down) {
        try {
            Set<String> ids = expectedIds();
            Object state = current(ids);
            if (state == null) return;
            scrollTo(state, down, Math.max(0, ids.size() - 1));
        } catch (Throwable t) {
            Log.e(TAG, "tab scroll failed", t);
        }
    }

    // Visible list/grid items are keyed by tab id. The state whose visible keys belong to the selected page
    // is the one on screen. Falls back to the newest state that shows anything.
    private static synchronized Object current(Set<String> ids) {
        Object fallback = null;
        for (int i = STATES.size() - 1; i >= 0; i--) {
            Object state = STATES.get(i).get();
            if (state == null) continue;
            List items = TabAccess.scrollItems(state);
            if (items == null || items.isEmpty()) continue;
            if (fallback == null) fallback = state;
            try {
                Object key = TabAccess.get(items.get(0), "getKey");
                if (key != null && ids.contains(String.valueOf(key))) return state;
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    private static Set<String> expectedIds() {
        Set<String> ids = new HashSet<>();
        try {
            Object state = tray;
            if (state == null) return ids;
            Object page = TabAccess.get(state, "getSelectedPage");
            boolean isPrivate = page instanceof Enum && "PrivateTabs".equals(((Enum) page).name());
            Object tabs = isPrivate
                ? TabAccess.get(TabAccess.get(state, "getPrivateBrowsing"), "getTabs")
                : TabAccess.get(TabAccess.get(state, "getNormalTabsState"), "getItems");
            if (tabs instanceof Iterable) {
                for (Object tab : (Iterable) tabs) ids.add((String) TabAccess.get(tab, "getId"));
            }
        } catch (Throwable t) {
            Log.e(TAG, "tab ids failed", t);
        }
        return ids;
    }

    private static void scrollTo(Object state, boolean down, int last) throws Exception {
        if (TabAccess.scrollRequest(state, down ? last : 0)) return;
        TabAccess.scrollRaw(state, down ? FAR : -FAR);
    }

    private static int resolveIcon(boolean up) {
        int cached = up ? upIcon : downIcon;
        if (cached != 0) return cached;
        try {
            Application app = (Application) Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication").invoke(null);
            String pkg = app.getPackageName();
            int id = app.getResources().getIdentifier(up ? "ftl_ic_scroll_up" : "ftl_ic_scroll_down", "drawable", pkg);
            if (id == 0) id = app.getResources().getIdentifier("mozac_ic_forward_24", "drawable", pkg);
            if (up) upIcon = id;
            else downIcon = id;
            return id;
        } catch (Throwable t) {
            Log.e(TAG, "scroll icon failed", t);
            return 0;
        }
    }
}
