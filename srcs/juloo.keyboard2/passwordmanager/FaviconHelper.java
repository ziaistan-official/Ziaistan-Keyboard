package juloo.keyboard2.passwordmanager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaviconHelper {

    private static final String FAVICON_URL = "https://www.google.com/s2/favicons?sz=64&domain_url=";
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final java.util.Map<String, Bitmap> cache = new java.util.HashMap<>();

    public interface FaviconCallback {
        void onFaviconLoaded(Bitmap bitmap);
        void onError(Exception e);
    }

    public static void loadFavicon(String domainUrl, FaviconCallback callback) {
        loadFavicon(null, domainUrl, callback);
    }

    public static void loadFavicon(android.content.Context context, String domainUrl, FaviconCallback callback) {
        if (domainUrl == null || domainUrl.isEmpty()) {
            callback.onError(new Exception("Invalid URL"));
            return;
        }

        // Try as package name first if context is provided
        if (context != null && !domainUrl.contains(".") && !domainUrl.startsWith("http")) {
            try {
                android.content.pm.PackageManager pm = context.getPackageManager();
                android.graphics.drawable.Drawable icon = pm.getApplicationIcon(domainUrl);
                Bitmap bitmap = Utils.drawableToBitmap(icon);
                if (bitmap != null) {
                    mainHandler.post(() -> callback.onFaviconLoaded(bitmap));
                    return;
                }
            } catch (Exception e) {
                // Not a package name, continue with favicon
            }
        }

        String domain = extractDomain(domainUrl);
        if (cache.containsKey(domain)) {
            callback.onFaviconLoaded(cache.get(domain));
            return;
        }

        executor.execute(() -> {
            try {
                URL url = new URL(FAVICON_URL + domain);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap != null) cache.put(domain, bitmap);

                mainHandler.post(() -> callback.onFaviconLoaded(bitmap));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private static String extractDomain(String url) {
        if (url == null) return "";
        String domain = url;
        if (domain.startsWith("http://")) domain = domain.substring(7);
        if (domain.startsWith("https://")) domain = domain.substring(8);
        int slash = domain.indexOf('/');
        if (slash != -1) domain = domain.substring(0, slash);
        return domain;
    }
}
