package app.ftl.extension.mobilism;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class ItemMenuDialog {
    private static final int PURPLE_SOFT = 0xFFEDEDFF;

    private ItemMenuDialog() {
    }

    public static void show(final Context context, final Object adapter, String subject) {
        final String name = TitleFilter.baseName(subject);
        if (name.length() == 0) {
            return;
        }
        final Dialog[] ref = new Dialog[1];
        LinearLayout root = ModUi.sheet(context);

        TextView title = ModUi.text(context, name, 20, ModUi.TEXT, true);
        title.setMaxLines(3);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(title);

        TextView sub = ModUi.text(context, "Choose what to do with this app.", 13, ModUi.SUB, false);
        sub.setPadding(0, ModUi.dp(context, 2), 0, ModUi.dp(context, 14));
        root.addView(sub);

        root.addView(action(context, "Hide this app",
                "Adds \u201c" + name + "\u201d to your Keyword filter, so every post with this name disappears from the lists. "
                        + "Undo any time from the 3-dot menu \u203a Keyword filter.",
                ModUi.ACCENT_SOFT, ModUi.ACCENT, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (ref[0] != null) {
                            ref[0].dismiss();
                        }
                        if (!TitleFilter.has(name)) {
                            TitleFilter.add(name);
                        }
                        if (adapter != null) {
                            TitleFilter.refilterTop(adapter);
                        }
                        Toast.makeText(context, "Hidden: " + name, Toast.LENGTH_SHORT).show();
                    }
                }));

        root.addView(action(context, "Copy name",
                "Copies \u201c" + name + "\u201d to the clipboard. Nothing is hidden.",
                PURPLE_SOFT, ModUi.PURPLE, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (ref[0] != null) {
                            ref[0].dismiss();
                        }
                        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("title", name));
                        }
                        Toast.makeText(context, "Copied: " + name, Toast.LENGTH_SHORT).show();
                    }
                }));

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(android.view.Gravity.END);
        footer.setPadding(0, ModUi.dp(context, 8), 0, 0);
        footer.addView(ModUi.textButton(context, "Cancel", ModUi.SUB, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ref[0] != null) {
                    ref[0].dismiss();
                }
            }
        }));
        root.addView(footer);

        ref[0] = ModUi.show(context, root);
    }

    private static View action(Context c, String title, String desc, int bg, int accent, View.OnClickListener l) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ModUi.ripple(c, bg, 18));
        card.setPadding(ModUi.dp(c, 16), ModUi.dp(c, 14), ModUi.dp(c, 16), ModUi.dp(c, 14));
        card.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ModUi.dp(c, 10);
        card.setLayoutParams(lp);

        card.addView(ModUi.text(c, title, 16, accent, true));
        TextView d = ModUi.text(c, desc, 13, ModUi.TEXT, false);
        d.setPadding(0, ModUi.dp(c, 4), 0, 0);
        d.setLineSpacing(0, 1.1f);
        card.addView(d);
        return card;
    }
}
