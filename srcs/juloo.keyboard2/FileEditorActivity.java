package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

public class FileEditorActivity extends Activity {

    private Spinner fileSpinner;
    private RecyclerView recyclerView;
    private Button btnAddItem, btnSave, btnBulkOps, btnImport;
    private TextView pathText;
    private FileEntryAdapter adapter;

    private List<String> fileList = new ArrayList<>();

    private String currentFileName;
    private List<FileEntry> entries = new ArrayList<>();

    private static class EditorLevel {
        String path;
        List<FileEntry> levelEntries;
        Object parentJson; // JSONObject or JSONArray

        EditorLevel(String path, List<FileEntry> entries, Object parent) {
            this.path = path;
            this.levelEntries = entries;
            this.parentJson = parent;
        }
    }

    private Stack<EditorLevel> navigationStack = new Stack<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Config config = Config.globalConfig();
        if (config != null) {
            setTheme(config.theme);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);

        fileSpinner = findViewById(R.id.file_spinner);
        recyclerView = findViewById(R.id.editor_recycler_view);
        btnAddItem = findViewById(R.id.btn_add_item);
        btnSave = findViewById(R.id.btn_save);
        btnBulkOps = findViewById(R.id.btn_bulk_ops);
        btnImport = findViewById(R.id.btn_import);
        pathText = findViewById(R.id.path_text);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileEntryAdapter();
        recyclerView.setAdapter(adapter);

