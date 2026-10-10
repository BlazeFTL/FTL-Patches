package app.ftl.extension.xplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

@SuppressWarnings("unused")
public class ModGate extends View {
    public ModGate(Context context) {
        super(context);
    }

    public ModGate(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ModGate(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        try {
            Object tag = getTag();
            ViewParent parent = getParent();
            if (tag instanceof String && parent instanceof ViewGroup) {
                ModViews.apply((String) tag, (ViewGroup) parent);
            }
        } catch (Throwable ignored) {
        }
    }
}
