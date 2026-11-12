package app.ftl.extension.snaptube;

import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public final class SnaptubeToolsHider {

    private static final String TAG = "MorpheSnaptube";

    private static final String[] SECTION_IDS = {
        "tv_whatsapp_clean_title",
        "cl_whatsapp_clean_cards",
        "tv_system_clean_title",
        "cl_system_clean_cards",
    };

    private static final String TILE_LABEL_ID = "tv_tool_name";

    private static final String[] HIDDEN_TILE_IDS = {
        "tool_vault",
        "tool_status_saver",
        "tool_file_recover",
        "tool_share",
        "tool_feedback",
    };

    private SnaptubeToolsHider() {
    }

    public static void hide(View root) {
        if (root == null) return;
        try {
            Resources res = root.getResources();
            String pkg = root.getContext().getPackageName();

            for (String name : SECTION_IDS) {
                int id = idOf(res, pkg, name);
                if (id == 0) continue;
                View view = root.findViewById(id);
                if (view != null) removeView(view);
            }

            int labelId = idOf(res, pkg, TILE_LABEL_ID);
            if (labelId == 0) return;

            List<View> labels = new ArrayList<>();
            collect(root, labelId, labels);

            Map<ViewGroup, List<View>> rows = new LinkedHashMap<>();
            Map<View, Boolean> hidden = new LinkedHashMap<>();

            for (View label : labels) {
                ViewParent tileParent = label.getParent();
                if (!(tileParent instanceof View)) continue;
                View tile = (View) tileParent;
                ViewParent rowParent = tile.getParent();
                if (!(rowParent instanceof ViewGroup)) continue;

                String name = entryName(res, tile.getId());
                boolean hide = shouldHide(name);
                Log.i(TAG, "tools tile " + name + " hide=" + hide);

                hidden.put(tile, hide);
                ViewGroup row = (ViewGroup) rowParent;
                List<View> tiles = rows.get(row);
                if (tiles == null) {
                    tiles = new ArrayList<>();
                    rows.put(row, tiles);
                }
                tiles.add(tile);
            }

            for (Map.Entry<ViewGroup, List<View>> entry : rows.entrySet()) {
                ViewGroup row = entry.getKey();
                List<View> tiles = entry.getValue();

                List<View> visible = new ArrayList<>();
                List<View> gone = new ArrayList<>();
                for (View tile : tiles) {
                    if (Boolean.TRUE.equals(hidden.get(tile))) gone.add(tile);
                    else visible.add(tile);
                }

                for (View tile : gone) tile.setVisibility(View.GONE);

                if (visible.isEmpty()) {
                    removeView(row);
                } else if (visible.size() == 1 && gone.size() == 1) {
                    alignToStart(row, visible.get(0), gone.get(0));
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "hide tools failed; target views may have changed", t);
        }
    }

    private static void alignToStart(final ViewGroup row, final View visible, final View gone) {
        row.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                ViewTreeObserver observer = row.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);

                int width = row.getWidth();
                if (width <= 0) return true;

                boolean rtl = row.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                int start = rtl ? width - visible.getRight() : visible.getLeft();
                if (start <= width / 4) return true;

                ViewGroup.LayoutParams visibleParams = visible.getLayoutParams();
                ViewGroup.LayoutParams goneParams = gone.getLayoutParams();
                visible.setLayoutParams(goneParams);
                gone.setLayoutParams(visibleParams);
                return false;
            }
        });
    }

    private static boolean shouldHide(String name) {
        for (String hidden : HIDDEN_TILE_IDS) {
            if (hidden.equals(name)) return true;
        }
        return false;
    }

    private static void removeView(View view) {
        ViewParent parent = view.getParent();
        if (parent instanceof LinearLayout) {
            ((ViewGroup) parent).removeView(view);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private static void collect(View view, int id, List<View> out) {
        if (view.getId() == id) out.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), id, out);
            }
        }
    }

    private static int idOf(Resources res, String pkg, String name) {
        return res.getIdentifier(name, "id", pkg);
    }

    private static String entryName(Resources res, int id) {
        if (id == View.NO_ID) return "";
        try {
            return res.getResourceEntryName(id);
        } catch (Resources.NotFoundException e) {
            return "";
        }
    }
}
