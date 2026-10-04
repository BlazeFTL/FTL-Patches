package app.ftl.extension.videodownloader;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;

@SuppressWarnings("unused")
public final class PopupMenuHook {
    private static final int ITEM_ID = 0x0a0b0c0d;
    private static final int ICON_COLOR = 0xFF7D8EAF;

    private PopupMenuHook() {
    }

    public static void addPopupRulesItem(final Activity activity, Menu menu) {
        try {
            if (menu == null || menu.findItem(ITEM_ID) != null) {
                return;
            }
            MenuItem item = menu.add(0, ITEM_ID, Menu.CATEGORY_SYSTEM | 0xFFFF, "Manage popup rules");
            item.setIcon(new PopupRulesIcon(activity.getResources().getDisplayMetrics().density, ICON_COLOR));
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem clicked) {
                    PopupManage.show(activity);
                    return true;
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
