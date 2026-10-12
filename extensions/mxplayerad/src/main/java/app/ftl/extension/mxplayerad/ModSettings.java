package app.ftl.extension.mxplayerad;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
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
        AlertDialog.Builder builder = new AlertDialog.Builder(host);
        final Context dc = builder.getContext();
        final Map<String, Boolean> initial = new HashMap<String, Boolean>();
        final boolean[] refreshFailed = {false};

        final int textColor = resolveColor(dc, android.R.attr.textColorPrimary, 0xFF222222);
        final int accent = resolveColor(dc, android.R.attr.colorControlActivated, 0xFF3D7BF4) | 0xFF000000;
        final int cardStroke = withAlpha(textColor, 0x26);
        final int dividerColor = withAlpha(textColor, 0x1A);
        final int rippleColor = withAlpha(textColor, 0x1F);
        final int chevronColor = withAlpha(textColor, 0xB0);

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

            final Group group = obtainGroup(dc, list, groups, entry.group, cardStroke, rippleColor,
                chevronColor, scroll);
            group.keys.add(entry.key);

            LinearLayout wrap = new LinearLayout(dc);
            wrap.setOrientation(LinearLayout.VERTICAL);
            if (group.content.getChildCount() > 0) wrap.addView(divider(dc, dividerColor));

            LinearLayout row = new LinearLayout(dc);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(dc, 16), dp(dc, 12), dp(dc, 16), dp(dc, 12));
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(ripple(dc, rippleColor, 0));

            LinearLayout texts = new LinearLayout(dc);
            texts.setOrientation(LinearLayout.VERTICAL);

            TextView title = new TextView(dc);
            title.setText(entry.title);
            title.setTextSize(15f);
            texts.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            if (entry.summary != null) {
                TextView summary = new TextView(dc);
                summary.setText(entry.summary);
                summary.setTextSize(12.5f);
                summary.setAlpha(0.65f);
                summary.setLineSpacing(dp(dc, 2), 1f);
                LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                summaryParams.topMargin = dp(dc, 3);
                texts.addView(summary, summaryParams);
            }

            row.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            final Switch toggle = new Switch(dc);
            toggle.setChecked(get(entry.key));
            toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
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
            if (group.content.getChildCount() > 0) wrap.addView(divider(dc, dividerColor));

            LinearLayout row = new LinearLayout(dc);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(dc, 16), dp(dc, 14), dp(dc, 16), dp(dc, 18));

            LinearLayout labelRow = new LinearLayout(dc);
            labelRow.setOrientation(LinearLayout.HORIZONTAL);
            labelRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(dc);
            title.setText(entry.title);
            title.setTextSize(15f);
            labelRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            final TextView valueChip = new TextView(dc);
            valueChip.setText(getInt(entry.key) + "%");
            valueChip.setTextSize(12.5f);
            valueChip.setTypeface(null, Typeface.BOLD);
            valueChip.setTextColor(accent);
            valueChip.setPadding(dp(dc, 10), dp(dc, 4), dp(dc, 10), dp(dc, 4));
            valueChip.setBackground(rounded(dc, withAlpha(accent, 0x26), 12));
            labelRow.addView(valueChip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(labelRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            SeekBar seekBar = new SeekBar(dc);
            seekBar.setMax(entry.max - entry.min);
            seekBar.setProgress(getInt(entry.key) - entry.min);
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
                summary.setAlpha(0.65f);
                summary.setLineSpacing(dp(dc, 2), 1f);
                LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                summaryParams.topMargin = dp(dc, 14);
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
            empty.setText("No configurable mods in this build.");
            empty.setPadding(dp(dc, 8), dp(dc, 16), dp(dc, 8), dp(dc, 16));
            list.addView(empty);
        }

        LinearLayout header = new LinearLayout(dc);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(dc, 24), dp(dc, 22), dp(dc, 24), dp(dc, 10));
        TextView headerTitle = new TextView(dc);
        headerTitle.setText("Mod Settings");
        headerTitle.setTextSize(22f);
        headerTitle.setTypeface(null, Typeface.BOLD);
        header.addView(headerTitle);
        TextView headerHint = new TextView(dc);
        headerHint.setText("Tap a section to expand");
        headerHint.setTextSize(13f);
        headerHint.setAlpha(0.6f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(dc, 2);
        header.addView(headerHint, hintParams);

        builder.setCustomTitle(header)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (!refreshFailed[0]) return;
                    for (Map.Entry<String, Boolean> before : initial.entrySet()) {
                        if (get(before.getKey()) != before.getValue().booleanValue()) {
                            restartActivity(host);
                            return;
                        }
                    }
                }
            })
            .show();
    }

    private static Group obtainGroup(
        final Context dc, LinearLayout list, final Map<String, Group> groups, String name,
        int cardStroke, int rippleColor, int chevronColor, final ScrollView scroll
    ) {
        Group existing = groups.get(name);
        if (existing != null) return existing;

        int hue = hueFor(name);

        LinearLayout card = new LinearLayout(dc);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(outlined(dc, cardStroke, 18));
        card.setClipToOutline(true);

        LinearLayout header = new LinearLayout(dc);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(dc, 72));
        header.setPadding(dp(dc, 16), dp(dc, 12), dp(dc, 16), dp(dc, 12));
        header.setClickable(true);
        header.setFocusable(true);
        header.setBackground(ripple(dc, rippleColor, 18));

        FrameLayout tile = new FrameLayout(dc);
        tile.setBackground(rounded(dc, withAlpha(hue, 0x2E), 14));
        IconView icon = new IconView(dc, iconFor(name), hue, 1.8f);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(dc, 24), dp(dc, 24));
        iconParams.gravity = Gravity.CENTER;
        tile.addView(icon, iconParams);
        header.addView(tile, new LinearLayout.LayoutParams(dp(dc, 44), dp(dc, 44)));

        LinearLayout texts = new LinearLayout(dc);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(dc);
        title.setText(name);
        title.setTextSize(16f);
        title.setTypeface(null, Typeface.BOLD);
        texts.addView(title);
        TextView subtitle = new TextView(dc);
        subtitle.setTextSize(12.5f);
        subtitle.setAlpha(0.65f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(dc, 1);
        texts.addView(subtitle, subtitleParams);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = dp(dc, 14);
        header.addView(texts, textsParams);

        IconView chevron = new IconView(dc, IconView.ICON_CHEVRON, chevronColor, 1.8f);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(dc, 22), dp(dc, 22));
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

        final Group group = new Group(card, content, chevron, subtitle);
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

    private static View divider(Context dc, int color) {
        View divider = new View(dc);
        divider.setBackgroundColor(color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(dc, 1) / 2));
        params.leftMargin = dp(dc, 16);
        params.rightMargin = dp(dc, 16);
        divider.setLayoutParams(params);
        return divider;
    }

    private static GradientDrawable outlined(Context context, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x00000000);
        drawable.setStroke(Math.max(1, dp(context, 1)), strokeColor);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    private static GradientDrawable rounded(Context context, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    private static Drawable ripple(Context context, int color, int radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFF000000);
        mask.setCornerRadius(dp(context, radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(color), null, mask);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int hueFor(String groupName) {
        if ("SpeedUp overlay".equals(groupName)) return 0xFFFF9800;
        if ("Smart Enhance".equals(groupName)) return 0xFF9C6BFF;
        if ("Home screen".equals(groupName)) return 0xFF3D7BF4;
        if ("Me tab".equals(groupName)) return 0xFF22B573;
        if ("Sidebar".equals(groupName)) return 0xFF00ACC1;
        return 0xFF3D7BF4;
    }

    private static int iconFor(String groupName) {
        if ("SpeedUp overlay".equals(groupName)) return IconView.ICON_SPEED;
        if ("Smart Enhance".equals(groupName)) return IconView.ICON_ENHANCE;
        if ("Home screen".equals(groupName)) return IconView.ICON_HOME;
        if ("Me tab".equals(groupName)) return IconView.ICON_PERSON;
        if ("Sidebar".equals(groupName)) return IconView.ICON_SIDEBAR;
        return IconView.ICON_ENHANCE;
    }

    /**
     * Resolves a themed color attribute to a raw ARGB int, working around
     * android.R.attr.textColorSecondary/Primary resolving to a ColorStateList *reference*
     * rather than a raw color (see the comment on the title TextView above) by falling back
     * to Context#getColor(int) when that happens.
     */
    private static int resolveColor(Context dc, int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (!dc.getTheme().resolveAttribute(attr, value, true)) return fallback;
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        try {
            return dc.getColor(value.resourceId);
        } catch (Exception ignored) {
            return fallback;
        }
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

    /**
     * Small stroke-only vector icon drawn on a 24x24 virtual grid (no drawable/resource files,
     * so no resource patch or public.xml entry needed) - matches the thin round-cap stroke
     * style already used by the Me tab's own ic_me_tab_mod_settings.xml.
     */
    private static final class IconView extends View {
        static final int ICON_SPEED = 0;
        static final int ICON_ENHANCE = 1;
        static final int ICON_HOME = 2;
        static final int ICON_PERSON = 3;
        static final int ICON_SIDEBAR = 4;
        static final int ICON_CHEVRON = 5;

        private final int kind;
        private final Paint paint;

        IconView(Context context, int kind, int color, float strokeWidthPx) {
            super(context);
            this.kind = kind;
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidthPx);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
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
                default:
                    break;
            }
            canvas.restore();
        }

        private void drawSpeed(Canvas canvas) {
            canvas.drawArc(new RectF(3f, 4f, 21f, 22f), 150f, 240f, false, paint);
            canvas.drawLine(12f, 5.8f, 12f, 7.4f, paint);
            canvas.drawLine(4.8f, 13f, 6.4f, 13f, paint);
            canvas.drawLine(17.6f, 13f, 19.2f, 13f, paint);
            canvas.drawLine(12f, 13f, 16.2f, 8.8f, paint);
            Paint.Style style = paint.getStyle();
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            canvas.drawCircle(12f, 13f, 1.4f, paint);
            paint.setStyle(style);
        }

        private void drawEnhance(Canvas canvas) {
            drawStar(canvas, 10.5f, 13.5f, 8.5f, 1.4f);
            Paint.Style style = paint.getStyle();
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            drawStar(canvas, 19f, 5.5f, 3.3f, 0.7f);
            paint.setStyle(style);
        }

        private void drawStar(Canvas canvas, float cx, float cy, float r, float k) {
            Path path = new Path();
            path.moveTo(cx, cy - r);
            path.quadTo(cx + k, cy - k, cx + r, cy);
            path.quadTo(cx + k, cy + k, cx, cy + r);
            path.quadTo(cx - k, cy + k, cx - r, cy);
            path.quadTo(cx - k, cy - k, cx, cy - r);
            path.close();
            canvas.drawPath(path, paint);
        }

        private void drawHome(Canvas canvas) {
            Path roof = new Path();
            roof.moveTo(3.5f, 11f);
            roof.lineTo(12f, 3.8f);
            roof.lineTo(20.5f, 11f);
            canvas.drawPath(roof, paint);
            Path body = new Path();
            body.moveTo(5.5f, 9.8f);
            body.lineTo(5.5f, 20f);
            body.lineTo(10f, 20f);
            body.lineTo(10f, 14.5f);
            body.lineTo(14f, 14.5f);
            body.lineTo(14f, 20f);
            body.lineTo(18.5f, 20f);
            body.lineTo(18.5f, 9.8f);
            canvas.drawPath(body, paint);
        }

        private void drawPerson(Canvas canvas) {
            canvas.drawCircle(12f, 8f, 3.8f, paint);
            Path shoulders = new Path();
            shoulders.moveTo(5f, 20.5f);
            shoulders.cubicTo(5f, 16.5f, 8f, 15f, 12f, 15f);
            shoulders.cubicTo(16f, 15f, 19f, 16.5f, 19f, 20.5f);
            canvas.drawPath(shoulders, paint);
        }

        private void drawSidebar(Canvas canvas) {
            canvas.drawRoundRect(new RectF(3.5f, 4.5f, 20.5f, 19.5f), 3f, 3f, paint);
            canvas.drawLine(14.5f, 4.5f, 14.5f, 19.5f, paint);
            canvas.drawLine(7f, 9.5f, 11f, 9.5f, paint);
            canvas.drawLine(7f, 13f, 11f, 13f, paint);
        }

        private void drawChevron(Canvas canvas) {
            Path path = new Path();
            path.moveTo(6.5f, 9.5f);
            path.lineTo(12f, 15f);
            path.lineTo(17.5f, 9.5f);
            canvas.drawPath(path, paint);
        }
    }

    private static final class Group {
        final LinearLayout card;
        final LinearLayout content;
        final IconView chevron;
        final TextView subtitle;
        final List<String> keys = new ArrayList<String>();
        boolean expanded;

        Group(LinearLayout card, LinearLayout content, IconView chevron, TextView subtitle) {
            this.card = card;
            this.content = content;
            this.chevron = chevron;
            this.subtitle = subtitle;
        }

        void refreshSubtitle() {
            int on = 0;
            for (String key : keys) if (get(key)) on++;
            if (keys.size() == 1) {
                subtitle.setText(on == 1 ? "On" : "Off");
            } else {
                subtitle.setText(on + " of " + keys.size() + " on");
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
