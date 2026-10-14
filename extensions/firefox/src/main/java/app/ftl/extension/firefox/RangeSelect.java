package app.ftl.extension.firefox;

import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class RangeSelect {

    private static final String TAG = "MorpheFirefox";
    private static final long ARMED = Long.MAX_VALUE;
    private static final long GRACE_MS = 300L;
    private static final String ADD_SELECT = "org.mozilla.fenix.tabstray.redux.action.TabsTrayAction$AddSelectTab";
    private static final String LIST_STATE = "androidx.compose.foundation.lazy.LazyListState";
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final RangeSelectNoOp NOOP = new RangeSelectNoOp();

    private static volatile long suppressUntil;
    private static volatile Constructor<?> addSelect;

    private RangeSelect() {
    }

    public static boolean longPress(Object lambda, Object item) {
        if (!ModSettings.rangeSelect()) return false;
        boolean selecting = false;
        try {
            Object store = store(lambda);
            Object state = call(store, "getState");
            Set selected = (Set) call(read(state, "mode"), "getSelectedTabs");
            if (selected == null || selected.isEmpty()) return false;
            selecting = true;
            Object page = read(state, "selectedPage");
            if (!(page instanceof Enum) || !"NormalTabs".equals(((Enum) page).name())) return true;
            select(store, state, selected, item);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "range select failed", t);
            return selecting;
        }
    }

    public static boolean consume() {
        long until = suppressUntil;
        if (until > 0L && SystemClock.uptimeMillis() < until) {
            suppressUntil = 0L;
            return true;
        }
        return false;
    }

    static void endGesture() {
        if (suppressUntil == ARMED) suppressUntil = SystemClock.uptimeMillis() + GRACE_MS;
    }

    public static Object noop() {
        return NOOP;
    }

    public static Object start(Object gesture) {
        Object listState = null;
        Object state = null;
        try {
            for (Field field : gesture.getClass().getDeclaredFields()) {
                if (field.getType().isPrimitive()) continue;
                field.setAccessible(true);
                Object value = field.get(gesture);
                if (LIST_STATE.equals(field.getType().getName())) listState = value;
                else state = value;
            }
        } catch (Throwable t) {
            Log.e(TAG, "range select start failed", t);
        }
        return new RangeSelectStart(listState, state);
    }

    private static void select(Object store, Object state, Set selected, Object item) throws Exception {
        HashSet<String> chosen = new HashSet<>();
        Object last = null;
        for (Object tab : selected) {
            last = tab;
            chosen.add((String) call(tab, "getId"));
        }
        String anchorId = (String) call(last, "getId");
        String itemId = (String) call(item, "getId");
        List items = (List) read(read(state, "normalTabsState"), "items");
        int a = -1;
        int b = -1;
        for (int i = 0, n = items.size(); i < n; i++) {
            String id = (String) call(items.get(i), "getId");
            if (a < 0 && anchorId.equals(id)) a = i;
            if (b < 0 && itemId.equals(id)) b = i;
        }
        if (a < 0 || b < 0) return;

        Constructor<?> constructor = addSelect(store);
        Class<?> tabType = constructor.getParameterTypes()[0];
        int step = a <= b ? 1 : -1;
        for (int i = a; ; i += step) {
            Object current = items.get(i);
            if (tabType.isInstance(current) && !chosen.contains((String) call(current, "getId"))) {
                dispatch(store, constructor.newInstance(current));
            }
            if (i == b) break;
        }
        suppressUntil = ARMED;
    }

    private static Constructor<?> addSelect(Object store) throws Exception {
        Constructor<?> cached = addSelect;
        if (cached != null) return cached;
        Class<?> type = Class.forName(ADD_SELECT, true, store.getClass().getClassLoader());
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length == 1) {
                constructor.setAccessible(true);
                addSelect = constructor;
                return constructor;
            }
        }
        throw new NoSuchMethodException("AddSelectTab constructor");
    }

    private static void dispatch(Object store, Object action) throws Exception {
        for (Class<?> type = store.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if ("dispatch".equals(method.getName()) && p.length == 1 && p[0].isInstance(action)) {
                    method.setAccessible(true);
                    method.invoke(store, action);
                    return;
                }
            }
        }
        throw new NoSuchMethodException("Store.dispatch");
    }

    private static Object store(Object lambda) throws Exception {
        for (Field field : lambda.getClass().getDeclaredFields()) {
            if (field.getType().isPrimitive()) continue;
            field.setAccessible(true);
            Object owner = field.get(lambda);
            if (owner == null) continue;
            Method method = find(owner.getClass(), "getTabsTrayStore$app");
            if (method != null) return method.invoke(owner);
        }
        throw new NoSuchFieldException("TabManagementFragment");
    }

    static Object call(Object target, String name) throws Exception {
        Method method = find(target.getClass(), name);
        if (method == null) throw new NoSuchMethodException(name);
        return method.invoke(target);
    }

    private static Method find(Class<?> type, String name) {
        String key = type.getName() + '#' + name;
        Method cached = METHODS.get(key);
        if (cached != null) return cached;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == 0) {
                    method.setAccessible(true);
                    METHODS.put(key, method);
                    return method;
                }
            }
        }
        return null;
    }

    static Object read(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        String key = type.getName() + '#' + name;
        Field field = FIELDS.get(key);
        if (field == null) {
            for (Class<?> current = type; current != null && field == null; current = current.getSuperclass()) {
                try {
                    field = current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (field == null) throw new NoSuchFieldException(name);
            field.setAccessible(true);
            FIELDS.put(key, field);
        }
        return field.get(target);
    }
}
