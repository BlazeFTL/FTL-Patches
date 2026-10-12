package app.ftl.extension.firefox;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class TabCloseDuplicates {

    private static final String TAG = "MorpheFirefox";

    private final boolean privateMode;

    private TabCloseDuplicates(boolean privateMode) {
        this.privateMode = privateMode;
    }

    public static TabCloseDuplicates of(boolean privateMode) {
        return new TabCloseDuplicates(privateMode);
    }

    public Object invoke() {
        try {
            Application app = application();
            Object store = store(app);
            Object state = call(store, "getState");
            List<Object> tabs = (List<Object>) read(state, "tabs");
            String selectedId = (String) read(state, "selectedTabId");

            Set<String> seen = new HashSet<>();
            List<String> duplicates = new ArrayList<>();
            if (selectedId != null) {
                for (Object tab : tabs) {
                    if (selectedId.equals(read(tab, "id"))) {
                        String key = key(tab);
                        if (key != null) seen.add(key);
                        break;
                    }
                }
            }
            for (Object tab : tabs) {
                String id = (String) read(tab, "id");
                if (selectedId != null && selectedId.equals(id)) continue;
                String key = key(tab);
                if (key != null && !seen.add(key)) duplicates.add(id);
            }

            if (!duplicates.isEmpty()) dispatchRemove(store, duplicates);
            toast(app, duplicates.size());
        } catch (Throwable t) {
            Log.e(TAG, "close duplicates failed", t);
        }
        return ModLambda.unit();
    }

    private String key(Object tab) throws Exception {
        Object content = read(tab, "content");
        boolean isPrivate = Boolean.TRUE.equals(read(content, "private"));
        if (isPrivate != privateMode) return null;
        return (String) read(content, "url");
    }

    private static void dispatchRemove(Object store, List<String> ids) throws Exception {
        ClassLoader loader = store.getClass().getClassLoader();
        Class<?> removeType = Class.forName(
            "mozilla.components.browser.state.action.TabListAction$RemoveTabsAction", true, loader);
        Object action = removeType.getConstructor(List.class, Set.class)
            .newInstance(ids, Collections.emptySet());
        for (Method method : store.getClass().getMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if ("dispatch".equals(method.getName()) && p.length == 1 && p[0].isInstance(action)) {
                method.invoke(store, action);
                return;
            }
        }
        throw new NoSuchMethodException("Store.dispatch");
    }

    private static void toast(Application app, int count) {
        String text = count == 0 ? "No duplicate tabs"
            : "Closed " + count + (count == 1 ? " duplicate tab" : " duplicate tabs");
        Toast.makeText(app, text, Toast.LENGTH_SHORT).show();
    }

    private static Application application() throws Exception {
        return (Application) Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication").invoke(null);
    }

    private static Object store(Application app) throws Exception {
        Object components = call(app, "getComponents");
        Object core = call(components, "getCore");
        return call(core, "getStore");
    }

    private static Object call(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
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
