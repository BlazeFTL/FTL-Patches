package app.ftl.extension.mobilism;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class ItemMenuDialog {
    private ItemMenuDialog() {
    }

    public static void show(final Context context, final Object adapter, String subject) {
        final String name = TitleFilter.baseName(subject);
        if (name.length() == 0) {
            return;
        }
        final Dialog[] ref = new Dialog[1];
        LinearLayout root = ModUi.sheet(context);

        TextView title = ModUi.text(context, name, 18, ModUi.TEXT, true);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(title);

        TextView sub = ModUi.text(context, "Choose what to do with this app.", 12.5f, ModUi.SUB, false);
        sub.setPadding(0, ModUi.dp(context, 3), 0, ModUi.dp(context, 12));
        root.addView(sub);

        LinearLayout card = ModUi.card(context);
        ModUi.addRow(context, card, action(context, ModUi.Icon.EYEOFF, ModUi.ACCENT, "Hide this app",
                "Adds \u201c" + name + "\u201d to your Keyword filter, so every post with this name disappears from the lists. "
                        + "Undo any time from the 3-dot menu \u203a Keyword filter.",
                new View.OnClickListener() {
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
        ModUi.addRow(context, card, action(context, ModUi.Icon.COPY, ModUi.TEXT, "Copy name",
                "Copies \u201c" + name + "\u201d to the clipboard. Nothing is hidden.",
                new View.OnClickListener() {
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
        root.addView(card);

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END);
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

    private static View action(Context c, int icon, int iconColor, String title, String desc, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setBackground(ModUi.ripple(c, Color.TRANSPARENT, 0));
        row.setPadding(ModUi.dp(c, 14), ModUi.dp(c, 12), ModUi.dp(c, 14), ModUi.dp(c, 12));
        row.setOnClickListener(l);

        ModUi.Icon ic = new ModUi.Icon(c, icon, iconColor);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ModUi.dp(c, 22), ModUi.dp(c, 22));
        ilp.rightMargin = ModUi.dp(c, 12);
        ilp.topMargin = ModUi.dp(c, 1);
        row.addView(ic, ilp);

        LinearLayout labels = new LinearLayout(c);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(ModUi.text(c, title, 15, ModUi.TEXT, true));
        TextView d = ModUi.text(c, desc, 12.5f, ModUi.SUB, false);
        d.setPadding(0, ModUi.dp(c, 2), 0, 0);
        labels.addView(d);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }
}
