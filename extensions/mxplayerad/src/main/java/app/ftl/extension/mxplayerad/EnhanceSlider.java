package app.ftl.extension.mxplayerad;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;

public final class EnhanceSlider implements SeekBar.OnSeekBarChangeListener {
    public interface Host {
        void onEnhancePercent(int percent);
    }

    private static final float MAX_LEVEL = 0.18f;

    private final Host host;
    private PopupWindow popup;
    private TextView label;

    public EnhanceSlider(Host host) {
        this.host = host;
    }

    public void show(View anchor, float level) {
        dismiss();
        Context context = anchor.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        float pad = 16f * density;

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding((int) pad, (int) pad, (int) pad, (int) pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC1A1A1A);
        bg.setCornerRadius(pad);
        box.setBackgroundDrawable(bg);

        int percent = level <= 0f ? 0 : Math.round(level / MAX_LEVEL * 100f);
        if (percent > 100) percent = 100;

        label = new TextView(context);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setTextSize(16f);
        label.setText(percent + "%");
        box.addView(label);

        SeekBar bar = new SeekBar(context);
        bar.setMax(100);
        bar.setProgress(percent);
        bar.setOnSeekBarChangeListener(this);
        box.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        PopupWindow window = new PopupWindow(
                box, (int) (300f * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setOutsideTouchable(true);
        window.setFocusable(false);
        window.setBackgroundDrawable(new ColorDrawable(0));
        popup = window;
        window.showAsDropDown(anchor, 0, 0);
    }

    public void dismiss() {
        PopupWindow window = popup;
        popup = null;
        label = null;
        if (window != null && window.isShowing()) {
            window.dismiss();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (!fromUser) return;
        TextView text = label;
        if (text != null) {
            text.setText(progress + "%");
        }
        host.onEnhancePercent(progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}
}
