package app.ftl.extension.mobilism;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public final class KeywordDialog {
    private static final String SUB_TEXT = "Posts with any of these words in the title are hidden from lists.";

    private final Activity activity;
    private Dialog dialog;
    private boolean searching;

    private LinearLayout titleRow;
    private LinearLayout searchBar;
    private LinearLayout switchCard;
    private LinearLayout addRow;
    private TextView sub;
    private TextView count;
    private EditText addField;
    private EditText searchField;
    private View clearButton;
    private ModUi.CappedScroll scroll;
    private LinearLayout list;

    private KeywordDialog(Activity activity) {
        this.activity = activity;
    }

    public static void show(Activity activity) {
        new KeywordDialog(activity).open();
    }

    private void open() {
        final Activity a = activity;
        LinearLayout root = ModUi.sheet(a);

        titleRow = new LinearLayout(a);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ModUi.text(a, "Keyword filter", 22, ModUi.TEXT, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        count = ModUi.text(a, "0", 13, ModUi.ACCENT, true);
        count.setBackground(ModUi.rounded(a, ModUi.ACCENT_SOFT, 12));
        count.setGravity(Gravity.CENTER);
        count.setPadding(ModUi.dp(a, 12), ModUi.dp(a, 4), ModUi.dp(a, 12), ModUi.dp(a, 4));
        titleRow.addView(count);
        View searchButton = ModUi.iconButton(a, ModUi.Icon.SEARCH, ModUi.TEXT, ModUi.SURFACE_ALT, 40,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setSearching(true);
                    }
                });
        ((LinearLayout.LayoutParams) searchButton.getLayoutParams()).leftMargin = ModUi.dp(a, 10);
        titleRow.addView(searchButton);
        root.addView(titleRow);

        searchBar = new LinearLayout(a);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setVisibility(View.GONE);
        View backButton = ModUi.iconButton(a, ModUi.Icon.BACK, ModUi.TEXT, ModUi.SURFACE_ALT, 40,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setSearching(false);
                    }
                });
        searchBar.addView(backButton);
        searchField = ModUi.inputField(a, "Search added keywords");
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        fieldLp.leftMargin = ModUi.dp(a, 8);
        fieldLp.rightMargin = ModUi.dp(a, 4);
        searchBar.addView(searchField, fieldLp);
        clearButton = ModUi.iconButton(a, ModUi.Icon.CLOSE, ModUi.SUB, 0x00000000, 40,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        searchField.setText("");
                    }
                });
        clearButton.setVisibility(View.GONE);
        searchBar.addView(clearButton);
        root.addView(searchBar);

        sub = ModUi.text(a, SUB_TEXT, 13, ModUi.SUB, false);
        sub.setPadding(0, ModUi.dp(a, 6), 0, ModUi.dp(a, 12));
        root.addView(sub);

        switchCard = ModUi.card(a);
        switchCard.addView(ModUi.switchRow(a, "Hide in search results",
                "ON: matching apps are also hidden in search (applies to new searches).",
                "OFF: search shows every app, even matching ones.",
                TitleFilter.isSearchFilterEnabled(),
                new PillSwitch.Listener() {
                    @Override
                    public void onChanged(boolean checked) {
                        TitleFilter.setSearchFilterEnabled(checked);
                    }
                }));
        root.addView(switchCard);

        addRow = new LinearLayout(a);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addRow.setPadding(0, ModUi.dp(a, 14), 0, 0);
        addField = ModUi.inputField(a, "Add keyword, e.g. MX Player");
        addField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        addRow.addView(addField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView addButton = ModUi.circleButton(a, "+", ModUi.ACCENT, 0xFFFFFFFF, 44, 24);
        ((LinearLayout.LayoutParams) addButton.getLayoutParams()).leftMargin = ModUi.dp(a, 10);
        addRow.addView(addButton);
        root.addView(addRow);

        scroll = ModUi.cappedScroll(a, ModUi.heightFraction(a, 0.34f));
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, ModUi.dp(a, 12), 0, 0);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);

        LinearLayout footer = new LinearLayout(a);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.setPadding(0, ModUi.dp(a, 8), 0, 0);
        footer.addView(ModUi.textButton(a, "Done", ModUi.ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        }));
        root.addView(footer);

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        addField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                submit();
                return true;
            }
        });
        searchField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                hideKeyboard();
                return true;
            }
        });
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearButton.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                render();
            }
        });

        render();
        dialog = ModUi.show(a, root);
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface d, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && searching) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        setSearching(false);
                    }
                    return true;
                }
                return false;
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                hideKeyboard();
                TitleFilter.target = null;
            }
        });
    }

    private void setSearching(boolean on) {
        searching = on;
        titleRow.setVisibility(on ? View.GONE : View.VISIBLE);
        searchBar.setVisibility(on ? View.VISIBLE : View.GONE);
        switchCard.setVisibility(on ? View.GONE : View.VISIBLE);
        addRow.setVisibility(on ? View.GONE : View.VISIBLE);
        scroll.setMaxHeightPx(ModUi.heightFraction(activity, on ? 0.46f : 0.34f));
        if (on) {
            searchField.requestFocus();
            searchField.postDelayed(new Runnable() {
                @Override
                public void run() {
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }, 80);
        } else {
            hideKeyboard();
            searchField.setText("");
        }
        render();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && searchField != null) {
                imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private void submit() {
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
        TitleFilter.applyLive();
        render();
    }

    private void render() {
        list.removeAllViews();
        List<String> all = TitleFilter.getDisplayKeywords();
        count.setText(String.valueOf(all.size()));
        String query = searching ? searchField.getText().toString().trim() : "";
        String q = query.toLowerCase(Locale.ROOT);
        int shown = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            final String keyword = all.get(i);
            if (q.length() != 0 && !keyword.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            shown++;
            list.addView(row(keyword));
        }
        if (searching) {
            String info;
            if (all.isEmpty()) {
                info = "No keywords added yet.";
            } else if (q.length() == 0) {
                info = all.size() + (all.size() == 1 ? " keyword. Tap \u00d7 to remove." : " keywords. Tap \u00d7 to remove.");
            } else {
                info = shown + " of " + all.size() + " match. Tap \u00d7 to remove.";
            }
            sub.setText(info);
            sub.setVisibility(View.VISIBLE);
        } else {
            sub.setText(SUB_TEXT);
            sub.setVisibility(View.VISIBLE);
        }
        if (shown == 0) {
            String message = all.isEmpty()
                    ? "No keywords yet. Add one above."
                    : "No keywords match \u201c" + query + "\u201d.";
            TextView empty = ModUi.text(activity, message, 14, ModUi.SUB, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, ModUi.dp(activity, 20), 0, ModUi.dp(activity, 20));
            list.addView(empty);
        }
    }

    private View row(final String keyword) {
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

        View remove = ModUi.iconButton(activity, ModUi.Icon.CLOSE, ModUi.SUB, 0x00000000, 40,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TitleFilter.remove(keyword);
                        TitleFilter.applyLive();
                        render();
                    }
                });
        row.addView(remove);
        return row;
    }
}
