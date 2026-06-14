package juloo.keyboard2;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class DataSyncService {

    private static final String TAG = "DataSyncService";
    private static final String EXTERNAL_DIR_NAME = "ziaistan_keyboard_backup";
    private static final String CUSTOM_DICT_FILENAME = "custom.txt";
    private static final String CLIPBOARD_EXPORT_FILENAME = "clipboard_export.json";
    private static final String CLIPBOARD_INTERNAL_FILENAME = "clipboard_history.json";

    private final Context context;

    public DataSyncService(Context context) {
        this.context = context;
    }

    public boolean importData() {
        boolean dictSuccess = importDictionary();
        boolean clipSuccess = importClipboard();
        return dictSuccess || clipSuccess;
    }

    public boolean exportData() {
        boolean dictSuccess = exportDictionary();
        boolean clipSuccess = exportClipboard();
        return dictSuccess && clipSuccess;
    }

    public boolean importDictionary() {
        boolean ur = importFile("custom_ur.txt", "custom_ur.txt");
        boolean en = importFile("custom_en.txt", "custom_en.txt");
        return ur || en;
    }

    public boolean importClipboard() {
        return importFile(CLIPBOARD_EXPORT_FILENAME, CLIPBOARD_INTERNAL_FILENAME);
    }

    public boolean exportDictionary() {
        boolean ur = exportFile("custom_ur.txt", "custom_ur.txt");
        boolean en = exportFile("custom_en.txt", "custom_en.txt");
        return ur && en;
    }

    public boolean exportClipboard() {
        return exportFile(CLIPBOARD_EXPORT_FILENAME, CLIPBOARD_INTERNAL_FILENAME);
    }

    private boolean importFile(String sourceFileName, String destFileName) {
        File externalDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXTERNAL_DIR_NAME);
        File sourceFile = new File(externalDir, sourceFileName);
        File destFile = new File(context.getFilesDir(), destFileName);

        Log.d(TAG, "Importing from: " + sourceFile.getAbsolutePath() + " to " + destFile.getAbsolutePath());

        if (!sourceFile.exists()) {
            Log.e(TAG, "Import failed: Source file does not exist: " + sourceFile.getAbsolutePath());
            return false;
        }

        try (FileChannel sourceChannel = new FileInputStream(sourceFile).getChannel();
             FileChannel destChannel = new FileOutputStream(destFile).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
            Log.d(TAG, "Successfully imported " + sourceFileName);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to import " + sourceFileName, e);
            return false;
        }
    }

    private boolean exportFile(String destFileName, String sourceFileName) {
        File internalFile = new File(context.getFilesDir(), sourceFileName);
        if (!internalFile.exists()) {
            Log.e(TAG, "Export failed: Internal file not found for export: " + internalFile.getAbsolutePath());
            return false;
        }

        File externalDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXTERNAL_DIR_NAME);
        if (!externalDir.exists()) {
            if (!externalDir.mkdirs()) {
                Log.e(TAG, "Export failed: Failed to create external directory: " + externalDir.getAbsolutePath());
                return false;
            }
        }

        File destFile = new File(externalDir, destFileName);
        Log.d(TAG, "Exporting from: " + internalFile.getAbsolutePath() + " to " + destFile.getAbsolutePath());

        try (FileChannel sourceChannel = new FileInputStream(internalFile).getChannel();
             FileChannel destChannel = new FileOutputStream(destFile).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
            Log.d(TAG, "Successfully exported " + destFileName);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to export " + destFileName, e);
            return false;
        }
    }
}