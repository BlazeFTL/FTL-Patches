package app.ftl.extension.mobilism;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class TitleFilter {
    public static final int ITEM_ID = 0x4c460001;

    private static final String KEY_KEYWORDS = "title_filter_keywords";
    private static final String KEY_IN_SEARCH = "title_filter_in_search";
    private static final int PAGE_SIZE = 10;
    private static final int MIN_VISIBLE = 6;
    private static final Class<?>[] NO_TYPES = new Class<?>[0];

    private static final Map<Object, ArrayList<Object>> ALL = new WeakHashMap<Object, ArrayList<Object>>();
    private static final Map<Object, int[]> TOP_STATE = new WeakHashMap<Object, int[]>();
    private static final Map<Object, Integer> SEARCH_PAGE = new WeakHashMap<Object, Integer>();
    private static final Map<String, Field> FIELDS = new HashMap<String, Field>();
    private static final Map<String, Method> METHODS = new HashMap<String, Method>();

    private static IdentityHashMap<Object, List<Object>> homeFull = new IdentityHashMap<Object, List<Object>>();
    private static int searchRaw;
    static Object target;

    private TitleFilter() {
    }

    public static SharedPreferences prefs() {
        Context c = ModPrefs.context();
        return c == null ? null : c.getSharedPreferences(c.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    public static String getRaw() {
        SharedPreferences p = prefs();
        return p == null ? "" : p.getString(KEY_KEYWORDS, "");
    }

    public static void save(String raw) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putString(KEY_KEYWORDS, raw).apply();
        }
    }

    public static boolean isSearchFilterEnabled() {
        SharedPreferences p = prefs();
        return p == null || p.getBoolean(KEY_IN_SEARCH, true);
    }

    public static void setSearchFilterEnabled(boolean enabled) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean(KEY_IN_SEARCH, enabled).apply();
        }
    }

    public static List<String> getKeywords() {
        ArrayList<String> out = new ArrayList<String>();
        for (String part : getRaw().split("[\n,]+")) {
            String t = part.trim();
            if (t.length() != 0) {
                out.add(t.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    public static List<String> getDisplayKeywords() {
        ArrayList<String> out = new ArrayList<String>();
        for (String part : getRaw().split("[\n,]+")) {
            String t = part.trim();
            if (t.length() != 0) {
                out.add(t);
            }
        }
        return out;
    }

    public static boolean has(String keyword) {
        return getKeywords().contains(keyword.toLowerCase(Locale.ROOT));
    }

    public static void add(String keyword) {
        String raw = getRaw();
        StringBuilder sb = new StringBuilder(raw);
        if (raw.length() != 0) {
            sb.append("\n");
        }
        sb.append(keyword);
        save(sb.toString());
    }

    public static void remove(String keyword) {
        StringBuilder sb = new StringBuilder();
        for (String part : getRaw().split("[\n,]+")) {
            String t = part.trim();
            if (t.length() != 0 && !t.equalsIgnoreCase(keyword)) {
                if (sb.length() != 0) {
                    sb.append("\n");
                }
                sb.append(t);
            }
        }
        save(sb.toString());
    }

    public static boolean matches(String subject, List<String> keywords) {
        if (subject == null) {
            return false;
        }
        String s = subject.toLowerCase(Locale.ROOT);
        for (String k : keywords) {
            if (s.contains(k)) {
                return true;
            }
        }
        return false;
    }

    public static String baseName(String subject) {
        if (subject == null) {
            return "";
        }
        String text;
        if (Build.VERSION.SDK_INT >= 24) {
            text = Html.fromHtml(subject, 0).toString().trim();
        } else {
            text = Html.fromHtml(subject).toString().trim();
        }
        String base = text.split("\\s+v\\d", 2)[0];
        if (base.length() == text.length()) {
            base = text.split("\\s+[\\[(]", 2)[0];
        }
        base = base.trim();
        return base.length() == 0 ? text : base;
    }

    public static void filter(List list) {
        if (list == null) {
            return;
        }
        List<String> kws = getKeywords();
        if (kws.isEmpty()) {
            return;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            if (matches(subjectOf(list.get(i)), kws)) {
                list.remove(i);
            }
        }
    }

    public static void filterHome(List items) {
        if (items == null) {
            return;
        }
        for (Object item : items) {
            if (isHomeRow(item)) {
                List inner = (List) call(item, "getItems", NO_TYPES);
                if (inner != null) {
                    homeFull.put(item, new ArrayList<Object>(inner));
                    filter(inner);
                }
            }
        }
    }

    public static void refilterHome(List items) {
        if (items == null) {
            return;
        }
        IdentityHashMap<Object, List<Object>> next = new IdentityHashMap<Object, List<Object>>();
        for (Object item : items) {
            if (!isHomeRow(item)) {
                continue;
            }
            List<Object> full = homeFull.get(item);
            if (full == null) {
                continue;
            }
            next.put(item, full);
            List inner = (List) call(item, "getItems", NO_TYPES);
            if (inner != null) {
                inner.clear();
                inner.addAll(full);
                filter(inner);
            }
        }
        homeFull = next;
    }

    private static boolean isHomeRow(Object item) {
        Object type = call(item, "getType", NO_TYPES);
        return type instanceof Integer && ((Integer) type) == 0;
    }

    public static String subjectOf(Object model) {
        Object s = call(model, "getSubject", NO_TYPES);
        if (!(s instanceof String)) {
            s = call(model, "getTitle", NO_TYPES);
        }
        return s instanceof String ? (String) s : null;
    }

    public static void addMenuItem(Menu menu) {
        if (menu.findItem(ITEM_ID) == null) {
            menu.add(0, ITEM_ID, 100, "Keyword filter");
        }
    }

    public static boolean onOptionsItemSelected(Object fragment, MenuItem item) {
        if (item == null || item.getItemId() != ITEM_ID) {
            return false;
        }
        Object a = call(fragment, "getActivity", NO_TYPES);
        if (a instanceof Activity) {
            target = fragment;
            KeywordDialog.show((Activity) a);
        }
        return true;
    }

    public static void onAdd(Object adapter, List list) {
        ArrayList<Object> all = ALL.get(adapter);
        if (all == null) {
            all = new ArrayList<Object>();
            ALL.put(adapter, all);
        }
        all.addAll(list);
        filter(list);
    }

    public static void onClear(Object adapter) {
        ArrayList<Object> all = ALL.get(adapter);
        if (all != null) {
            all.clear();
        }
    }

    public static void onBind(Object holder, Object model) {
        Object view = get(holder, "itemView");
        if (!(view instanceof View)) {
            return;
        }
        final String subject = subjectOf(model);
        final Object adapter = get(holder, "this$0");
        ((View) view).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ItemMenuDialog.show(v.getContext(), adapter, subject);
                return true;
            }
        });
    }

    public static void refilterTop(Object adapter) {
        ArrayList<Object> all = ALL.get(adapter);
        Object itemsObj = get(adapter, "items");
        if (all == null || !(itemsObj instanceof List)) {
            return;
        }
        List items = (List) itemsObj;
        List<String> kws = getKeywords();
        int i = 0;
        for (Object model : new ArrayList<Object>(all)) {
            boolean hidden = matches(subjectOf(model), kws);
            boolean shown = i < items.size() && items.get(i) == model;
            if (shown) {
                if (hidden) {
                    items.remove(i);
                    call(adapter, "notifyItemRemoved", new Class<?>[]{int.class}, i);
                } else {
                    i++;
                }
            } else if (!hidden) {
                items.add(i, model);
                call(adapter, "notifyItemInserted", new Class<?>[]{int.class}, i);
                i++;
            }
        }
    }

    private static int[] topState(Object fragment) {
        int[] s = TOP_STATE.get(fragment);
        if (s == null) {
            s = new int[2];
            TOP_STATE.put(fragment, s);
        }
        return s;
    }

    public static void onTopLoad(Object fragment, int page) {
        topState(fragment)[1] = page;
    }

    public static void onTopPageStart(Object runnable) {
        Object fragment = get(runnable, "this$0");
        Object models = get(runnable, "val$newModelsTop");
        if (fragment != null) {
            topState(fragment)[0] = models instanceof List && ((List) models).size() >= PAGE_SIZE ? 1 : 0;
        }
    }

    public static void onTopPageEnd(Object runnable) {
        Object fragment = get(runnable, "this$0");
        if (fragment != null) {
            autoMore(fragment);
        }
    }

    public static void onTopPageFail(Object runnable) {
        Object fragment = get(runnable, "this$0");
        if (fragment != null) {
            topState(fragment)[0] = 0;
        }
    }

    public static void autoMore(Object fragment) {
        int[] state = topState(fragment);
        if (state[0] == 0) {
            return;
        }
        Object adapter = get(fragment, "adapter");
        if (adapter == null || Boolean.TRUE.equals(call(adapter, "isLoading", NO_TYPES))) {
            return;
        }
        Object count = call(adapter, "getItemCount", NO_TYPES);
        if (!(count instanceof Integer) || ((Integer) count) >= MIN_VISIBLE) {
            return;
        }
        call(adapter, "addFooter", NO_TYPES);
        int page = state[1] + 1;
        Object listener = get(fragment, "endlessRecyclerOnScrollListener");
        if (listener != null) {
            call(listener, "setCurrent_page", new Class<?>[]{int.class}, page);
            call(listener, "setLoading", new Class<?>[]{boolean.class}, true);
        }
        call(fragment, "load", new Class<?>[]{int.class}, page);
    }

    public static void applyLive() {
        Object fragment = target;
        if (fragment == null) {
            return;
        }
        String name = fragment.getClass().getName();
        if (name.contains("TopFragment")) {
            Object adapter = get(fragment, "adapter");
            if (adapter != null) {
                refilterTop(adapter);
            }
            autoMore(fragment);
        } else if (name.contains("HomeFragment")) {
            Object adapter = get(fragment, "adapter");
            if (adapter != null) {
                Object rows = get(adapter, "recyclerViewItems");
                if (rows instanceof List) {
                    refilterHome((List) rows);
                }
                call(adapter, "notifyDataSetChanged", NO_TYPES);
            }
        }
    }

    public static void onSearchLoad(Object activity, int page) {
        SEARCH_PAGE.put(activity, page);
    }

    public static void onSearchPageStart(Object runnable) {
        Object models = get(runnable, "val$models");
        searchRaw = models instanceof List ? ((List) models).size() : 0;
        if (models instanceof List && isSearchFilterEnabled()) {
            filter((List) models);
        }
    }

    public static boolean onSearchPageEnd(Object runnable) {
        Object activity = get(runnable, "this$0");
        Object models = get(runnable, "val$models");
        if (!(activity instanceof Activity)) {
            return false;
        }
        Object recycler = get(activity, "list");
        Object listAdapter = recycler == null ? null : call(recycler, "getAdapter", NO_TYPES);
        Object count = listAdapter == null ? null : call(listAdapter, "getItemCount", NO_TYPES);
        if (count instanceof Integer && ((Integer) count) < MIN_VISIBLE && searchRaw >= PAGE_SIZE) {
            Object searchAdapter = get(activity, "adapter");
            Object searchView = get(activity, "searchView");
            Object query = searchView == null ? null : call(searchView, "getQuery", NO_TYPES);
            if (searchAdapter != null && query != null) {
                call(searchAdapter, "addFooter", NO_TYPES);
                Integer last = SEARCH_PAGE.get(activity);
                int page = (last == null ? 0 : last) + 1;
                Object listener = get(activity, "endlessRecyclerOnScrollListener");
                if (listener != null) {
                    call(listener, "setCurrent_page", new Class<?>[]{int.class}, page);
                    call(listener, "setLoading", new Class<?>[]{boolean.class}, true);
                }
                call(activity, "load", new Class<?>[]{String.class, int.class}, query.toString(), page);
                return true;
            }
        }
        if (models instanceof List && ((List) models).isEmpty() && searchRaw != 0) {
            Toast.makeText((Activity) activity, "All results hidden by keyword filter", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private static Field field(Class<?> type, String name) {
        String key = type.getName() + "#" + name;
        synchronized (FIELDS) {
            Field f = FIELDS.get(key);
            if (f != null) {
                return f;
            }
            for (Class<?> k = type; k != null; k = k.getSuperclass()) {
                try {
                    f = k.getDeclaredField(name);
                    f.setAccessible(true);
                    FIELDS.put(key, f);
                    return f;
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        return null;
    }

    static Object get(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field f = field(target.getClass(), name);
            return f == null ? null : f.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method method(Class<?> type, String name, Class<?>[] types) {
        String key = type.getName() + "#" + name + Arrays.toString(types);
        synchronized (METHODS) {
            Method m = METHODS.get(key);
            if (m != null) {
                return m;
            }
            for (Class<?> k = type; k != null; k = k.getSuperclass()) {
                try {
                    m = k.getDeclaredMethod(name, types);
                    m.setAccessible(true);
                    METHODS.put(key, m);
                    return m;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    static Object call(Object target, String name, Class<?>[] types, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method m = method(target.getClass(), name, types);
            return m == null ? null : m.invoke(target, args);
        } catch (Throwable t) {
            return null;
        }
    }
}
