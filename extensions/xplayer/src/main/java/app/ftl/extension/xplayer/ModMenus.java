package app.ftl.extension.xplayer;

import android.content.Context;
import android.content.res.Resources;
import android.view.Menu;
import android.view.MenuItem;

@SuppressWarnings("unused")
public final class ModMenus {
    private static final String[] CLEAN_ITEMS = {"lock", "add_to_playlist"};

    private ModMenus() {
    }

    public static void clean(Context context, Menu menu) {
        try {
            if (context == null || menu == null) {
                return;
            }
            if (!ModPrefs.get(context, ModPrefs.KEY_CLEAN_SHEETS, true)) {
                return;
            }
            Resources resources = context.getResources();
            String pkg = context.getPackageName();
            for (String name : CLEAN_ITEMS) {
                int id = resources.getIdentifier(name, "id", pkg);
                if (id == 0) {
                    continue;
                }
                MenuItem item = menu.findItem(id);
                if (item != null) {
                    item.setVisible(false);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
