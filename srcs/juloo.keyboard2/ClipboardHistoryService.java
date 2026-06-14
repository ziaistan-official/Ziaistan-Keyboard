package juloo.keyboard2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ClipboardHistoryService {
    private static final String TAG = "ClipboardHistoryService";
    private static final String PERSIST_FILE_NAME = "clipboard_history.json";
    private static final String TYPING_HISTORY_FILE_NAME = "typing_history.json";
    public static final String RELOAD_CLIPBOARD_HISTORY_ACTION = "juloo.keyboard2.RELOAD_CLIPBOARD_HISTORY";

    private static ClipboardHistoryService _service = null;
    private static ClipboardPasteCallback _paste_callback = null;

    private final Context context;
    private final ClipboardManager clipboardManager;
    private final List<ClipboardItem> items;
    private final List<ClipboardItem> typingHistoryItems;
    private OnClipboardHistoryChange listener = null;


    private ClipboardItem currentTypingSessionItem = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable persistTypingHistoryRunnable = this::persistTypingHistory;
    private static final long PERSIST_DELAY_MS = 100;

    public static void on_startup(Context ctx, ClipboardPasteCallback cb) {
        get_service(ctx);
        _paste_callback = cb;
    }

    public static ClipboardHistoryService get_service(Context ctx) {
        if (_service == null) {
            _service = new ClipboardHistoryService(ctx.getApplicationContext());
        }
        return _service;
    }

    public static void set_history_enabled(boolean e) {
        Config.globalConfig().set_clipboard_history_enabled(e);
        if (_service == null) return;
        if (e) {
            _service.addCurrentClip();
        } else {
            _service.clearHistory();
        }
    }

    public static void paste(String clip) {
        if (_paste_callback != null) {
            _paste_callback.paste_from_clipboard_pane(clip);
        }
    }

    private ClipboardHistoryService(Context ctx) {
        this.context = ctx;
        this.items = new ArrayList<>();
        this.typingHistoryItems = new ArrayList<>();
        this.clipboardManager = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        this.clipboardManager.addPrimaryClipChangedListener(new SystemListener());
        loadItems();
        loadTypingHistory();

        IntentFilter filter = new IntentFilter(RELOAD_CLIPBOARD_HISTORY_ACTION);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadItems();
                notifyHistoryChange();
            }
        };

        if (VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }

    public List<ClipboardItem> getItems() {
        return new ArrayList<>(items);
    }

    public List<ClipboardItem> getTypingHistory() {
        return new ArrayList<>(typingHistoryItems);
    }

    public void startNewTypingSession() {

        currentTypingSessionItem = null;
    }

    public void updateCurrentTypingSession(String text) {
        if (!Config.globalConfig().enable_typing_history) {
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (currentTypingSessionItem == null) {







            if (!typingHistoryItems.isEmpty() && typingHistoryItems.get(0).getText().equals(text)) {
                currentTypingSessionItem = typingHistoryItems.get(0);
                currentTypingSessionItem.setTimestamp(currentTime);
            } else {
                currentTypingSessionItem = new ClipboardItem(text, currentTime, false);
                typingHistoryItems.add(0, currentTypingSessionItem);
            }
        } else {


            if (currentTypingSessionItem.getText().equals(text)) {
                return;
            }

            int index = typingHistoryItems.indexOf(currentTypingSessionItem);
            if (index != -1) {
                currentTypingSessionItem = new ClipboardItem(text, currentTime, currentTypingSessionItem.isPinned(), currentTypingSessionItem.isArchived(), currentTypingSessionItem.getName());
                typingHistoryItems.set(index, currentTypingSessionItem);
            } else {

                currentTypingSessionItem = new ClipboardItem(text, currentTime, false);
                typingHistoryItems.add(0, currentTypingSessionItem);
            }
        }


        while (typingHistoryItems.size() > 10000) {
            typingHistoryItems.remove(typingHistoryItems.size() - 1);
        }


        handler.removeCallbacks(persistTypingHistoryRunnable);
        handler.postDelayed(persistTypingHistoryRunnable, PERSIST_DELAY_MS);
    }

    public void saveTypingHistoryNow() {
        handler.removeCallbacks(persistTypingHistoryRunnable);
        persistTypingHistory();
    }


    public void addTypingHistory(String text) {
        updateCurrentTypingSession(text);
        saveTypingHistoryNow();
    }

    public void addClip(String clip) {
        if (!Config.globalConfig().clipboard_history_enabled || clip == null || clip.trim().isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        ClipboardItem newItem = new ClipboardItem(clip, currentTime, false);


        items.remove(newItem);
        items.add(newItem);


        trimHistory();
        sortItems(items);
        persistClipboardItems();
        notifyHistoryChange();
    }

    public void removeUnpinnedItemsByTime(long durationMillis, boolean isTypingHistory) {
        long now = System.currentTimeMillis();
        long threshold = now - durationMillis;
        List<ClipboardItem> targetList = isTypingHistory ? typingHistoryItems : items;
        boolean changed = false;
        Iterator<ClipboardItem> it = targetList.iterator();
        while (it.hasNext()) {
            ClipboardItem item = it.next();
            if (!item.isPinned() && !item.isArchived() && item.getTimestamp() >= threshold) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            if (isTypingHistory) persistTypingHistory(); else persistClipboardItems();
            notifyHistoryChange();
        }
    }

    public void removeUnpinnedItemsOlderThan(long ageMillis, boolean isTypingHistory) {
        long now = System.currentTimeMillis();
        long threshold = now - ageMillis;
        List<ClipboardItem> targetList = isTypingHistory ? typingHistoryItems : items;
        boolean changed = false;
        Iterator<ClipboardItem> it = targetList.iterator();
        while (it.hasNext()) {
            ClipboardItem item = it.next();
            if (!item.isPinned() && !item.isArchived() && item.getTimestamp() < threshold) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            if (isTypingHistory) persistTypingHistory(); else persistClipboardItems();
            notifyHistoryChange();
        }
    }

    public void removeAllUnpinned(boolean isTypingHistory) {
        List<ClipboardItem> targetList = isTypingHistory ? typingHistoryItems : items;
        boolean changed = false;
        Iterator<ClipboardItem> it = targetList.iterator();
        while (it.hasNext()) {
            ClipboardItem item = it.next();
            if (!item.isPinned() && !item.isArchived()) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            if (isTypingHistory) persistTypingHistory(); else persistClipboardItems();
            notifyHistoryChange();
        }
    }

    public void removeItem(ClipboardItem item) {
        if (items.remove(item)) {

            if (isSystemClipboard(item.getText())) {
                if (VERSION.SDK_INT >= 28) {
                    clipboardManager.clearPrimaryClip();
                } else {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));
                }
            }
            persistClipboardItems();
            notifyHistoryChange();
        } else if (typingHistoryItems.remove(item)) {
            if (item == currentTypingSessionItem) {
                currentTypingSessionItem = null;
            }
            persistTypingHistory();
            notifyHistoryChange();
        }
    }

    public void restoreItem(ClipboardItem item) {
















        restoreItem(item, false);
    }

    public void restoreItem(ClipboardItem item, boolean isTypingHistory) {
        List<ClipboardItem> targetList = isTypingHistory ? typingHistoryItems : items;
        if (!targetList.contains(item)) {
            targetList.add(item);
            sortItems(targetList);
            if (isTypingHistory) persistTypingHistory(); else persistClipboardItems();
            notifyHistoryChange();
        }
    }

    public void archiveItem(ClipboardItem item, String name) {
        boolean isTyping = typingHistoryItems.contains(item);
        boolean isClip = items.contains(item);

        if (isTyping || isClip) {
            item.setArchived(true);
            if (name == null || name.isEmpty()) {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(System.currentTimeMillis()));
                String text = item.getText();
                String[] words = text.trim().split("\\s+");
                int limit = Math.min(words.length, 7);

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < limit; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(words[i]);
                }
                sb.append(" - ").append(timestamp);
                item.setName(sb.toString());
            } else {
                item.setName(name);
            }
            if (isTyping) {
                sortItems(typingHistoryItems);
                persistTypingHistory();
            } else {
                sortItems(items);
                persistClipboardItems();
            }
            notifyHistoryChange();
        }
    }

    public void renameItem(ClipboardItem item, String newName) {
        boolean isTyping = typingHistoryItems.contains(item);
        boolean isClip = items.contains(item);

        if (isTyping || isClip) {
            item.setName(newName);
            if (isTyping) {
                sortItems(typingHistoryItems);
                persistTypingHistory();
            } else {
                sortItems(items);
                persistClipboardItems();
            }
            notifyHistoryChange();
        }
    }

    public void unarchiveItem(ClipboardItem item) {
        boolean isTyping = typingHistoryItems.contains(item);
        boolean isClip = items.contains(item);

        if (isTyping || isClip) {
            item.setArchived(false);
            item.setName(null);
            if (isTyping) {
                sortItems(typingHistoryItems);
                persistTypingHistory();
            } else {
                sortItems(items);
                persistClipboardItems();
            }
            notifyHistoryChange();
        }
    }

    public void togglePin(ClipboardItem item) {
        boolean isTyping = typingHistoryItems.contains(item);
        boolean isClip = items.contains(item);

        if (isTyping || isClip) {
            item.setPinned(!item.isPinned());
            item.setTimestamp(System.currentTimeMillis());
            if (isTyping) {
                sortItems(typingHistoryItems);
                persistTypingHistory();
            } else {
                sortItems(items);
                persistClipboardItems();
            }
            notifyHistoryChange();
        }
    }

    public void clearHistory() {
        items.clear();
        persistClipboardItems();
        notifyHistoryChange();
    }

    public void clearTypingHistory() {
        typingHistoryItems.clear();
        currentTypingSessionItem = null;
        persistTypingHistory();
        notifyHistoryChange();
    }

    public void setOnClipboardHistoryChange(OnClipboardHistoryChange l) {
        listener = l;
    }

    private void sortItems(List<ClipboardItem> list) {
        Collections.sort(list, new Comparator<ClipboardItem>() {
            @Override
            public int compare(ClipboardItem o1, ClipboardItem o2) {
                if (o1.isArchived() != o2.isArchived()) {
                    return o1.isArchived() ? 1 : -1;
                }
                if (o1.isArchived()) {
                    String n1 = o1.getName() != null ? o1.getName() : "";
                    String n2 = o2.getName() != null ? o2.getName() : "";
                    return n1.compareToIgnoreCase(n2);
                }
                if (o1.isPinned() == o2.isPinned()) {
                    return o1.isPinned() ? Long.compare(o1.getTimestamp(), o2.getTimestamp())
                                         : Long.compare(o2.getTimestamp(), o1.getTimestamp());
                }
                return o1.isPinned() ? 1 : -1;
            }
        });
    }

    private void sortItems() {
        sortItems(items);
    }

    private void trimHistory() {

    }

    private void notifyHistoryChange() {
        if (listener != null) {
            listener.on_clipboard_history_change();
        }
    }

    private void addCurrentClip() {
        ClipData clip = clipboardManager.getPrimaryClip();
        if (clip == null) return;
        int count = clip.getItemCount();
        for (int i = 0; i < count; i++) {
            CharSequence text = clip.getItemAt(i).getText();
            if (text != null) {
                addClip(text.toString());
            }
        }
    }

    private boolean isSystemClipboard(String text) {
        ClipData clip = clipboardManager.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence clipText = clip.getItemAt(0).getText();
            return clipText != null && clipText.toString().equals(text);
        }
        return false;
    }

    private void loadItems() {
        File file = new File(context.getFilesDir(), PERSIST_FILE_NAME);
        if (!file.exists()) {
            migrateFromPrefs();
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONArray jsonArray = new JSONArray(sb.toString());
            items.clear();
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    items.add(ClipboardItem.fromJSON(jsonArray.getJSONObject(i)));
                } catch (JSONException e) {
                    Log.e(TAG, "Skipping malformed item in clipboard history", e);
                }
            }
            sortItems(items);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to load clipboard history", e);
        }
    }

    private void loadTypingHistory() {
        File file = new File(context.getFilesDir(), TYPING_HISTORY_FILE_NAME);
        if (!file.exists()) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONArray jsonArray = new JSONArray(sb.toString());
            typingHistoryItems.clear();
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    typingHistoryItems.add(ClipboardItem.fromJSON(jsonArray.getJSONObject(i)));
                } catch (JSONException e) {
                    Log.e(TAG, "Skipping malformed item in typing history", e);
                }
            }
            sortItems(typingHistoryItems);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to load typing history", e);
        }
    }

    private void persistClipboardItems() {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            JSONArray jsonArray = new JSONArray();
            synchronized (items) {
                for (ClipboardItem item : items) {
                    try {
                        jsonArray.put(item.toJSON());
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to convert item to JSON", e);
                    }
                }
            }

            String jsonPayload = jsonArray.toString();

            File file = new File(context.getFilesDir(), PERSIST_FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                writer.write(jsonPayload);
            } catch (IOException e) {
                Log.e(TAG, "Failed to persist clipboard history", e);
            }

            // Also write to external backup for real-time sync
            try {
                File externalDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup");
                if (!externalDir.exists()) {
                    externalDir.mkdirs();
                }
                File externalFile = new File(externalDir, "clipboard_export.json");
                try (FileOutputStream fos = new FileOutputStream(externalFile);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    writer.write(jsonPayload);
                }
                // Trigger Drive Sync
                DriveSyncHelper.syncFileToDrive(context, externalFile, "application/json");
            } catch (Exception e) {
                Log.e(TAG, "Failed to export clipboard history to external storage", e);
            }
        });
    }

    private void persistTypingHistory() {

        List<ClipboardItem> copy;
        synchronized (typingHistoryItems) {
             copy = new ArrayList<>(typingHistoryItems);
        }

        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            JSONArray jsonArray = new JSONArray();
            for (ClipboardItem item : copy) {
                try {
                    jsonArray.put(item.toJSON());
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to convert item to JSON", e);
                }
            }

            String jsonPayload = jsonArray.toString();

            File file = new File(context.getFilesDir(), TYPING_HISTORY_FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                writer.write(jsonPayload);
            } catch (IOException e) {
                Log.e(TAG, "Failed to persist typing history", e);
            }
        });
    }

    private void migrateFromPrefs() {
        SharedPreferences store = context.getSharedPreferences("pinned_clipboards", Context.MODE_PRIVATE);
        String arr_s = store.getString("pinned", null);
        if (arr_s == null) return;

        try {
            JSONArray arr = new JSONArray(arr_s);
            long currentTime = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                String text = arr.getString(i);
                items.add(new ClipboardItem(text, currentTime + i, true));
            }
            sortItems(items);
            persistClipboardItems();
            store.edit().clear().apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to migrate pinned clips", e);
        }
    }

    public void importFromUri(Uri uri) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read import file", e);
            return;
        }
        mergeJsonData(sb.toString());
    }

    public void mergeWithFile(File file) {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read import file", e);
            return;
        }
        mergeJsonData(sb.toString());
    }

    private void mergeJsonData(String jsonString) {
        try {
            JSONArray jsonArray = null;
            String trimmed = jsonString.trim();
            if (trimmed.startsWith("{")) {
                JSONObject obj = new JSONObject(trimmed);
                if (obj.has("clips")) jsonArray = obj.getJSONArray("clips");
                else if (obj.has("history")) jsonArray = obj.getJSONArray("history");
                else if (obj.has("items")) jsonArray = obj.getJSONArray("items");
            } else if (trimmed.startsWith("[")) {
                jsonArray = new JSONArray(trimmed);
            }

            if (jsonArray == null) return;

            Set<ClipboardItem> existingItems = new HashSet<>(items);
            int importedCount = 0;
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    ClipboardItem newItem = ClipboardItem.fromJSON(jsonArray.getJSONObject(i));
                    if (newItem.getText() != null && !newItem.getText().isEmpty() && !existingItems.contains(newItem)) {
                        items.add(newItem);
                        existingItems.add(newItem);
                        importedCount++;
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Skipping malformed item during merge", e);
                }
            }
            if (importedCount > 0) {
                sortItems(items);
                persistClipboardItems();
                notifyHistoryChange();
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse import data", e);
        }
    }

    public void exportToUri(Uri uri) {
        JSONArray jsonArray = new JSONArray();
        for (ClipboardItem item : items) {
            try {
                jsonArray.put(item.toJSON());
            } catch (JSONException e) {
                Log.e(TAG, "Failed to convert item to JSON for export", e);
            }
        }

        try (OutputStream os = context.getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            writer.write(jsonArray.toString(2));
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to export clipboard history", e);
        }
    }

    public interface OnClipboardHistoryChange {
        void on_clipboard_history_change();
    }

    public interface ClipboardPasteCallback {
        void paste_from_clipboard_pane(String content);
    }

    private final class SystemListener implements ClipboardManager.OnPrimaryClipChangedListener {
        @Override
        public void onPrimaryClipChanged() {
            addCurrentClip();
        }
    }
}
