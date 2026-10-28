package app.ftl.extension.mobilism;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;

public final class ModDrawer {
    public static final class Item {
        public final String key;
        public final String idName;
        public final String groupName;
        public final String title;
        public final boolean defaultShown;

        Item(String key, String idName, String groupName, String title, boolean defaultShown) {
            this.key = key;
            this.idName = idName;
            this.groupName = groupName;
            this.title = title;
            this.defaultShown = defaultShown;
        }
    }

    public static final Item[] MAIN = {
            new Item("announcements", "announcements", "top_group", "Announcements", true),
            new Item("messages", "messages", "top_group", "Messages", true),
            new Item("books", "books", "second_middle_group", "Books", true),
            new Item("premium", "premiumDownloads", "premium_group", "Mobilism Premium", false),
            new Item("logout", "nav_logout", "bottom_group", "Log out", true)
    };

    public static final Item[] EXTRA = {
            new Item("contests", "contests", "top_group", "Contests", false),
            new Item("qr", "qr_code_scanner", "middle_group", "QR code scanner", false),
            new Item("chitchat", "chitchat", "third_middle_group", "ChitChat", false),
            new Item("services", "services", "third_middle_group", "Services", false),
            new Item("feedback", "feedback", "third_middle_group", "Suggestions & feedback", false)
    };

    private static final String NAV_VIEW = "com.google.android.material.navigation.NavigationView";

    private static volatile int[] hiddenPairs;

    private ModDrawer() {
    }

    public static boolean isShown(Item item) {
        return ModPrefs.getBoolean("show_" + item.key, item.defaultShown);
    }

    public static void setShown(Item item, boolean shown) {
        ModPrefs.setBoolean("show_" + item.key, shown);
        hiddenPairs = null;
    }

    public static void resetCache() {
        hiddenPairs = null;
    }

    private static int[] pairs() {
        int[] cached = hiddenPairs;
        if (cached != null) {
            return cached;
        }
        Context ctx = ModPrefs.context();
        if (ctx == null) {
            return new int[0];
        }
        Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();
        int[] tmp = new int[(MAIN.length + EXTRA.length) * 2];
        int n = 0;
        for (int pass = 0; pass < 2; pass++) {
            Item[] list = pass == 0 ? MAIN : EXTRA;
            for (Item item : list) {
                if (isShown(item)) {
                    continue;
                }
                int id = res.getIdentifier(item.idName, "id", pkg);
                int group = res.getIdentifier(item.groupName, "id", pkg);
                if (id == 0 || group == 0) {
                    continue;
                }
                tmp[n++] = id;
                tmp[n++] = group;
            }
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        hiddenPairs = out;
        return out;
    }

    public static boolean forceHidden(Object menuItem) {
        if (!(menuItem instanceof MenuItem)) {
            return false;
        }
        int[] p = pairs();
        if (p.length == 0) {
            return false;
        }
        MenuItem item = (MenuItem) menuItem;
        int id = item.getItemId();
        int group = item.getGroupId();
        for (int i = 0; i < p.length; i += 2) {
            if (p[i] == id && p[i + 1] == group) {
                return true;
            }
        }
        return false;
    }

    public static boolean filterVisible(Object menuItem, boolean visible) {
        return visible && !forceHidden(menuItem);
    }

    private static int[] allPairs() {
        Context ctx = ModPrefs.context();
        if (ctx == null) {
            return new int[0];
        }
        Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();
        int[] tmp = new int[(MAIN.length + EXTRA.length) * 2];
        int n = 0;
        for (int pass = 0; pass < 2; pass++) {
            Item[] list = pass == 0 ? MAIN : EXTRA;
            for (Item item : list) {
                int id = res.getIdentifier(item.idName, "id", pkg);
                int group = res.getIdentifier(item.groupName, "id", pkg);
                if (id == 0 || group == 0) {
                    continue;
                }
                tmp[n++] = id;
                tmp[n++] = group;
            }
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    private static void reapply(Menu menu, int[] all) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            SubMenu sub = item.getSubMenu();
            if (sub != null) {
                reapply(sub, all);
            }
            int id = item.getItemId();
            int group = item.getGroupId();
            for (int j = 0; j < all.length; j += 2) {
                if (all[j] == id && all[j + 1] == group) {
                    item.setVisible(true);
                    break;
                }
            }
        }
    }

    public static void refresh(Activity activity) {
        try {
            View root = activity.getWindow().getDecorView();
            View nav = findNav(root);
            if (nav == null) {
                return;
            }
            Menu menu = (Menu) nav.getClass().getMethod("getMenu").invoke(nav);
            if (menu == null) {
                return;
            }
            reapply(menu, allPairs());
        } catch (Throwable ignored) {
        }
    }

    static View findNav(View v) {
        if (v.getClass().getName().equals(NAV_VIEW)) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findNav(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
