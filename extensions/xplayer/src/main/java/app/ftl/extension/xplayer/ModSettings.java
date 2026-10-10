package app.ftl.extension.xplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;

@SuppressWarnings("unused")
public final class ModSettings {
    private ModSettings() {
    }

    public static void show(Context context) {
        try {
            if (!(context instanceof Activity)) {
                return;
            }
            final Activity activity = (Activity) context;
            if (activity.isFinishing()) {
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            Context themed = builder.getContext();
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            final boolean[] recreate = {false};

            LinearLayout root = new LinearLayout(themed);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(pad, pad / 2, pad, pad / 2);

            int gap = pad / 2;
            root.addView(toggle(activity, themed, "Hide cast button", ModPrefs.KEY_HIDE_CAST, false, recreate, gap));
            root.addView(toggle(activity, themed, "Clean 3 dot menus", ModPrefs.KEY_CLEAN_SHEETS, false, recreate, gap));
            root.addView(toggle(activity, themed, "Clean player side menu", ModPrefs.KEY_CLEAN_PLAYER_MENU, false, recreate, gap));
            root.addView(toggle(activity, themed, "Hide bottom bar", ModPrefs.KEY_HIDE_BOTTOM_BAR, true, recreate, gap));
            root.addView(toggle(activity, themed, "Hide player cast / FF / FB buttons", ModPrefs.KEY_HIDE_PLAYER_BUTTONS, false, recreate, gap));
            root.addView(toggle(activity, themed, "Volume boost", ModPrefs.KEY_VOLUME_BOOST, false, recreate, gap));
            root.addView(toggle(activity, themed, "Extra file info", ModPrefs.KEY_EXTRA_INFO, true, recreate, gap));

            ScrollView scroll = new ScrollView(themed);
            scroll.addView(root);

            builder.setTitle("Mod Settings")
                    .setView(scroll)
                    .setPositiveButton("Close", null)
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            if (recreate[0] && !activity.isFinishing()) {
                                activity.recreate();
                            }
                        }
                    })
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private static Switch toggle(final Activity activity, Context themed, String label, final String key,
                                 final boolean needsRecreate, final boolean[] recreate, int pad) {
        Switch toggle = new Switch(themed);
        toggle.setText(label);
        toggle.setPadding(0, pad, 0, pad);
        toggle.setChecked(ModPrefs.get(activity, key, true));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                ModPrefs.put(activity, key, checked);
                activity.invalidateOptionsMenu();
                if (needsRecreate) {
                    recreate[0] = true;
                }
            }
        });
        return toggle;
    }
}
