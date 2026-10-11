package app.ftl.extension.firefox;

@SuppressWarnings("unused")
public final class TabScrollClick {

    static final TabScrollClick UP = new TabScrollClick(false);
    static final TabScrollClick DOWN = new TabScrollClick(true);

    private final boolean down;

    private TabScrollClick(boolean down) {
        this.down = down;
    }

    public Object invoke() {
        TabScroll.go(down);
        return ModLambda.unit();
    }
}
