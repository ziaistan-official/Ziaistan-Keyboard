package juloo.keyboard2;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

public class GoogleDriveService {
    private static final String TAG = "GoogleDriveService";
    private static final String APP_NAME = "Ziaistan Keyboard";
    private static final String BACKUP_FOLDER_NAME = "ziaistan_keyboard_backup";

    public GoogleSignInClient getGoogleSignInClient(Context context) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();
        return GoogleSignIn.getClient(context, gso);
    }

    public Task<GoogleSignInAccount> handleSignInResult(Intent data) {
        return GoogleSignIn.getSignedInAccountFromIntent(data);
    }

    public void uploadBackupFile(Context context, GoogleSignInAccount account, java.io.File localFile, String mimeType, UploadCallback callback) {
        overwriteBackupFile(context, account, localFile, mimeType, callback);
    }

    public void overwriteBackupFile(Context context, GoogleSignInAccount account, java.io.File localFile, String mimeType, UploadCallback callback) {
        new Thread(() -> {
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context, Collections.singleton(DriveScopes.DRIVE_FILE));
                credential.setSelectedAccount(account.getAccount());

                Drive googleDriveService = new Drive.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        new GsonFactory(),
                        credential)
                        .setApplicationName(APP_NAME)
                        .build();

                // 1. Check/Create Folder
                String folderId = getOrCreateBackupFolder(googleDriveService);
                if (folderId == null) {
                    if (callback != null) callback.onFailure(new Exception("Could not create/find backup folder"));
                    return;
                }

                // 2. Check if file exists
                String query = "'" + folderId + "' in parents and name='" + localFile.getName() + "' and trashed=false";
                FileList result = googleDriveService.files().list().setQ(query).setSpaces("drive").execute();

                String fileId = null;
                if (!result.getFiles().isEmpty()) {
                    fileId = result.getFiles().get(0).getId();
                }

                FileContent mediaContent = new FileContent(mimeType, localFile);

                File file;
                if (fileId != null) {
                    // Update existing
                    File fileMetadata = new File();
                    fileMetadata.setName(localFile.getName());
                    file = googleDriveService.files().update(fileId, fileMetadata, mediaContent)
                            .setFields("id")
                            .execute();
                } else {
                    // Create new
                    File fileMetadata = new File();
                    fileMetadata.setName(localFile.getName());
                    fileMetadata.setParents(Collections.singletonList(folderId));
                    file = googleDriveService.files().create(fileMetadata, mediaContent)
                            .setFields("id")
                            .execute();
                }

                Log.d(TAG, "File ID: " + file.getId());
                if (callback != null) callback.onSuccess(file.getId());

            } catch (Exception e) {
                Log.e(TAG, "Upload failed", e);
                if (callback != null) callback.onFailure(e);
            }
        }).start();
    }

    public void listBackups(Context context, GoogleSignInAccount account, ListBackupsCallback callback) {
        new Thread(() -> {
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context, Collections.singleton(DriveScopes.DRIVE_FILE));
                credential.setSelectedAccount(account.getAccount());

                Drive googleDriveService = new Drive.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        new GsonFactory(),
                        credential)
                        .setApplicationName(APP_NAME)
                        .build();

                String folderId = getOrCreateBackupFolder(googleDriveService);

                String query = "'" + folderId + "' in parents and trashed=false";
                FileList result = googleDriveService.files().list()
                        .setQ(query)
                        .setOrderBy("createdTime desc")
                        .setFields("files(id, name, createdTime, size, trashed)")
                        .execute();

                if (callback != null) callback.onSuccess(result.getFiles());

            } catch (Exception e) {
                Log.e(TAG, "List failed", e);
                if (callback != null) callback.onFailure(e);
            }
        }).start();
    }

    public void downloadBackupFile(Context context, GoogleSignInAccount account, String fileId, java.io.File destFile, DownloadCallback callback) {
        new Thread(() -> {
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context, Collections.singleton(DriveScopes.DRIVE_FILE));
                credential.setSelectedAccount(account.getAccount());

                Drive googleDriveService = new Drive.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        new GsonFactory(),
                        credential)
                        .setApplicationName(APP_NAME)
                        .build();

                try (OutputStream outputStream = new FileOutputStream(destFile)) {
                    googleDriveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
                }

                if (callback != null) callback.onSuccess();

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                if (callback != null) callback.onFailure(e);
            }
        }).start();
    }

    private String getOrCreateBackupFolder(Drive driveService) throws java.io.IOException {
        // Check if folder exists
        String query = "mimeType='application/vnd.google-apps.folder' and name='" + BACKUP_FOLDER_NAME + "' and trashed=false";
        FileList result = driveService.files().list().setQ(query).setSpaces("drive").execute();

        if (!result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        // Create folder
        File fileMetadata = new File();
        fileMetadata.setName(BACKUP_FOLDER_NAME);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        File file = driveService.files().create(fileMetadata)
                .setFields("id")
                .execute();
        return file.getId();
    }

    public interface UploadCallback {
        void onSuccess(String fileId);
        void onFailure(Exception e);
    }

    public interface ListBackupsCallback {
        void onSuccess(List<File> files);
        void onFailure(Exception e);
    }

    public interface DownloadCallback {
        void onSuccess();
        void onFailure(Exception e);
    }
}
