package app.ftl.extension.firefox;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;

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
            Object state = TabAccess.state(store);
            List<Object> tabs = TabAccess.tabs(state);
            String selectedId = TabAccess.selected(state);

            Set<String> seen = new HashSet<>();
            List<String> duplicates = new ArrayList<>();
            if (selectedId != null) {
                for (Object tab : tabs) {
                    if (selectedId.equals(TabAccess.id(tab))) {
                        String key = key(tab);
                        if (key != null) seen.add(key);
                        break;
                    }
                }
            }
            for (Object tab : tabs) {
                String id = TabAccess.id(tab);
                if (selectedId != null && selectedId.equals(id)) continue;
                String key = key(tab);
                if (key != null && !seen.add(key)) duplicates.add(id);
            }

            if (!duplicates.isEmpty()) TabAccess.remove(store, duplicates, Collections.emptySet());
            toast(app, duplicates.size());
        } catch (Throwable t) {
            Log.e(TAG, "close duplicates failed", t);
        }
        return TabAccess.unit();
    }

    private String key(Object tab) throws Exception {
        if (TabAccess.isPrivate(tab) != privateMode) return null;
        return TabAccess.url(tab);
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

    // Application.getComponents().getCore().getStore(): all Fenix members, names are kept.
    private static Object store(Application app) throws Exception {
        Object components = call(app, "getComponents");
        Object core = call(components, "getCore");
        return call(core, "getStore");
    }

    private static Object call(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }
}
