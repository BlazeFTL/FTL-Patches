package app.ftl.extension.firefox;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public final class RangeSelectStart {

    private static final String TAG = "MorpheFirefox";

    private final Object listState;
    private final Object state;

    RangeSelectStart(Object listState, Object state) {
        this.listState = listState;
        this.state = state;
    }

    public Object invoke(Object offset) {
        try {
            press(offset);
        } catch (Throwable t) {
            Log.e(TAG, "range long press failed", t);
        }
        return ModLambda.unit();
    }

    private void press(Object offset) throws Exception {
        if (listState == null || state == null || offset == null) return;
        Field packedField = offset.getClass().getDeclaredField("packedValue");
        packedField.setAccessible(true);
        long packed = packedField.getLong(offset);

        Object layout = RangeSelect.call(listState, "getLayoutInfo");
        boolean vertical = ((Enum) RangeSelect.call(layout, "getOrientation")).ordinal() == 0;
        int position = (int) Float.intBitsToFloat(vertical ? (int) packed : (int) (packed >> 32));

        Object hit = null;
        for (Object item : (List) RangeSelect.call(layout, "getVisibleItemsInfo")) {
            int offsetStart = (Integer) RangeSelect.call(item, "getOffset");
            int end = offsetStart + (Integer) RangeSelect.call(item, "getSize");
            if (offsetStart <= position && position <= end) {
                hit = item;
                break;
            }
        }
        if (hit == null) return;

        haptic();
        Object callback = RangeSelect.read(state, "onLongPress");
        Method invoke = callback.getClass().getMethod("invoke", Object.class);
        invoke.setAccessible(true);
        invoke.invoke(callback, hit);
    }

    private void haptic() {
        try {
            Object feedback = RangeSelect.read(state, "hapticFeedback");
            for (Method method : feedback.getClass().getMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (method.getName().startsWith("performHapticFeedback") && p.length == 1 && p[0] == int.class) {
                    method.setAccessible(true);
                    method.invoke(feedback, 0);
                    return;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "range select haptic failed", t);
        }
    }
}
