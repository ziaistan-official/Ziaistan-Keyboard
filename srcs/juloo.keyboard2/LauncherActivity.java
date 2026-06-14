package juloo.keyboard2;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Animatable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class LauncherActivity extends Activity implements Handler.Callback {
    private static final int MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 1;

    TextView _tryhere_text;
    EditText _tryhere_area;
    List<Animatable> _animations;
    Handler _handler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
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
        setContentView(R.layout.launcher_activity);
        _tryhere_text = findViewById(R.id.launcher_tryhere_text);
        _tryhere_area = findViewById(R.id.launcher_tryhere_area);
        if (Build.VERSION.SDK_INT >= 28) {
            _tryhere_area.addOnUnhandledKeyEventListener(new Tryhere_OnUnhandledKeyEventListener());
        }
        _handler = new Handler(getMainLooper(), this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Permission granted.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Permission denied.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        _animations = new ArrayList<>();
        _animations.add(find_anim(R.id.launcher_anim_swipe));
        _animations.add(find_anim(R.id.launcher_anim_round_trip));
        _animations.add(find_anim(R.id.launcher_anim_circle));
        _handler.removeMessages(0);
        _handler.sendEmptyMessageDelayed(0, 500);
    }

    @Override
    public boolean handleMessage(Message _msg) {
        for (Animatable anim : _animations) {
            anim.start();
        }
        _handler.sendEmptyMessageDelayed(0, 3000);
        return true;
    }

    @Override
    public final boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.launcher_menu, menu);
        return true;
    }

    @Override
    public final boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.btnLaunchSettingsActivity) {
            Intent intent = new Intent(LauncherActivity.this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    public void launch_imesettings(View _btn) {
        startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
    }

    public void launch_imepicker(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.showInputMethodPicker();
    }

    public void launch_readme(View v) {
        startActivity(new Intent(this, ReadmeActivity.class));
    }

    Animatable find_anim(int id) {
        ImageView img = findViewById(id);
        return (Animatable) img.getDrawable();
    }

    @TargetApi(28)
    final class Tryhere_OnUnhandledKeyEventListener implements View.OnUnhandledKeyEventListener {
        public boolean onUnhandledKeyEvent(View v, KeyEvent ev) {
            if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK) return false;
            if (KeyEvent.isModifierKey(ev.getKeyCode())) return false;
            StringBuilder s = new StringBuilder();
            if (ev.isAltPressed()) s.append("Alt+");
            if (ev.isShiftPressed()) s.append("Shift+");
            if (ev.isCtrlPressed()) s.append("Ctrl+");
            if (ev.isMetaPressed()) s.append("Meta+");
            String kc = KeyEvent.keyCodeToString(ev.getKeyCode());
            s.append(kc.replaceFirst("^KEYCODE_", ""));
            _tryhere_text.setText(s.toString());
            return false;
        }
    }
}