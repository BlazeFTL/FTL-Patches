package app.ftl.extension.firefox;

@SuppressWarnings("unused")
public final class RangeSelectNoOp {

    public Object invoke() {
        RangeSelect.endGesture();
        return TabAccess.unit();
    }

    public Object invoke(Object a, Object b) {
        return TabAccess.unit();
    }
}
