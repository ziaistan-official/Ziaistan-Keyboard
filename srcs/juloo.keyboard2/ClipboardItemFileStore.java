package juloo.keyboard2;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class ClipboardItemFileStore {
    private static final String TAG = "ClipboardItemFileStore";
    private final File baseDir;

    public ClipboardItemFileStore(Context context) {
        this.baseDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup/clipboards");
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    private String getDateString(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US);
        return sdf.format(new java.util.Date(timestamp));
    }

    private File getFolderForItem(ClipboardItem item) {
        String dateStr = getDateString(item.getCreatedAt());
        String mainFolder = (item.isPinned() || item.isArchived()) ? "pinned_and_archived" : "unpinned";
        File dir = new File(baseDir, mainFolder + "/" + dateStr);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public void saveItem(ClipboardItem item) throws IOException, JSONException {
        File dir = getFolderForItem(item);
        String fileName = item.getId() + "_" + item.getCreatedAt() + ".json";
        File file = new File(dir, fileName);

        String oldPath = item.getFilePath();
        item.setFilePath(file.getAbsolutePath());

        File tmpFile = new File(dir, fileName + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmpFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(item.toJSON().toString(2));
            writer.flush();
            fos.getFD().sync();
        }

        if (!tmpFile.renameTo(file)) {
            throw new IOException("Failed to rename tmp file to " + file.getAbsolutePath());
        }

        // Clean up old file if path changed
        if (oldPath != null && !oldPath.equals(file.getAbsolutePath())) {
            File oldFile = new File(oldPath);
            if (oldFile.exists()) {
                oldFile.delete();
            }
        }
    }

    public void moveItem(ClipboardItem item) {
        if (item.getFilePath() == null) return;
        File oldFile = new File(item.getFilePath());
        if (!oldFile.exists()) return;

        File newDir = getFolderForItem(item);
        File newFile = new File(newDir, oldFile.getName());

        if (!oldFile.getAbsolutePath().equals(newFile.getAbsolutePath())) {
            if (oldFile.renameTo(newFile)) {
                item.setFilePath(newFile.getAbsolutePath());
            }
        }
    }

    public ClipboardItem loadItem(File file) throws IOException, JSONException {
        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            ClipboardItem item = ClipboardItem.fromJSON(new JSONObject(sb.toString()));
            item.setFilePath(file.getAbsolutePath());
            return item;
        }
    }

    public void deleteItem(ClipboardItem item) {
        if (item.getFilePath() != null) {
            File file = new File(item.getFilePath());
            if (file.exists()) {
                File trashDir = new File(baseDir, "trash");
                if (!trashDir.exists()) trashDir.mkdirs();
                File target = new File(trashDir, file.getName());
                file.renameTo(target);
                item.setFilePath(target.getAbsolutePath());
            }
        }
    }
}
