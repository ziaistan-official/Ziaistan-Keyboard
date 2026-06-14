package juloo.keyboard2;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.util.TypedValue;
import android.widget.PopupMenu;
import android.app.Dialog;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ClipboardView extends FrameLayout implements ClipboardHistoryService.OnClipboardHistoryChange {

    private ClipboardHistoryService service;
    private ClipboardAdapter adapter;
    private RecyclerView recyclerView;
    private FrameLayout undoContainer;
    private TextView undoButton;
    private ClipboardItem recentlyRemovedItem;
    private Keyboard2.Receiver keyboardReceiver;
    private ClipboardItem itemBeingRenamed;
    private int itemBeingRenamedPosition = -1;
    private Handler handler = new Handler();
    private boolean isShowingTypingHistory = false;
    private int mFixedKeyboardHeight = -1;

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

    private Runnable hideUndoRunnable = new Runnable() {
        @Override
        public void run() {
            undoContainer.setVisibility(View.GONE);
            recentlyRemovedItem = null;
        }
    };

    public void setKeyboardReceiver(Keyboard2.Receiver receiver) {
        this.keyboardReceiver = receiver;
    }

    private Keyboard2 getKeyboard2() {
        Context ctx = getContext();
        while (ctx instanceof android.content.ContextWrapper) {
            if (ctx instanceof Keyboard2) return (Keyboard2) ctx;
            ctx = ((android.content.ContextWrapper)ctx).getBaseContext();
        }
        return null;
    }

    private void performPaste(String text) {
        Keyboard2 k2 = getKeyboard2();
        if (k2 != null) {
            InputConnection ic = k2.getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(text, 1);
            } else if (service != null) {
                ClipboardHistoryService.paste(text);
            }
        } else if (service != null) {
            ClipboardHistoryService.paste(text);
        }
    }

    public void finishRenaming(String newName) {
        if (itemBeingRenamed != null && service != null) {
            service.renameItem(itemBeingRenamed, newName);
            if (itemBeingRenamedPosition != -1) {
                recyclerView.scrollToPosition(itemBeingRenamedPosition);
                adapter.notifyItemChanged(itemBeingRenamedPosition, "FLASH");
            }
        }
        itemBeingRenamed = null;
        itemBeingRenamedPosition = -1;
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
        handler.postDelayed(hideUndoRunnable, 25000);
    }

    public void showTypingHistory(boolean show) {
        isShowingTypingHistory = show;
        updateData();

        Button importButton = findViewById(R.id.clipboard_import_button);
        Button exportButton = findViewById(R.id.clipboard_export_button);
        ImageButton clearButton = findViewById(R.id.clipboard_clear_button);

        if (importButton != null) importButton.setVisibility(show ? View.GONE : View.VISIBLE);
        if (exportButton != null) exportButton.setVisibility(show ? View.GONE : View.VISIBLE);
        if (clearButton != null) clearButton.setVisibility(View.VISIBLE);
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

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                ClipboardItem item = adapter.getItem(position);
                if (item != null && item.isArchived()) {
                    return 3;
                }
                if (isShowingTypingHistory) {
                    return 3;
                }
                return 1;
            }
        });
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new GestureCallback());
        itemTouchHelper.attachToRecyclerView(recyclerView);

        Keyboard2View bottomRowView = findViewById(R.id.clipboard_bottom_row_view);
        if (bottomRowView != null) {
            KeyboardData bottomRowLayout = KeyboardData.load(getContext().getResources(), R.xml.clipboard_bottom_row);
            bottomRowView.setKeyboard(bottomRowLayout);
            bottomRowView.setKeyEventHandler(Config.globalConfig().handler);
        }

        Button importButton = findViewById(R.id.clipboard_import_button);
        if (importButton != null) {
            importButton.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), ImportClipboardActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            });
        }

        Button exportButton = findViewById(R.id.clipboard_export_button);
        if (exportButton != null) {
            exportButton.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), ExportClipboardActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            });
        }

        ImageButton clearButton = findViewById(R.id.clipboard_clear_button);
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                if (service == null) return;

                if (isShowingTypingHistory) {
                    PopupMenu popup = new PopupMenu(getContext(), clearButton);
                    popup.getMenu().add(0, 1, 0, R.string.clipboard_time_15m);
                    popup.getMenu().add(0, 2, 0, R.string.clipboard_time_1h);
                    popup.getMenu().add(0, 3, 0, R.string.clipboard_time_24h);
                    popup.getMenu().add(0, 4, 0, R.string.clipboard_time_old);
                    popup.getMenu().add(0, 5, 0, R.string.clipboard_time_all);
                    popup.getMenu().add(0, 6, 0, "Clear All History");

                    popup.setOnMenuItemClickListener(item -> {
                        switch (item.getItemId()) {
                            case 1: service.removeUnpinnedItemsByTime(15 * 60 * 1000, true); return true;
                            case 2: service.removeUnpinnedItemsByTime(60 * 60 * 1000, true); return true;
                            case 3: service.removeUnpinnedItemsByTime(24 * 60 * 60 * 1000, true); return true;
                            case 4: service.removeUnpinnedItemsOlderThan(24 * 60 * 60 * 1000, true); return true;
                            case 5: service.removeAllUnpinned(true); return true;
                            case 6: service.clearTypingHistory(); return true;
                        }
                        return false;
                    });
                    popup.show();
                } else {
                    PopupMenu popup = new PopupMenu(getContext(), clearButton);
                    popup.getMenu().add(0, 1, 0, R.string.clipboard_time_15m);
                    popup.getMenu().add(0, 2, 0, R.string.clipboard_time_1h);
                    popup.getMenu().add(0, 3, 0, R.string.clipboard_time_24h);
                    popup.getMenu().add(0, 4, 0, R.string.clipboard_time_old);
                    popup.getMenu().add(0, 5, 0, R.string.clipboard_time_all);

                    popup.setOnMenuItemClickListener(item -> {
                        switch (item.getItemId()) {
                            case 1: service.removeUnpinnedItemsByTime(15 * 60 * 1000, false); return true;
                            case 2: service.removeUnpinnedItemsByTime(60 * 60 * 1000, false); return true;
                            case 3: service.removeUnpinnedItemsByTime(24 * 60 * 60 * 1000, false); return true;
                            case 4: service.removeUnpinnedItemsOlderThan(24 * 60 * 60 * 1000, false); return true;
                            case 5: service.removeAllUnpinned(false); return true;
                        }
                        return false;
                    });
                    popup.show();
                }
            });
        }

        Button backButton = findViewById(R.id.clipboard_back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                KeyEventHandler handler = (KeyEventHandler) Config.globalConfig().handler;
                if (handler != null) {
                    handler.key_up(KeyValue.getSpecialKeyByName("switch_back_clipboard"), Pointers.Modifiers.EMPTY);
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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateActionRowPosition();
        if (service != null) {
            service.setOnClipboardHistoryChange(this);
            updateData();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (service != null) {
            service.setOnClipboardHistoryChange(null);
        }
        handler.removeCallbacks(hideUndoRunnable);
    }

    @Override
    public void on_clipboard_history_change() {
        updateData();
    }

    private void updateData() {
        if (service != null && adapter != null) {
            List<ClipboardItem> rawItems;
            if (isShowingTypingHistory) {
                rawItems = service.getTypingHistory();
            } else {
                rawItems = service.getItems();
            }
            adapter.setItems(rawItems);
        }
    }

    private class GestureCallback extends ItemTouchHelper.SimpleCallback {
        GestureCallback() {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
             return 0.3f;
        }

        @Override
        public float getSwipeEscapeVelocity(float defaultValue) {
            return defaultValue * 0.5f;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getAdapterPosition();
            ClipboardItem item = adapter.getItem(position);
            if (item == null) return;

            if (direction == ItemTouchHelper.LEFT) {
                service.archiveItem(item, null);
            } else if (direction == ItemTouchHelper.RIGHT) {
                recentlyRemovedItem = item;
                service.removeItem(item);
                undoButton.setText(R.string.undo);
                undoContainer.setVisibility(View.VISIBLE);
                handler.removeCallbacks(hideUndoRunnable);
                handler.postDelayed(hideUndoRunnable, 25000);
            }
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }
    }

    private class ClipboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_GRID = 0;
        private static final int TYPE_LIST = 1;

        private List<ClipboardItem> items = new ArrayList<>();

        void setItems(List<ClipboardItem> newItems) {
            this.items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        ClipboardItem getItem(int position) {
            if (position >= 0 && position < items.size()) {
                return items.get(position);
            }
            return null;
        }

        @Override
        public int getItemViewType(int position) {
            ClipboardItem item = items.get(position);
            return (item != null && item.isArchived()) || isShowingTypingHistory ? TYPE_LIST : TYPE_GRID;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_LIST) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.clipboard_grid_item, parent, false);
                return new ListViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.clipboard_grid_item, parent, false);
                return new GridViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, List<Object> payloads) {
            if (!payloads.isEmpty()) {
                for (Object payload : payloads) {
                    if ("FLASH".equals(payload)) {
                        holder.itemView.setBackgroundColor(Color.LTGRAY);
                        holder.itemView.postDelayed(() -> holder.itemView.setBackgroundColor(Color.TRANSPARENT), 500);
                    }
                }
            }
            super.onBindViewHolder(holder, position, payloads);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ClipboardItem item = items.get(position);
            if (holder instanceof ListViewHolder) {
                ((ListViewHolder) holder).bind(item);
            } else if (holder instanceof GridViewHolder) {
                ((GridViewHolder) holder).bind(item);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class GridViewHolder extends RecyclerView.ViewHolder {
            final TextView textView;
            final ImageView pinIcon;
            final View menuButton;

            GridViewHolder(View view) {
                super(view);
                textView = view.findViewById(R.id.clipboard_item_text);
                pinIcon = view.findViewById(R.id.clipboard_item_pin_icon);
                menuButton = view.findViewById(R.id.clipboard_item_menu);
            }

            void bind(ClipboardItem item) {
                textView.setText(item.getText());
                pinIcon.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);
                menuButton.setVisibility(View.GONE);
                textView.setMaxLines(5);

                itemView.setOnClickListener(v -> performPaste(item.getText()));

                itemView.setOnLongClickListener(v -> {
                    if (service != null) service.togglePin(item);
                    return true;
                });
            }
        }

        class ListViewHolder extends RecyclerView.ViewHolder {
            final TextView textView;
            final ImageView pinIcon;
            final View menuButton;

            ListViewHolder(View view) {
                super(view);
                textView = view.findViewById(R.id.clipboard_item_text);
                pinIcon = view.findViewById(R.id.clipboard_item_pin_icon);
                menuButton = view.findViewById(R.id.clipboard_item_menu);
                if (isShowingTypingHistory) {
                    textView.setMaxLines(10);
                } else {
                    textView.setMaxLines(1);
                }
            }

            void bind(ClipboardItem item) {
                if (isShowingTypingHistory) {
                    textView.setText(item.getText());
                    textView.setMaxLines(10);
                    pinIcon.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);
                    menuButton.setVisibility(View.VISIBLE);

                    setupMenu(menuButton, item);

                    itemView.setOnClickListener(v -> performPaste(item.getText()));
                    itemView.setOnLongClickListener(v -> {
                        if (service != null) service.togglePin(item);
                        return true;
                    });
                    return;
                }

                String displayName = item.getName();
                if (displayName == null || displayName.isEmpty()) displayName = item.getText();

                if (item.isExpanded()) {
                    textView.setMaxLines(Integer.MAX_VALUE);
                    textView.setText("ARCHIVED: " + displayName + "\n\n" + item.getText());
                } else {
                    textView.setMaxLines(Integer.MAX_VALUE);
                    textView.setText("ARCHIVED: " + displayName + "\n\n" + item.getText());
                }

                pinIcon.setVisibility(View.GONE);
                menuButton.setVisibility(View.VISIBLE);
                setupMenu(menuButton, item);

                textView.setOnClickListener(v -> performPaste(item.getText()));

                itemView.setOnLongClickListener(v -> {
                    if (service != null) service.unarchiveItem(item);
                    return true;
                });
            }

            private void setupMenu(View btn, ClipboardItem item) {
                btn.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(getContext(), btn);
                    popup.getMenu().add(0, 1, 0, "Rename");
                    if (!isShowingTypingHistory) {
                        if (item.isExpanded()) popup.getMenu().add(0, 3, 0, "Collapse");
                        else popup.getMenu().add(0, 3, 0, "Expand");
                    }
                    popup.getMenu().add(0, 4, 0, "Share");
                    popup.getMenu().add(0, 2, 0, isShowingTypingHistory ? (item.isArchived() ? "Unarchive" : "Archive") : "Unarchive");

                    popup.setOnMenuItemClickListener(menuItem -> {
                        if (menuItem.getItemId() == 1) {
                            if (keyboardReceiver != null) {
                                itemBeingRenamed = item;
                                itemBeingRenamedPosition = getAdapterPosition();
                                keyboardReceiver.startRenamingInMainLayout(item.getName());
                            }
                            return true;
                        } else if (menuItem.getItemId() == 2) {
                            if (item.isArchived()) service.unarchiveItem(item);
                            else service.archiveItem(item, null);
                            return true;
                        } else if (menuItem.getItemId() == 3) {
                            item.setExpanded(!item.isExpanded());
                            notifyItemChanged(getAdapterPosition());
                            return true;
                        } else if (menuItem.getItemId() == 4) {
                            Intent sendIntent = new Intent();
                            sendIntent.setAction(Intent.ACTION_SEND);
                            sendIntent.putExtra(Intent.EXTRA_TEXT, item.getText());
                            sendIntent.setType("text/plain");
                            Intent shareIntent = Intent.createChooser(sendIntent, null);
                            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            getContext().startActivity(shareIntent);
                            return true;
                        }
                        return false;
                    });
                    popup.show();
                });
            }
        }
    }
}
