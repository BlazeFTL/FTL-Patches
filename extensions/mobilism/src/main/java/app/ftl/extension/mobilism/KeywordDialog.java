package app.ftl.extension.mobilism;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KeywordDialog {
    private static final String SUB_TEXT = "Posts with any of these words in the title are hidden. Separate several with commas.";
    private static final String OFF_TEXT = "Keyword filtering is off. Every post is shown.";

    private final Activity activity;
    private Dialog dialog;
    private boolean searching;
    private boolean tuneOpen;

    private LinearLayout titleRow;
    private LinearLayout searchBar;
    private LinearLayout tunePanel;
    private LinearLayout addRow;
    private TextView sub;
    private TextView count;
    private EditText addField;
    private EditText searchField;
    private View clearButton;
    private ModUi.Icon addButton;
    private ModUi.Icon tuneButton;
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
        TextView title = ModUi.text(a, "Keyword filter", 20, ModUi.TEXT, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        count = ModUi.text(a, "0", 12, ModUi.ON_TEXT, true);
        count.setBackground(ModUi.rounded(a, ModUi.ACCENT_SOFT, 12));
        count.setGravity(Gravity.CENTER);
        count.setPadding(ModUi.dp(a, 10), ModUi.dp(a, 3), ModUi.dp(a, 10), ModUi.dp(a, 3));
        titleRow.addView(count);
        View searchButton = ModUi.iconButton(a, ModUi.Icon.SEARCH, ModUi.TEXT, Color.TRANSPARENT, 38,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setSearching(true);
                    }
                });
        ((LinearLayout.LayoutParams) searchButton.getLayoutParams()).leftMargin = ModUi.dp(a, 6);
        titleRow.addView(searchButton);
        tuneButton = ModUi.iconButton(a, ModUi.Icon.TUNE, ModUi.TEXT, Color.TRANSPARENT, 38,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setTuneOpen(!tuneOpen);
                    }
                });
        titleRow.addView(tuneButton);
        root.addView(titleRow);

        searchBar = new LinearLayout(a);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setVisibility(View.GONE);
        View backButton = ModUi.iconButton(a, ModUi.Icon.BACK, ModUi.TEXT, Color.TRANSPARENT, 38,
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
        fieldLp.leftMargin = ModUi.dp(a, 6);
        fieldLp.rightMargin = ModUi.dp(a, 4);
        searchBar.addView(searchField, fieldLp);
        clearButton = ModUi.iconButton(a, ModUi.Icon.CLOSE, ModUi.SUB, Color.TRANSPARENT, 38,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        searchField.setText("");
                    }
                });
        clearButton.setVisibility(View.GONE);
        searchBar.addView(clearButton);
        root.addView(searchBar);

        sub = ModUi.text(a, SUB_TEXT, 12.5f, ModUi.SUB, false);
        sub.setPadding(0, ModUi.dp(a, 4), 0, ModUi.dp(a, 10));
        root.addView(sub);

        tunePanel = ModUi.card(a);
        ModUi.addRow(a, tunePanel, ModUi.switchRow(a, ModUi.Icon.EYEOFF, "Keyword filtering",
                "On: matching posts are hidden",
                "Off: every post is shown",
                TitleFilter.isEnabled(),
                new PillSwitch.Listener() {
                    @Override
                    public void onChanged(boolean checked) {
                        TitleFilter.setEnabled(checked);
                        TitleFilter.applyLive();
                        render();
                    }
                }));
        ModUi.addRow(a, tunePanel, ModUi.switchRow(a, ModUi.Icon.SEARCH, "Hide in search results",
                "Matching apps are hidden in search too",
                "Search shows every app",
                TitleFilter.isSearchFilterEnabled(),
                new PillSwitch.Listener() {
                    @Override
                    public void onChanged(boolean checked) {
                        TitleFilter.setSearchFilterEnabled(checked);
                    }
                }));
        tunePanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams tuneLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tuneLp.bottomMargin = ModUi.dp(a, 10);
        root.addView(tunePanel, tuneLp);

        addRow = new LinearLayout(a);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addField = ModUi.inputField(a, "Add keywords, e.g. MX Player, Video Player");
        addField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        addRow.addView(addField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addButton = ModUi.iconButton(a, ModUi.Icon.PLUS, ModUi.SUB, ModUi.LINE, 44, null);
        ((LinearLayout.LayoutParams) addButton.getLayoutParams()).leftMargin = ModUi.dp(a, 8);
        addRow.addView(addButton);
        root.addView(addRow);

        scroll = ModUi.cappedScroll(a, ModUi.heightFraction(a, 0.3f));
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, ModUi.dp(a, 12), 0, 0);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);

        LinearLayout footer = new LinearLayout(a);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.setPadding(0, ModUi.dp(a, 12), 0, 0);
        footer.addView(ModUi.filledButton(a, "Done", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commitPending();
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        }));
        root.addView(footer);

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commitPending();
            }
        });
        addField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                commitPending();
                return true;
            }
        });
        addField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateAddButton();
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

        updateAddButton();
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
                commitPending();
                TitleFilter.target = null;
            }
        });
    }

    private void updateAddButton() {
        boolean has = addField.getText().toString().trim().length() != 0;
        addButton.setColor(has ? Color.WHITE : ModUi.SUB);
        addButton.setBackground(ModUi.ripple(activity, has ? ModUi.ACCENT : ModUi.LINE, 22, 0x33FFFFFF));
    }

    private void setTuneOpen(boolean on) {
        tuneOpen = on;
        tunePanel.setVisibility(on && !searching ? View.VISIBLE : View.GONE);
        tuneButton.setColor(on ? ModUi.ACCENT : ModUi.TEXT);
    }

    private void setSearching(boolean on) {
        searching = on;
        titleRow.setVisibility(on ? View.GONE : View.VISIBLE);
        searchBar.setVisibility(on ? View.VISIBLE : View.GONE);
        tunePanel.setVisibility(!on && tuneOpen ? View.VISIBLE : View.GONE);
        addRow.setVisibility(on ? View.GONE : View.VISIBLE);
        scroll.setMaxHeightPx(ModUi.heightFraction(activity, on ? 0.5f : 0.3f));
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

    private void commitPending() {
        String raw = addField.getText().toString();
        if (raw.trim().length() == 0) {
            return;
        }
        int added = 0;
        int skipped = 0;
        for (String part : raw.split("[,\\n]+")) {
            String keyword = part.trim();
            if (keyword.length() == 0) {
                continue;
            }
            if (TitleFilter.has(keyword)) {
                skipped++;
                continue;
            }
            TitleFilter.add(keyword);
            added++;
        }
        addField.setText("");
        if (added > 0) {
            TitleFilter.applyLive();
            render();
        }
        String message;
        if (added == 0) {
            message = "Already in the list";
        } else if (skipped > 0) {
            message = "Added " + added + ", " + skipped + " already in the list";
        } else {
            message = added == 1 ? "Keyword added" : added + " keywords added";
        }
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private void render() {
        list.removeAllViews();
        List<String> all = TitleFilter.getDisplayKeywords();
        count.setText(String.valueOf(all.size()));
        String query = searching ? searchField.getText().toString().trim() : "";
        String q = query.toLowerCase(Locale.ROOT);
        ArrayList<String> matches = new ArrayList<String>();
        for (int i = all.size() - 1; i >= 0; i--) {
            String keyword = all.get(i);
            if (q.length() != 0 && !keyword.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            matches.add(keyword);
        }
        int shown = matches.size();
        if (shown > 0) {
            LinearLayout card = ModUi.card(activity);
            for (int i = 0; i < shown; i++) {
                ModUi.addRow(activity, card, row(matches.get(i)));
            }
            list.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        } else {
            sub.setText(TitleFilter.isEnabled() ? SUB_TEXT : OFF_TEXT);
        }
        sub.setVisibility(View.VISIBLE);
        if (shown == 0) {
            String message = all.isEmpty()
                    ? "No keywords yet. Add one above."
                    : "No keywords match \u201c" + query + "\u201d.";
            TextView empty = ModUi.text(activity, message, 13, ModUi.SUB, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, ModUi.dp(activity, 16), 0, ModUi.dp(activity, 16));
            list.addView(empty);
        }
    }

    private View row(final String keyword) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ModUi.dp(activity, 46));
        row.setPadding(ModUi.dp(activity, 16), ModUi.dp(activity, 2), ModUi.dp(activity, 6), ModUi.dp(activity, 2));

        TextView label = ModUi.text(activity, keyword, 15, ModUi.TEXT, false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View remove = ModUi.iconButton(activity, ModUi.Icon.CLOSE, ModUi.SUB, Color.TRANSPARENT, 38,
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
