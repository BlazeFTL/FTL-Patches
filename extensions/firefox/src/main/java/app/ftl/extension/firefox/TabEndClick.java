package app.ftl.extension.firefox;

@SuppressWarnings("unused")
public final class TabEndClick {

    private final Object base;

    private TabEndClick(Object base) {
        this.base = base;
    }

    public static TabEndClick of(Object base) {
        return new TabEndClick(base);
    }

    public Object invoke() {
        TabOrder.markEnd();
        ExtensionPin.invokeFunction0(base);
        return ModLambda.unit();
    }
}
