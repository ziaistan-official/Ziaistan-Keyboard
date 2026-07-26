package juloo.keyboard2;

import android.content.Context;
import android.os.FileObserver;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClipboardRepository {
    private static final String TAG = "ClipboardRepository";
    private final ClipboardItemFileStore fileStore;
    private final ClipboardItemIndexStore indexStore;
    private final List<ClipboardItem> items = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> contentHashes = Collections.synchronizedSet(new HashSet<>());
    private final List<FileObserver> observers = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicBoolean isSyncingOrWriting = new java.util.concurrent.atomic.AtomicBoolean(false);

    public ClipboardRepository(Context context) {
        this.fileStore = new ClipboardItemFileStore(context);
        this.indexStore = new ClipboardItemIndexStore(context);
        loadFromIndex();
        startObserving();
    }

    private void startObserving() {
        File baseDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup/clipboards");
        if (!baseDir.exists()) baseDir.mkdirs();

        addObserver(baseDir);

        File unpinnedDir = new File(baseDir, "unpinned");
        if (!unpinnedDir.exists()) unpinnedDir.mkdirs();
        addObserver(unpinnedDir);

        File pinnedDir = new File(baseDir, "pinned_and_archived");
        if (!pinnedDir.exists()) pinnedDir.mkdirs();
        addObserver(pinnedDir);

        String[] subDirs = {"pdf", "docx", "txt", "csv", "xlsx", "html", "py", "js"};
        for (String sub : subDirs) {
            File dir = new File(baseDir, sub);
            if (!dir.exists()) dir.mkdirs();
            addObserver(dir);
        }
    }

    private void addObserver(File dir) {
        FileObserver observer = new FileObserver(dir.getAbsolutePath(), FileObserver.CREATE | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE) {
            @Override
            public void onEvent(int event, String path) {
                if (path != null && !path.endsWith(".tmp")) {
                    if (isSyncingOrWriting.get()) {
                        return;
                    }
                    syncWithDisk();
                }
            }
        };
        observer.startWatching();
        observers.add(observer);
    }

    private void loadFromIndex() {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            isSyncingOrWriting.set(true);
            try {
                List<ClipboardItem> loaded = indexStore.loadIndex();
                List<ClipboardItem> deduplicated = new ArrayList<>();
                Set<String> hashes = new HashSet<>();
                boolean changed = false;

                for (ClipboardItem item : loaded) {
                    String hash = item.getContentHash();
                    // Load body if missing to ensure we can calculate hash correctly if it was legacy
                    if ((hash == null || hash.isEmpty()) && item.getFilePath() != null) {
                        try {
                            ClipboardItem full = fileStore.loadItem(new File(item.getFilePath()));
                            item.setBody(full.getBody());
                            item.setHasBody(true);
                            hash = ClipboardItem.calculateHash(item.getText());
                        } catch (Exception e) {}
                    }

                    if (hash != null && !hash.isEmpty()) {
                        if (hashes.add(hash)) {
                            deduplicated.add(item);
                        } else {
                            // Duplicate found, delete from disk
                            fileStore.deleteItem(item);
                            changed = true;
                        }
                    } else {
                        deduplicated.add(item);
                    }
                }

                synchronized (items) {
                    items.clear();
                    items.addAll(deduplicated);
                    contentHashes.clear();
                    contentHashes.addAll(hashes);
                }

                if (changed) {
                    indexStore.saveIndex(deduplicated);
                }

                // Eagerly load bodies for all items in background for faster search
                for (ClipboardItem item : deduplicated) {
                    if (!item.hasBody() && item.getFilePath() != null) {
                        try {
                            ClipboardItem full = fileStore.loadItem(new File(item.getFilePath()));
                            item.setBody(full.getBody());
                            item.setHasBody(true);
                        } catch (Exception e) {}
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load index", e);
            } finally {
                isSyncingOrWriting.set(false);
            }
        });
    }

    public List<ClipboardItem> getItems() {
        synchronized (items) {
            List<ClipboardItem> copy = new ArrayList<>();
            for (ClipboardItem item : items) copy.add(item.clone());
            return copy;
        }
    }

    public List<ClipboardItem> getItemsRaw() {
        return items;
    }

    public void addItem(ClipboardItem item, Runnable onComplete) {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            isSyncingOrWriting.set(true);
            try {
                addItemSynchronous(item);
            } finally {
                isSyncingOrWriting.set(false);
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public ClipboardItem loadItemFromFile(File file) throws IOException, JSONException {
        return fileStore.loadItem(file);
    }

    public void addItemSynchronous(ClipboardItem item) {
        synchronized (items) {
            String hash = item.getContentHash();
            ClipboardItem existing = null;
            for (ClipboardItem i : items) {
                if (hash.equals(i.getContentHash())) {
                    existing = i;
                    break;
                }
            }

            if (existing != null) {
                // Update existing item to new timestamp and move to top
                existing.setCreatedAt(item.getCreatedAt());
                existing.setLastUsedAt(item.getCreatedAt());

                // Refresh folder/path on disk
                try {
                    getFullTextSynchronous(existing);
                    fileStore.saveItem(existing);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to update item on disk", e);
                }

                items.remove(existing);
                items.add(0, existing);
                try {
                    indexStore.saveIndex(items);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save index after update", e);
                }
                return;
            }
        }

        try {
            fileStore.saveItem(item);
            items.add(0, item);
            contentHashes.add(item.getContentHash());
            indexStore.saveIndex(items);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add item", e);
        }
    }

    public void removeItem(ClipboardItem item) {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            isSyncingOrWriting.set(true);
            try {
                fileStore.deleteItem(item);
                synchronized (items) {
                    items.removeIf(i -> i.getId().equals(item.getId()));
                }
                contentHashes.remove(item.getContentHash());
                indexStore.saveIndex(items);
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove item", e);
            } finally {
                isSyncingOrWriting.set(false);
            }
        });
    }

    public void updateItem(ClipboardItem item) {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            isSyncingOrWriting.set(true);
            try {
                fileStore.moveItem(item);
                fileStore.saveItem(item);
                synchronized (items) {
                    for (int i = 0; i < items.size(); i++) {
                        if (items.get(i).getId().equals(item.getId())) {
                            items.set(i, item);
                            break;
                        }
                    }
                }
                indexStore.saveIndex(items);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update item", e);
            } finally {
                isSyncingOrWriting.set(false);
            }
        });
    }

    public void loadFullContent(Context context, ClipboardItem item, ContentCallback callback) {
        if (item.hasBody() && item.getText() != null && !item.getText().isEmpty() && !item.getText().startsWith("[Binary File:")) {
            callback.onLoaded(item);
            return;
        }

        if (item.getContentLength() > 10 * 1024 * 1024) {
            item.setBody(new ClipboardItem.Body("text", "[FILE TOO LARGE TO PREVIEW (>10MB)]"));
            callback.onLoaded(item);
            return;
        }


        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            try {
                if (item.getFilePath() != null) {
                    ClipboardItem fullItem = fileStore.loadItem(new File(item.getFilePath()));
                    item.setBody(fullItem.getBody());
                    callback.onLoaded(item);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load full content", e);
            }
        });
    }

    public interface ContentCallback {
        void onLoaded(ClipboardItem item);
    }

    private final android.util.LruCache<String, String> fullTextCache = new android.util.LruCache<>(200);

    public String getFullTextSynchronous(ClipboardItem item) {
        if (item.hasBody() && item.getText() != null && !item.getText().isEmpty()) {
            return item.getText();
        }

        String cached = fullTextCache.get(item.getId());
        if (cached != null) return cached;

        if (item.getFilePath() != null) {
            File f = new File(item.getFilePath());
            if (f.exists()) {
                try {
                    // Safety limit: 500KB to avoid OOM
                    long len = f.length();
                    if (len > 500 * 1024) return item.getContentPreview();

                    ClipboardItem full = fileStore.loadItem(f);
                    if (full != null && full.getText() != null) {
                        String text = full.getText();
                        item.setBody(full.getBody());
                        item.setHasBody(true);
                        fullTextCache.put(item.getId(), text);
                        return text;
                    }
                } catch (Exception e) {}
            }
        }
        return item.getContentPreview();
    }

    public void syncWithDisk() {
        if (!isSyncingOrWriting.compareAndSet(false, true)) {
            return;
        }
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            try {
                File baseDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup/clipboards");
            if (!baseDir.exists()) return;

            List<File> allFiles = new ArrayList<>();
            scanFiles(baseDir, allFiles);

            boolean changed = false;

            // Step 1: Global Deduplication Pass (ID and Hash based)
            java.util.Map<String, File> idToFile = new java.util.HashMap<>();
            java.util.Map<String, File> hashToBestFile = new java.util.HashMap<>();
            java.util.Map<File, String> fileToHash = new java.util.HashMap<>();

            for (File file : allFiles) {
                if (file.getName().endsWith(".tmp")) continue;
                try {
                    String hash;
                    if (file.getName().endsWith(".json")) {
                        ClipboardItem item = fileStore.loadItem(file);
                        hash = item.getContentHash();
                    } else {
                        hash = ClipboardItem.calculateHash(Utils.read_all_utf8(new java.io.FileInputStream(file)));
                    }
                    fileToHash.put(file, hash);

                    if (hashToBestFile.containsKey(hash)) {
                        File existing = hashToBestFile.get(hash);
                        if (shouldReplaceDuplicate(existing, file)) {
                            existing.delete();
                            hashToBestFile.put(hash, file);
                            changed = true;
                        } else {
                            file.delete();
                            changed = true;
                        }
                    } else {
                        hashToBestFile.put(hash, file);
                    }
                } catch (Exception e) {}
            }

            // Refresh ID map after hash deduplication
            for (File file : hashToBestFile.values()) {
                String name = file.getName();
                int underscoreIdx = name.indexOf('_');
                String id = (underscoreIdx != -1) ? name.substring(0, underscoreIdx) :
                           (name.endsWith(".json") ? name.substring(0, name.length() - 5) : null);
                if (id != null) {
                    if (idToFile.containsKey(id)) {
                        File existing = idToFile.get(id);
                        if (shouldReplaceDuplicate(existing, file)) {
                            existing.delete();
                            idToFile.put(id, file);
                            changed = true;
                        } else {
                            file.delete();
                            changed = true;
                        }
                    } else {
                        idToFile.put(id, file);
                    }
                }
            }

            // Step 2: Sync in-memory items
            synchronized (items) {
                java.util.Iterator<ClipboardItem> it = items.iterator();
                while (it.hasNext()) {
                    ClipboardItem item = it.next();
                    File f = idToFile.get(item.getId());
                    if (f == null || !f.exists()) {
                        contentHashes.remove(item.getContentHash());
                        it.remove();
                        changed = true;
                    } else {
                        if (!f.getAbsolutePath().equals(item.getFilePath())) {
                            item.setFilePath(f.getAbsolutePath());
                            // Ensure internal status matches physical location
                            if (f.getAbsolutePath().contains("/pinned_and_archived/")) {
                                item.setPinned(true);
                            } else if (f.getAbsolutePath().contains("/unpinned/")) {
                                item.setPinned(false);
                                item.setArchived(false);
                            }

                            try {
                                getFullTextSynchronous(item);
                                fileStore.saveItem(item);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to persist updated path in JSON", e);
                            }
                        }
                        idToFile.remove(item.getId()); // Handled
                    }
                }
            }

            // Step 3: Add truly new files
            for (File file : idToFile.values()) {
                if (!file.exists()) continue;
                try {
                    ClipboardItem item;
                    if (file.getName().endsWith(".json")) {
                        item = fileStore.loadItem(file);
                    } else {
                        item = convertToItem(file);
                        fileStore.saveItem(item);
                        file.delete();
                        file = new File(item.getFilePath());
                    }

                    if (item != null && !contentHashes.contains(item.getContentHash())) {
                        item.setFilePath(file.getAbsolutePath());
                        fileStore.saveItem(item);
                        items.add(0, item);
                        contentHashes.add(item.getContentHash());
                        changed = true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to sync file: " + file.getName(), e);
                }
            }

            if (changed) {
                try {
                    indexStore.saveIndex(items);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save index after sync", e);
                }
            }

            // Re-populate content hashes to avoid drifts
            synchronized (items) {
                contentHashes.clear();
                for (ClipboardItem item : items) {
                    contentHashes.add(item.getContentHash());
                }
            }
            } finally {
                isSyncingOrWriting.set(false);
            }
        });
    }

    private void scanFiles(File dir, List<File> results) {
        if (dir.getName().equalsIgnoreCase("trash")) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanFiles(f, results);
            } else {
                results.add(f);
            }
        }
    }

    private ClipboardItem convertToItem(File file) throws IOException {
        String ext = "";
        int dotIndex = file.getName().lastIndexOf('.');
        if (dotIndex > 0) ext = file.getName().substring(dotIndex + 1).toLowerCase();

        String preview = "";
        boolean isDoc = ext.equals("pdf") || ext.equals("docx");

        if (file.length() > 10 * 1024 * 1024) {
             preview = "[FILE TOO LARGE TO AUTO-IMPORT (" + (file.length() / 1024 / 1024) + "MB)]";
        } else if (!isDoc) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
            }
            preview = sb.toString();
        } else {
            preview = "[Binary File: " + ext.toUpperCase() + " ID: " + Integer.toHexString(file.getAbsolutePath().hashCode()) + "]";
        }

        ClipboardItem item = new ClipboardItem(preview, file.lastModified(), "manual_import");
        item.setExtension(ext);
        item.setFilePath(file.getAbsolutePath());
        if (isDoc) {
            item.setHasBody(false);
            item.setBody(null);
        }
        return item;
    }

    private boolean shouldReplaceDuplicate(File existing, File current) {
        String existingPath = existing.getAbsolutePath();
        String currentPath = current.getAbsolutePath();

        // 1. Priority for non-trash
        boolean existingInTrash = existingPath.contains("/trash/");
        boolean currentInTrash = currentPath.contains("/trash/");
        if (existingInTrash && !currentInTrash) return true;
        if (!existingInTrash && currentInTrash) return false;

        // 2. Priority for pinned/archived
        boolean existingPinned = existingPath.contains("/pinned_and_archived/");
        boolean currentPinned = currentPath.contains("/pinned_and_archived/");
        if (!existingPinned && currentPinned) return true;
        if (existingPinned && !currentPinned) return false;

        // 3. Keep newer
        return current.lastModified() > existing.lastModified();
    }

    private boolean isKnown(File file) {
        String path = file.getAbsolutePath();
        String name = file.getName();
        // Extract ID from filename if possible (item.getId() + "_" + item.getCreatedAt() + ".json")
        int underscoreIdx = name.indexOf('_');
        String id = (underscoreIdx != -1) ? name.substring(0, underscoreIdx) :
                   (name.endsWith(".json") ? name.substring(0, name.length() - 5) : null);

        synchronized (items) {
            for (ClipboardItem item : items) {
                if (path.equals(item.getFilePath())) return true;
                if (id != null && id.equals(item.getId())) {
                    // Update path if it changed but item is same
                    if (!path.equals(item.getFilePath())) {
                        item.setFilePath(path);
                        // Synchronously update metadata on disk if possible or at least ensure it happens
                        try {
                            getFullTextSynchronous(item);
                            fileStore.saveItem(item);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to update filePath in JSON", e);
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