        ItemTouchHelper.SimpleCallback touchHelperCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.1f;
            }

            @Override
            public float getSwipeEscapeVelocity(float defaultValue) {
                return defaultValue * 0.1f;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                FileEntry entry = entries.get(position);

                if (direction == ItemTouchHelper.RIGHT) {
                    entries.remove(position);
                    adapter.notifyItemRemoved(position);
                    saveFileData();

                    Snackbar.make(recyclerView, "Entry deleted", Snackbar.LENGTH_LONG)
                            .setAction("UNDO", v -> {
                                entries.add(position, entry);
                                adapter.notifyItemInserted(position);
                                saveFileData();
                            })
                            .show();
                } else if (direction == ItemTouchHelper.LEFT) {
                    showEditDialog(entry, position);
                    adapter.notifyItemChanged(position);
                }
            }
        };
        new ItemTouchHelper(touchHelperCallback).attachToRecyclerView(recyclerView);

        refreshFileList();
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, fileList) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                TypedValue tv = new TypedValue();
                if (getTheme().resolveAttribute(R.attr.colorLabel, tv, true)) {
                    v.setTextColor(tv.data);
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                TypedValue tv = new TypedValue();
                if (getTheme().resolveAttribute(R.attr.colorLabel, tv, true)) {
                    v.setTextColor(tv.data);
                }
                TypedValue tvBg = new TypedValue();
                if (getTheme().resolveAttribute(R.attr.colorKey, tvBg, true)) {
                    v.setBackgroundColor(tvBg.data);
                }
                return v;
            }
        };
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fileSpinner.setAdapter(spinnerAdapter);

        fileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFileName = fileList.get(position);
                navigationStack.clear();
                loadFileData(currentFileName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnAddItem.setOnClickListener(v -> showEditDialog(null, -1));
        btnSave.setOnClickListener(v -> saveFileData());
        if (btnBulkOps != null) {
            btnBulkOps.setOnClickListener(v -> showBulkOpsDialog());
        }
        if (btnImport != null) {
            btnImport.setOnClickListener(v -> startImportPicker());
        }
    }

    private void startImportPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, 42);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 42 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                handleImportMerge(uri);
            }
        }
    }

    private void handleImportMerge(Uri uri) {
        File tempFile = new File(getCacheDir(), "temp_import");
        try (java.io.InputStream is = getContentResolver().openInputStream(uri);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (currentFileName.equals("termux-commands.json")) {
                mergeTermuxCommands(tempFile);
            } else if (currentFileName.startsWith("custom_")) {
                SuggestionProvider sp = new SuggestionProvider(this);
                sp.setScript(currentFileName.contains("_ur") ? "ur" : "en");
                sp.mergeCustomDictionary(tempFile);
            } else if (currentFileName.startsWith("suggestion_filters_")) {
                SuggestionProvider sp = new SuggestionProvider(this);
                sp.setScript(currentFileName.contains("_ur") ? "ur" : "en");
                sp.mergeFilters(tempFile);
            } else if (currentFileName.startsWith("next_word_prob_")) {
                NextWordProbability nwp = new NextWordProbability(this);
                nwp.setScript(currentFileName.contains("_ur") ? "ur" : "en");
                nwp.mergeNextWordProbabilities(tempFile);
            } else if (currentFileName.equals("clipboard_history.json")) {
                ClipboardHistoryService.get_service(this).mergeWithFile(tempFile);
            } else {
                performGenericMerge(tempFile);
            }
            Toast.makeText(this, "Imported and merged successfully", Toast.LENGTH_SHORT).show();

            // Refresh UI after merge: Clear stack and reload root
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                navigationStack.clear();
                loadFileData(currentFileName);
            }, 500); // 500ms delay for background persistence tasks
        } catch (Exception e) {
            Toast.makeText(this, "Merge failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            tempFile.delete();
        }
    }

    private void performGenericMerge(File tempFile) throws Exception {
        if (currentFileName.endsWith(".json")) {
            Object currentRoot = reconstructJson(0);
            String incomingStr = juloo.keyboard2.Utils.read_all_utf8(new java.io.FileInputStream(tempFile));
            Object incomingRoot = incomingStr.trim().startsWith("[") ? new JSONArray(incomingStr) : new JSONObject(incomingStr);

            if (currentRoot instanceof JSONArray && incomingRoot instanceof JSONArray) {
                JSONArray currArr = (JSONArray) currentRoot;
                JSONArray incArr = (JSONArray) incomingRoot;
                for (int i = 0; i < incArr.length(); i++) {
                    Object val = incArr.get(i);
                    boolean exists = false;
                    for (int j = 0; j < currArr.length(); j++) {
                        if (currArr.get(j).toString().equals(val.toString())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) currArr.put(val);
                }
                // Save back
                File internal = new File(getFilesDir(), currentFileName);
                try (FileOutputStream fos = new FileOutputStream(internal)) {
                    fos.write(currArr.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } else if (currentFileName.endsWith(".txt")) {
            List<String> currentLines = new ArrayList<>();
            for (FileEntry e : navigationStack.get(0).levelEntries) {
                currentLines.add(e.title + (e.subtitle.isEmpty() ? "" : " " + e.subtitle));
            }
            String incomingStr = juloo.keyboard2.Utils.read_all_utf8(new java.io.FileInputStream(tempFile));
            String[] incLines = incomingStr.split("\n");
            boolean changed = false;
            for (String line : incLines) {
                line = line.trim();
                if (!line.isEmpty() && !currentLines.contains(line)) {
                    currentLines.add(line);
                    changed = true;
                }
            }
            if (changed) {
                File internal = new File(getFilesDir(), currentFileName);
                try (java.io.FileWriter writer = new java.io.FileWriter(internal)) {
                    for (String l : currentLines) {
                        writer.write(l);
                        writer.write("\n");
                    }
                }
            }
        }
    }

    private void mergeTermuxCommands(File tempFile) {
        try {
            String existingJsonStr = "";
            File backupDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup");
            File existingFile = new File(backupDir, "termux-commands.json");
            if (!existingFile.exists()) existingFile = new File(getFilesDir(), "termux-commands.json");

            if (existingFile.exists()) {
                existingJsonStr = juloo.keyboard2.Utils.read_all_utf8(new java.io.FileInputStream(existingFile));
            }

            String incomingJsonStr = juloo.keyboard2.Utils.read_all_utf8(new java.io.FileInputStream(tempFile));

            JSONObject root;
            if (existingJsonStr.trim().isEmpty()) {
                root = new JSONObject();
                root.put("commands", new JSONArray());
            } else {
                root = new JSONObject(existingJsonStr);
            }

            JSONArray existingArray;
            if (root.has("commands")) existingArray = root.getJSONArray("commands");
            else existingArray = new JSONArray();

            JSONObject incomingRoot = new JSONObject(incomingJsonStr);
            JSONArray incomingArray;
            if (incomingRoot.has("commands")) incomingArray = incomingRoot.getJSONArray("commands");
            else incomingArray = new JSONArray();

            for (int i = 0; i < incomingArray.length(); i++) {
                JSONObject incomingCmd = incomingArray.getJSONObject(i);
                boolean exists = false;
                for (int j = 0; j < existingArray.length(); j++) {
                    JSONObject existingCmd = existingArray.getJSONObject(j);
                    if (incomingCmd.getString("command").trim().equals(existingCmd.getString("command").trim()) &&
                        incomingCmd.getString("name").trim().equals(existingCmd.getString("name").trim())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    existingArray.put(incomingCmd);
                }
            }

            root.put("commands", existingArray);
            String output = root.toString(2);

            // Save to all locations
            try (FileOutputStream fos = new FileOutputStream(existingFile)) {
                fos.write(output.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            File internal = new File(getFilesDir(), "termux-commands.json");
            try (FileOutputStream fos = new FileOutputStream(internal)) {
                fos.write(output.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                File dpFile = new File(createDeviceProtectedStorageContext().getFilesDir(), "termux-commands.json");
                try (FileOutputStream fos = new FileOutputStream(dpFile)) {
                    fos.write(output.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Merge failed: " + e.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        if (navigationStack.size() > 1) {
            try {
                // Update parent's originalVal before popping
                Object currentJson = reconstructJson(navigationStack.size() - 1);
                EditorLevel currentLevel = navigationStack.pop();
                EditorLevel parentLevel = navigationStack.peek();
                // Find which entry in parent level pointed to this JSON
                for (FileEntry e : parentLevel.levelEntries) {
                    if (e.originalVal instanceof JSONObject || e.originalVal instanceof JSONArray) {
                        if (e.originalVal == currentLevel.parentJson) {
                            e.originalVal = currentJson;
                            String subtitle = currentJson.toString();
                            if (currentJson instanceof JSONObject) {
                                subtitle = "Object with " + ((JSONObject) currentJson).length() + " items";
                            } else if (currentJson instanceof JSONArray) {
                                subtitle = "Array with " + ((JSONArray) currentJson).length() + " items";
                            }
                            e.subtitle = subtitle;
                            break;
                        }
                    }
                }
                entries = parentLevel.levelEntries;
            } catch (Exception e) {
                navigationStack.pop();
                EditorLevel current = navigationStack.peek();
                entries = current.levelEntries;
            }
            updatePathUI();
            adapter.notifyDataSetChanged();
        } else {
            super.onBackPressed();
        }
    }

    private void updatePathUI() {
        if (pathText != null) {
            StringBuilder sb = new StringBuilder();
            for (EditorLevel level : navigationStack) {
                if (sb.length() > 0) sb.append(" > ");
                sb.append(level.path);
            }
            pathText.setText(sb.toString());
        }
    }

    private void refreshFileList() {
        fileList.clear();
        String[] internalFiles = getFilesDir().list();
        if (internalFiles != null) {
            for (String f : internalFiles) {
                if (f.endsWith(".txt") || f.endsWith(".json") || f.endsWith(".xml")) {
                    fileList.add(f);
                }
            }
        }
        File backupDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup");
        if (backupDir.exists()) {
            String[] externalFiles = backupDir.list();
            if (externalFiles != null) {
                for (String f : externalFiles) {
                    if (f.endsWith(".xml") || f.endsWith(".txt") || f.endsWith(".json")) {
                        if (!fileList.contains(f)) fileList.add(f);
                    }
                }
            }
        }
        if (!fileList.contains("termux-commands.json")) {
            fileList.add("termux-commands.json");
        }
        if (fileList.isEmpty()) {
            fileList.add("custom_en.txt");
            fileList.add("custom_ur.txt");
        }
        java.util.Collections.sort(fileList);
    }

    private void loadFileData(String fileName) {
        entries = new ArrayList<>();
        File file;
        File backupDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup");
        File externalFile = new File(backupDir, fileName);
        File internalFile = new File(getFilesDir(), fileName);

        if (fileName.equals("termux-commands.json")) {
            file = externalFile.exists() ? externalFile : internalFile;
        } else {
            file = internalFile.exists() ? internalFile : externalFile;
        }

        if (!file.exists()) {
            navigationStack.push(new EditorLevel(fileName, entries, null));
            updatePathUI();
            adapter.notifyDataSetChanged();
            return;
        }

        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            if (fileName.endsWith(".json")) {
                while ((line = reader.readLine()) != null) sb.append(line);
                String jsonStr = sb.toString().trim();
                if (jsonStr.startsWith("\ufeff")) {
                    jsonStr = jsonStr.substring(1).trim();
                }
                Object json;
                if (jsonStr.startsWith("[")) json = new JSONArray(jsonStr);
                else json = new JSONObject(jsonStr);
                parseJsonLevel(json, fileName);
            } else {
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    if (fileName.startsWith("custom_") || fileName.startsWith("typed_")) {
                        entries.add(new FileEntry(line.trim(), ""));
                    } else if (fileName.startsWith("next_word_prob_")) {
                        String[] parts = line.split(" ", 2);
                        if (parts.length == 2) {
                            entries.add(new FileEntry(parts[0], parts[1]));
                        } else {
                            entries.add(new FileEntry(line.trim(), ""));
                        }
                    }
                }
                navigationStack.push(new EditorLevel(fileName, entries, null));
            }
        } catch (IOException | JSONException e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            navigationStack.push(new EditorLevel(fileName, entries, null));
        }
        updatePathUI();
        adapter.notifyDataSetChanged();
    }

    private void parseJsonLevel(Object json, String path) throws JSONException {
        List<FileEntry> levelEntries = new ArrayList<>();
        if (json instanceof JSONArray) {
            JSONArray arr = (JSONArray) json;
            for (int i = 0; i < arr.length(); i++) {
                Object val = arr.get(i);
                String subtitle = val.toString();
                String title = String.valueOf(i);
                if (val instanceof JSONObject) {
                    JSONObject obj = (JSONObject) val;
                    title = obj.optString("text", "Item " + i);
                    subtitle = "Object with " + obj.length() + " items";
                } else if (val instanceof JSONArray) {
                    subtitle = "Array with " + ((JSONArray) val).length() + " items";
                }
                levelEntries.add(new FileEntry(title, subtitle, val));
            }
        } else if (json instanceof JSONObject) {
            JSONObject obj = (JSONObject) json;
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object val = obj.get(key);
                String subtitle = val.toString();
                if (val instanceof JSONObject) {
                    subtitle = "Object with " + ((JSONObject) val).length() + " items";
                } else if (val instanceof JSONArray) {
                    subtitle = "Array with " + ((JSONArray) val).length() + " items";
                }
                levelEntries.add(new FileEntry(key, subtitle, val));
            }
        }
        entries = levelEntries;
        navigationStack.push(new EditorLevel(path, entries, json));
    }

    private void saveFileData() {
        // We need to reconstruct the full JSON from the root of navigationStack
        if (navigationStack.isEmpty()) return;

        File file = new File(getFilesDir(), currentFileName);
        // If it was loaded from backup, save it back there too or prefer internal
        File backupDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup");
        File backupFile = new File(backupDir, currentFileName);
        try {
            String content;
            if (currentFileName.endsWith(".json")) {
                Object rootJson = navigationStack.get(0).parentJson;
                // Since our current implementation of "drill-down" edits entries in-place in their parent level's list,
                // but we don't actually update the original JSON objects until save.
                // Let's implement a recursive reconstructor.
                Object root = reconstructJson(0);
                if (root instanceof JSONObject) content = ((JSONObject) root).toString(2);
                else if (root instanceof JSONArray) content = ((JSONArray) root).toString(2);
                else content = root.toString();
            } else {
                StringBuilder sb = new StringBuilder();
                for (FileEntry e : navigationStack.get(0).levelEntries) {
                    sb.append(e.title).append(e.subtitle.isEmpty() ? "" : " " + e.subtitle).append("\n");
                }
                content = sb.toString();
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (currentFileName.equals("termux-commands.json") && android.os.Build.VERSION.SDK_INT >= 24) {
                File dpDir = createDeviceProtectedStorageContext().getFilesDir();
                File dpFile = new File(dpDir, "termux-commands.json");
                try (FileOutputStream fos = new FileOutputStream(dpFile)) {
                    fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            if (backupDir.exists()) {
                try (FileOutputStream fos = new FileOutputStream(backupFile)) {
                    fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            Toast.makeText(this, "Saved successfully", Toast.LENGTH_SHORT).show();
            triggerReload(currentFileName);
        } catch (IOException | JSONException e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Object reconstructJson(int levelIdx) throws JSONException {
        if (levelIdx >= navigationStack.size()) return null;
        EditorLevel level = navigationStack.get(levelIdx);

        if (level.parentJson instanceof JSONArray) {
            JSONArray arr = new JSONArray();
            for (FileEntry e : level.levelEntries) {
                if (e.originalVal instanceof JSONObject || e.originalVal instanceof JSONArray) {
                    // If this complex object is further down the stack, reconstruct it from there
                    int nestedIdx = findNestedLevel(levelIdx, e);
                    if (nestedIdx != -1) {
                        arr.put(reconstructJson(nestedIdx));
                    } else {
                        // Otherwise use the possibly updated originalVal
                        arr.put(e.originalVal);
                    }
                } else {
                    // Primitive value
                    arr.put(parseValue(e.subtitle));
                }
            }
            return arr;
        } else {
            JSONObject obj = new JSONObject();
            for (FileEntry e : level.levelEntries) {
                if (e.originalVal instanceof JSONObject || e.originalVal instanceof JSONArray) {
                    int nestedIdx = findNestedLevel(levelIdx, e);
                    if (nestedIdx != -1) {
                        obj.put(e.title, reconstructJson(nestedIdx));
                    } else {
                        obj.put(e.title, e.originalVal);
                    }
                } else {
                    obj.put(e.title, parseValue(e.subtitle));
                }
            }
            return obj;
        }
    }

    private int findNestedLevel(int currentIdx, FileEntry entry) {
        for (int i = currentIdx + 1; i < navigationStack.size(); i++) {
            if (navigationStack.get(i).parentJson == entry.originalVal) return i;
        }
        return -1;
    }

    private Object parseValue(String s) {
        try {
            if (s.startsWith("[") && s.endsWith("]")) return new JSONArray(s);
            if (s.startsWith("{") && s.endsWith("}")) return new JSONObject(s);
            if (s.equalsIgnoreCase("true")) return true;
            if (s.equalsIgnoreCase("false")) return false;
            try { return Integer.parseInt(s); } catch (NumberFormatException e) {}
            try { return Long.parseLong(s); } catch (NumberFormatException e) {}
            try { return Double.parseDouble(s); } catch (NumberFormatException e) {}
        } catch (JSONException e) {}
        return s;
    }

    private void triggerReload(String fileName) {
        String action = null;
        if (fileName.startsWith("custom_")) action = CustomDictionarySettingsActivity.RELOAD_CUSTOM_DICTIONARY_ACTION;
        else if (fileName.startsWith("suggestion_filters_")) action = SuggestionProvider.RELOAD_FILTERS_ACTION;
        else if (fileName.equals("clipboard_history.json")) action = ClipboardHistoryService.RELOAD_CLIPBOARD_HISTORY_ACTION;
        else if (fileName.startsWith("next_word_prob_")) action = NextWordProbability.RELOAD_NEXT_WORD_ACTION;
        else if (fileName.startsWith("typed_")) action = SuggestionProvider.RELOAD_FILTERS_ACTION;

        if (action != null) {
            Intent intent = new Intent(action);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        }
    }

    private void showBulkOpsDialog() {
        Config config = Config.globalConfig();
        Context themeContext = (config != null) ? new android.view.ContextThemeWrapper(this, config.theme) : this;
        View dialogView = LayoutInflater.from(themeContext).inflate(R.layout.dialog_find_replace, null);
        EditText findInput = dialogView.findViewById(R.id.find_input);
        EditText replaceInput = dialogView.findViewById(R.id.replace_input);
        CheckBox caseSensitive = dialogView.findViewById(R.id.check_case_sensitive);
        CheckBox useRegex = dialogView.findViewById(R.id.check_regex);

        new AlertDialog.Builder(themeContext)
            .setTitle("Find & Replace (Current Level)")
            .setView(dialogView)
            .setPositiveButton("Replace All", (dialog, which) -> {
                String findText = findInput.getText().toString();
                String replaceText = replaceInput.getText().toString();
                if (findText.isEmpty()) return;

                int count = 0;
                for (FileEntry entry : entries) {
                    String oldTitle = entry.title;
                    String oldSubtitle = entry.subtitle;

                    if (useRegex.isChecked()) {
                        try {
                            entry.title = entry.title.replaceAll("(?m" + (caseSensitive.isChecked() ? "" : "i") + ")" + findText, replaceText);
                            entry.subtitle = entry.subtitle.replaceAll("(?m" + (caseSensitive.isChecked() ? "" : "i") + ")" + findText, replaceText);
                        } catch (Exception e) {
                            Toast.makeText(this, "Regex error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        if (caseSensitive.isChecked()) {
                            entry.title = entry.title.replace(findText, replaceText);
                            entry.subtitle = entry.subtitle.replace(findText, replaceText);
                        } else {
                            entry.title = entry.title.replaceAll("(?i)" + java.util.regex.Pattern.quote(findText), replaceText);
                            entry.subtitle = entry.subtitle.replaceAll("(?i)" + java.util.regex.Pattern.quote(findText), replaceText);
                        }
                    }

                    if (!oldTitle.equals(entry.title) || !oldSubtitle.equals(entry.subtitle)) {
                        count++;
                    }
                }
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "Replaced items: " + count, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showEditDialog(FileEntry entry, int position) {
        Config config = Config.globalConfig();
        Context themeContext = (config != null) ? new android.view.ContextThemeWrapper(this, config.theme) : this;
        View dialogView = LayoutInflater.from(themeContext).inflate(R.layout.dialog_file_entry_edit, null);
        EditText titleInput = dialogView.findViewById(R.id.edit_title);
        EditText subtitleInput = dialogView.findViewById(R.id.edit_subtitle);

        if (entry != null) {
            titleInput.setText(entry.title);
            subtitleInput.setText(entry.subtitle);
        }

        if (currentFileName.startsWith("custom_") || currentFileName.startsWith("typed_")) {
            subtitleInput.setVisibility(View.GONE);
        }

        new AlertDialog.Builder(themeContext)
            .setTitle(entry == null ? "Add Entry" : "Edit Entry")
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                String t = titleInput.getText().toString().trim();
                String s = subtitleInput.getText().toString().trim();
                if (t.isEmpty()) return;

                if (entry == null) {
                    entries.add(new FileEntry(t, s));
                } else {
                    entry.title = t;
                    entry.subtitle = s;
                }
                adapter.notifyDataSetChanged();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private static class FileEntry {
        String title;
        String subtitle;
        Object originalVal;

        FileEntry(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        FileEntry(String title, String subtitle, Object obj) {
            this.title = title;
            this.subtitle = subtitle;
            this.originalVal = obj;
        }
    }

    private class FileEntryAdapter extends RecyclerView.Adapter<FileEntryAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_editor, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileEntry entry = entries.get(position);
            holder.title.setText(entry.title);
            holder.subtitle.setText(entry.subtitle);

            boolean isDrillable = entry.originalVal instanceof JSONObject || entry.originalVal instanceof JSONArray;
            holder.itemView.setOnClickListener(isDrillable ? v -> {
                try {
                    parseJsonLevel(entry.originalVal, entry.title);
                    updatePathUI();
                    notifyDataSetChanged();
                } catch (JSONException e) {
                    Toast.makeText(FileEditorActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } : null);

            holder.btnEdit.setOnClickListener(v -> showEditDialog(entry, holder.getBindingAdapterPosition()));
            holder.btnDelete.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    entries.remove(pos);
                    notifyItemRemoved(pos);
                    notifyItemRangeChanged(pos, entries.size());
                }
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            ImageButton btnEdit, btnDelete;

            ViewHolder(View v) {
                super(v);
                title = v.findViewById(R.id.item_title);
                subtitle = v.findViewById(R.id.item_subtitle);
                btnEdit = v.findViewById(R.id.btn_edit);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
