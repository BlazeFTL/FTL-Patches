package app.ftl.extension.videodownloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Map;

public final class PopupManage {
    private PopupManage() {
    }

    public static void show(final Activity activity) {
        final ArrayList<String> hosts = new ArrayList<String>();
        final ArrayList<String> files = new ArrayList<String>();
        ArrayList<String> labels = new ArrayList<String>();
        collect(activity, PopupStore.REDIRECT, hosts, files, labels);
        collect(activity, PopupStore.WINDOW, hosts, files, labels);
        if (hosts.isEmpty()) {
            Toast.makeText(activity, "No saved popup rules", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Popup rules (tap to remove)")
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PopupStore.remove(activity, files.get(which), hosts.get(which));
                        show(activity);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private static void collect(Activity activity, String file, ArrayList<String> hosts,
                                ArrayList<String> files, ArrayList<String> labels) {
        Map<String, ?> all = activity.getSharedPreferences(file, 0).getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (!(entry.getValue() instanceof Integer)) {
                continue;
            }
            int value = (Integer) entry.getValue();
            String state;
            if (PopupStore.REDIRECT.equals(file)) {
                if (value != PopupStore.BLOCK) {
                    continue;
                }
                state = "blocked (redirect)";
            } else if (value == PopupStore.BLOCK) {
                state = "blocked (new window)";
            } else if (value == PopupStore.ALLOW) {
                state = "allowed (new window)";
            } else {
                continue;
            }
            hosts.add(entry.getKey());
            files.add(file);
            labels.add(entry.getKey() + "  |  " + state);
        }
    }
}
