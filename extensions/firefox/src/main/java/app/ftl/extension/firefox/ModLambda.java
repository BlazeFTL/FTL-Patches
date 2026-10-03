package app.ftl.extension.firefox;

import android.content.Context;

@SuppressWarnings("unused")
public final class ModLambda {

    private static final int EXTENSIONS_PAGE = 0;
    private static final int LIBRARY_LIST = 1;
    private static final int MOD_ROW = 2;
    private static final int OPEN_MOD_SETTINGS = 3;

    private static Object unit;

    private final int kind;
    private final Object a;
    private final Object b;
    private final Object c;
    private final Object d;

    private ModLambda(int kind, Object a, Object b, Object c, Object d) {
        this.kind = kind;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    public static ModLambda extensionsPage(Object content, Object onBack) {
        return new ModLambda(EXTENSIONS_PAGE, content, onBack, null, null);
    }

    public static ModLambda libraryList(Object history, Object bookmarks, Object downloads, Object passwords) {
        return new ModLambda(LIBRARY_LIST, history, bookmarks, downloads, passwords);
    }

    public static ModLambda modRow() {
        return new ModLambda(MOD_ROW, null, null, null, null);
    }

    public static ModLambda openModSettings(Context context) {
        return new ModLambda(OPEN_MOD_SETTINGS, context, null, null, null);
    }

    private static Object unit() {
        if (unit == null) {
            try {
                unit = Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
            } catch (Throwable ignored) {
            }
        }
        return unit;
    }

    public Object invoke() {
        if (kind == OPEN_MOD_SETTINGS) ModSettings.open((Context) a);
        return unit();
    }

    public Object invoke(Object composer, Object flags) {
        switch (kind) {
            case EXTENSIONS_PAGE:
                ModCompose.extensionsPage(a, b, composer);
                break;
            case LIBRARY_LIST:
                ModCompose.libraryList(a, b, c, d, composer);
                break;
            case MOD_ROW:
                ModCompose.modRowContent(composer);
                break;
            default:
                break;
        }
        return unit();
    }
}
