package juloo.keyboard2;

import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchReplaceController {

    private final Context context;
    private final KeyEventHandler.IReceiver receiver;
    private final ClipboardHistoryService clipboardService;
    private Dialog dialog;
    private String mOriginalText = "";
    private int mSelectionStart = 0;
    private int mSelectionEnd = 0;


    private SearchAdapter adapter;
    private List<ClipboardItem> allItems;
    private boolean isClipboardMode = false;

    public SearchReplaceController(Context context, KeyEventHandler.IReceiver receiver) {
        this.context = context;
        this.receiver = receiver;
        this.clipboardService = ClipboardHistoryService.get_service(context);
    }

    public void showSearchReplaceDialog() {

        captureEditorState();


        Context themeContext = new android.view.ContextThemeWrapper(context, Config.globalConfig().theme);
        dialog = new Dialog(context);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(themeContext).inflate(R.layout.dialog_search_replace, null);
        dialog.setContentView(view);

        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.0f;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }

        dialog.getWindow().setAttributes(params);


        final LinearLayout root = dialog.findViewById(R.id.search_replace_root);
        final Button modeEditor = dialog.findViewById(R.id.mode_editor);
        final Button modeClipboard = dialog.findViewById(R.id.mode_clipboard);
        final EditText searchInput = dialog.findViewById(R.id.search_input);


        final LinearLayout editorContainer = dialog.findViewById(R.id.editor_mode_container);
        final EditText replaceInput = dialog.findViewById(R.id.replace_input);
        final CheckBox regexCheck = dialog.findViewById(R.id.check_regex);
        final CheckBox caseCheck = dialog.findViewById(R.id.check_case);
        final CheckBox wholeWordCheck = dialog.findViewById(R.id.check_whole_word);
        final Button btnFindPrev = dialog.findViewById(R.id.btn_find_prev);
        final Button btnFindNext = dialog.findViewById(R.id.btn_find_next);
        final Button btnReplace = dialog.findViewById(R.id.btn_replace);
        final Button btnReplaceAll = dialog.findViewById(R.id.btn_replace_all);
        final ImageButton btnClose = dialog.findViewById(R.id.btn_close);


        final LinearLayout clipboardContainer = dialog.findViewById(R.id.clipboard_mode_container);
        final CheckBox typingHistoryCheck = dialog.findViewById(R.id.check_typing_history);
        final RecyclerView recyclerView = dialog.findViewById(R.id.search_results_recycler);


        applyTheme(root, searchInput, replaceInput, regexCheck, caseCheck, wholeWordCheck, typingHistoryCheck);
        applyThemeToButton(btnFindPrev);
        applyThemeToButton(btnFindNext);
        applyThemeToButton(btnReplace);
        applyThemeToButton(btnReplaceAll);

        Theme theme = new Theme(themeContext, null);
        modeEditor.setTextColor(theme.labelColor);
        modeClipboard.setTextColor(theme.labelColor);
        if (btnClose != null) {
             btnClose.setColorFilter(theme.labelColor);
             btnClose.setOnClickListener(v -> dialog.dismiss());
        }


        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new SearchAdapter();
        recyclerView.setAdapter(adapter);


        View.OnClickListener modeListener = v -> {
            boolean editorMode = v.getId() == R.id.mode_editor;
            isClipboardMode = !editorMode;

            editorContainer.setVisibility(editorMode ? View.VISIBLE : View.GONE);
            clipboardContainer.setVisibility(editorMode ? View.GONE : View.VISIBLE);

            modeEditor.setAlpha(editorMode ? 1.0f : 0.5f);
            modeClipboard.setAlpha(editorMode ? 0.5f : 1.0f);


            WindowManager.LayoutParams p = dialog.getWindow().getAttributes();
            if (isClipboardMode) {

                p.height = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.6);
            } else {

                p.height = WindowManager.LayoutParams.WRAP_CONTENT;
            }
            dialog.getWindow().setAttributes(p);

            if (isClipboardMode) {

                loadItems(typingHistoryCheck.isChecked());
                filter(searchInput.getText().toString());
            } else {

                captureEditorState();
            }
        };

        modeEditor.setOnClickListener(modeListener);
        modeClipboard.setOnClickListener(modeListener);


        modeEditor.performClick();


        btnFindNext.setOnClickListener(v -> performFind(searchInput.getText().toString(), regexCheck.isChecked(), caseCheck.isChecked(), wholeWordCheck.isChecked(), true));
        btnFindPrev.setOnClickListener(v -> performFind(searchInput.getText().toString(), regexCheck.isChecked(), caseCheck.isChecked(), wholeWordCheck.isChecked(), false));
        btnReplace.setOnClickListener(v -> performReplace(searchInput.getText().toString(), replaceInput.getText().toString(), regexCheck.isChecked(), caseCheck.isChecked(), wholeWordCheck.isChecked()));
        btnReplaceAll.setOnClickListener(v -> performReplaceAll(searchInput.getText().toString(), replaceInput.getText().toString(), regexCheck.isChecked(), caseCheck.isChecked(), wholeWordCheck.isChecked()));


        typingHistoryCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isClipboardMode) {
                loadItems(isChecked);
                filter(searchInput.getText().toString());
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isClipboardMode) {
                    filter(s.toString());
                }
            }
        });

        dialog.show();
        searchInput.requestFocus();
    }

    private void captureEditorState() {
        InputConnection ic = receiver.getCurrentInputConnection();
        if (ic != null) {
            ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
            if (et != null && et.text != null) {
                mOriginalText = et.text.toString();
                mSelectionStart = et.selectionStart;
                mSelectionEnd = et.selectionEnd;
            } else {

                CharSequence before = ic.getTextBeforeCursor(100000, 0);
                CharSequence after = ic.getTextAfterCursor(100000, 0);
                mOriginalText = (before == null ? "" : before.toString()) + (after == null ? "" : after.toString());
                mSelectionStart = (before == null ? 0 : before.length());
                mSelectionEnd = mSelectionStart;
            }
        }
    }



    private void loadItems(boolean useTypingHistory) {
        if (useTypingHistory) {
            allItems = clipboardService.getTypingHistory();
        } else {
            allItems = clipboardService.getItems();
        }
    }

    private void filter(String query) {
        if (allItems == null) return;
        List<ClipboardItem> filtered = new ArrayList<>();
        String q = query.toLowerCase();
        for (ClipboardItem item : allItems) {
            if (item.getText().toLowerCase().contains(q) ||
               (item.getName() != null && item.getName().toLowerCase().contains(q))) {
                filtered.add(item);
            }
        }
        adapter.setItems(filtered);
    }

    private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private List<ClipboardItem> items = new ArrayList<>();

        void setItems(List<ClipboardItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.clipboard_grid_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ClipboardItem item = items.get(position);
            holder.text.setText(item.getText());
            holder.pinIcon.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);
            holder.menuButton.setVisibility(View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (context instanceof Keyboard2) {
                    ((Keyboard2) context).setPendingCommitText(item.getText());
                }
                dialog.dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView text;
            final View pinIcon;
            final View menuButton;

            ViewHolder(View view) {
                super(view);
                text = view.findViewById(R.id.clipboard_item_text);
                pinIcon = view.findViewById(R.id.clipboard_item_pin_icon);
                menuButton = view.findViewById(R.id.clipboard_item_menu);
                text.setMaxLines(3);
            }
        }
    }



    private Pattern createPattern(String search, boolean useRegex, boolean matchCase, boolean wholeWord) {
        int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE;
        String patternStr;
        if (useRegex) {
            patternStr = search;
        } else {
            patternStr = Pattern.quote(search);
        }
        if (wholeWord) {
            patternStr = "\\b" + patternStr + "\\b";
        }
        return Pattern.compile(patternStr, flags);
    }

    private void performFind(String search, boolean useRegex, boolean matchCase, boolean wholeWord, boolean forward) {
        if (search.isEmpty()) return;

        Pattern pattern;
        try {
            pattern = createPattern(search, useRegex, matchCase, wholeWord);
        } catch (Exception e) {
            Toast.makeText(context, "Invalid Regex", Toast.LENGTH_SHORT).show();
            return;
        }

        Matcher matcher = pattern.matcher(mOriginalText);
        boolean found = false;
        int start = -1;
        int end = -1;

        if (forward) {
            while (matcher.find()) {
                if (matcher.start() >= mSelectionEnd) {
                    start = matcher.start();
                    end = matcher.end();
                    found = true;
                    break;
                }
            }
            if (!found) {
                matcher.reset();
                if (matcher.find()) {
                    start = matcher.start();
                    end = matcher.end();
                    found = true;
                }
            }
        } else {
            int lastStart = -1;
            int lastEnd = -1;
            while (matcher.find()) {
                if (matcher.end() <= mSelectionStart) {
                    lastStart = matcher.start();
                    lastEnd = matcher.end();
                } else {
                    break;
                }
            }
            if (lastStart != -1) {
                start = lastStart;
                end = lastEnd;
                found = true;
            }
            if (!found) {
                 while (matcher.find()) {
                     lastStart = matcher.start();
                     lastEnd = matcher.end();
                 }
                 if (lastStart != -1) {
                     start = lastStart;
                     end = lastEnd;
                     found = true;
                 }
            }
        }

        if (found) {
            mSelectionStart = start;
            mSelectionEnd = end;
            if (context instanceof Keyboard2) {
                ((Keyboard2) context).setPendingSelection(start, end);
            }


        } else {
            Toast.makeText(context, "Not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void performReplace(String search, String replace, boolean useRegex, boolean matchCase, boolean wholeWord) {
        if (search.isEmpty()) return;

        int end = Math.min(mSelectionEnd, mOriginalText.length());
        int start = Math.min(mSelectionStart, end);

        String selectedText = "";
        if (end > start) {
            selectedText = mOriginalText.substring(start, end);
        }

        Pattern pattern;
        try {
            pattern = createPattern(search, useRegex, matchCase, wholeWord);
        } catch (Exception e) { return; }

        Matcher m = pattern.matcher(selectedText);
        boolean selectionMatches = m.matches();

        if (selectionMatches) {
            String replacement;
            if (useRegex) {
                replacement = m.replaceAll(replace);
            } else {
                replacement = replace;
            }

            if (context instanceof Keyboard2) {
                ((Keyboard2) context).setPendingSelection(start, end);
                ((Keyboard2) context).setPendingCommitText(replacement);
            }


            mOriginalText = mOriginalText.substring(0, start) + replacement + mOriginalText.substring(end);
            mSelectionStart = start + replacement.length();
            mSelectionEnd = mSelectionStart;


            performFind(search, useRegex, matchCase, wholeWord, true);
        } else {
            performFind(search, useRegex, matchCase, wholeWord, true);
        }
    }

    private void performReplaceAll(String search, String replace, boolean useRegex, boolean matchCase, boolean wholeWord) {
        if (search.isEmpty()) return;

        try {
            Pattern pattern = createPattern(search, useRegex, matchCase, wholeWord);
            Matcher matcher = pattern.matcher(mOriginalText);

            String replacementStr = useRegex ? replace : Matcher.quoteReplacement(replace);
            String newText = matcher.replaceAll(replacementStr);

            if (newText.equals(mOriginalText)) {
                Toast.makeText(context, "No matches found.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (context instanceof Keyboard2) {

                ((Keyboard2) context).setPendingSelection(0, mOriginalText.length());
                ((Keyboard2) context).setPendingCommitText(newText);
            }

            mOriginalText = newText;
            mSelectionStart = 0;
            mSelectionEnd = 0;

            dialog.dismiss();
            Toast.makeText(context, "Replaced all occurrences.", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
             Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }



    private void applyTheme(LinearLayout root, EditText search, EditText replace, CheckBox regex, CheckBox matchCase, CheckBox wholeWord, CheckBox history) {
        Context themeContext = new android.view.ContextThemeWrapper(context, Config.globalConfig().theme);
        Theme theme = new Theme(themeContext, null);

        root.setBackgroundColor(theme.colorKey);
        search.setTextColor(theme.labelColor);
        search.setHintTextColor(theme.secondaryLabelColor);
        if (replace != null) {
            replace.setTextColor(theme.labelColor);
            replace.setHintTextColor(theme.secondaryLabelColor);
        }
        regex.setTextColor(theme.labelColor);
        matchCase.setTextColor(theme.labelColor);
        if (wholeWord != null) wholeWord.setTextColor(theme.labelColor);
        if (history != null) history.setTextColor(theme.labelColor);
    }

    private void applyThemeToButton(Button btn) {
        btn.setTextColor(0xFFFFFFFF);
    }
}
