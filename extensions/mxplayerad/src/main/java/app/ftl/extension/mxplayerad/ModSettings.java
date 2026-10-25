package app.ftl.extension.mxplayerad;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModSettings {
    private static final String PREFS = "ftl_mod_settings";
    private static final String FLAG_DIR = "ftl_mod";

    private static final int COLOR_SURFACE = 0xFFF6F7FB;
    private static final int COLOR_CARD = 0xFFFFFFFF;
    private static final int COLOR_STROKE = 0xFFE4E7F0;
    private static final int COLOR_DIVIDER = 0xFFEDEFF5;
    private static final int COLOR_TEXT = 0xFF1A1C2C;
    private static final int COLOR_TEXT_SECONDARY = 0xFF565B72;
    private static final int COLOR_TRACK_OFF = 0xFFC9CDDA;
    private static final int COLOR_ACCENT = 0xFF6750A4;
    private static final int COLOR_ACCENT_LIGHT = 0xFF8E73E0;

    private static final Entry[] ENTRIES = {
        new Entry(
            "SpeedUp overlay",
            "speedup_no_ui",
            "No UI",
            "On: the long-press SpeedUp overlay never shows. Off: 2x UI.",
            false,
            false
        ),
        new Entry(
            "Smart Enhance",
            "smart_enhance_skip_popup",
            "Skip intro popup",
            "On: the player menu item toggles Smart Enhance directly, without the popup and animation. Off: stock popup.",
            true,
            false
        ),
        new Entry(
            "Smart Enhance",
            "smart_enhance_toast",
            "Toast on enable",
            "On: toast when Smart Enhance turns on. Off: silent toggle.",
            false,
            false
        ),
        new Entry(
            "Smart Enhance",
            "smart_enhance_slider",
            "Control slider",
            "On: the player menu item opens a 0-100% popup slider. Off: stock on/off toggle.",
            true,
            false
        ),
        new Entry(
            "Smart Enhance",
            "smart_enhance_always_on",
            "Always On",
            "Turns Smart Enhance on automatically every time a video starts, at the level below. " +
                "With the Control slider on, dragging it overrides the level for the current video only.",
            false,
            false
        ),
        new Entry(
            "Home screen",
            "me_hide_tiles_pager",
            "Hide local tiles pager",
            "Hides the local tiles pager and its indicator.",
            true,
            false
        ),
        new Entry(
            "Home screen",
            "home_hide_bottom_bar",
            "Hide bottom navigation bar",
            "The app reloads to the Local tab when you close this dialog. The Me tab button in " +
                "the toolbar stays either way, so this switch can never lock you out of itself.",
            true,
            false,
            true
        ),
        new Entry(
            "Me tab",
            "me_hide_status_saver",
            "Hide Status Saver row",
            "Collapses the WhatsApp Status Saver row.",
            true,
            false
        ),
        new Entry(
            "Me tab",
            "me_hide_legal_help",
            "Hide Legal / Help group",
            "Hides Legal, Help and Data privacy.",
            true,
            false
        ),
        new Entry(
            "Me tab",
            "me_hide_music_player",
            "Hide Music Player tile",
            "Removes the Music Player tile from the Me tab.",
            true,
            true
        ),
        new Entry(
            "Me tab",
            "me_hide_cloud_drive",
            "Hide Cloud Drive tile",
            "Removes the Cloud Drive tile from the Me tab.",
            true,
            true
        ),
        new Entry(
            "Me tab",
            "me_show_network_stream",
            "Network Stream tile",
            "Replaces the Video Playlists tile with Network Stream.",
            true,
            true
        ),
        new Entry(
            "Me tab",
            "me_hide_recycle_bin",
            "Hide Recycle Bin tile",
            "Only hides the tile - deleted files are always removed permanently while this patch " +
                "is applied, on or off.",
            true,
            true
        ),
        new Entry(
            "Me tab",
            "hide_private_folder",
            "Hide Private Folder",
            "Hidden everywhere: Me tab tile, 3-dot menu of each file, and the multi-select menu.",
            true,
            true
        ),
        new Entry(
            "Me tab",
            "hide_file_transfer",
            "Hide File Transfer",
            "Hidden everywhere: Me tab tile, 3-dot menu of each file, and the multi-select menu.",
            true,
            true
        ),
        new Entry(
            "Me tab",
            "hide_add_to_playlist",
            "Hide Add to Playlist",
            "Hidden everywhere: 3-dot menu of each file, the multi-select menu, and the split toolbar.",
            true,
            false
        ),
        new Entry(
            "Sidebar",
            "sidebar_hide_bookmark",
            "Hide Bookmark",
            "Removes the Bookmark shortcut from the player sidebar.",
            true,
            false
        ),
        new Entry(
            "Sidebar",
            "sidebar_hide_favourite",
            "Hide Favourite",
            "Removes the Favourite shortcut. If Add to Playlist is also hidden, hiding this hides both.",
            true,
            false
        ),
        new Entry(
            "Sidebar",
            "sidebar_hide_add_to_playlist",
            "Hide Add to Playlist",
            "Removes the Add to Playlist shortcut from the player sidebar.",
            true,
            false
        ),
        new Entry(
            "Sidebar",
            "sidebar_hide_tutorial",
            "Hide Tutorial",
            "Removes the Tutorial shortcut from the player sidebar.",
            true,
            false
        ),
        new Entry(
            "Sidebar",
            "sidebar_hide_playing_queue",
            "Hide Playing Queue",
            "Removes the Playing Queue shortcut from the player sidebar.",
            true,
            false
        ),
        new Entry(
            "Sidebar",
            "sidebar_hide_video_display",
            "Hide Video Display row",
            "Hides the Video Display row in the player sidebar.",
            true,
            false
        ),
        new Entry(
            "Sidebar",
            "sidebar_hide_help",
            "Hide More menu Help section",
            "Hides What's New, Features, FAQ, Check for Update, Bug Report and About.",
            true,
            false
        ),
        new Entry(
            "Sidebar",
            "subtitle_open_settings",
            "Open subtitle settings by default",
            "Expands Sync/Speed/Panel/Customization the next time you open the subtitle menu.",
            true,
            false
        ),
    };

    // Only rendered while its dependsOnKey Entry is checked - see the second pass in
    // showDialog(). Same isPatched() asset-marker gate as ENTRIES; type has no bearing
    // on that check, so modSettingFlagPatch(key) is reused as-is for these too.
    private static final IntEntry[] INT_ENTRIES = {
        new IntEntry(
            "Smart Enhance",
            "smart_enhance_default_pct",
            "Default level",
            "Strength Always On applies to every video.",
            20,
            0,
            100,
            "smart_enhance_always_on"
        ),
    };

    private static Context appContext;
    private static WeakReference<Object> tilesOwner;
    private static String tilesMethod;

    private ModSettings() {}

    public static boolean get(String key) {
        Entry entry = find(key);
        boolean def = entry != null && entry.def;
        SharedPreferences prefs = prefs(context());
        return prefs == null ? def : prefs.getBoolean(key, def);
    }

    public static void onTilesOwner(Object owner, String method) {
        tilesOwner = new WeakReference<Object>(owner);
        tilesMethod = method;
    }

    private static boolean refreshTiles() {
        Object owner = tilesOwner == null ? null : tilesOwner.get();
        if (owner == null || tilesMethod == null) return false;
        try {
            Method method = owner.getClass().getDeclaredMethod(tilesMethod);
            method.setAccessible(true);
            method.invoke(owner);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void set(String key, boolean value) {
        SharedPreferences prefs = prefs(context());
        if (prefs != null) prefs.edit().putBoolean(key, value).apply();
        ModViewHider.refreshAll();
    }

    private static IntEntry findInt(String key) {
        for (IntEntry entry : INT_ENTRIES) if (entry.key.equals(key)) return entry;
        return null;
    }

    public static int getInt(String key) {
        IntEntry entry = findInt(key);
        int def = entry == null ? 0 : entry.def;
        SharedPreferences prefs = prefs(context());
        return prefs == null ? def : prefs.getInt(key, def);
    }

    public static void setInt(String key, int value) {
        SharedPreferences prefs = prefs(context());
        if (prefs != null) prefs.edit().putInt(key, value).apply();
    }

    public static void showDialog(final Context host) {
        final Dialog dialog = new Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final Context dc = dialog.getContext();
        final Map<String, Boolean> initial = new HashMap<String, Boolean>();
        final boolean[] refreshFailed = {false};

        final ScrollView scroll = new ScrollView(dc);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout list = new LinearLayout(dc);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(dc, 16), dp(dc, 4), dp(dc, 16), dp(dc, 16));
        scroll.addView(list);

        final Map<String, Group> groups = new LinkedHashMap<String, Group>();
        final Map<String, List<View>> dependentRows = new HashMap<String, List<View>>();
        int shown = 0;

        for (final Entry entry : ENTRIES) {
            if (!isPatched(dc, entry.key)) continue;
            shown++;
            if (entry.tiles || entry.needsReload) initial.put(entry.key, get(entry.key));

            final Group group = obtainGroup(dc, list, groups, entry.group, scroll);
            group.keys.add(entry.key);

            LinearLayout wrap = new LinearLayout(dc);
            wrap.setOrientation(LinearLayout.VERTICAL);
            if (group.content.getChildCount() > 0) wrap.addView(divider(dc));

            LinearLayout row = new LinearLayout(dc);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(dc, 18), dp(dc, 13), dp(dc, 16), dp(dc, 13));
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(ripple(dc, withAlpha(group.hue, 0x26), 0));

            LinearLayout texts = new LinearLayout(dc);
            texts.setOrientation(LinearLayout.VERTICAL);

            TextView title = new TextView(dc);
            title.setText(titleCase(entry.title));
            title.setTextSize(15f);
            title.setTextColor(COLOR_TEXT);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            texts.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            if (entry.summary != null) {
                TextView summary = new TextView(dc);
                summary.setText(entry.summary);
                summary.setTextSize(12.5f);
                summary.setTextColor(COLOR_TEXT_SECONDARY);
                summary.setLineSpacing(dp(dc, 2), 1f);
                LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                summaryParams.topMargin = dp(dc, 3);
                texts.addView(summary, summaryParams);
            }

            row.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            final ModSwitch toggle = new ModSwitch(dc, group.hue, get(entry.key));
            toggle.setListener(new ModSwitch.Listener() {
                @Override
                public void onChanged(boolean checked) {
                    set(entry.key, checked);
                    if (entry.tiles && !refreshTiles()) refreshFailed[0] = true;
                    if (entry.needsReload) refreshFailed[0] = true;
                    List<View> dependents = dependentRows.get(entry.key);
                    if (dependents != null) {
                        for (View dependent : dependents) {
                            dependent.setVisibility(checked ? View.VISIBLE : View.GONE);
                        }
                    }
                    group.refreshSubtitle();
                }
            });
            LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            toggleParams.leftMargin = dp(dc, 16);
            row.addView(toggle, toggleParams);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggle.toggle();
                }
            });

            wrap.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            group.content.addView(wrap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        for (final IntEntry entry : INT_ENTRIES) {
            if (!isPatched(dc, entry.key)) continue;
            Group group = groups.get(entry.group);
            if (group == null) continue;
            shown++;

            LinearLayout wrap = new LinearLayout(dc);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.setVisibility(entry.dependsOnKey == null || get(entry.dependsOnKey)
                ? View.VISIBLE : View.GONE);
            if (group.content.getChildCount() > 0) wrap.addView(divider(dc));

            LinearLayout row = new LinearLayout(dc);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(dc, 18), dp(dc, 14), dp(dc, 18), dp(dc, 18));

            LinearLayout labelRow = new LinearLayout(dc);
            labelRow.setOrientation(LinearLayout.HORIZONTAL);
            labelRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(dc);
            title.setText(titleCase(entry.title));
            title.setTextSize(15f);
            title.setTextColor(COLOR_TEXT);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            labelRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            final TextView valueChip = new TextView(dc);
            valueChip.setText(getInt(entry.key) + "%");
            valueChip.setTextSize(13f);
            valueChip.setTypeface(Typeface.DEFAULT_BOLD);
            valueChip.setTextColor(darken(group.hue, 0.78f));
            valueChip.setPadding(dp(dc, 12), dp(dc, 5), dp(dc, 12), dp(dc, 5));
            valueChip.setBackground(rounded(dc, withAlpha(group.hue, 0x26), 14));
            labelRow.addView(valueChip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(labelRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            SeekBar seekBar = new SeekBar(dc);
            seekBar.setMax(entry.max - entry.min);
            seekBar.setProgress(getInt(entry.key) - entry.min);
            seekBar.setProgressTintList(ColorStateList.valueOf(group.hue));
            seekBar.setThumbTintList(ColorStateList.valueOf(group.hue));
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(COLOR_TRACK_OFF));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    int value = progress + entry.min;
                    setInt(entry.key, value);
                    valueChip.setText(value + "%");
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {}

                @Override
                public void onStopTrackingTouch(SeekBar bar) {}
            });
            LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            seekParams.topMargin = dp(dc, 10);
            row.addView(seekBar, seekParams);

            if (entry.summary != null) {
                TextView summary = new TextView(dc);
                summary.setText(entry.summary);
                summary.setTextSize(12.5f);
                summary.setTextColor(COLOR_TEXT_SECONDARY);
                summary.setLineSpacing(dp(dc, 2), 1f);
                LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                summaryParams.topMargin = dp(dc, 12);
                row.addView(summary, summaryParams);
            }

            wrap.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            group.content.addView(wrap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            if (entry.dependsOnKey != null) {
                List<View> dependents = dependentRows.get(entry.dependsOnKey);
                if (dependents == null) {
                    dependents = new ArrayList<View>();
                    dependentRows.put(entry.dependsOnKey, dependents);
                }
                dependents.add(wrap);
            }
        }

        for (Group group : groups.values()) group.refreshSubtitle();

        if (shown == 0) {
            TextView empty = new TextView(dc);
            empty.setText(titleCase("No configurable mods in this build."));
            empty.setTextColor(COLOR_TEXT_SECONDARY);
            empty.setPadding(dp(dc, 8), dp(dc, 16), dp(dc, 8), dp(dc, 16));
            list.addView(empty);
        }

        LinearLayout header = new LinearLayout(dc);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(dc, 22), dp(dc, 22), dp(dc, 22), dp(dc, 14));

        FrameLayout headerTile = new FrameLayout(dc);
        headerTile.setBackground(gradient(dc, COLOR_ACCENT_LIGHT, COLOR_ACCENT, 14));
        FrameLayout.LayoutParams headerIconParams = new FrameLayout.LayoutParams(dp(dc, 24), dp(dc, 24));
        headerIconParams.gravity = Gravity.CENTER;
        headerTile.addView(new IconView(dc, IconView.ICON_TUNE, 0xFFFFFFFF), headerIconParams);
        header.addView(headerTile, new LinearLayout.LayoutParams(dp(dc, 46), dp(dc, 46)));

        LinearLayout headerTexts = new LinearLayout(dc);
        headerTexts.setOrientation(LinearLayout.VERTICAL);
        TextView headerTitle = new TextView(dc);
        headerTitle.setText(titleCase("Mod Settings"));
        headerTitle.setTextSize(21f);
        headerTitle.setTextColor(COLOR_TEXT);
        headerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        headerTexts.addView(headerTitle);
        TextView headerHint = new TextView(dc);
        headerHint.setText(titleCase("Tap a section to expand"));
        headerHint.setTextSize(13f);
        headerHint.setTextColor(COLOR_TEXT_SECONDARY);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(dc, 2);
        headerTexts.addView(headerHint, hintParams);
        LinearLayout.LayoutParams headerTextsParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerTextsParams.leftMargin = dp(dc, 14);
        header.addView(headerTexts, headerTextsParams);

        LinearLayout footer = new LinearLayout(dc);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(dc, 20), dp(dc, 10), dp(dc, 20), dp(dc, 14));

        TextView ok = new TextView(dc);
        ok.setText(titleCase(host.getString(android.R.string.ok)));
        ok.setTextSize(14.5f);
        ok.setTextColor(0xFFFFFFFF);
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(dp(dc, 30), dp(dc, 11), dp(dc, 30), dp(dc, 11));
        ok.setClickable(true);
        ok.setFocusable(true);
        ok.setBackground(rippleOver(dc, 0x33FFFFFF, rounded(dc, COLOR_ACCENT, 22), 22));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        footer.addView(ok, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout root = new LinearLayout(dc) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.88f);
                if (View.MeasureSpec.getMode(heightSpec) != View.MeasureSpec.EXACTLY) {
                    int size = View.MeasureSpec.getSize(heightSpec);
                    if (size == 0 || size > max) size = max;
                    heightSpec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(dc, COLOR_SURFACE, 28));
        root.setClipToOutline(true);
        root.addView(header, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(footer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                if (!refreshFailed[0]) return;
                for (Map.Entry<String, Boolean> before : initial.entrySet()) {
                    if (get(before.getKey()) != before.getValue().booleanValue()) {
                        restartActivity(host);
                        return;
                    }
                }
            }
        });

        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics metrics = dc.getResources().getDisplayMetrics();
            int width = Math.min(metrics.widthPixels - dp(dc, 32), dp(dc, 440));
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setDimAmount(0.5f);
        }
        dialog.show();
    }

    private static Group obtainGroup(
        final Context dc, LinearLayout list, final Map<String, Group> groups, String name,
        final ScrollView scroll
    ) {
        Group existing = groups.get(name);
        if (existing != null) return existing;

        int hue = hueFor(name);

        LinearLayout card = new LinearLayout(dc);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground(dc, COLOR_STROKE));
        card.setClipToOutline(true);

        LinearLayout header = new LinearLayout(dc);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(dc, 72));
        header.setPadding(dp(dc, 16), dp(dc, 12), dp(dc, 16), dp(dc, 12));
        header.setClickable(true);
        header.setFocusable(true);
        header.setBackground(ripple(dc, withAlpha(hue, 0x26), 20));

        FrameLayout tile = new FrameLayout(dc);
        tile.setBackground(gradient(dc, withAlpha(hue, 0x26), withAlpha(hue, 0x52), 14));
        IconView icon = new IconView(dc, iconFor(name), hue);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(dc, 26), dp(dc, 26));
        iconParams.gravity = Gravity.CENTER;
        tile.addView(icon, iconParams);
        header.addView(tile, new LinearLayout.LayoutParams(dp(dc, 46), dp(dc, 46)));

        LinearLayout texts = new LinearLayout(dc);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(dc);
        title.setText(titleCase(name));
        title.setTextSize(16f);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(title);
        TextView subtitle = new TextView(dc);
        subtitle.setTextSize(12.5f);
        subtitle.setTextColor(COLOR_TEXT_SECONDARY);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(dc, 1);
        texts.addView(subtitle, subtitleParams);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = dp(dc, 14);
        header.addView(texts, textsParams);

        IconView chevron = new IconView(dc, IconView.ICON_CHEVRON, COLOR_TEXT_SECONDARY);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(dc, 24), dp(dc, 24));
        chevronParams.leftMargin = dp(dc, 8);
        header.addView(chevron, chevronParams);

        LinearLayout content = new LinearLayout(dc);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setVisibility(View.GONE);

        card.addView(header, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(content, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(dc, 5);
        cardParams.bottomMargin = dp(dc, 5);
        list.addView(card, cardParams);

        final Group group = new Group(card, content, chevron, subtitle, hue);
        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectGroup(group, groups.values(), scroll);
            }
        });
        groups.put(name, group);
        return group;
    }

    private static void selectGroup(final Group target, Collection<Group> all, final ScrollView scroll) {
        boolean expand = !target.expanded;
        for (Group other : all) {
            if (other != target && other.expanded) setExpanded(other, false);
        }
        setExpanded(target, expand);
        if (expand) {
            scroll.post(new Runnable() {
                @Override
                public void run() {
                    scroll.smoothScrollTo(0, Math.max(0, target.card.getTop() - dp(scroll.getContext(), 8)));
                }
            });
        }
    }

    private static void setExpanded(Group group, boolean expand) {
        if (group.expanded == expand) return;
        group.expanded = expand;
        group.card.setBackground(cardBackground(group.card.getContext(),
            expand ? withAlpha(group.hue, 0x80) : COLOR_STROKE));
        group.chevron.animate().rotation(expand ? 180f : 0f).setDuration(160).start();
        View content = group.content;
        content.animate().cancel();
        if (expand) {
            content.setAlpha(0f);
            content.setVisibility(View.VISIBLE);
            content.animate().alpha(1f).setDuration(180).start();
        } else {
            content.setVisibility(View.GONE);
            content.setAlpha(1f);
        }
    }

    private static View divider(Context dc) {
        View divider = new View(dc);
        divider.setBackgroundColor(COLOR_DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(dc, 1)));
        params.leftMargin = dp(dc, 18);
        params.rightMargin = dp(dc, 18);
        divider.setLayoutParams(params);
        return divider;
    }

    private static GradientDrawable cardBackground(Context context, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_CARD);
        drawable.setStroke(Math.max(1, dp(context, 1)), strokeColor);
        drawable.setCornerRadius(dp(context, 20));
        return drawable;
    }

    private static GradientDrawable rounded(Context context, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    private static GradientDrawable gradient(Context context, int from, int to, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {from, to});
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    private static Drawable ripple(Context context, int color, int radiusDp) {
        return rippleOver(context, color, null, radiusDp);
    }

    private static Drawable rippleOver(Context context, int color, Drawable content, int radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFF000000);
        mask.setCornerRadius(dp(context, radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(color), content, mask);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int darken(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int blend(int from, int to, float t) {
        int a = channel(from, to, t, 24);
        int r = channel(from, to, t, 16);
        int g = channel(from, to, t, 8);
        int b = channel(from, to, t, 0);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int to, float t, int shift) {
        int a = (from >>> shift) & 0xFF;
        int b = (to >>> shift) & 0xFF;
        return Math.round(a + (b - a) * t);
    }

    private static String titleCase(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length());
        boolean upper = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = Character.isWhitespace(c);
        }
        return out.toString();
    }

    private static int hueFor(String groupName) {
        if ("SpeedUp overlay".equals(groupName)) return 0xFFF97316;
        if ("Smart Enhance".equals(groupName)) return 0xFF8B5CF6;
        if ("Home screen".equals(groupName)) return 0xFF3B82F6;
        if ("Me tab".equals(groupName)) return 0xFF10B981;
        if ("Sidebar".equals(groupName)) return 0xFFF43F5E;
        return COLOR_ACCENT;
    }

    private static int iconFor(String groupName) {
        if ("SpeedUp overlay".equals(groupName)) return IconView.ICON_SPEED;
        if ("Smart Enhance".equals(groupName)) return IconView.ICON_ENHANCE;
        if ("Home screen".equals(groupName)) return IconView.ICON_HOME;
        if ("Me tab".equals(groupName)) return IconView.ICON_PERSON;
        if ("Sidebar".equals(groupName)) return IconView.ICON_SIDEBAR;
        return IconView.ICON_ENHANCE;
    }

    private static void restartActivity(Context context) {
        Activity activity = activityOf(context);
        if (activity == null || activity.isFinishing()) return;
        Intent intent = new Intent(activity.getIntent());
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.finish();
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

    private static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static Entry find(String key) {
        for (Entry entry : ENTRIES) {
            if (entry.key.equals(key)) return entry;
        }
        return null;
    }

    private static boolean isPatched(Context context, String key) {
        try {
            String[] names = context.getAssets().list(FLAG_DIR);
            if (names == null) return false;
            for (String name : names) {
                if (name.equals(key)) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static Context context() {
        if (appContext != null) return appContext;
        try {
            Object app = Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null);
            if (app instanceof Context) appContext = (Context) app;
        } catch (Throwable ignored) {
        }
        return appContext;
    }

    private static SharedPreferences prefs(Context context) {
        return context == null ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class ModSwitch extends View {
        interface Listener {
            void onChanged(boolean checked);
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int onColor;
        private boolean checked;
        private float progress;
        private Listener listener;
        private ValueAnimator animator;

        ModSwitch(Context context, int onColor, boolean checked) {
            super(context);
            this.onColor = onColor;
            this.checked = checked;
            this.progress = checked ? 1f : 0f;
        }

        void setListener(Listener listener) {
            this.listener = listener;
        }

        void toggle() {
            checked = !checked;
            animateTo(checked ? 1f : 0f);
            if (listener != null) listener.onChanged(checked);
        }

        private void animateTo(float target) {
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(170);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    progress = ((Float) animation.getAnimatedValue()).floatValue();
                    invalidate();
                }
            });
            animator.start();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(dp(getContext(), 48), dp(getContext(), 28));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blend(COLOR_TRACK_OFF, onColor, progress));
            rect.set(0f, 0f, w, h);
            canvas.drawRoundRect(rect, h / 2f, h / 2f, paint);
            float inset = h / 2f;
            float radius = h * (0.29f + 0.10f * progress);
            float cx = inset + (w - 2f * inset) * progress;
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, h / 2f, radius, paint);
        }
    }

    private static final class IconView extends View {
        static final int ICON_SPEED = 0;
        static final int ICON_ENHANCE = 1;
        static final int ICON_HOME = 2;
        static final int ICON_PERSON = 3;
        static final int ICON_SIDEBAR = 4;
        static final int ICON_CHEVRON = 5;
        static final int ICON_TUNE = 6;

        private final int kind;
        private final Paint stroke;
        private final Paint fill;
        private final Paint pane;
        private final Paint solid;

        IconView(Context context, int kind, int color) {
            super(context);
            this.kind = kind;
            stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.7f);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setColor(color);
            fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(withAlpha(color, 0x40));
            pane = new Paint(Paint.ANTI_ALIAS_FLAG);
            pane.setStyle(Paint.Style.FILL);
            pane.setColor(withAlpha(color, 0x80));
            solid = new Paint(Paint.ANTI_ALIAS_FLAG);
            solid.setStyle(Paint.Style.FILL);
            solid.setColor(color);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int size = Math.min(getWidth(), getHeight());
            if (size <= 0) return;
            float scale = size / 24f;
            canvas.save();
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.scale(scale, scale);
            switch (kind) {
                case ICON_SPEED:
                    drawSpeed(canvas);
                    break;
                case ICON_ENHANCE:
                    drawEnhance(canvas);
                    break;
                case ICON_HOME:
                    drawHome(canvas);
                    break;
                case ICON_PERSON:
                    drawPerson(canvas);
                    break;
                case ICON_SIDEBAR:
                    drawSidebar(canvas);
                    break;
                case ICON_CHEVRON:
                    drawChevron(canvas);
                    break;
                case ICON_TUNE:
                    drawTune(canvas);
                    break;
                default:
                    break;
            }
            canvas.restore();
        }

        private void filled(Canvas canvas, Path path) {
            canvas.drawPath(path, fill);
            canvas.drawPath(path, stroke);
        }

        private void drawSpeed(Canvas canvas) {
            Path first = new Path();
            first.moveTo(3.5f, 6f);
            first.lineTo(11f, 12f);
            first.lineTo(3.5f, 18f);
            first.close();
            filled(canvas, first);
            Path second = new Path();
            second.moveTo(12.5f, 6f);
            second.lineTo(20f, 12f);
            second.lineTo(12.5f, 18f);
            second.close();
            filled(canvas, second);
        }

        private void drawEnhance(Canvas canvas) {
            filled(canvas, star(10.5f, 13.5f, 8.3f, 2.0f));
            Path small = star(19f, 5.3f, 3.3f, 0.9f);
            canvas.drawPath(small, solid);
            canvas.drawPath(small, stroke);
        }

        private Path star(float cx, float cy, float r, float k) {
            Path path = new Path();
            path.moveTo(cx, cy - r);
            path.quadTo(cx + k, cy - k, cx + r, cy);
            path.quadTo(cx + k, cy + k, cx, cy + r);
            path.quadTo(cx - k, cy + k, cx - r, cy);
            path.quadTo(cx - k, cy - k, cx, cy - r);
            path.close();
            return path;
        }

        private void drawHome(Canvas canvas) {
            Path house = new Path();
            house.moveTo(4.5f, 10.8f);
            house.lineTo(12f, 4.2f);
            house.lineTo(19.5f, 10.8f);
            house.lineTo(19.5f, 18.8f);
            house.quadTo(19.5f, 20.2f, 18.1f, 20.2f);
            house.lineTo(5.9f, 20.2f);
            house.quadTo(4.5f, 20.2f, 4.5f, 18.8f);
            house.close();
            filled(canvas, house);
            Path door = new Path();
            door.moveTo(9.8f, 20.2f);
            door.lineTo(9.8f, 15.6f);
            door.quadTo(9.8f, 14.6f, 10.8f, 14.6f);
            door.lineTo(13.2f, 14.6f);
            door.quadTo(14.2f, 14.6f, 14.2f, 15.6f);
            door.lineTo(14.2f, 20.2f);
            canvas.drawPath(door, stroke);
        }

        private void drawPerson(Canvas canvas) {
            canvas.drawCircle(12f, 8.2f, 3.7f, fill);
            canvas.drawCircle(12f, 8.2f, 3.7f, stroke);
            Path body = new Path();
            body.moveTo(5f, 20f);
            body.cubicTo(5f, 16.5f, 8f, 14.8f, 12f, 14.8f);
            body.cubicTo(16f, 14.8f, 19f, 16.5f, 19f, 20f);
            body.close();
            filled(canvas, body);
        }

        private void drawSidebar(Canvas canvas) {
            Path outer = new Path();
            outer.addRoundRect(new RectF(3.5f, 4.5f, 20.5f, 19.5f), 3.5f, 3.5f, Path.Direction.CW);
            canvas.drawPath(outer, fill);
            canvas.save();
            canvas.clipPath(outer);
            canvas.drawRect(new RectF(14.5f, 4f, 21f, 20f), pane);
            canvas.restore();
            canvas.drawPath(outer, stroke);
            canvas.drawLine(14.5f, 4.5f, 14.5f, 19.5f, stroke);
            canvas.drawLine(7f, 9.5f, 11f, 9.5f, stroke);
            canvas.drawLine(7f, 13f, 11f, 13f, stroke);
        }

        private void drawChevron(Canvas canvas) {
            Path path = new Path();
            path.moveTo(6.5f, 9.5f);
            path.lineTo(12f, 15f);
            path.lineTo(17.5f, 9.5f);
            canvas.drawPath(path, stroke);
        }

        private void drawTune(Canvas canvas) {
            float[][] knobs = {{9f, 7f}, {15.5f, 12f}, {8f, 17f}};
            float r = 2.3f;
            for (float[] knob : knobs) {
                canvas.drawLine(4f, knob[1], knob[0] - r, knob[1], stroke);
                canvas.drawLine(knob[0] + r, knob[1], 20f, knob[1], stroke);
                canvas.drawCircle(knob[0], knob[1], r, fill);
                canvas.drawCircle(knob[0], knob[1], r, stroke);
            }
        }
    }

    private static final class Group {
        final LinearLayout card;
        final LinearLayout content;
        final IconView chevron;
        final TextView subtitle;
        final int hue;
        final List<String> keys = new ArrayList<String>();
        boolean expanded;

        Group(LinearLayout card, LinearLayout content, IconView chevron, TextView subtitle, int hue) {
            this.card = card;
            this.content = content;
            this.chevron = chevron;
            this.subtitle = subtitle;
            this.hue = hue;
        }

        void refreshSubtitle() {
            int on = 0;
            for (String key : keys) if (get(key)) on++;
            if (keys.size() == 1) {
                subtitle.setText(on == 1 ? "On" : "Off");
            } else {
                subtitle.setText(titleCase(on + " of " + keys.size() + " on"));
            }
        }
    }

    private static final class Entry {
        final String group;
        final String key;
        final String title;
        final String summary;
        final boolean def;
        final boolean tiles;
        // Never attempted live, unlike tiles: hiding the bottom bar in place can strand
        // the user if they reached this screen via the bottom bar's own tab click (no
        // back stack, no back arrow) rather than the toolbar's Me tab icon (which pushes
        // its own back-navigable screen). Always falls through to the automatic reload
        // instead, which resets to the Local tab and so can never trap anyone.
        final boolean needsReload;

        Entry(String group, String key, String title, String summary, boolean def, boolean tiles) {
            this(group, key, title, summary, def, tiles, false);
        }

        Entry(
            String group, String key, String title, String summary,
            boolean def, boolean tiles, boolean needsReload
        ) {
            this.group = group;
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.def = def;
            this.tiles = tiles;
            this.needsReload = needsReload;
        }
    }

    private static final class IntEntry {
        final String group;
        final String key;
        final String title;
        final String summary;
        final int def;
        final int min;
        final int max;
        // Null means always shown. Non-null must name a boolean Entry's key in the
        // same build - the row tracks that switch live, no dialog rebuild needed.
        final String dependsOnKey;

        IntEntry(
            String group, String key, String title, String summary,
            int def, int min, int max, String dependsOnKey
        ) {
            this.group = group;
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.def = def;
            this.min = min;
            this.max = max;
            this.dependsOnKey = dependsOnKey;
        }
    }
}
