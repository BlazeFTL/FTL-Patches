package app.ftl.extension.firefox;

@SuppressWarnings("unused")
public final class TabScrollClick {

    static final TabScrollClick INSTANCE = new TabScrollClick();

    private TabScrollClick() {
    }

    public Object invoke() {
        TabScroll.toggle();
        return ModLambda.unit();
    }
}
