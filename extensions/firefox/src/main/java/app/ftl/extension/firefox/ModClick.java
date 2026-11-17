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

    // Context comes from the menu dialog (activity themed), so no Compose LocalContext read is needed.
    public static ModClick open() {
        return new ModClick(OldMenu.context());
    }

    public Object invoke() {
        if (context != null) ModSettings.open(context);
        return TabAccess.unit();
    }
}
