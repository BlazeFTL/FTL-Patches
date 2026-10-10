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

    void run(ViewGroup scope) {
        try {
            Object tag = getTag();
            if (tag instanceof String) {
                ModViews.apply((String) tag, scope);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            run((ViewGroup) parent);
        }
    }
}
