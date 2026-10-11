package app.ftl.extension.firefox;

@SuppressWarnings("unused")
public final class TabFab {

    private TabFab() {
    }

    public static boolean endButton() {
        return ModSettings.tabNext() && ModSettings.tabEnd();
    }
}
