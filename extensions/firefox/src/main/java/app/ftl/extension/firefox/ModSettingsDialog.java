package app.ftl.extension.firefox;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

final class ModSettingsDialog {

    private final Activity activity;
    private final Context app;
    private final float density;
    private final boolean night;

    private final int background;
    private final int card;
    private final int stroke;
    private final int text;
    private final int textMuted;
    private final int accent;

    private final View[] cards = new View[2];
    private final RadioView[] radios = new RadioView[2];
    private TextView hint;
    private boolean oldSelected;

    private ModSettingsDialog(Activity activity) {
        this.activity = activity;
        this.app = activity.getApplicationContext() != null ? activity.getApplicationContext() : activity;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.night = (activity.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        background = night ? 0xFF2B2A33 : 0xFFFFFFFF;
        card = night ? 0xFF38373F : 0xFFF9F9FB;
        stroke = night ? 0xFF52525E : 0xFFE0E0E6;
        text = night ? 0xFFFBFBFE : 0xFF15141A;
        textMuted = night ? 0xFFBFBFC9 : 0xFF5B5B66;
        accent = resolveAccent();
        oldSelected = ModSettings.isOldMenu(app);
    }

    static void show(Activity activity) {
        new ModSettingsDialog(activity).build().show();
    }

    private int resolveAccent() {
        TypedValue value = new TypedValue();
        if (activity.getTheme().resolveAttribute(android.R.attr.colorAccent, value, true)
            && value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT
            && Color.alpha(value.data) == 255) {
            return value.data;
        }
        return night ? 0xFFC69FFF : 0xFF6B4DFF;
    }

    private Dialog build() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(18));
        root.setBackground(round(background, 28, 0, 0));

        root.addView(header());
        root.addView(label("3 DOT MENU STYLE"), margins(0, 22, 0, 8));

        cards[0] = option(true, "Old style menu",
            "Compact popup with flat rows and dividers", "Recommended");
        cards[1] = option(false, "Bottom sheet menu",
            "Firefox default sheet that slides up from the bottom", "Stock");
        root.addView(cards[0]);
        root.addView(cards[1], margins(0, 10, 0, 0));

        root.addView(label("EXTENSIONS"), margins(0, 20, 0, 8));
        root.addView(pinRow());

        hint = new TextView(activity);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        hint.setTextColor(textMuted);
        hint.setGravity(Gravity.CENTER);
        hint.setText("Changes apply the next time you open the menu");
        root.addView(hint, margins(0, 16, 0, 14));

