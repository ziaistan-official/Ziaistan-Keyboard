package juloo.keyboard2;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CustomDictionarySettingsActivity extends PreferenceActivity {

    private static final int IMPORT_REQUEST_CODE = 1;
    private static final int EXPORT_REQUEST_CODE = 2;
    private static final String CUSTOM_DICTIONARY_BASE = "custom";
    public static final String RELOAD_CUSTOM_DICTIONARY_ACTION = "juloo.keyboard2.RELOAD_CUSTOM_DICTIONARY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.custom_dictionary_settings);

        Preference importPref = findPreference("import_custom_dictionary");
        importPref.setOnPreferenceClickListener(p -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, IMPORT_REQUEST_CODE);
            return true;
        });

        Preference exportPref = findPreference("export_custom_dictionary");
        exportPref.setOnPreferenceClickListener(p -> {
            // Simplified export: exports both ur and en
            exportDictionaries();
            return true;
        });

        Preference manualEditPref = findPreference("manual_file_editing");
        manualEditPref.setOnPreferenceClickListener(p -> {
            Intent intent = new Intent(this, FileEditorActivity.class);
            startActivity(intent);
            return true;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }

        if (requestCode == IMPORT_REQUEST_CODE) {
            importDictionary(uri);
        }
    }

    private void importDictionary(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            List<String> words = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String w = line.trim();
                if (!w.isEmpty()) words.add(w);
            }

            if (!words.isEmpty()) {
                // KeyEventHandler has the logic to split by script
                KeyEventHandler handler = new KeyEventHandler(null, null, null, null); // Dummy for updateCustomDictionary
                // Actually SuggestionProvider merge is better but KeyEventHandler is already refactored
                // I will use a simple implementation here to avoid complex dependencies
                updateDictionariesWithWords(words);
                Toast.makeText(this, "Dictionary imported successfully.", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error importing dictionary.", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void updateDictionariesWithWords(List<String> newWords) {
        for (String w : newWords) {
            String script = Utils.isUrdu(w) ? "ur" : "en";
            String fileName = "custom_" + script + ".txt";
            try (FileOutputStream fos = openFileOutput(fileName, MODE_APPEND)) {
                fos.write((w.toLowerCase() + "\n").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        sendBroadcast(new Intent(RELOAD_CUSTOM_DICTIONARY_ACTION));
    }

    private void exportDictionaries() {
        File backupDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup");
        if (!backupDir.exists()) backupDir.mkdirs();

        boolean exported = false;
        String[] scripts = {"en", "ur"};
        for (String s : scripts) {
            File internal = new File(getFilesDir(), "custom_" + s + ".txt");
            if (internal.exists()) {
                File external = new File(backupDir, "custom_" + s + ".txt");
                try {
                    copyFile(internal, external);
                    exported = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (exported) {
            Toast.makeText(this, "Dictionaries exported to Downloads/ziaistan_keyboard_backup", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "No dictionaries to export.", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
    }
}