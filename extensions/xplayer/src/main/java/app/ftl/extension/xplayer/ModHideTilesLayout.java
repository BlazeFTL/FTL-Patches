package app.ftl.extension.xplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

@SuppressWarnings("unused")
public class ModHideTilesLayout extends LinearLayout {
    public ModHideTilesLayout(Context context) {
        super(context);
    }

    public ModHideTilesLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ModHideTilesLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private boolean hidden() {
        return ModPrefs.get(getContext(), ModPrefs.KEY_HIDE_HOME_TILES, true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (hidden()) {
            setMeasuredDimension(0, 0);
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        try {
            ViewParent parent = getParent();
            if (hidden() && parent instanceof HorizontalScrollView) {
                View scroll = (View) parent;
                ViewGroup.LayoutParams params = scroll.getLayoutParams();
                if (params != null) {
                    params.width = 0;
                    params.height = 0;
                    scroll.setLayoutParams(params);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
