package juloo.keyboard2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BackupRestoreActivity extends PreferenceActivity {
    private static final String TAG = "BackupRestoreActivity";
    private static final int REQUEST_SIGN_IN = 1001;
    private static final String LOCAL_BACKUP_DIR = "ziaistan_keyboard_backup";

    private BackupManager backupManager;
    private GoogleDriveService driveService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.backup_restore);

        backupManager = new BackupManager(this);
        driveService = new GoogleDriveService();

        initPreferences();
        updateSignInState();
    }

    private void applyTheme() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String themeName = prefs.getString("app_theme", "system");
        int themeId;
        switch (themeName) {
            case "ocean": themeId = R.style.AppTheme_Ocean; break;
            case "forest": themeId = R.style.AppTheme_Forest; break;
            case "sunset": themeId = R.style.AppTheme_Sunset; break;
            case "midnight": themeId = R.style.AppTheme_Midnight; break;
            default: themeId = R.style.settingsTheme; break;
        }
        setTheme(themeId);
    }

    private void initPreferences() {
        findPreference("backup_local").setOnPreferenceClickListener(p -> {
            backupEverythingToLocal();
            return true;
        });

        findPreference("restore_local").setOnPreferenceClickListener(p -> {
            restoreEverythingFromLocal();
            return true;
        });

        findPreference("google_sign_in").setOnPreferenceClickListener(p -> {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null) {
                signOut();
            } else {
                signIn();
            }
            return true;
        });

        findPreference("backup_drive").setOnPreferenceClickListener(p -> {
            backupEverythingToDrive();
            return true;
        });

        findPreference("restore_drive").setOnPreferenceClickListener(p -> {
            restoreEverythingFromDrive();
            return true;
        });
    }

    private void updateSignInState() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        Preference signInPref = findPreference("google_sign_in");
        Preference backupDrivePref = findPreference("backup_drive");
        Preference restoreDrivePref = findPreference("restore_drive");

        if (account != null) {
            signInPref.setTitle("Sign Out");
            signInPref.setSummary("Signed in as " + account.getEmail());
            backupDrivePref.setEnabled(true);
            restoreDrivePref.setEnabled(true);
        } else {
            signInPref.setTitle("Sign In with Google");
            signInPref.setSummary("Sign in to enable Drive backup");
            backupDrivePref.setEnabled(false);
            restoreDrivePref.setEnabled(false);
        }
    }

    private void signIn() {
        Intent signInIntent = driveService.getGoogleSignInClient(this).getSignInIntent();
        startActivityForResult(signInIntent, REQUEST_SIGN_IN);
    }

    private void signOut() {
        driveService.getGoogleSignInClient(this).signOut().addOnCompleteListener(task -> {
            updateSignInState();
            Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                task.getResult(ApiException.class);
                updateSignInState();
                Toast.makeText(this, "Signed in successfully", Toast.LENGTH_SHORT).show();
            } catch (ApiException e) {
                Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
                Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ================= LOCAL BACKUP / RESTORE =================

    private void backupEverythingToLocal() {
        new Thread(() -> {
            try {
                File backupDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), LOCAL_BACKUP_DIR);
                if (!backupDir.exists() && !backupDir.mkdirs()) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to create backup directory", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 1. Settings
                BackupData data = backupManager.createBackup();
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
                String settingsFileName = "keyboard_backup_" + timestamp + ".json";
                File settingsFile = new File(backupDir, settingsFileName);

                String jsonString = data.toJSON().toString(2);
                try (FileOutputStream fos = new FileOutputStream(settingsFile)) {
                    fos.write(jsonString.getBytes());
                }

                // 2. Data Files
                String[] baseFiles = {"custom_en.txt", "custom_ur.txt", "typed_en.txt", "typed_ur.txt",
                                     "suggestion_filters_en.json", "suggestion_filters_ur.json",
                                     "next_word_prob_en.txt", "next_word_prob_ur.txt"};
                for (String f : baseFiles) {
                    copyInternalFileToBackup(backupDir, f, f);
                }
                copyInternalFileToBackup(backupDir, "clipboard_history.json", "clipboard_export.json");

                runOnUiThread(() -> Toast.makeText(this, "Local Backup Completed!", Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                Log.e(TAG, "Local backup failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void restoreEverythingFromLocal() {
        new Thread(() -> {
            try {
                File backupDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), LOCAL_BACKUP_DIR);
                if (!backupDir.exists()) {
                    runOnUiThread(() -> Toast.makeText(this, "Backup folder not found", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 1. Find latest settings file
                File latestSettings = null;
                File[] files = backupDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().startsWith("keyboard_backup_") && f.getName().endsWith(".json")) {
                            if (latestSettings == null || f.lastModified() > latestSettings.lastModified()) {
                                latestSettings = f;
                            }
                        }
                    }
                }

                if (latestSettings != null) {
                    BackupData backup = backupManager.loadBackupFromFile(Uri.fromFile(latestSettings));
                    if (backup != null) {
                        runOnUiThread(() -> backupManager.restoreBackup(backup));
                    }
                }

                // 2. Data Files
                String[] baseFiles = {"custom_en.txt", "custom_ur.txt", "typed_en.txt", "typed_ur.txt",
                                     "suggestion_filters_en.json", "suggestion_filters_ur.json",
                                     "next_word_prob_en.txt", "next_word_prob_ur.txt"};
                for (String f : baseFiles) {
                    copyBackupFileToInternal(backupDir, f, f);
                }
                copyBackupFileToInternal(backupDir, "clipboard_export.json", "clipboard_history.json");

                // 3. Notify Changes
                Intent reloadClipboard = new Intent(ClipboardHistoryService.RELOAD_CLIPBOARD_HISTORY_ACTION);
                sendBroadcast(reloadClipboard);

                Intent reloadDict = new Intent(CustomDictionarySettingsActivity.RELOAD_CUSTOM_DICTIONARY_ACTION);
                sendBroadcast(reloadDict);

                runOnUiThread(() -> Toast.makeText(this, "Local Restore Completed!", Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                Log.e(TAG, "Local restore failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ================= DRIVE BACKUP / RESTORE =================

    private void backupEverythingToDrive() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) return;

        Toast.makeText(this, "Starting Drive Backup...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // 1. Settings (Create temp file)
                BackupData data = backupManager.createBackup();
                String settingsFileName = "keyboard_settings.json";
                File tempSettings = new File(getCacheDir(), settingsFileName);

                String jsonString = data.toJSON().toString(2);
                try (FileOutputStream fos = new FileOutputStream(tempSettings)) {
                    fos.write(jsonString.getBytes());
                }

                // Upload Settings
                uploadFileToDriveSync(account, tempSettings, "application/json");
                tempSettings.delete();

                // 2. Data Files
                String[] txtFiles = {"custom_en.txt", "custom_ur.txt", "typed_en.txt", "typed_ur.txt", "next_word_prob_en.txt", "next_word_prob_ur.txt"};
                for (String f : txtFiles) uploadInternalFileToDrive(account, f, f, "text/plain");

                String[] jsonFiles = {"suggestion_filters_en.json", "suggestion_filters_ur.json"};
                for (String f : jsonFiles) uploadInternalFileToDrive(account, f, f, "application/json");

                uploadInternalFileToDrive(account, "clipboard_history.json", "clipboard_export.json", "application/json");

                runOnUiThread(() -> Toast.makeText(this, "Drive Backup Completed!", Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                Log.e(TAG, "Drive backup failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Drive Backup Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void restoreEverythingFromDrive() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) return;

        Toast.makeText(this, "Starting Drive Restore...", Toast.LENGTH_SHORT).show();

        driveService.listBackups(this, account, new GoogleDriveService.ListBackupsCallback() {
            @Override
            public void onSuccess(List<com.google.api.services.drive.model.File> files) {
                new Thread(() -> {
                    try {
                        // 1. Find latest settings file
                        com.google.api.services.drive.model.File latestSettings = null;
                        for (com.google.api.services.drive.model.File f : files) {
                            if ("keyboard_settings.json".equals(f.getName()) && (f.getTrashed() == null || !f.getTrashed())) {
                                latestSettings = f;
                                break;
                            }
                            // Fallback for legacy timestamped files
                            if (f.getName().startsWith("keyboard_backup_") && f.getName().endsWith(".json") && (f.getTrashed() == null || !f.getTrashed())) {
                                if (latestSettings == null || f.getCreatedTime().getValue() > latestSettings.getCreatedTime().getValue()) {
                                    latestSettings = f;
                                }
                            }
                        }

                        if (latestSettings != null) {
                            File tempSettings = new File(getCacheDir(), "keyboard_settings_restore.json");
                            downloadFileFromDriveSync(account, latestSettings.getId(), tempSettings);

                            BackupData backup = backupManager.loadBackupFromFile(Uri.fromFile(tempSettings));
                            if (backup != null) {
                                runOnUiThread(() -> backupManager.restoreBackup(backup));
                            }
                            tempSettings.delete();
                        }

                        // 2. Data Files
                        String[] baseFiles = {"custom_en.txt", "custom_ur.txt", "typed_en.txt", "typed_ur.txt",
                                             "suggestion_filters_en.json", "suggestion_filters_ur.json",
                                             "next_word_prob_en.txt", "next_word_prob_ur.txt"};
                        for (String f : baseFiles) {
                            downloadDriveFileToInternal(account, files, f, f);
                        }

                        // Merge Clipboard History instead of overwriting
                        File tempClipboard = new File(getCacheDir(), "clipboard_import.json");
                        String clipboardFileId = findFileId(files, "clipboard_export.json");
                        if (clipboardFileId != null) {
                            downloadFileFromDriveSync(account, clipboardFileId, tempClipboard);
                            if (tempClipboard.exists()) {
                                ClipboardHistoryService.get_service(BackupRestoreActivity.this).mergeWithFile(tempClipboard);
                                tempClipboard.delete();
                            }
                        }

                        // 3. Notify Changes
                        Intent reloadClipboard = new Intent(ClipboardHistoryService.RELOAD_CLIPBOARD_HISTORY_ACTION);
                        sendBroadcast(reloadClipboard);

                        Intent reloadDict = new Intent(CustomDictionarySettingsActivity.RELOAD_CUSTOM_DICTIONARY_ACTION);
                        sendBroadcast(reloadDict);

                        runOnUiThread(() -> Toast.makeText(BackupRestoreActivity.this, "Drive Restore Completed!", Toast.LENGTH_SHORT).show());

                    } catch (Exception e) {
                        Log.e(TAG, "Drive restore failed", e);
                        runOnUiThread(() -> Toast.makeText(BackupRestoreActivity.this, "Drive Restore Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> Toast.makeText(BackupRestoreActivity.this, "Failed to list backups: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ================= HELPERS =================

    private void copyInternalFileToBackup(File backupDir, String internalName, String backupName) {
        File internalFile = new File(getFilesDir(), internalName);
        if (internalFile.exists()) {
            File destFile = new File(backupDir, backupName);
            try {
                copyFile(internalFile, destFile);
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy " + internalName, e);
            }
        }
    }

    private void copyBackupFileToInternal(File backupDir, String backupName, String internalName) {
        File backupFile = new File(backupDir, backupName);
        if (backupFile.exists()) {
            File destFile = new File(getFilesDir(), internalName);
            try {
                copyFile(backupFile, destFile);
            } catch (IOException e) {
                Log.e(TAG, "Failed to restore " + backupName, e);
            }
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream inStream = new FileInputStream(src);
             FileOutputStream outStream = new FileOutputStream(dst);
             FileChannel inChannel = inStream.getChannel();
             FileChannel outChannel = outStream.getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }

    private void uploadInternalFileToDrive(GoogleSignInAccount account, String internalName, String driveName, String mimeType) {
        File internalFile = new File(getFilesDir(), internalName);
        if (internalFile.exists()) {
            if (!internalName.equals(driveName)) {
                File temp = new File(getCacheDir(), driveName);
                try {
                    copyFile(internalFile, temp);
                    uploadFileToDriveSync(account, temp, mimeType);
                    temp.delete();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to prep upload " + internalName, e);
                }
            } else {
                uploadFileToDriveSync(account, internalFile, mimeType);
            }
        }
    }

    private void uploadFileToDriveSync(GoogleSignInAccount account, File file, String mimeType) {
        final Object lock = new Object();
        final boolean[] success = {false};

        driveService.overwriteBackupFile(this, account, file, mimeType, new GoogleDriveService.UploadCallback() {
            @Override
            public void onSuccess(String fileId) {
                synchronized (lock) {
                    success[0] = true;
                    lock.notify();
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Upload failed for " + file.getName(), e);
                synchronized (lock) {
                    lock.notify();
                }
            }
        });

        synchronized (lock) {
            try {
                lock.wait(30000); // 30s timeout
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void downloadDriveFileToInternal(GoogleSignInAccount account, List<com.google.api.services.drive.model.File> files, String driveName, String internalName) {
        String fileId = findFileId(files, driveName);
        if (fileId != null) {
            File destFile = new File(getFilesDir(), internalName);
            downloadFileFromDriveSync(account, fileId, destFile);
        }
    }

    private String findFileId(List<com.google.api.services.drive.model.File> files, String name) {
        for (com.google.api.services.drive.model.File f : files) {
            if (name.equals(f.getName()) && (f.getTrashed() == null || !f.getTrashed())) {
                return f.getId();
            }
        }
        return null;
    }

    private void downloadFileFromDriveSync(GoogleSignInAccount account, String fileId, File destFile) {
        final Object lock = new Object();

        driveService.downloadBackupFile(this, account, fileId, destFile, new GoogleDriveService.DownloadCallback() {
            @Override
            public void onSuccess() {
                synchronized (lock) {
                    lock.notify();
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Download failed for " + fileId, e);
                synchronized (lock) {
                    lock.notify();
                }
            }
        });

        synchronized (lock) {
            try {
                lock.wait(30000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
