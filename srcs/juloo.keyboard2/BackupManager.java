package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import android.preference.PreferenceManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BackupManager {
    private static final String TAG = "BackupManager";
    private Context context;
    private SharedPreferences prefs;

    public BackupManager(Context context) {
        this.context = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }


    public BackupData createBackup() {
        BackupData backup = new BackupData();
        try {
            String versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            backup.setAppVersion(versionName);
        } catch (Exception e) {
            Log.e(TAG, "Could not get app version", e);
        }


        Map<String, ?> allPrefs = prefs.getAll();
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            backup.putSetting(entry.getKey(), entry.getValue());
        }


        backupTrackpadSettings(backup);
        backupMagnifierSettings(backup);
        backupThemeSettings(backup);
        backupLayoutSettings(backup);
        backupTermuxCommands(backup);

        Log.d(TAG, "Backup created with " + backup.getSettings().size() + " settings");
        return backup;
    }


    public void restoreBackup(BackupData backup) {
        SharedPreferences.Editor editor = prefs.edit();


        for (Map.Entry<String, Object> entry : backup.getSettings().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();


            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof Double) {
                editor.putFloat(key, ((Double) value).floatValue());
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            }
        }
        editor.apply();


        restoreTrackpadSettings(backup);
        restoreMagnifierSettings(backup);
        restoreThemeSettings(backup);
        restoreLayoutSettings(backup);
        restoreTermuxCommands(backup);

        Log.d(TAG, "Backup restored successfully");
        Toast.makeText(context, "Keyboard settings restored", Toast.LENGTH_SHORT).show();
    }


    public boolean saveBackupToFile(BackupData backup, Uri uri) {
        try {
            JSONObject json = backup.toJSON();
            String jsonString = json.toString(2);

            OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
            if (outputStream != null) {
                outputStream.write(jsonString.getBytes());
                outputStream.close();

                Toast.makeText(context, "Backup saved successfully", Toast.LENGTH_SHORT).show();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save backup", e);
            Toast.makeText(context, "Failed to save backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return false;
    }


    public BackupData loadBackupFromFile(Uri uri) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getContentResolver().openInputStream(uri))
            );

            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(jsonBuilder.toString());
            BackupData backup = BackupData.fromJSON(json);

            Log.d(TAG, "Backup loaded from file (version: " + backup.getVersion() + ")");
            return backup;

        } catch (Exception e) {
            Log.e(TAG, "Failed to load backup", e);
            Toast.makeText(context, "Failed to load backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return null;
    }


    public String generateBackupFilename() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        return "keyboard_backup_" + sdf.format(new Date()) + ".json";
    }



    private void backupTrackpadSettings(BackupData backup) {
        Map<String, Object> trackpadState = new HashMap<>();
        trackpadState.put("cursor_size", prefs.getInt("trackpad_cursor_size", 10));
        trackpadState.put("cursor_speed", prefs.getFloat("trackpad_cursor_speed", 1.0f));
        trackpadState.put("sensitivity", prefs.getFloat("trackpad_sensitivity", 1.0f));
        trackpadState.put("enabled", prefs.getBoolean("trackpad_enabled", true));

        backup.putFeatureState("trackpad", trackpadState);
    }

    private void restoreTrackpadSettings(BackupData backup) {
        Map<String, Object> trackpadState = backup.getFeatureStates().get("trackpad");
        if (trackpadState == null) return;

        SharedPreferences.Editor editor = prefs.edit();

        if (trackpadState.containsKey("cursor_size")) {
            editor.putInt("trackpad_cursor_size", (Integer) trackpadState.get("cursor_size"));
        }
        if (trackpadState.containsKey("cursor_speed")) {
            editor.putFloat("trackpad_cursor_speed", ((Number) trackpadState.get("cursor_speed")).floatValue());
        }
        if (trackpadState.containsKey("sensitivity")) {
            editor.putFloat("trackpad_sensitivity", ((Number) trackpadState.get("sensitivity")).floatValue());
        }
        if (trackpadState.containsKey("enabled")) {
            editor.putBoolean("trackpad_enabled", (Boolean) trackpadState.get("enabled"));
        }

        editor.apply();
    }

    private void backupMagnifierSettings(BackupData backup) {
        Map<String, Object> magnifierState = new HashMap<>();
        magnifierState.put("size", prefs.getInt("magnifier_size", 170));
        magnifierState.put("zoom", prefs.getFloat("magnifier_zoom", 2.0f));
        magnifierState.put("enabled", prefs.getBoolean("magnifier_enabled", false));

        backup.putFeatureState("magnifier", magnifierState);
    }

    private void restoreMagnifierSettings(BackupData backup) {
        Map<String, Object> magnifierState = backup.getFeatureStates().get("magnifier");
        if (magnifierState == null) return;

        SharedPreferences.Editor editor = prefs.edit();

        if (magnifierState.containsKey("size")) {
            editor.putInt("magnifier_size", (Integer) magnifierState.get("size"));
        }
        if (magnifierState.containsKey("zoom")) {
            editor.putFloat("magnifier_zoom", ((Number) magnifierState.get("zoom")).floatValue());
        }
        if (magnifierState.containsKey("enabled")) {
            editor.putBoolean("magnifier_enabled", (Boolean) magnifierState.get("enabled"));
        }

        editor.apply();
    }

    private void backupThemeSettings(BackupData backup) {
        Map<String, Object> themeState = new HashMap<>();
        themeState.put("selected_theme", prefs.getString("selected_theme", "default"));
        themeState.put("custom_colors", prefs.getString("custom_colors", ""));

        backup.putFeatureState("theme", themeState);
    }

    private void restoreThemeSettings(BackupData backup) {
        Map<String, Object> themeState = backup.getFeatureStates().get("theme");
        if (themeState == null) return;

        SharedPreferences.Editor editor = prefs.edit();

        if (themeState.containsKey("selected_theme")) {
            editor.putString("selected_theme", (String) themeState.get("selected_theme"));
        }
        if (themeState.containsKey("custom_colors")) {
            editor.putString("custom_colors", (String) themeState.get("custom_colors"));
        }

        editor.apply();
    }

    private void backupLayoutSettings(BackupData backup) {
        Map<String, Object> layoutState = new HashMap<>();
        layoutState.put("current_layout", prefs.getString("current_layout", "latn_qwerty_us"));
        layoutState.put("custom_layouts", prefs.getString("custom_layouts", ""));

        backup.putFeatureState("layout", layoutState);
    }

    private void backupTermuxCommands(BackupData backup) {
        File file = new File(context.getFilesDir(), "termux_commands.json");
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> state = new HashMap<>();
            state.put("json_data", sb.toString());
            backup.putFeatureState("termux_commands", state);
        } catch (IOException e) {
            Log.e(TAG, "Failed to backup termux commands", e);
        }
    }

    private void restoreTermuxCommands(BackupData backup) {
        Map<String, Object> state = backup.getFeatureStates().get("termux_commands");
        if (state == null || !state.containsKey("json_data")) return;

        String jsonData = (String) state.get("json_data");
        File file = new File(context.getFilesDir(), "termux_commands.json");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(jsonData.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Failed to restore termux commands", e);
        }
    }

    private void restoreLayoutSettings(BackupData backup) {
        Map<String, Object> layoutState = backup.getFeatureStates().get("layout");
        if (layoutState == null) return;

        SharedPreferences.Editor editor = prefs.edit();

        if (layoutState.containsKey("current_layout")) {
            editor.putString("current_layout", (String) layoutState.get("current_layout"));
        }
        if (layoutState.containsKey("custom_layouts")) {
            editor.putString("custom_layouts", (String) layoutState.get("custom_layouts"));
        }

        editor.apply();
    }
}
