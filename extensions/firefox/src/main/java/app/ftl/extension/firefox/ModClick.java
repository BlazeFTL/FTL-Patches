package app.ftl.extension.firefox;

import android.content.Context;

@SuppressWarnings("unused")
public final class ModClick {

    private final Context context;

    private ModClick(Context context) {
        this.context = context;
    }

    public static ModClick open(Context context) {
        return new ModClick(context);
    }

    public Object invoke() {
        ModSettings.open(context);
        return ModLambda.unit();
    }
}
