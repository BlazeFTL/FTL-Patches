package app.ftl.extension.xplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
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

            LinearLayout root = new LinearLayout(themed);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(pad, pad / 2, pad, pad / 2);

            root.addView(toggle(activity, themed, "Hide cast button", ModPrefs.KEY_HIDE_CAST, true, pad / 2));

            builder.setTitle("Mod Settings")
                    .setView(root)
                    .setPositiveButton("Close", null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private static Switch toggle(final Activity activity, Context themed, String label,
                                 final String key, boolean fallback, int pad) {
        Switch toggle = new Switch(themed);
        toggle.setText(label);
        toggle.setPadding(0, pad, 0, pad);
        toggle.setChecked(ModPrefs.get(activity, key, fallback));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                ModPrefs.put(activity, key, checked);
                activity.invalidateOptionsMenu();
            }
        });
        return toggle;
    }
}
