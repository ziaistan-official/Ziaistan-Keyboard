package juloo.keyboard2;

import android.content.Context;
import android.util.Log;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DriveSyncHelper {
    private static final String TAG = "DriveSyncHelper";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void syncFileToDrive(Context context, File localFile, String mimeType) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) return;

        executor.execute(() -> {
            try {
                if (localFile.exists()) {
                    new GoogleDriveService().overwriteBackupFile(context, account, localFile, mimeType, new GoogleDriveService.UploadCallback() {
                        @Override
                        public void onSuccess(String fileId) {
                            Log.d(TAG, "Real-time sync success: " + localFile.getName());
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "Real-time sync failed: " + localFile.getName(), e);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in syncFileToDrive", e);
            }
        });
    }
}
