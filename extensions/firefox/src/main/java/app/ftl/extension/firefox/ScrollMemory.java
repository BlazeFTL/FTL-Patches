package app.ftl.extension.firefox;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class ScrollMemory {

    private static final Map<Long, Object> STATES = new HashMap<>();
    private static final Set<Object> REUSED =
            Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    private ScrollMemory() {
    }

    public static synchronized Object pick(Object composer, Object fresh) {
        if (!ModSettings.scrollMemory()) {
            STATES.clear();
            return fresh;
        }
        try {
            Long key = TabAccess.composerKey(composer);
            Object stored = STATES.get(key);
            if (stored == fresh) return fresh;
            if (stored != null && stored.getClass() == fresh.getClass()) {
                REUSED.add(stored);
                return stored;
            }
            STATES.put(key, fresh);
            return fresh;
        } catch (Throwable t) {
            return fresh;
        }
    }

    public static synchronized int resolveScrollIndex(
            int index, boolean offsetA, boolean offsetB, Object state) {
        if (!ModSettings.scrollMemory() || !REUSED.contains(state)) return index;
        if (index < 0) return -1;
        int adjusted = index + (offsetA ? 1 : 0) + (offsetB ? 1 : 0);
        try {
            return isVisible(state, adjusted) ? -1 : index;
        } catch (Throwable t) {
            return -1;
        }
    }

    // Visible item infos keep getIndex() (public interface member of the Compose item info).
    private static boolean isVisible(Object state, int target) throws Exception {
        List items = TabAccess.scrollItems(state);
        if (items == null) return true;
        for (int i = 0, n = items.size(); i < n; i++) {
            if (((Number) TabAccess.get(items.get(i), "getIndex")).intValue() == target) return true;
        }
        return false;
    }
}
