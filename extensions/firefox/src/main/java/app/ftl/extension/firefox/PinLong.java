package app.ftl.extension.firefox;

import android.content.Context;

public final class PinLong {

    private final Context context;
    private final Object item;

    PinLong(Context context, Object item) {
        this.context = context;
        this.item = item;
    }

    public Object invoke() {
        ExtensionPin.showDialog(context, item);
        return ModLambda.unit();
    }
}
