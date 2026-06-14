package juloo.keyboard2.passwordmanager;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FaviconSyncWorker extends Worker {
    private static final String TAG = "FaviconSyncWorker";

    public FaviconSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting favicon sync");
        PasswordDatabase db = PasswordDatabase.getDatabase(getApplicationContext());
        List<PasswordEntry> entries = db.passwordDao().getAllPasswordsSync();

        List<PasswordEntry> toUpdate = new ArrayList<>();
        for (PasswordEntry entry : entries) {
            if (entry.customIcon == null && entry.url != null) {
                toUpdate.add(entry);
            }
        }

        if (toUpdate.isEmpty()) return Result.success();

        final CountDownLatch latch = new CountDownLatch(toUpdate.size());
        for (PasswordEntry entry : toUpdate) {
            FaviconHelper.loadFavicon(getApplicationContext(), entry.url, new FaviconHelper.FaviconCallback() {
                @Override
                public void onFaviconLoaded(Bitmap bitmap) {
                    if (bitmap != null) {
                        PasswordDatabase.databaseWriteExecutor.execute(() -> {
                            try {
                                Bitmap resized = Bitmap.createScaledBitmap(bitmap, 128, 128, true);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                resized.compress(Bitmap.CompressFormat.PNG, 100, baos);
                                entry.customIcon = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                                db.passwordDao().update(entry);
                                Log.d(TAG, "Updated icon for: " + entry.siteName);
                            } catch (Exception e) {
                                Log.e(TAG, "Error saving favicon for " + entry.siteName, e);
                            } finally {
                                latch.countDown();
                            }
                        });
                    } else {
                        latch.countDown();
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Failed to load favicon for " + entry.siteName, e);
                    latch.countDown();
                }
            });
        }

        try {
            // Wait for all fetches to complete, max 5 minutes total to avoid worker timeout
            latch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for favicons", e);
        }

        return Result.success();
    }
}