        root.addView(doneButton(dialog));
        refresh();

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.addView(root);
        dialog.setContentView(scroll);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screen = activity.getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(dp(380), screen - dp(32)), ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.5f);
            window.setWindowAnimations(android.R.style.Animation_Dialog);
        }
        return dialog;
    }

    private View header() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout badge = new FrameLayout(activity);
        badge.setBackground(round(withAlpha(accent, night ? 0x40 : 0x22), 16, 0, 0));
        ImageView icon = new ImageView(activity);
        try {
            Drawable drawable = activity.getDrawable(OldMenu.modIcon(activity));
            if (drawable != null) {
                drawable = drawable.mutate();
                drawable.setTint(accent);
                icon.setImageDrawable(drawable);
            }
        } catch (Throwable ignored) {
        }
        badge.addView(icon, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        row.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(activity);
        title.setText("Mod Settings");
        title.setTextColor(text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView subtitle = new TextView(activity);
        subtitle.setText("Customize your Firefox");
        subtitle.setTextColor(textMuted);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titles.addView(title);
        titles.addView(subtitle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(14);
        row.addView(titles, lp);
        return row;
    }

    private TextView label(String value) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(accent);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        view.setLetterSpacing(0.08f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(dp(4), 0, 0, 0);
        return view;
    }

    private View option(final boolean old, String title, String description, String tag) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(14), dp(12));
        row.setClickable(true);

        row.addView(new PreviewView(activity, old), new LinearLayout.LayoutParams(dp(60), dp(68)));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(activity);
        name.setText(title);
        name.setTextColor(text);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleRow.addView(name);

        TextView chip = new TextView(activity);
        chip.setText(tag);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        chip.setTextColor(old ? accent : textMuted);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setPadding(dp(7), dp(2), dp(7), dp(2));
        chip.setBackground(round(old ? withAlpha(accent, 0x26) : withAlpha(textMuted, 0x22), 8, 0, 0));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.leftMargin = dp(8);
        titleRow.addView(chip, chipParams);

        TextView desc = new TextView(activity);
        desc.setText(description);
        desc.setTextColor(textMuted);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        desc.setPadding(0, dp(3), 0, 0);

        column.addView(titleRow);
        column.addView(desc);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.leftMargin = dp(12);
        columnParams.rightMargin = dp(10);
        row.addView(column, columnParams);

        RadioView radio = new RadioView(activity);
        row.addView(radio, new LinearLayout.LayoutParams(dp(24), dp(24)));
        radios[old ? 0 : 1] = radio;

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (oldSelected != old) {
                    oldSelected = old;
                    ModSettings.save(app, old);
                    hint.setText("Saved. Close and reopen the menu to apply");
                    hint.setTextColor(accent);
                    refresh();
                }
            }
        });
        return row;
    }

    private View pinRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(14), dp(12));
        row.setBackground(round(card, 18, 1, stroke));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(activity);
        name.setText("Pin extensions to search bar");
        name.setTextColor(text);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView desc = new TextView(activity);
        desc.setText("Long-press an extension in the menu to pin it beside the address bar");
        desc.setTextColor(textMuted);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        desc.setPadding(0, dp(3), 0, 0);
        column.addView(name);
        column.addView(desc);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.rightMargin = dp(12);
        row.addView(column, columnParams);

        final Switch toggle = new Switch(activity);
        toggle.setChecked(ModSettings.isPinSaved(app));
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{accent, night ? 0xFFBFBFC9 : 0xFF8F8F9D}));
        toggle.setTrackTintList(new ColorStateList(states,
            new int[]{withAlpha(accent, 0x66), night ? 0xFF52525E : 0xFFD7D7DB}));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                ModSettings.savePin(app, checked);
                ExtensionPin.applySetting();
                hint.setText(checked ? "Extension pinning enabled" : "Extension pinning disabled");
                hint.setTextColor(accent);
            }
        });
        row.addView(toggle);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle.toggle();
            }
        });
        return row;
    }

    private View doneButton(final Dialog dialog) {
        TextView done = new TextView(activity);
        done.setText("Done");
        done.setGravity(Gravity.CENTER);
        done.setTextColor(night ? 0xFF15141A : 0xFFFFFFFF);
        done.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        done.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        done.setClickable(true);
        Drawable content = round(accent, 24, 0, 0);
        done.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, null));
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        done.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return done;
    }

    private void refresh() {
        for (int i = 0; i < 2; i++) {
            boolean selected = (i == 0) == oldSelected;
            int fill = selected ? withAlpha(accent, night ? 0x2E : 0x14) : card;
            Drawable content = selected ? round(fill, 18, 2, accent) : round(fill, 18, 1, stroke);
            cards[i].setBackground(new RippleDrawable(
                ColorStateList.valueOf(withAlpha(accent, 0x22)), content, null));
            radios[i].setChecked(selected);
        }
    }

    private GradientDrawable round(int color, float radiusDp, float strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp * density);
        if (strokeDp > 0) drawable.setStroke(Math.max(1, (int) (strokeDp * density)), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private int dp(float value) {
        return (int) (value * density + 0.5f);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private final class RadioView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path check = new Path();
        private boolean checked;

        RadioView(Context context) {
            super(context);
        }

        void setChecked(boolean value) {
            checked = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float c = w / 2f;
            float r = c - dp(2);
            if (checked) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(accent);
                canvas.drawCircle(c, c, r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(night ? 0xFF15141A : 0xFFFFFFFF);
                check.reset();
                check.moveTo(w * 0.30f, w * 0.52f);
                check.lineTo(w * 0.45f, w * 0.66f);
                check.lineTo(w * 0.72f, w * 0.36f);
                canvas.drawPath(check, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(stroke);
                canvas.drawCircle(c, c, r, paint);
            }
        }
    }

    private final class PreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean old;

        PreviewView(Context context, boolean old) {
            super(context);
            this.old = old;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float radius = dp(10);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(accent, night ? 0x33 : 0x1F));
            canvas.drawRoundRect(new RectF(0, 0, w, h), radius, radius, paint);

            paint.setColor(withAlpha(textMuted, 0x44));
            canvas.drawRoundRect(new RectF(dp(8), dp(9), w * 0.55f, dp(14)), dp(3), dp(3), paint);
            canvas.drawRoundRect(new RectF(dp(8), dp(19), w * 0.4f, dp(24)), dp(3), dp(3), paint);

            RectF menu = old
                ? new RectF(w * 0.36f, dp(7), w - dp(5), h * 0.74f)
                : new RectF(dp(4), h * 0.40f, w - dp(4), h - dp(3));
            paint.setColor(night ? 0xFF4A4953 : 0xFFFFFFFF);
            canvas.drawRoundRect(menu, dp(7), dp(7), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density));
            paint.setColor(withAlpha(accent, 0x66));
            canvas.drawRoundRect(menu, dp(7), dp(7), paint);

            paint.setStyle(Paint.Style.FILL);
            float left = menu.left + dp(6);
            float right = menu.right - dp(6);
            float y = menu.top + dp(old ? 8 : 12);
            if (!old) {
                paint.setColor(withAlpha(textMuted, 0x77));
                float mid = (menu.left + menu.right) / 2f;
                canvas.drawRoundRect(new RectF(mid - dp(8), menu.top + dp(4), mid + dp(8), menu.top + dp(6.5f)),
                    dp(2), dp(2), paint);
            }
            int rows = old ? 4 : 3;
            for (int i = 0; i < rows; i++) {
                paint.setColor(i == 0 ? accent : withAlpha(textMuted, 0x66));
                canvas.drawCircle(left + dp(2.5f), y, dp(2.2f), paint);
                paint.setColor(withAlpha(textMuted, i == 0 ? 0xAA : 0x55));
                canvas.drawRoundRect(new RectF(left + dp(8), y - dp(1.5f), right, y + dp(1.5f)),
                    dp(1.5f), dp(1.5f), paint);
                y += dp(old ? 9 : 8);
            }
        }
    }
}
