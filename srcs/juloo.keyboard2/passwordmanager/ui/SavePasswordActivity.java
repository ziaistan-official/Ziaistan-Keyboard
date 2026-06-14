package juloo.keyboard2.passwordmanager.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import javax.crypto.SecretKey;

import juloo.keyboard2.R;
import juloo.keyboard2.passwordmanager.AuthManager;
import juloo.keyboard2.passwordmanager.BiometricHelper;
import juloo.keyboard2.passwordmanager.PasswordDatabase;
import juloo.keyboard2.passwordmanager.PasswordEntry;
import juloo.keyboard2.passwordmanager.SecurityUtils;

public class SavePasswordActivity extends AppCompatActivity {

    private EditText siteNameInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private EditText urlInput;
    private ImageView appIconView;
    private String incomingPassword;
    private String incomingPackageName;
    private String customIconBase64;

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            AuthManager.getInstance().logout();
            finish();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save_password);

        siteNameInput = findViewById(R.id.site_name_input);
        usernameInput = findViewById(R.id.username_input);
        passwordInput = findViewById(R.id.password_input);
        urlInput = findViewById(R.id.url_input);
        appIconView = findViewById(R.id.app_icon);
        Button saveButton = findViewById(R.id.save_button);
        Button cancelButton = findViewById(R.id.cancel_button);

        if (getIntent() != null) {
            incomingPassword = getIntent().getStringExtra("password");
            if (incomingPassword != null) {
                passwordInput.setText(incomingPassword);
            }
            incomingPackageName = getIntent().getStringExtra("package_name");
            if (incomingPackageName != null) {
                autoPopulateAppInfo(incomingPackageName);
            }
        }

        saveButton.setOnClickListener(v -> savePassword());
        cancelButton.setOnClickListener(v -> finish());


        AuthManager.getInstance().logout();

        checkAuthAndLoad();
    }

    private void autoPopulateAppInfo(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            CharSequence appLabel = pm.getApplicationLabel(appInfo);
            Drawable icon = pm.getApplicationIcon(appInfo);

            siteNameInput.setText(appLabel);
            urlInput.setText(packageName);
            if (appIconView != null) {
                appIconView.setImageDrawable(icon);
                appIconView.setVisibility(View.VISIBLE);
            }

            android.graphics.Bitmap bitmap = juloo.keyboard2.passwordmanager.Utils.drawableToBitmap(icon);
            if (bitmap != null) {
                android.graphics.Bitmap resized = android.graphics.Bitmap.createScaledBitmap(bitmap, 128, 128, true);
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                resized.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos);
                customIconBase64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.DEFAULT);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            urlInput.setText(packageName);
        }
    }

    private void checkAuthAndLoad() {
        AuthManager authManager = AuthManager.getInstance();
        if (!authManager.isMasterPasswordSet(this)) {
            Toast.makeText(this, "Set up Password Manager first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }


        if (BiometricHelper.isBiometricEnabled(this)) {
            BiometricHelper.unlockWithBiometric(this, new BiometricHelper.BiometricAuthCallback() {
                @Override
                public void onSuccess(String masterPassword) {
                    if (authManager.login(SavePasswordActivity.this, masterPassword)) {

                        Toast.makeText(SavePasswordActivity.this, "Authenticated", Toast.LENGTH_SHORT).show();
                    } else {
                        showPasswordPrompt();
                    }
                }

                @Override
                public void onError(String error) {

                    showPasswordPrompt();
                }
            });
        } else {
            showPasswordPrompt();
        }
    }

    private void showPasswordPrompt() {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Master Password");

        new android.app.AlertDialog.Builder(this)
            .setTitle("Unlock Vault")
            .setView(input)
            .setPositiveButton("Unlock", (d, w) -> {
                String pass = input.getText().toString();
                if (AuthManager.getInstance().login(this, pass)) {

                } else {
                    Toast.makeText(this, "Wrong Password", Toast.LENGTH_SHORT).show();
                    finish();
                }
            })
            .setNegativeButton("Cancel", (d, w) -> finish())
            .setCancelable(false)
            .show();
    }

    private void savePassword() {
        String siteName = siteNameInput.getText().toString();
        String username = usernameInput.getText().toString();
        String password = passwordInput.getText().toString();
        String url = urlInput.getText().toString();

        if (siteName.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Site Name and Password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            SecretKey key = AuthManager.getInstance().getSessionKey();
            if (key == null) {

                Toast.makeText(this, "Session expired, please re-authenticate", Toast.LENGTH_SHORT).show();
                checkAuthAndLoad();
                return;
            }
            String encryptedPass = SecurityUtils.encrypt(password, key);

            PasswordEntry entry = new PasswordEntry();
            entry.siteName = siteName;
            entry.username = username;
            entry.encryptedPassword = encryptedPass;
            entry.url = url;
            entry.customIcon = customIconBase64;
            entry.createdAt = System.currentTimeMillis();
            entry.modifiedAt = System.currentTimeMillis();


            new Thread(() -> {
                if (juloo.keyboard2.passwordmanager.BackupManager.isDuplicate(this, entry, key)) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Duplicate entry already exists", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                PasswordDatabase.getDatabase(this).passwordDao().insert(entry);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Password Saved", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving entry", Toast.LENGTH_SHORT).show();
        }
    }
}
