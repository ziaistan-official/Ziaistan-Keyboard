package juloo.keyboard2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class SettingsActivity extends PreferenceActivity
{
  public static final String EXTRA_REQUEST_VOICE_PERMISSION = "request_voice_permission";
  private static final int PERMISSION_REQUEST_CODE = 101;

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    if (getIntent().getBooleanExtra(EXTRA_REQUEST_VOICE_PERMISSION, false)) {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);

        } else {
             android.widget.Toast.makeText(this, "Permission already granted", android.widget.Toast.LENGTH_SHORT).show();
        }


    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    String themeName = prefs.getString("app_theme", "system");
    int themeId = R.style.settingsTheme;
    switch (themeName) {
        case "ocean": themeId = R.style.AppTheme_Ocean; break;
        case "forest": themeId = R.style.AppTheme_Forest; break;
        case "sunset": themeId = R.style.AppTheme_Sunset; break;
        case "midnight": themeId = R.style.AppTheme_Midnight; break;
        default: themeId = R.style.settingsTheme; break;
    }
    setTheme(themeId);

    super.onCreate(savedInstanceState);

    String action = getIntent().getAction();
    if ("juloo.keyboard2.CLEAR_EMOJI_HISTORY".equals(action)) {
        getSharedPreferences("emoji_last_use", MODE_PRIVATE).edit().clear().apply();
        android.widget.Toast.makeText(this, R.string.toast_emoji_cleared, android.widget.Toast.LENGTH_SHORT).show();
        finish();
        return;
    } else if ("juloo.keyboard2.CLEAR_EMOJI_FAVORITES".equals(action)) {
        getSharedPreferences("emoji_favorites", MODE_PRIVATE).edit().clear().apply();
        android.widget.Toast.makeText(this, R.string.toast_favorites_cleared, android.widget.Toast.LENGTH_SHORT).show();
        finish();
        return;
    }

    try
    {
      Config.migrate(getPreferenceManager().getSharedPreferences());
    }
    catch (Exception _e) { fallbackEncrypted(); return; }
    addPreferencesFromResource(R.xml.settings);

    boolean foldableDevice = FoldStateTracker.isFoldableDevice(this);
    findPreference("margin_bottom_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("margin_bottom_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_landscape_unfolded").setEnabled(foldableDevice);

    android.widget.ListView list = getListView();
    if (list != null) {
        int padding = (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        list.setPadding(padding, 0, padding, 0);
        list.setClipToPadding(false);
        list.setScrollBarStyle(android.view.View.SCROLLBARS_OUTSIDE_OVERLAY);
    }
  }

  void fallbackEncrypted()
  {
    finish();
  }

  protected void onStop()
  {
    DirectBootAwarePreferences
      .copy_preferences_to_protected_storage(this,
          getPreferenceManager().getSharedPreferences());
    super.onStop();
  }
}