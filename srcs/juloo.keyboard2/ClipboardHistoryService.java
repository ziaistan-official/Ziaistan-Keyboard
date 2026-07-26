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
import java.util.Arrays;
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

    public static class SentenceMatch {
        public final String originalTyped;
        public final String correctedPrefix;
        public final String completion;

        public SentenceMatch(String originalTyped, String correctedPrefix, String completion) {
            this.originalTyped = originalTyped;
            this.correctedPrefix = correctedPrefix;
            this.completion = completion;
        }

        @Override
        public String toString() {
            return completion;
        }
    }
    private static final String PERSIST_FILE_NAME = "clipboard_history.json";
    private static final String TYPING_HISTORY_FILE_NAME = "typing_history.json";
    public static final String RELOAD_CLIPBOARD_HISTORY_ACTION = "juloo.keyboard2.RELOAD_CLIPBOARD_HISTORY";

    private static ClipboardHistoryService _service = null;
    private static ClipboardPasteCallback _paste_callback = null;

    private final Context context;
    private final ClipboardManager clipboardManager;
    private final ClipboardRepository repository;
    private final List<ClipboardItem> typingHistoryItems;
    private OnClipboardHistoryChange listener = null;

    private ClipboardItem currentTypingSessionItem = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable persistTypingHistoryRunnable = this::persistTypingHistory;
    private static final long PERSIST_DELAY_MS = 2000; // Debounce persistence longer

    private String _last_pasted_text = null;

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
        if (_service != null) {
            _service._last_pasted_text = clip;
        }
        if (_paste_callback != null) {
            _paste_callback.paste_from_clipboard_pane(clip);
        }
    }

    private ClipboardHistoryService(Context ctx) {
        this.context = ctx;
        this.repository = new ClipboardRepository(ctx);
        this.typingHistoryItems = Collections.synchronizedList(new ArrayList<>());
        this.clipboardManager = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        this.clipboardManager.addPrimaryClipChangedListener(new SystemListener());

        migrateLegacyData();
        loadTypingHistory();

        IntentFilter filter = new IntentFilter(RELOAD_CLIPBOARD_HISTORY_ACTION);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                repository.syncWithDisk();
                notifyHistoryChange();
            }
        };

        if (VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }

    private void moveLegacyFilesToTimestampedFolders() {
        File baseClipDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup/clipboards");
        moveFiles(new File(baseClipDir, "unpinned"), false);
        moveFiles(new File(baseClipDir, "pinned_and_archived"), false);
        moveFiles(getTypingHistoryDir(), true);

        // Also migrate from extension folders (e.g. clipboards/txt/)
        String[] exts = {"pdf", "docx", "txt", "csv", "xlsx", "html", "py", "js"};
        for (String ext : exts) {
            moveFiles(new File(baseClipDir, ext), false);
        }
    }

    private void moveFiles(File dir, boolean isTyping) {
        if (!dir.exists() || !dir.isDirectory()) return;

        List<File> allFiles = new ArrayList<>();
        scanTypingFiles(dir, allFiles);

        for (File f : allFiles) {
            // Only migrate files that are not already in a timestamped folder
            // Timestamped folders are at depth 1 (unpinned/YYYY-MM-DD/file.json)
            // If the parent name matches our date pattern, skip it.
            String parentName = f.getParentFile().getName();
            if (parentName.matches("\\d{2}-\\d{2}-\\d{4}")) continue;

            if (f.isFile() && f.getName().endsWith(".json")) {
                try {
                    ClipboardItem item = isTyping ? ClipboardItem.fromJSON(new JSONObject(Utils.read_all_utf8(new FileInputStream(f)))) : repository.loadItemFromFile(f);
                    if (item != null) {
                        if (isTyping) saveTypingItemToDisk(item);
                        else repository.updateItem(item); // updateItem will move it via fileStore.moveItem

                        if (!f.getAbsolutePath().equals(item.getFilePath())) {
                            f.delete();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to migrate file " + f.getName(), e);
                }
            }
        }
    }

    private void migrateLegacyData() {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            moveLegacyFilesToTimestampedFolders();
            // Clipboard migration
            File legacyFile = new File(context.getFilesDir(), PERSIST_FILE_NAME);
            File legacyExport = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup/clipboard_export.json");

            if (legacyFile.exists()) {
                if (migrateFile(legacyFile)) legacyFile.delete();
            }
            if (legacyExport.exists()) {
                migrateFile(legacyExport);
            }

            // Typing History legacy JSON migration
            File legacyTyping = new File(context.getFilesDir(), TYPING_HISTORY_FILE_NAME);
            if (legacyTyping.exists()) {
                try (FileInputStream fis = new FileInputStream(legacyTyping);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    JSONArray array = new JSONArray(sb.toString());
                    for (int i = 0; i < array.length(); i++) {
                        ClipboardItem item = ClipboardItem.fromJSON(array.getJSONObject(i));
                        saveTypingItemToDisk(item);
                    }
                    legacyTyping.delete();
                } catch (Exception e) { Log.e(TAG, "Legacy typing migration error", e); }
            }

            // Migration from external typing history back to internal
            File externalTypingDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup/typing_history");
            if (externalTypingDir.exists()) {
                File[] files = externalTypingDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        File internalFile = new File(getTypingHistoryDir(), f.getName());
                        if (f.renameTo(internalFile)) {
                            Log.d(TAG, "Migrated typing history item: " + f.getName());
                        } else {
                            // Try copy if rename fails
                            try (InputStream in = new FileInputStream(f);
                                 OutputStream out = new FileOutputStream(internalFile)) {
                                byte[] buf = new byte[8192];
                                int len;
                                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                                f.delete();
                            } catch (IOException e) { Log.e(TAG, "Failed to migrate " + f.getName(), e); }
                        }
                    }
                }
            }
        });
    }

    private File getTypingHistoryDir() {
        File dir = new File(context.getFilesDir(), "typing_history");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private boolean migrateFile(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                try {
                    ClipboardItem item = ClipboardItem.fromJSON(array.getJSONObject(i));
                    // Synchronous-ish add for migration safety
                    repository.addItemSynchronous(item);
                } catch (JSONException e) {
                    Log.e(TAG, "Migration error for item " + i, e);
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate " + file.getName(), e);
            return false;
        }
    }

    public List<ClipboardItem> getItems() {
        return repository.getItems();
    }

    public List<ClipboardItem> getTypingHistory() {
        synchronized (typingHistoryItems) {
            return new ArrayList<>(typingHistoryItems);
        }
    }

    public ClipboardRepository getRepository() {
        return repository;
    }

    public void startNewTypingSession() {
        currentTypingSessionItem = null;
    }

    public void updateCurrentTypingSession(String text) {
        if (!Config.globalConfig().enable_typing_history || text == null || text.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        String hash = ClipboardItem.calculateHash(text);

        synchronized (typingHistoryItems) {
            // Deduplicate by hash
            for (int i = 0; i < Math.min(50, typingHistoryItems.size()); i++) {
                ClipboardItem item = typingHistoryItems.get(i);
                if (hash.equals(item.getContentHash())) {
                    currentTypingSessionItem = item;
                    item.setCreatedAt(currentTime);
                    return;
                }
            }

            if (currentTypingSessionItem == null) {
                // Quick check first few items for substring matches or fuzzy matches
                for (int i = 0; i < Math.min(10, typingHistoryItems.size()); i++) {
                    ClipboardItem item = typingHistoryItems.get(i);
                    String itemText = item.getText();
                    if (itemText.length() < 5000 && text.length() < 5000) {
                        if (itemText.contains(text) || text.contains(itemText) || Utils.fuzzyPhraseMatch(text, itemText) || Utils.fuzzyPhraseMatch(itemText, text)) {
                            if (text.length() > itemText.length()) {
                                item.setBody(new ClipboardItem.Body("text", text));
                                item.setCreatedAt(currentTime);
                            }
                            currentTypingSessionItem = item;
                            return;
                        }
                    }
                }
                currentTypingSessionItem = new ClipboardItem(text, currentTime, "typing");
                typingHistoryItems.add(0, currentTypingSessionItem);
            } else {
                String currentText = currentTypingSessionItem.getText();
                if (text.equals(currentText)) return;

                if (text.startsWith(currentText) || Utils.fuzzyPhraseMatch(currentText, text)) {
                    currentTypingSessionItem.setBody(new ClipboardItem.Body("text", text));
                    currentTypingSessionItem.setCreatedAt(currentTime);
                } else if (currentText.startsWith(text) || Utils.fuzzyPhraseMatch(text, currentText)) {
                    // Ignore shorter/already-matched version
                } else {
                    currentTypingSessionItem = new ClipboardItem(text, currentTime, "typing");
                    typingHistoryItems.add(0, currentTypingSessionItem);
                }
            }

            while (typingHistoryItems.size() > 2000) {
                typingHistoryItems.remove(typingHistoryItems.size() - 1);
            }
        }

        handler.removeCallbacks(persistTypingHistoryRunnable);
        handler.postDelayed(persistTypingHistoryRunnable, PERSIST_DELAY_MS);
        notifyHistoryChange();
    }

    private boolean typingHistoryHistoryMatches(String text) {
        if (typingHistoryItems.isEmpty()) return false;
        return typingHistoryItems.get(0).getText().equals(text);
    }

    public void addTypingHistory(String text) {
        updateCurrentTypingSession(text);
        persistTypingHistory();
    }

    public void addClip(String clip) {
        if (!Config.globalConfig().clipboard_history_enabled || clip == null || clip.trim().isEmpty()) {
            return;
        }

        if (clip.equals(_last_pasted_text)) {
            _last_pasted_text = null;
            return;
        }

        repository.addItem(new ClipboardItem(clip, System.currentTimeMillis(), "clipboard"), () -> {
            notifyHistoryChange();
            repository.syncWithDisk();
        });
    }

    public void removeUnpinnedItemsByTime(long durationMillis, boolean isTypingHistory) {
        long threshold = System.currentTimeMillis() - durationMillis;
        if (isTypingHistory) {
            synchronized (typingHistoryItems) {
                typingHistoryItems.removeIf(item -> !item.isPinned() && !item.isArchived() && item.getCreatedAt() >= threshold);
            }
            persistTypingHistory();
        } else {
            for (ClipboardItem item : repository.getItems()) {
                if (!item.isPinned() && !item.isArchived() && item.getCreatedAt() >= threshold) {
                    repository.removeItem(item);
                }
            }
        }
        notifyHistoryChange();
    }

    public void removeUnpinnedItemsOlderThan(long ageMillis, boolean isTypingHistory) {
        long threshold = System.currentTimeMillis() - ageMillis;
        if (isTypingHistory) {
            synchronized (typingHistoryItems) {
                typingHistoryItems.removeIf(item -> !item.isPinned() && !item.isArchived() && item.getCreatedAt() < threshold);
            }
            persistTypingHistory();
        } else {
            for (ClipboardItem item : repository.getItems()) {
                if (!item.isPinned() && !item.isArchived() && item.getCreatedAt() < threshold) {
                    repository.removeItem(item);
                }
            }
        }
        notifyHistoryChange();
    }

    public void removeAllUnpinned(boolean isTypingHistory) {
        if (isTypingHistory) {
            synchronized (typingHistoryItems) {
                typingHistoryItems.removeIf(item -> !item.isPinned() && !item.isArchived());
            }
            persistTypingHistory();
        } else {
            for (ClipboardItem item : repository.getItems()) {
                if (!item.isPinned() && !item.isArchived()) {
                    repository.removeItem(item);
                }
            }
        }
        notifyHistoryChange();
    }

    public void removeItem(ClipboardItem item) {
        if (typingHistoryItems.remove(item)) {
            if (item == currentTypingSessionItem) currentTypingSessionItem = null;
            persistTypingHistory();
        } else {
            repository.removeItem(item);
        }
        notifyHistoryChange();
    }

    public void restoreItem(ClipboardItem item, boolean isTypingHistory) {
        if (isTypingHistory) {
            if (!typingHistoryItems.contains(item)) {
                typingHistoryItems.add(0, item);
                persistTypingHistory();
            }
        } else {
            repository.addItem(item, this::notifyHistoryChange);
        }
        notifyHistoryChange();
    }

    public void archiveItem(ClipboardItem item, String name) {
        item.setArchived(true);
        if (name != null) item.setName(name);
        updateItem(item);
        notifyHistoryChange();
    }

    public void unarchiveItem(ClipboardItem item) {
        item.setArchived(false);
        item.setName(null);
        updateItem(item);
        notifyHistoryChange();
    }

    public void renameItem(ClipboardItem item, String newName) {
        item.setName(newName);
        updateItem(item);
        notifyHistoryChange();
    }

    public void togglePin(ClipboardItem item) {
        item.setPinned(!item.isPinned());
        updateItem(item);
        notifyHistoryChange();
    }

    private void updateItem(ClipboardItem item) {
        if ("typing".equals(item.getSource())) {
            KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
                saveTypingItemToDisk(item);
            });
        } else {
            repository.updateItem(item);
        }
    }

    public void clearHistory() {
        for (ClipboardItem item : repository.getItems()) {
            repository.removeItem(item);
        }
        notifyHistoryChange();
    }

    public void clearTypingHistory() {
        typingHistoryItems.clear();
        currentTypingSessionItem = null;
        persistTypingHistory();
        notifyHistoryChange();
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
        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            mergeJsonData(sb.toString());
        } catch (IOException e) {
            Log.e(TAG, "Failed to read import file", e);
        }
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

            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    ClipboardItem newItem = ClipboardItem.fromJSON(jsonArray.getJSONObject(i));
                    repository.addItem(newItem, null);
                } catch (JSONException e) {
                    Log.e(TAG, "Skipping malformed item during merge", e);
                }
            }
            notifyHistoryChange();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse import data", e);
        }
    }

    public void exportToUri(Uri uri) {
        JSONArray jsonArray = new JSONArray();
        for (ClipboardItem item : repository.getItems()) {
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

    public List<SentenceMatch> getSentenceCompletions(String currentPrefix) {
        if (!Config.globalConfig().enable_suggestions) return Collections.emptyList();
        if (currentPrefix == null || currentPrefix.length() < 3) return Collections.emptyList();

        List<SentenceMatch> completions = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Scan Clipboard Items
        List<ClipboardItem> cbItems = repository.getItemsRaw();
        synchronized (cbItems) {
            for (ClipboardItem item : cbItems) {
                collectMatches(item, currentPrefix, completions, seen);
                if (completions.size() >= 20) break;
            }
        }

        // Scan External Files via IndexingService
        List<IndexingService.CompletionResult> extCompletions = IndexingService.getInstance(context).getCompletions(currentPrefix);
        if (!extCompletions.isEmpty()) {
            // Prioritize results based on user-defined file priority
            final String priorityStr = Config.globalConfig().clipboard_autocomplete_file_priority;
            final List<String> priorityList = (priorityStr == null || priorityStr.isEmpty())
                ? Collections.emptyList()
                : Arrays.asList(priorityStr.split(","));

            Collections.sort(extCompletions, (a, b) -> {
                int indexA = priorityList.indexOf(a.sourceFile);
                int indexB = priorityList.indexOf(b.sourceFile);

                if (indexA != -1 && indexB != -1) return Integer.compare(indexA, indexB);
                if (indexA != -1) return -1;
                if (indexB != -1) return 1;

                // Fallback to "notes" prioritization if no specific priority is set
                boolean aIsNotes = a.sourceFile.toLowerCase().contains("notes");
                boolean bIsNotes = b.sourceFile.toLowerCase().contains("notes");
                if (aIsNotes && !bIsNotes) return -1;
                if (!aIsNotes && bIsNotes) return 1;
                return a.sourceFile.compareTo(b.sourceFile);
            });

            for (IndexingService.CompletionResult result : extCompletions) {
                if (result.text.length() <= currentPrefix.length()) continue;

                if (result.text.regionMatches(true, 0, currentPrefix, 0, currentPrefix.length())) {
                    String correctedPrefix = result.text.substring(0, currentPrefix.length());
                    String suffix = result.text.substring(currentPrefix.length());
                    suffix = limitWords(suffix, Config.globalConfig().clipboard_autocomplete_word_count);
                    if (suffix != null && !suffix.isEmpty()) {
                        String full = correctedPrefix + suffix;
                        if (seen.add(full)) {
                            completions.add(new SentenceMatch(currentPrefix, correctedPrefix, suffix));
                        }
                    }
                }
            }
        }

        return completions;
    }


    private void collectMatches(ClipboardItem item, String currentPrefix, List<SentenceMatch> results, Set<String> seen) {
        if (item.getFilePath() != null && item.getFilePath().contains("/trash/")) return;
        String text = repository.getFullTextSynchronous(item);
        if (text == null || text.length() < currentPrefix.length()) return;

        int prefixLen = currentPrefix.length();
        for (int i = 0; i <= text.length() - prefixLen; i++) {
            if (i > 0 && Utils.isWordPart(text.charAt(i - 1))) continue;

            String sub = text.substring(i, i + prefixLen);
            if (Utils.fuzzyPhraseMatch(currentPrefix, sub)) {
                String correctedPrefix = sub;
                String suffix = text.substring(i + prefixLen);
                suffix = limitWords(suffix, Config.globalConfig().clipboard_autocomplete_word_count);

                if (suffix != null && !suffix.isEmpty()) {
                    String full = correctedPrefix + suffix;
                    if (seen.add(full)) {
                        results.add(new SentenceMatch(currentPrefix, correctedPrefix, suffix));
                        if (results.size() >= 20) return;
                    }
                }
            }
        }
    }

    private String limitWords(String text, int maxWords) {
        if (text == null || text.isEmpty()) return text;
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) return text;

        int wordCount = 0;
        int lastIdx = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                if (i > 0 && !Character.isWhitespace(text.charAt(i-1))) {
                    wordCount++;
                    if (wordCount == maxWords) {
                        lastIdx = i;
                        break;
                    }
                }
            }
        }
        if (wordCount < maxWords) return text;
        return text.substring(0, lastIdx);
    }

    public void setOnClipboardHistoryChange(OnClipboardHistoryChange l) {
        listener = l;
    }

    private void notifyHistoryChange() {
        if (listener != null) {
            handler.post(() -> {
                if (listener != null) {
                    listener.on_clipboard_history_change();
                }
            });
        }
    }

    private void addCurrentClip() {
        ClipData clip = clipboardManager.getPrimaryClip();
        if (clip == null) return;
        for (int i = 0; i < clip.getItemCount(); i++) {
            CharSequence text = clip.getItemAt(i).getText();
            if (text != null) addClip(text.toString());
        }
    }

    private void loadTypingHistory() {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            File baseDir = getTypingHistoryDir();
            List<File> allFiles = new ArrayList<>();
            scanTypingFiles(baseDir, allFiles);

            List<ClipboardItem> allLoaded = new ArrayList<>();
            for (File file : allFiles) {
                if (!file.getName().endsWith(".json")) continue;
                try (FileInputStream fis = new FileInputStream(file);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    allLoaded.add(ClipboardItem.fromJSON(new JSONObject(sb.toString())));
                } catch (Exception e) { Log.e(TAG, "Load typing item error", e); }
            }

            Collections.sort(allLoaded, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

            List<ClipboardItem> deduplicated = new ArrayList<>();
            Set<String> seenHashes = new HashSet<>();
            for (ClipboardItem item : allLoaded) {
                String hash = item.getContentHash();
                if (hash == null || hash.isEmpty()) {
                    hash = ClipboardItem.calculateHash(item.getText());
                }

                if (hash != null && !hash.isEmpty()) {
                    if (seenHashes.add(hash)) {
                        deduplicated.add(item);
                    } else {
                        // Delete duplicate file
                        if (item.getFilePath() != null) {
                            File file = new File(item.getFilePath());
                            if (file.exists()) file.delete();
                        }
                    }
                } else {
                    deduplicated.add(item);
                }
            }

            // Post-load deduplication to remove shorter versions within 5 minutes of a longer version
            Iterator<ClipboardItem> it = deduplicated.iterator();
            while (it.hasNext()) {
                ClipboardItem current = it.next();
                String currentText = current.getText();
                for (ClipboardItem other : deduplicated) {
                    if (current == other) continue;
                    String otherText = other.getText();
                    if (otherText.length() > currentText.length() && (otherText.startsWith(currentText) || Utils.fuzzyPhraseMatch(currentText, otherText))) {
                        // If they are within 5 minutes
                        if (Math.abs(current.getCreatedAt() - other.getCreatedAt()) < 5 * 60 * 1000) {
                            if (current.getFilePath() != null) {
                                File f = new File(current.getFilePath());
                                if (f.exists()) f.delete();
                            }
                            it.remove();
                            break;
                        }
                    }
                }
            }

            synchronized (typingHistoryItems) {
                typingHistoryItems.clear();
                typingHistoryItems.addAll(deduplicated);
            }
            notifyHistoryChange();
        });
    }

    private void persistTypingHistory() {
        ClipboardItem current = currentTypingSessionItem;
        if (current == null) return;

        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            saveTypingItemToDisk(current);
        });
    }

    private String getDateString(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US);
        return sdf.format(new java.util.Date(timestamp));
    }

    private void scanTypingFiles(File dir, List<File> results) {
        if (dir.getName().equalsIgnoreCase("trash")) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanTypingFiles(f, results);
            } else {
                results.add(f);
            }
        }
    }

    private void saveTypingItemToDisk(ClipboardItem item) {
        File baseDir = getTypingHistoryDir();
        String dateStr = getDateString(item.getCreatedAt());
        String mainFolder = (item.isPinned() || item.isArchived()) ? "pinned_and_archived" : "unpinned";
        File dir = new File(baseDir, mainFolder + "/" + dateStr);
        if (!dir.exists()) dir.mkdirs();

        String fileName = item.getId() + ".json";
        File file = new File(dir, fileName);

        // If item already had a path, move it if necessary
        if (item.getFilePath() != null) {
            File oldFile = new File(item.getFilePath());
            if (oldFile.exists() && !oldFile.getAbsolutePath().equals(file.getAbsolutePath())) {
                if (!oldFile.renameTo(file)) {
                    // fall back to rewrite if rename fails
                }
            }
        }

        item.setFilePath(file.getAbsolutePath());

        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(item.toJSON().toString(2));
        } catch (Exception e) { Log.e(TAG, "Persist typing item error", e); }
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
