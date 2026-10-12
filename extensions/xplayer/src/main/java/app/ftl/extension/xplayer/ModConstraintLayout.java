package app.ftl.extension.xplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;

@SuppressWarnings("unused")
public class ModConstraintLayout extends ConstraintLayout {
    public ModConstraintLayout(Context context) {
        super(context);
    }

    public ModConstraintLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ModConstraintLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        try {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof ModGate) {
                    ((ModGate) child).runEarly(this);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
