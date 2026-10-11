package app.ftl.extension.firefox;

import android.app.Application;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class TabScroll {

    private static final String TAG = "MorpheFirefox";
    private static final int HISTORY = 6;
    private static final float FAR = 1.0e7f;

    private static final List<WeakReference<Object>> STATES = new ArrayList<>();
    private static volatile Object tray;
    private static int upIcon;
    private static int downIcon;

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

    public static boolean enabled() {
        return ModSettings.tabScroll();
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
            Object state = current();
            if (state == null) return;
            scrollTo(state, down ? Math.max(0, total(state) - 1) : 0);
        } catch (Throwable t) {
            Log.e(TAG, "tab scroll failed", t);
        }
    }

    private static synchronized Object current() {
        int expected = expectedCount();
        Object best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int i = STATES.size() - 1; i >= 0; i--) {
            Object state = STATES.get(i).get();
            if (state == null) continue;
            int total;
            try {
                total = total(state);
            } catch (Throwable t) {
                continue;
            }
            if (total <= 0) continue;
            int score = expected >= 0 ? Math.abs(total - expected) : 0;
            if (best == null || score < bestScore) {
                best = state;
                bestScore = score;
            }
        }
        return best;
    }

    private static int expectedCount() {
        try {
            Object state = tray;
            if (state == null) return -1;
            Object page = read(state, "selectedPage");
            boolean isPrivate = page instanceof Enum && "PrivateTabs".equals(((Enum) page).name());
            Object tabs = read(state, isPrivate ? "privateTabs" : "normalTabs");
            return tabs instanceof Collection ? ((Collection) tabs).size() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int total(Object state) throws Exception {
        Object info = call(state, "getLayoutInfo");
        return ((Number) call(info, "getTotalItemsCount")).intValue();
    }

    private static void scrollTo(Object state, int index) throws Exception {
        try {
            Method request = state.getClass().getMethod("requestScrollToItem", int.class, int.class);
            request.setAccessible(true);
            request.invoke(state, index, 0);
            return;
        } catch (NoSuchMethodException ignored) {
        }
        Method raw = Class.forName("androidx.compose.foundation.gestures.ScrollableState", false,
            state.getClass().getClassLoader()).getMethod("dispatchRawDelta", float.class);
        raw.invoke(state, index <= 0 ? -FAR : FAR);
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

    private static Object call(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object read(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
