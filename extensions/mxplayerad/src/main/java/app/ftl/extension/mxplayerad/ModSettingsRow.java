package app.ftl.extension.mxplayerad;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

public class ModSettingsRow extends LinearLayout {
    public ModSettingsRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ModSettings.showDialog(view.getContext());
            }
        });
    }
}
