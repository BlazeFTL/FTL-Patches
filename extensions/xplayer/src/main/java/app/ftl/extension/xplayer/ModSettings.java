package app.ftl.extension.xplayer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

@SuppressWarnings("unused")
public final class ModSettings {
    private static final Section[] SECTIONS = {
            new Section("Home screen", new Entry[]{
                    new Entry(ModPrefs.KEY_HIDE_HOME_TILES, "Hide top tiles",
                            "Removes the tile row (All Videos, Downloader, Privacy, Cleaner) at the top of the home page.", true),
                    new Entry(ModPrefs.KEY_HIDE_RECENT, "Hide recent videos",
                            "Removes the recently played row from the home page.", true),
                    new Entry(ModPrefs.KEY_HIDE_BOTTOM_BAR, "Hide bottom bar",
                            "Hides the Video, Music and Playlist tab bar.", true),
                    new Entry(ModPrefs.KEY_HIDE_CAST, "Hide cast button",
                            "Removes the cast icon from the top bar menus.", false)
            }),
            new Section("Menus and lists", new Entry[]{
                    new Entry(ModPrefs.KEY_CLEAN_SHEETS, "Clean 3 dot menus",
                            "Removes Lock and Add to playlist from the video and folder menus.", false),
                    new Entry(ModPrefs.KEY_EXTRA_INFO, "Extra file info",
                            "Shows resolution, date, size and last played on videos, and count, size and path on folders.", true),
                    new Entry(ModPrefs.KEY_HIDE_FOLDER_ROWS, "Hide Recent Added and Directory",
                            "Removes the Recent Added and Directory rows from the folder list.", true)
            }),
            new Section("Player", new Entry[]{
                    new Entry(ModPrefs.KEY_CLEAN_PLAYER_MENU, "Clean player side menu",
                            "Removes audio, subtitle, cast, bookmark, favorite, play mode, brightness and volume from the side menu.", false),
                    new Entry(ModPrefs.KEY_HIDE_PLAYER_BUTTONS, "Hide cast / FF / FB buttons",
                            "Removes the cast and custom buttons from the top bar and the 10 second skip buttons.", false),
                    new Entry(ModPrefs.KEY_VOLUME_BOOST, "Volume boost",
                            "Raises the volume boost limit in the player and background playback.", false)
            })
    };

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

            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            final float d = metrics.density;
            final Palette palette = new Palette(activity);
            final boolean[] refresh = {false};

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(round(palette.surface, dp(d, 28)));
            root.setClipToOutline(true);
            root.addView(header(activity, palette, d));

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(d, 16), 0, dp(d, 16), dp(d, 8));
            for (int i = 0; i < SECTIONS.length; i++) {
                content.addView(sectionLabel(activity, palette, d, SECTIONS[i].title, i == 0));
                content.addView(card(activity, palette, d, SECTIONS[i].entries, refresh));
            }

            final int maxScroll = Math.max(dp(d, 140), (int) (metrics.heightPixels * 0.9f) - dp(d, 176));
            ScrollView scroll = new ScrollView(activity) {
                @Override
                protected void onMeasure(int widthSpec, int heightSpec) {
                    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxScroll, MeasureSpec.AT_MOST));
                }
            };
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.addView(content);
            root.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final Dialog dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            root.addView(footer(activity, palette, d, dialog));

            int width = Math.min(metrics.widthPixels - dp(d, 32), dp(d, 480));
            dialog.setContentView(root, new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.CENTER);
                WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.windowAnimations = android.R.style.Animation_Dialog;
                window.setAttributes(attributes);
            }
            dialog.setCanceledOnTouchOutside(true);
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    if (refresh[0] && !activity.isFinishing()) {
                        activity.recreate();
                    }
                }
            });
            dialog.show();
        } catch (Throwable ignored) {
        }
    }

    private static View header(Activity activity, Palette palette, float d) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(d, 24), dp(d, 24), dp(d, 24), dp(d, 12));

        Drawable icon = iconDrawable(activity);
        if (icon != null) {
            ImageView badge = new ImageView(activity);
            badge.setBackground(oval(alpha(palette.accent, 0x26)));
            badge.setPadding(dp(d, 10), dp(d, 10), dp(d, 10), dp(d, 10));
            badge.setImageDrawable(icon);
            badge.setImageTintList(ColorStateList.valueOf(palette.accent));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(d, 44), dp(d, 44));
            badgeParams.rightMargin = dp(d, 14);
            header.addView(badge, badgeParams);
        }

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(activity, "Mod Settings", 22, palette.primary, true));
        TextView subtitle = text(activity, "Tap a row to switch it on or off", 13, palette.secondary, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(d, 4);
        texts.addView(subtitle, subtitleParams);
        header.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private static View sectionLabel(Activity activity, Palette palette, float d, String title, boolean first) {
        TextView label = text(activity, title.toUpperCase(Locale.getDefault()), 12, palette.accent, true);
        label.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(d, 8);
        params.topMargin = dp(d, first ? 8 : 20);
        params.bottomMargin = dp(d, 8);
        label.setLayoutParams(params);
        return label;
    }

    private static View card(Activity activity, Palette palette, float d, Entry[] entries, boolean[] refresh) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(palette.card, dp(d, 18)));
        card.setClipToOutline(true);
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) {
                View divider = new View(activity);
                divider.setBackgroundColor(palette.divider);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                dividerParams.leftMargin = dp(d, 16);
                dividerParams.rightMargin = dp(d, 16);
                card.addView(divider, dividerParams);
            }
            card.addView(row(activity, palette, d, entries[i], refresh));
        }
        return card;
    }

    private static View row(final Activity activity, Palette palette, float d, Entry entry, final boolean[] refresh) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(d, 16), dp(d, 14), dp(d, 12), dp(d, 14));
        row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(alpha(palette.accent, 0x26)), null, new ColorDrawable(Color.BLACK)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(activity, entry.title, 16, palette.primary, true));
        TextView description = text(activity, entry.description, 13, palette.secondary, false);
        description.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(d, 3);
        texts.addView(description, descriptionParams);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.rightMargin = dp(d, 12);
        row.addView(texts, textsParams);

        final String key = entry.key;
        final boolean needsRefresh = entry.refresh;
        final Switch toggle = new Switch(activity);
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{palette.accent, palette.thumbOff}));
        toggle.setTrackTintList(new ColorStateList(states,
                new int[]{alpha(palette.accent, 0x80), alpha(palette.secondary, 0x55)}));
        toggle.setChecked(ModPrefs.get(activity, key, true));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                ModPrefs.put(activity, key, checked);
                activity.invalidateOptionsMenu();
                if (needsRefresh) {
                    refresh[0] = true;
                }
            }
        });
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle.toggle();
            }
        });
        return row;
    }

    private static View footer(Activity activity, Palette palette, float d, final Dialog dialog) {
        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(d, 16), dp(d, 8), dp(d, 24), dp(d, 20));

        TextView done = text(activity, "Done", 14, palette.onAccent, true);
        done.setGravity(Gravity.CENTER);
        done.setMinHeight(dp(d, 40));
        done.setPadding(dp(d, 28), 0, dp(d, 28), 0);
        done.setBackground(new RippleDrawable(
                ColorStateList.valueOf(alpha(palette.onAccent, 0x33)),
                round(palette.accent, dp(d, 20)),
                round(Color.BLACK, dp(d, 20))));
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        footer.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return footer;
    }

    private static Drawable iconDrawable(Activity activity) {
        try {
            String pkg = ModViews.packageOf((ViewGroup) activity.getWindow().getDecorView());
            int id = activity.getResources().getIdentifier("ic_mod_settings", "drawable", pkg);
            if (id != 0) {
                return activity.getResources().getDrawable(id, activity.getTheme());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static TextView text(Context context, CharSequence value, float sp, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (medium) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    private static GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private static int dp(float density, int value) {
        return (int) (value * density + 0.5f);
    }

    private static int alpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private static int blend(int base, int over, float amount) {
        int r = (int) (Color.red(base) * (1f - amount) + Color.red(over) * amount);
        int g = (int) (Color.green(base) * (1f - amount) + Color.green(over) * amount);
        int b = (int) (Color.blue(base) * (1f - amount) + Color.blue(over) * amount);
        return Color.rgb(r, g, b);
    }

    private static float luminance(int color) {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f;
    }

    private static int themeColor(Context context, int attr, int fallback) {
        try {
            TypedValue value = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, value, true)) {
                if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return value.data;
                }
                if (value.resourceId != 0) {
                    return context.getColor(value.resourceId);
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static final class Palette {
        final int surface;
        final int card;
        final int divider;
        final int primary;
        final int secondary;
        final int accent;
        final int onAccent;
        final int thumbOff;

        Palette(Context context) {
            boolean night = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int background = themeColor(context, android.R.attr.colorBackground, night ? 0xff121212 : Color.WHITE);
            boolean dark = luminance(background) < 0.5f;

            primary = dark ? 0xffececec : 0xff1c1c1c;
            secondary = dark ? 0xffa9a9a9 : 0xff6a6a6a;
            surface = dark ? blend(Color.rgb(Color.red(background), Color.green(background), Color.blue(background)),
                    Color.WHITE, 0.07f) : Color.WHITE;
            card = blend(surface, primary, dark ? 0.08f : 0.05f);
            divider = blend(card, primary, 0.1f);

            int resolved = themeColor(context, android.R.attr.colorAccent, 0xff12b000);
            accent = Color.rgb(Color.red(resolved), Color.green(resolved), Color.blue(resolved));
            onAccent = luminance(accent) > 0.65f ? 0xff111111 : Color.WHITE;
            thumbOff = dark ? 0xffbdbdbd : 0xfff4f4f4;
        }
    }

    private static final class Entry {
        final String key;
        final String title;
        final String description;
        final boolean refresh;

        Entry(String key, String title, String description, boolean refresh) {
            this.key = key;
            this.title = title;
            this.description = description;
            this.refresh = refresh;
        }
    }

    private static final class Section {
        final String title;
        final Entry[] entries;

        Section(String title, Entry[] entries) {
            this.title = title;
            this.entries = entries;
        }
    }
}
