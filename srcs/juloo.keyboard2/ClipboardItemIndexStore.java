package juloo.keyboard2;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
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
import java.util.ArrayList;
import java.util.List;

public class ClipboardItemIndexStore {
    private static final String TAG = "ClipboardItemIndexStore";
    private final File indexFile;

    public ClipboardItemIndexStore(Context context) {
        File dir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup/index");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.indexFile = new File(dir, "clipboard_index.json");
    }

    public void saveIndex(List<ClipboardItem> items) throws IOException, JSONException {
        JSONArray array = new JSONArray();
        for (ClipboardItem item : items) {
            JSONObject indexEntry = item.toJSON();
            indexEntry.remove("body"); // Ensure body text is not in the index
            array.put(indexEntry);
        }

        File tmpFile = new File(indexFile.getParentFile(), indexFile.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmpFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(array.toString());
            writer.flush();
            fos.getFD().sync();
        }

        if (!tmpFile.renameTo(indexFile)) {
            throw new IOException("Failed to rename index tmp file");
        }
    }

    public List<ClipboardItem> loadIndex() throws IOException, JSONException {
        List<ClipboardItem> items = new ArrayList<>();
        if (!indexFile.exists()) return items;

        try (FileInputStream fis = new FileInputStream(indexFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                items.add(ClipboardItem.fromJSON(array.getJSONObject(i)));
            }
        }
        return items;
    }
}
