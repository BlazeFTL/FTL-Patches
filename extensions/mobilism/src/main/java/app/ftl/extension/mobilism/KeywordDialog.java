package app.ftl.extension.mobilism;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public final class KeywordDialog {
    private KeywordDialog() {
    }

    public static void show(final Activity activity) {
        final LinearLayout root = ModUi.sheet(activity);

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ModUi.text(activity, "Keyword filter", 22, ModUi.TEXT, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView count = ModUi.text(activity, "0", 13, ModUi.ACCENT, true);
        count.setBackground(ModUi.rounded(activity, ModUi.ACCENT_SOFT, 12));
        count.setGravity(Gravity.CENTER);
        count.setPadding(ModUi.dp(activity, 12), ModUi.dp(activity, 4), ModUi.dp(activity, 12), ModUi.dp(activity, 4));
        titleRow.addView(count);
        root.addView(titleRow);

        TextView sub = ModUi.text(activity, "Posts with any of these words in the title are hidden from lists.", 13, ModUi.SUB, false);
        sub.setPadding(0, ModUi.dp(activity, 2), 0, ModUi.dp(activity, 12));
        root.addView(sub);

        LinearLayout switchCard = ModUi.card(activity);
        switchCard.addView(ModUi.switchRow(activity, "Hide in search results",
                "Also filter the search screen. Applies to new searches.",
                TitleFilter.isSearchFilterEnabled(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        TitleFilter.setSearchFilterEnabled(checked);
                    }
                }));
        root.addView(switchCard);

        LinearLayout addRow = new LinearLayout(activity);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addRow.setPadding(0, ModUi.dp(activity, 14), 0, 0);
        final EditText addField = ModUi.inputField(activity, "Add keyword, e.g. MX Player");
        addField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        addRow.addView(addField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView addButton = ModUi.circleButton(activity, "+", ModUi.ACCENT, 0xFFFFFFFF, 44, 24);
        ((LinearLayout.LayoutParams) addButton.getLayoutParams()).leftMargin = ModUi.dp(activity, 10);
        addRow.addView(addButton);
        root.addView(addRow);

        final EditText searchField = ModUi.inputField(activity, "Search added keywords");
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = ModUi.dp(activity, 10);
        root.addView(searchField, searchLp);

        ScrollView scroll = ModUi.cappedScroll(activity, ModUi.heightFraction(activity, 0.32f));
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, ModUi.dp(activity, 12), 0, 0);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.setPadding(0, ModUi.dp(activity, 8), 0, 0);
        final Dialog[] ref = new Dialog[1];
        footer.addView(ModUi.textButton(activity, "Done", ModUi.ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ref[0] != null) {
                    ref[0].dismiss();
                }
            }
        }));
        root.addView(footer);

        final Runnable render = new Runnable() {
            @Override
            public void run() {
                renderList(activity, list, count, searchField.getText().toString(), this);
            }
        };

        final Runnable submit = new Runnable() {
            @Override
            public void run() {
                String keyword = addField.getText().toString().replace(",", " ").trim();
                if (keyword.length() == 0) {
                    return;
                }
                if (TitleFilter.has(keyword)) {
                    Toast.makeText(activity, "Already in the list", Toast.LENGTH_SHORT).show();
                    return;
                }
                TitleFilter.add(keyword);
                addField.setText("");
                searchField.setText("");
                TitleFilter.applyLive();
                render.run();
            }
        };

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit.run();
            }
        });
        addField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                submit.run();
                return true;
            }
        });
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                render.run();
            }
        });

        render.run();
        ref[0] = ModUi.show(activity, root);
        ref[0].setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                TitleFilter.target = null;
            }
        });
    }

    private static void renderList(final Activity activity, final LinearLayout list, TextView count,
                                   String query, final Runnable rerender) {
        list.removeAllViews();
        List<String> all = TitleFilter.getDisplayKeywords();
        count.setText(String.valueOf(all.size()));
        String q = query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            final String keyword = all.get(i);
            if (q.length() != 0 && !keyword.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            shown++;
            list.addView(row(activity, keyword, rerender));
        }
        if (shown == 0) {
            String message = all.isEmpty()
                    ? "No keywords yet. Add one above."
                    : "No keywords match \u201c" + query.trim() + "\u201d.";
            TextView empty = ModUi.text(activity, message, 14, ModUi.SUB, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, ModUi.dp(activity, 20), 0, ModUi.dp(activity, 20));
            list.addView(empty);
        }
    }

    private static View row(final Activity activity, final String keyword, final Runnable rerender) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(ModUi.rounded(activity, ModUi.SURFACE_ALT, 14));
        row.setPadding(ModUi.dp(activity, 16), ModUi.dp(activity, 4), ModUi.dp(activity, 6), ModUi.dp(activity, 4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ModUi.dp(activity, 8);
        row.setLayoutParams(lp);

        TextView label = ModUi.text(activity, keyword, 16, ModUi.TEXT, false);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView remove = ModUi.circleButton(activity, "\u00d7", 0x00000000, ModUi.SUB, 40, 22);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TitleFilter.remove(keyword);
                TitleFilter.applyLive();
                rerender.run();
            }
        });
        row.addView(remove);
        return row;
    }
}
