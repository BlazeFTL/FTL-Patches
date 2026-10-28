package app.ftl.extension.mobilism;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

public final class ModSettings {
    public static final int ITEM_ID = 0x4c460002;

    private ModSettings() {
    }

    public static void addMenuItem(Menu menu) {
        if (menu.findItem(ITEM_ID) == null) {
            menu.add(0, ITEM_ID, 101, "Mod settings");
        }
    }

    public static boolean onOptionsItemSelected(Object fragment, MenuItem item) {
        if (item == null || item.getItemId() != ITEM_ID) {
            return false;
        }
        Activity activity = activityOf(fragment);
        if (activity != null) {
            show(activity);
        }
        return true;
    }

    private static Activity activityOf(Object fragment) {
        try {
            Object a = fragment.getClass().getMethod("getActivity").invoke(fragment);
            return a instanceof Activity ? (Activity) a : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void show(final Activity activity) {
        final LinearLayout root = ModUi.sheet(activity);
        final Dialog[] ref = new Dialog[1];
        build(activity, root, ref);
        ref[0] = ModUi.show(activity, root);
    }

    private static void build(final Activity activity, final LinearLayout root, final Dialog[] ref) {
        ModUi.clearSheet(root);

        root.addView(ModUi.text(activity, "Mod settings", 24, ModUi.TEXT, true));
        View sub = ModUi.text(activity, "Sidebar and premium button changes apply instantly. Other changes apply next time the screen loads.", 14, ModUi.SUB, false);
        sub.setPadding(0, ModUi.dp(activity, 4), 0, ModUi.dp(activity, 4));
        root.addView(sub);

        ScrollView scroll = ModUi.cappedScroll(activity, ModUi.maxBodyHeight(activity));
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);

        body.addView(ModUi.sectionLabel(activity, "Sidebar"));
        body.addView(itemCard(activity, ModDrawer.MAIN));

        body.addView(ModUi.sectionLabel(activity, "More sidebar items"));
        body.addView(itemCard(activity, ModDrawer.EXTRA));

        addFeatureSections(activity, body);

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, ModUi.dp(activity, 16), 0, 0);

        footer.addView(ModUi.textButton(activity, "Reset", ModUi.SUB, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModPrefs.clear();
                ModDrawer.resetCache();
                ModDrawer.refresh(activity);
                build(activity, root, ref);
            }
        }));
        View spacer = new View(activity);
        footer.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        footer.addView(ModUi.filledButton(activity, "Done", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ref[0] != null) {
                    ref[0].dismiss();
                }
            }
        }));
        root.addView(footer);
    }

    private static final class Toggle {
        final String key;
        final String title;
        final String onText;
        final String offText;

        Toggle(String key, String title, String onText, String offText) {
            this.key = key;
            this.title = title;
            this.onText = onText;
            this.offText = offText;
        }
    }

    private static void addFeatureSections(Activity activity, LinearLayout body) {
        if (ModFeatures.present(ModFeatures.PREMIUM)) {
            body.addView(ModUi.sectionLabel(activity, "Premium"));
            body.addView(toggleCard(activity, new Toggle[]{
                    new Toggle(ModFeatures.HIDE_PREMIUM_HEADER, "Hide Go premium button", "Hidden", "Shown"),
                    new Toggle(ModFeatures.HIDE_PREMIUM_DOWNLOAD, "Hide premium download button", "Hidden", "Shown")
            }));
        }
        if (ModFeatures.present(ModFeatures.DISCUSSIONS)) {
            body.addView(ModUi.sectionLabel(activity, "Home sections"));
            body.addView(toggleCard(activity, new Toggle[]{
                    new Toggle(ModFeatures.HIDE_RECENT_DISCUSSIONS, "Hide Recent discussions", "Hidden", "Shown"),
                    new Toggle(ModFeatures.HIDE_REVIEWS, "Hide Reviews", "Hidden", "Shown"),
                    new Toggle(ModFeatures.HIDE_CONTESTS, "Hide Contests", "Hidden", "Shown")
            }));
        }
        boolean latest = ModFeatures.present(ModFeatures.LATEST);
        boolean links = ModFeatures.present(ModFeatures.LINKS);
        if (latest || links) {
            body.addView(ModUi.sectionLabel(activity, "Extras"));
            Toggle[] extras;
            if (latest && links) {
                extras = new Toggle[]{
                        new Toggle(ModFeatures.OPEN_LATEST, "Open Latest on launch", "Opens Latest list", "Opens Home"),
                        new Toggle(ModFeatures.COPY_LINKS, "Copy button on links", "Shown", "Hidden")
                };
            } else if (latest) {
                extras = new Toggle[]{
                        new Toggle(ModFeatures.OPEN_LATEST, "Open Latest on launch", "Opens Latest list", "Opens Home")
                };
            } else {
                extras = new Toggle[]{
                        new Toggle(ModFeatures.COPY_LINKS, "Copy button on links", "Shown", "Hidden")
                };
            }
            body.addView(toggleCard(activity, extras));
        }
    }

    private static View toggleCard(final Activity activity, Toggle[] toggles) {
        LinearLayout card = ModUi.card(activity);
        for (int i = 0; i < toggles.length; i++) {
            final Toggle t = toggles[i];
            card.addView(ModUi.switchRow(activity, t.title, t.onText, t.offText,
                    ModFeatures.isOn(t.key), i, toggles.length,
                    new PillSwitch.Listener() {
                        @Override
                        public void onChanged(boolean checked) {
                            ModPrefs.setBoolean(t.key, checked);
                            if (ModFeatures.HIDE_PREMIUM_HEADER.equals(t.key)) {
                                HidePremium.applyHeader(activity);
                            }
                        }
                    }));
        }
        return card;
    }

    private static View itemCard(final Activity activity, ModDrawer.Item[] items) {
        LinearLayout card = ModUi.card(activity);
        for (int i = 0; i < items.length; i++) {
            final ModDrawer.Item item = items[i];
            card.addView(ModUi.switchRow(activity, item.title, "Shown in sidebar", "Hidden from sidebar",
                    ModDrawer.isShown(item), i, items.length,
                    new PillSwitch.Listener() {
                        @Override
                        public void onChanged(boolean checked) {
                            ModDrawer.setShown(item, checked);
                            ModDrawer.refresh(activity);
                        }
                    }));
        }
        return card;
    }
}
