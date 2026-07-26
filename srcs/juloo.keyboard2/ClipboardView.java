package juloo.keyboard2;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClipboardView extends FrameLayout implements ClipboardHistoryService.OnClipboardHistoryChange {

    private ClipboardHistoryService service;
    private ClipboardAdapter adapter;
    private RecyclerView recyclerView;

    private static class DisplayItem {
        static final int TYPE_MAIN_FOLDER = 0;
        static final int TYPE_DATE_FOLDER = 1;
        static final int TYPE_CLIPBOARD = 2;

        final int type;
        final String id;
        final String name;
        final ClipboardItem item;
        boolean expanded = false;

        DisplayItem(int type, String id, String name) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.item = null;
        }

        DisplayItem(ClipboardItem item) {
            this.type = TYPE_CLIPBOARD;
            this.id = item.getId();
            this.name = null;
            this.item = item;
        }
    }
    private FrameLayout undoContainer;
    private TextView undoButton;
    private ClipboardItem recentlyRemovedItem;
    private ClipboardItem recentlyRenamedItem;
    private Keyboard2.Receiver keyboardReceiver;
    private Handler handler = new Handler();
    private String _currentSearchQuery = "";
    private final Set<String> savedExpandedIds = new HashSet<>();
    private boolean isSearching = false;
    private boolean isShowingTypingHistory = false;
    private int mFixedKeyboardHeight = -1;
    private boolean multiSelectMode = false;

    public enum SortMode {
        LATEST, OLDEST, LARGEST, SMALLEST,
        PINNED_LATEST, PINNED_OLDEST, PINNED_LARGEST, PINNED_SMALLEST,
        ARCHIVED_LATEST, ARCHIVED_OLDEST, ARCHIVED_LARGEST, ARCHIVED_SMALLEST
    }

    private SortMode currentSortMode = SortMode.LATEST;

    public void setKeyboardHeight(int height) {
        mFixedKeyboardHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mFixedKeyboardHeight > 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(mFixedKeyboardHeight, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private Runnable hideUndoRunnable = () -> {
        undoContainer.setVisibility(View.GONE);
        recentlyRemovedItem = null;
    };

    public void setKeyboardReceiver(Keyboard2.Receiver receiver) {
        this.keyboardReceiver = receiver;
    }

    private void performPaste(String text) {
        ClipboardHistoryService.paste(text);
    }

    public void finishRenaming(String newName) {
        if (recentlyRenamedItem != null && service != null) {
            service.renameItem(recentlyRenamedItem, newName);
        }
        recentlyRenamedItem = null;
    }

    public void showUndoPaste() {
        undoButton.setText("Undo Paste");
        undoContainer.setVisibility(View.VISIBLE);
        undoContainer.setOnClickListener(v -> {
            if (keyboardReceiver != null) {
                keyboardReceiver.undoLastPaste();
            }
            undoContainer.setVisibility(View.GONE);
            handler.removeCallbacks(hideUndoRunnable);
        });
        handler.removeCallbacks(hideUndoRunnable);
        handler.postDelayed(hideUndoRunnable, 10000);
    }

    public void showTypingHistory(boolean show) {
        isShowingTypingHistory = show;
        updateData();

        View actions = findViewById(R.id.clipboard_action_row);
        if (actions != null) {
            actions.findViewById(R.id.clipboard_import_button).setVisibility(show ? View.GONE : View.VISIBLE);
            actions.findViewById(R.id.clipboard_export_button).setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    public void performSearchFromExternal(String query) {
        _currentSearchQuery = query;
        isSearching = !query.isEmpty();
        updateData();
    }

    public ClipboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        service = ClipboardHistoryService.get_service(context);
    }

    public void updateActionRowPosition() {
        View actionRow = findViewById(R.id.clipboard_action_row);
        ViewGroup containerTop = findViewById(R.id.clipboard_action_row_container_top);
        ViewGroup containerBottom = findViewById(R.id.clipboard_action_row_container_bottom);

        if (actionRow == null || containerTop == null || containerBottom == null) return;

        ((ViewGroup) actionRow.getParent()).removeView(actionRow);

        if (Config.globalConfig().clipboardActionsOnTop) {
            containerTop.addView(actionRow);
        } else {
            containerBottom.addView(actionRow);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        updateActionRowPosition();

        adapter = new ClipboardAdapter();
        recyclerView = findViewById(R.id.clipboard_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        new ItemTouchHelper(new GestureCallback()).attachToRecyclerView(recyclerView);

        Keyboard2View bottomRowView = findViewById(R.id.clipboard_bottom_row_view);
        if (bottomRowView != null) {
            KeyboardData bottomRowLayout = KeyboardData.load(getContext().getResources(), R.xml.clipboard_bottom_row);
            bottomRowView.setKeyboard(bottomRowLayout);
            bottomRowView.setKeyEventHandler(Config.globalConfig().handler);
        }

        findViewById(R.id.clipboard_expand_all_button).setOnClickListener(this::showExpandMenu);
        findViewById(R.id.clipboard_collapse_all_button).setOnClickListener(this::showCollapseMenu);

        findViewById(R.id.clipboard_import_button).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ImportClipboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        });

        findViewById(R.id.clipboard_export_button).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ExportClipboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        });

        findViewById(R.id.clipboard_sort_button).setOnClickListener(this::showSortMenu);
        findViewById(R.id.clipboard_clear_button).setOnClickListener(this::showClearMenu);
        findViewById(R.id.clipboard_back_button).setOnClickListener(v -> {
            if (multiSelectMode) {
                setMultiSelectMode(false);
            } else {
                _currentSearchQuery = "";
                isSearching = false;
                Keyboard2.Receiver r = (Keyboard2.Receiver) keyboardReceiver;
                if (r != null) r.handle_event_key(KeyValue.Event.SWITCH_BACK_CLIPBOARD);
            }
        });

        android.widget.EditText searchInput = findViewById(R.id.clipboard_search_input);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    String query = s.toString().trim();
                    if (query.isEmpty() && isSearching) {
                        // Search ended, restore state
                        isSearching = false;
                        expandedIds.clear();
                        expandedIds.addAll(savedExpandedIds);
                    } else if (!query.isEmpty() && !isSearching) {
                        // Search started, save state
                        isSearching = true;
                        savedExpandedIds.clear();
                        savedExpandedIds.addAll(expandedIds);
                    }
                    _currentSearchQuery = query;
                    updateData();
                }
            });
        }

        undoContainer = findViewById(R.id.clipboard_undo_container);
        undoButton = findViewById(R.id.clipboard_undo_button);
        undoContainer.setOnClickListener(v -> {
            if (recentlyRemovedItem != null) {
                service.restoreItem(recentlyRemovedItem, isShowingTypingHistory);
                undoContainer.setVisibility(View.GONE);
                recentlyRemovedItem = null;
                handler.removeCallbacks(hideUndoRunnable);
            }
        });
    }

    private void showExpandMenu(View v) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.getMenu().add(0, 1, 0, "Expand Folders");
        popup.getMenu().add(0, 2, 0, "Expand Contexts");
        popup.getMenu().add(0, 3, 0, "Expand Everything");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: adapter.expandAllFolders(true); break;
                case 2: adapter.expandAllContexts(true); break;
                case 3: adapter.expandEverything(true); break;
            }
            return true;
        });
        popup.show();
    }

    private void showCollapseMenu(View v) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.getMenu().add(0, 1, 0, "Collapse Folders");
        popup.getMenu().add(0, 2, 0, "Collapse Contexts");
        popup.getMenu().add(0, 3, 0, "Collapse Everything");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: adapter.expandAllFolders(false); break;
                case 2: adapter.expandAllContexts(false); break;
                case 3: adapter.expandEverything(false); break;
            }
            return true;
        });
        popup.show();
    }

    private void showSortMenu(View v) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.getMenu().add(0, 0, 0, "Latest");
        popup.getMenu().add(0, 1, 0, "Oldest");
        popup.getMenu().add(0, 2, 0, "Largest");
        popup.getMenu().add(0, 3, 0, "Smallest");
        popup.getMenu().add(0, 4, 0, "Pinned Latest");
        popup.getMenu().add(0, 5, 0, "Pinned Oldest");
        popup.getMenu().add(0, 6, 0, "Pinned Largest");
        popup.getMenu().add(0, 7, 0, "Pinned Smallest");
        popup.getMenu().add(0, 8, 0, "Archived Latest");
        popup.getMenu().add(0, 9, 0, "Archived Oldest");
        popup.getMenu().add(0, 10, 0, "Archived Largest");
        popup.getMenu().add(0, 11, 0, "Archived Smallest");

        popup.setOnMenuItemClickListener(item -> {
            currentSortMode = SortMode.values()[item.getItemId()];
            updateData();
            return true;
        });
        popup.show();
    }

    private void showClearMenu(View v) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.getMenu().add(0, 1, 0, R.string.clipboard_time_15m);
        popup.getMenu().add(0, 2, 0, R.string.clipboard_time_1h);
        popup.getMenu().add(0, 3, 0, R.string.clipboard_time_24h);
        popup.getMenu().add(0, 4, 0, R.string.clipboard_time_old);
        popup.getMenu().add(0, 5, 0, R.string.clipboard_time_all);
        if (isShowingTypingHistory) popup.getMenu().add(0, 6, 0, "Clear All History");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: service.removeUnpinnedItemsByTime(15 * 60 * 1000, isShowingTypingHistory); break;
                case 2: service.removeUnpinnedItemsByTime(60 * 60 * 1000, isShowingTypingHistory); break;
                case 3: service.removeUnpinnedItemsByTime(24 * 60 * 60 * 1000, isShowingTypingHistory); break;
                case 4: service.removeUnpinnedItemsOlderThan(24 * 60 * 60 * 1000, isShowingTypingHistory); break;
                case 5: service.removeAllUnpinned(isShowingTypingHistory); break;
                case 6: service.clearTypingHistory(); break;
            }
            return true;
        });
        popup.show();
    }

    public void setMultiSelectMode(boolean enabled) {
        this.multiSelectMode = enabled;
        adapter.notifyDataSetChanged();
        if (!enabled) adapter.clearSelection();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (service != null) {
            service.setOnClipboardHistoryChange(this);
            updateData();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (service != null) service.setOnClipboardHistoryChange(null);
        handler.removeCallbacks(hideUndoRunnable);
        _currentSearchQuery = "";
        isSearching = false;
    }

    @Override
    public void on_clipboard_history_change() {
        updateData();
    }

    private static final Set<String> expandedIds = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> expandedContextIds = Collections.synchronizedSet(new HashSet<>());
    private static boolean _expandAllFoldersRequested = false;

    private long _searchTaskId = 0;
    private void updateData() {
        if (service == null || adapter == null) return;

        final String query = _currentSearchQuery;
        final boolean typingHistory = isShowingTypingHistory;
        final long taskId = ++_searchTaskId;

        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            List<ClipboardItem> raw = typingHistory ? service.getTypingHistory() : service.getItems();
            List<ClipboardItem> filtered;

            if (query.isEmpty()) {
                filtered = raw;
                for (ClipboardItem item : filtered) {
                    item.setExpanded(expandedContextIds.contains(item.getId()));
                }
            } else {
                filtered = new ArrayList<>();
                String q = query.toLowerCase();
                for (ClipboardItem item : raw) {
                    String text = service.getRepository().getFullTextSynchronous(item);
                    if ((text != null && text.toLowerCase().contains(q)) || (item.getName() != null && item.getName().toLowerCase().contains(q))) {
                        item.setExpanded(true);
                        filtered.add(item);
                    } else {
                        item.setExpanded(false);
                    }
                }
            }

            List<ClipboardItem> sorted = sortItems(filtered);
            List<DisplayItem> displayItems = groupByHierarchy(sorted, !query.isEmpty());

            handler.post(() -> {
                if (taskId == _searchTaskId) {
                    adapter.setItems(displayItems);
                }
            });
        });
    }

    private List<DisplayItem> groupByHierarchy(List<ClipboardItem> items, boolean autoExpand) {
        if (items.isEmpty()) return Collections.emptyList();

        List<ClipboardItem> pinnedArchived = new ArrayList<>();
        List<ClipboardItem> recent = new ArrayList<>();

        for (ClipboardItem item : items) {
            if (item.isPinned() || item.isArchived()) pinnedArchived.add(item);
            else recent.add(item);
        }

        List<DisplayItem> result = new ArrayList<>();
        addMainFolder(result, "Pinned & Archived", "main_pinned", pinnedArchived, autoExpand);
        addMainFolder(result, "Recent", "main_recent", recent, autoExpand);

        return result;
    }

    private void addMainFolder(List<DisplayItem> result, String name, String id, List<ClipboardItem> items, boolean autoExpand) {
        if (items.isEmpty()) return;

        DisplayItem main = new DisplayItem(DisplayItem.TYPE_MAIN_FOLDER, id, name);
        main.expanded = autoExpand || expandedIds.contains(id);
        result.add(main);

        if (main.expanded) {
            java.util.LinkedHashMap<String, List<ClipboardItem>> dateGroups = new java.util.LinkedHashMap<>();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US);
            for (ClipboardItem item : items) {
                String date = sdf.format(new java.util.Date(item.getCreatedAt()));
                if (!dateGroups.containsKey(date)) dateGroups.put(date, new ArrayList<>());
                dateGroups.get(date).add(item);
            }

            for (java.util.Map.Entry<String, List<ClipboardItem>> entry : dateGroups.entrySet()) {
                String dateId = id + "_" + entry.getKey();

                DisplayItem dateFolder = new DisplayItem(DisplayItem.TYPE_DATE_FOLDER, dateId, entry.getKey());
                boolean isDateExpanded = autoExpand || _expandAllFoldersRequested || expandedIds.contains(dateId);
                dateFolder.expanded = isDateExpanded;
                if (_expandAllFoldersRequested) {
                    expandedIds.add(dateId);
                }
                result.add(dateFolder);

                if (dateFolder.expanded) {
                    for (ClipboardItem item : entry.getValue()) {
                        result.add(new DisplayItem(item));
                    }
                }
            }
        }
    }

    private class GestureCallback extends ItemTouchHelper.SimpleCallback {
        GestureCallback() { super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT); }
        @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) { return false; }
        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
            int pos = vh.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            DisplayItem di = adapter.items.get(pos);
            if (di.item == null) {
                adapter.notifyItemChanged(pos);
                return;
            }
            ClipboardItem item = di.item;
            if (dir == ItemTouchHelper.LEFT) {
                service.archiveItem(item, null);
            } else {
                recentlyRemovedItem = item;
                service.removeItem(item);
                undoButton.setText(R.string.undo);
                undoContainer.setVisibility(View.VISIBLE);
                handler.removeCallbacks(hideUndoRunnable);
                handler.postDelayed(hideUndoRunnable, 10000);
            }
        }
    }

    private List<ClipboardItem> sortItems(List<ClipboardItem> list) {
        Comparator<ClipboardItem> comparator = (a, b) -> {
            switch (currentSortMode) {
                case OLDEST:
                case PINNED_OLDEST:
                case ARCHIVED_OLDEST:
                    return Long.compare(a.getCreatedAt(), b.getCreatedAt());
                case LARGEST:
                case PINNED_LARGEST:
                case ARCHIVED_LARGEST:
                    return Long.compare(b.getContentLength(), a.getContentLength());
                case SMALLEST:
                case PINNED_SMALLEST:
                case ARCHIVED_SMALLEST:
                    return Long.compare(a.getContentLength(), b.getContentLength());
                case LATEST:
                case PINNED_LATEST:
                case ARCHIVED_LATEST:
                default:
                    return Long.compare(b.getCreatedAt(), a.getCreatedAt());
            }
        };

        List<ClipboardItem> primary = new ArrayList<>();
        List<ClipboardItem> secondary = new ArrayList<>();
        List<ClipboardItem> tertiary = new ArrayList<>();

        for (ClipboardItem item : list) {
            if (currentSortMode == SortMode.LATEST || currentSortMode == SortMode.OLDEST ||
                currentSortMode == SortMode.LARGEST || currentSortMode == SortMode.SMALLEST) {
                // User requested absolute global sort, no forcing pinned to top
                primary.add(item);
            } else if (currentSortMode.name().startsWith("PINNED")) {
                if (item.isPinned()) primary.add(item);
                else if (item.isArchived()) tertiary.add(item);
                else secondary.add(item);
            } else if (currentSortMode.name().startsWith("ARCHIVED")) {
                if (item.isArchived()) primary.add(item);
                else if (item.isPinned()) secondary.add(item);
                else tertiary.add(item);
            } else {
                primary.add(item);
            }
        }

        Collections.sort(primary, comparator);
        if (!secondary.isEmpty()) Collections.sort(secondary, comparator);
        if (!tertiary.isEmpty()) Collections.sort(tertiary, comparator);

        List<ClipboardItem> result = new ArrayList<>();
        result.addAll(primary);
        result.addAll(secondary);
        result.addAll(tertiary);
        return result;
    }

    private class ClipboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<DisplayItem> items = new ArrayList<>();
        private final int highlightColor;
        private final int textColor;
        private final int separationBgColor;

        ClipboardAdapter() {
            Context themeContext = new android.view.ContextThemeWrapper(getContext(), Config.globalConfig().theme);
            Theme theme = new Theme(themeContext, null);
            this.highlightColor = (theme.activatedColor & 0x00FFFFFF) | 0x88000000;
            this.textColor = theme.labelColor;
            this.separationBgColor = (theme.activatedColor & 0x00FFFFFF) | 0x15000000;
        }

        void setItems(List<DisplayItem> newItems) {
            this.items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        void toggleFolder(int position) {
            DisplayItem folder = items.get(position);
            if (folder.type == DisplayItem.TYPE_CLIPBOARD) return;

            _expandAllFoldersRequested = false;
            if (expandedIds.contains(folder.id)) {
                expandedIds.remove(folder.id);
            } else {
                expandedIds.add(folder.id);
            }
            updateData();
        }

        void expandAllFolders(boolean expand) {
            if (!expand) {
                _expandAllFoldersRequested = false;
                expandedIds.clear();
            } else {
                expandedIds.add("main_pinned");
                expandedIds.add("main_recent");
                _expandAllFoldersRequested = true;
            }
            updateData();
        }

        void expandAllContexts(boolean expand) {
            expandedContextIds.clear();
            if (expand) {
                List<ClipboardItem> raw = isShowingTypingHistory ? service.getTypingHistory() : service.getItems();
                for (ClipboardItem item : raw) {
                    expandedContextIds.add(item.getId());
                }
            }
            updateData();
        }

        void expandEverything(boolean expand) {
            expandAllFolders(expand);
            expandAllContexts(expand);
        }

        void clearSelection() {
            for (DisplayItem di : items) {
                if (di.item != null) di.item.setSelected(false);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == DisplayItem.TYPE_MAIN_FOLDER || viewType == DisplayItem.TYPE_DATE_FOLDER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.clipboard_folder_item, parent, false);
                return new FolderViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.clipboard_grid_item, parent, false);
                return new ItemViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof FolderViewHolder) {
                ((FolderViewHolder) holder).bind(items.get(position));
            } else if (holder instanceof ItemViewHolder) {
                ((ItemViewHolder) holder).bind(items.get(position).item);
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        class FolderViewHolder extends RecyclerView.ViewHolder {
            final TextView name;
            final ImageView chevron;
            final ImageView icon;
            final View container;

            FolderViewHolder(View view) {
                super(view);
                container = view.findViewById(R.id.folder_container);
                name = view.findViewById(R.id.folder_name);
                chevron = view.findViewById(R.id.folder_chevron);
                icon = view.findViewById(R.id.folder_icon);
            }

            void bind(DisplayItem folder) {
                name.setText(folder.name);
                chevron.setImageResource(folder.expanded ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_right);

                if (folder.type == DisplayItem.TYPE_MAIN_FOLDER) {
                    container.setPadding(8, 8, 8, 8);
                    name.setTextSize(18);
                    icon.setImageResource(R.drawable.ic_folder); // Use a distinct icon if available
                } else {
                    container.setPadding(32, 8, 8, 8); // Indent date folders
                    name.setTextSize(14);
                    icon.setImageResource(R.drawable.ic_folder);
                }

                itemView.setOnClickListener(v -> toggleFolder(getAdapterPosition()));
                itemView.setOnLongClickListener(v -> {
                    showFolderMenu(v, folder);
                    return true;
                });
            }
        }

        class ItemViewHolder extends RecyclerView.ViewHolder {
            final TextView textView;
            final ImageView statusGlyph;
            final CheckBox checkmark;
            final View container;
            final View menuBtn;

            ItemViewHolder(View view) {
                super(view);
                container = view.findViewById(R.id.clipboard_item_container);
                textView = view.findViewById(R.id.clipboard_item_text);
                statusGlyph = view.findViewById(R.id.clipboard_item_status_glyph);
                checkmark = view.findViewById(R.id.clipboard_item_checkmark);
                menuBtn = view.findViewById(R.id.clipboard_item_menu);
            }

            void bind(ClipboardItem item) {
                boolean expanded = item.isExpanded();
                String text = item.getText();
                if (text == null) text = "";

                String folderInfo = "";
                if (!_currentSearchQuery.isEmpty()) {
                    String folderName = item.isPinned() || item.isArchived() ? "Pinned & Archived" : "Recent";
                    String dateStr = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).format(new java.util.Date(item.getCreatedAt()));
                    folderInfo = "[" + folderName + " / " + dateStr + "]\n";
                }

                android.text.SpannableStringBuilder ssb;
                if (!_currentSearchQuery.isEmpty()) {
                    String q = _currentSearchQuery.toLowerCase();
                    String lowerText = text.toLowerCase();
                    int index = lowerText.indexOf(q);

                    if (expanded) {
                        String displayedText = folderInfo + text;
                        ssb = new android.text.SpannableStringBuilder(displayedText);
                        int startSearch = 0;
                        while (true) {
                            int idx = lowerText.indexOf(q, startSearch);
                            if (idx == -1) break;

                            int highlightStart = folderInfo.length() + idx;
                            int highlightEnd = highlightStart + q.length();

                            ssb.setSpan(new android.text.style.BackgroundColorSpan(highlightColor), highlightStart, highlightEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            ssb.setSpan(new android.text.style.ForegroundColorSpan(textColor), highlightStart, highlightEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), highlightStart, highlightEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                            startSearch = idx + q.length();
                        }
                    } else {
                        if (index != -1) {
                            int contextLimit = 64;
                            int start = Math.max(0, index - contextLimit);
                            int end = Math.min(text.length(), index + q.length() + contextLimit);

                            String matchSegment = text.substring(start, end);
                            String prefix = (start > 0 ? "..." : "");
                            String suffix = (end < text.length() ? "..." : "");
                            String contextText = folderInfo + prefix + matchSegment + suffix;
                            ssb = new android.text.SpannableStringBuilder(contextText);

                            int highlightStart = folderInfo.length() + prefix.length() + (index - start);
                            int highlightEnd = highlightStart + q.length();

                            highlightStart = Math.max(0, Math.min(highlightStart, ssb.length()));
                            highlightEnd = Math.max(highlightStart, Math.min(highlightEnd, ssb.length()));

                            if (highlightStart < highlightEnd) {
                                ssb.setSpan(new android.text.style.BackgroundColorSpan(highlightColor), highlightStart, highlightEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                ssb.setSpan(new android.text.style.ForegroundColorSpan(textColor), highlightStart, highlightEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), highlightStart, highlightEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        } else {
                            String preview = item.getContentPreview();
                            ssb = new android.text.SpannableStringBuilder(folderInfo + (preview != null ? preview : ""));
                        }
                    }
                } else {
                    String preview = item.isExpanded() ? text : item.getContentPreview();
                    ssb = new android.text.SpannableStringBuilder(preview != null ? preview : "");
                }

                if (!folderInfo.isEmpty()) {
                    ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC), 0, folderInfo.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new android.text.style.ForegroundColorSpan(0xFF888888), 0, folderInfo.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new android.text.style.RelativeSizeSpan(0.85f), 0, folderInfo.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                textView.setText(ssb);
                textView.setMaxLines(expanded ? Integer.MAX_VALUE : 3);

                // Set separation background color if query matches this item
                if (!_currentSearchQuery.isEmpty() && text.toLowerCase().contains(_currentSearchQuery.toLowerCase())) {
                    container.setBackgroundColor(separationBgColor);
                } else {
                    android.util.TypedValue outValue = new android.util.TypedValue();
                    getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                    container.setBackgroundResource(outValue.resourceId);
                }

                if (item.isPinned()) {
                    statusGlyph.setImageResource(R.drawable.ic_pin);
                    statusGlyph.setVisibility(View.VISIBLE);
                } else if (item.isArchived()) {
                    statusGlyph.setImageResource(R.drawable.ic_archive);
                    statusGlyph.setVisibility(View.VISIBLE);
                } else {
                    statusGlyph.setVisibility(View.GONE);
                }

                if (multiSelectMode) {
                    checkmark.setVisibility(View.VISIBLE);
                    checkmark.setChecked(item.isSelected());
                    menuBtn.setVisibility(View.GONE);
                } else {
                    checkmark.setVisibility(View.GONE);
                    menuBtn.setVisibility(View.VISIBLE);
                    menuBtn.setOnClickListener(v -> showItemMenu(v, item, getAdapterPosition()));
                }


                container.setOnClickListener(v -> {
                    if (multiSelectMode) {
                        item.setSelected(!item.isSelected());
                        checkmark.setChecked(item.isSelected());
                    } else {
                        if (item.hasBody() && item.getText() != null && !item.getText().isEmpty()) {
                            performPaste(item.getText());
                        } else {
                            service.getRepository().loadFullContent(getContext(), item, i -> {
                                handler.post(() -> performPaste(item.getText()));
                            });
                        }
                    }
                });

                container.setOnLongClickListener(v -> {
                    if (!multiSelectMode) {
                        setMultiSelectMode(true);
                        item.setSelected(true);
                        notifyDataSetChanged();
                        showBulkMenu(v);
                    } else {
                        showBulkMenu(v);
                    }
                    return true;
                });
            }
        }
    }

    private void showItemMenu(View v, ClipboardItem item, int pos) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.getMenu().add(0, 1, 0, "Rename");
        popup.getMenu().add(0, 2, 0, item.isArchived() ? "Unarchive" : "Archive");
        popup.getMenu().add(0, 3, 0, item.isPinned() ? "Unpin" : "Pin");
        popup.getMenu().add(0, 4, 0, "Delete");
        popup.getMenu().add(0, 7, 0, item.isExpanded() ? "Collapse Context" : "Expand Context");
        popup.getMenu().add(0, 8, 0, "Collapse Folder");

        popup.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case 1:
                    if (keyboardReceiver != null) {
                        recentlyRenamedItem = item;
                        Keyboard2.Receiver r = (Keyboard2.Receiver) keyboardReceiver;
                        r.startRenamingInMainLayout(item.getName());
                    }
                    break;
                case 2: if (item.isArchived()) service.unarchiveItem(item); else service.archiveItem(item, null); break;
                case 3: service.togglePin(item); break;
                case 4: service.removeItem(item); break;
                case 7:
                    if (!expandedContextIds.contains(item.getId())) {
                        service.getRepository().loadFullContent(getContext(), item, i -> {
                            expandedContextIds.add(item.getId());
                            handler.post(() -> adapter.notifyItemChanged(pos));
                        });
                    } else {
                        expandedContextIds.remove(item.getId());
                        adapter.notifyItemChanged(pos);
                    }
                    break;
                case 8:
                    _expandAllFoldersRequested = false;
                    java.text.SimpleDateFormat sdf8 = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US);
                    String date8 = sdf8.format(new java.util.Date(item.getCreatedAt()));
                    String dateId8 = (item.isPinned() || item.isArchived() ? "main_pinned" : "main_recent") + "_" + date8;
                    expandedIds.remove(dateId8);
                    updateData();
                    break;
            }
            return true;
        });
        popup.show();
    }

    private void showFolderMenu(View v, DisplayItem folder) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.getMenu().add(0, 1, 0, "Expand Contexts");
        popup.getMenu().add(0, 2, 0, "Collapse Contexts");

        popup.setOnMenuItemClickListener(menuItem -> {
            boolean expand = menuItem.getItemId() == 1;
            for (DisplayItem di : adapter.items) {
                // If it's a clipboard item that belongs to this folder
                if (di.type == DisplayItem.TYPE_CLIPBOARD) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US);
                    String date = sdf.format(new java.util.Date(di.item.getCreatedAt()));
                    String dateId = (di.item.isPinned() || di.item.isArchived() ? "main_pinned" : "main_recent") + "_" + date;

                    if (dateId.equals(folder.id) || folder.id.equals("main_pinned") || folder.id.equals("main_recent")) {
                         di.item.setExpanded(expand);
                    }
                }
            }
            adapter.notifyDataSetChanged();
            return true;
        });
        popup.show();
    }

    private void showBulkMenu(View v) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.getMenu().add(0, 1, 0, "Pin Selected");
        popup.getMenu().add(0, 2, 0, "Archive Selected");
        popup.getMenu().add(0, 3, 0, "Delete Selected");
        popup.getMenu().add(0, 5, 0, "Exit Selection");

        popup.setOnMenuItemClickListener(menuItem -> {
            List<ClipboardItem> selected = new ArrayList<>();
            for (DisplayItem di : adapter.items) {
                if (di.item != null && di.item.isSelected()) selected.add(di.item);
            }

            switch (menuItem.getItemId()) {
                case 1: for (ClipboardItem ci : selected) service.togglePin(ci); break;
                case 2: for (ClipboardItem ci : selected) service.archiveItem(ci, null); break;
                case 3: for (ClipboardItem ci : selected) service.removeItem(ci); break;
                case 5: setMultiSelectMode(false); break;
            }
            setMultiSelectMode(false);
            return true;
        });
        popup.show();
    }
}
