package juloo.keyboard2;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.InputStream;
import java.util.Scanner;

public class ReadmeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String themeName = prefs.getString("app_theme", "system");
        int themeId = R.style.settingsTheme;
        String bgColor = "#0d1117";
        String textColor = "#c9d1d9";

        switch (themeName) {
            case "ocean":
                themeId = R.style.AppTheme_Ocean;
                bgColor = "#001F3F"; textColor = "#7FDBFF";
                break;
            case "forest":
                themeId = R.style.AppTheme_Forest;
                bgColor = "#002b20"; textColor = "#69f0ae";
                break;
            case "sunset":
                themeId = R.style.AppTheme_Sunset;
                bgColor = "#3E2723"; textColor = "#FFAB91";
                break;
            case "midnight":
                themeId = R.style.AppTheme_Midnight;
                bgColor = "#1A1A2E"; textColor = "#E94560";
                break;
            default:
                themeId = R.style.settingsTheme;
                break;
        }
        setTheme(themeId);
        super.onCreate(savedInstanceState);

        // Use the glass panel layout
        setContentView(R.layout.readme_activity);

        // Replace the TextView with a WebView programmatically inside the glass container
        // or just use the WebView as the main content but styled correctly.
        // The user wanted a "Glass" aesthetic for the content container.

        WebView webView = new WebView(this);
        android.widget.LinearLayout container = findViewById(R.id.glass_container);
        container.removeAllViews();
        container.addView(webView);

        webView.setBackgroundColor(0x00000000); // Transparent to show glass panel background
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        String markdown = loadAsset("README.md");
        String escapedMarkdown = markdown.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$");

        String htmlTemplate = "<html><head>" +
                            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                            "<link rel='stylesheet' href='file:///android_asset/github-markdown.css'>" +
                            "<script src='file:///android_asset/marked.min.js'></script>" +
                            "<style>" +
                            "body { color: " + textColor + " !important; padding: 0px; }" +
                            "</style></head>" +
                            "<body class='markdown-body'>" +
                            "<div id='content'></div>" +
                            "<script>" +
                            "document.getElementById('content').innerHTML = marked.parse(`" + escapedMarkdown + "`);" +
                            "document.querySelectorAll('img').forEach(img => {" +
                            "  let src = img.getAttribute('src');" +
                            "  if (src === 'layouts.gif') {" +
                            "    img.src = 'file:///android_asset/layouts.gif';" +
                            "  }" +
                            "});" +
                            "</script></body></html>";

        webView.loadDataWithBaseURL("file:///android_asset/", htmlTemplate, "text/html", "UTF-8", null);
    }

    private String loadAsset(String filename) {
        try {
            InputStream is = getAssets().open(filename);
            Scanner s = new Scanner(is).useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
