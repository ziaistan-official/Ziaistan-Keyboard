package juloo.keyboard2.passwordmanager.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import juloo.keyboard2.R;
import juloo.keyboard2.passwordmanager.AuthManager;

public class PasswordManagerActivity extends AppCompatActivity {

    private boolean isLaunchingExternalActivity = false;

    public void setLaunchingExternalActivity(boolean launching) {
        android.util.Log.d("VaultLifecycle", "Setting launching external activity: " + launching);
        this.isLaunchingExternalActivity = launching;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_manager);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Password Manager");
        }

        if (savedInstanceState == null) {
            checkAuthAndNavigate();
            scheduleFaviconSync();
        }
    }

    private void scheduleFaviconSync() {
        androidx.work.OneTimeWorkRequest syncRequest =
                new androidx.work.OneTimeWorkRequest.Builder(juloo.keyboard2.passwordmanager.FaviconSyncWorker.class)
                        .setConstraints(new androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .build())
                        .build();
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork("favicon_sync", androidx.work.ExistingWorkPolicy.KEEP, syncRequest);
    }

    private void checkAuthAndNavigate() {
        AuthManager authManager = AuthManager.getInstance();
        boolean openNotes = "secure_notes".equals(getIntent().getStringExtra("target"));

        if (!authManager.isMasterPasswordSet(this)) {
            navigateTo(new SetupPasswordFragment());
        } else if (authManager.isAuthenticated()) {
            if (openNotes) {
                replaceFragment(new SecureNotesFragment());
            } else {
                navigateTo(new VaultFragment());
            }
        } else {
            if (juloo.keyboard2.passwordmanager.BiometricHelper.isBiometricEnabled(this)) {
                juloo.keyboard2.passwordmanager.BiometricHelper.unlockWithBiometric(this, new juloo.keyboard2.passwordmanager.BiometricHelper.BiometricAuthCallback() {
                    @Override
                    public void onSuccess(String masterPassword) {
                        if (authManager.login(PasswordManagerActivity.this, masterPassword)) {
                            promotePendingNotes();
                            if (openNotes) {
                                replaceFragment(new SecureNotesFragment());
                            } else {
                                navigateTo(new VaultFragment());
                            }
                        } else {
                            navigateTo(new LoginFragment());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        navigateTo(new LoginFragment());
                    }
                });
            } else {
                navigateTo(new LoginFragment());
            }
        }
    }

    public void navigateTo(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }


    public void replaceFragment(Fragment fragment) {
        getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    public void promotePendingNotes() {
        AuthManager authManager = AuthManager.getInstance();
        if (!authManager.isAuthenticated()) return;

        juloo.keyboard2.passwordmanager.PasswordDatabase db = juloo.keyboard2.passwordmanager.PasswordDatabase.getDatabase(this);
        juloo.keyboard2.passwordmanager.PasswordDatabase.databaseWriteExecutor.execute(() -> {
            java.util.List<juloo.keyboard2.passwordmanager.PendingNote> pending = db.pendingNoteDao().getAllPendingNotes();
            if (pending.isEmpty()) return;

            javax.crypto.SecretKey sessionKey = authManager.getSessionKey();
            for (juloo.keyboard2.passwordmanager.PendingNote p : pending) {
                try {
                    juloo.keyboard2.passwordmanager.SecureNote note = new juloo.keyboard2.passwordmanager.SecureNote();
                    note.title = p.title;
                    note.encryptedContent = juloo.keyboard2.passwordmanager.SecurityUtils.encrypt(p.content, sessionKey);
                    note.createdAt = p.createdAt;
                    note.modifiedAt = System.currentTimeMillis();

                    db.noteDao().insert(note);
                    db.pendingNoteDao().delete(p);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            runOnUiThread(() -> android.widget.Toast.makeText(this, "Pending notes promoted to secure vault", android.widget.Toast.LENGTH_SHORT).show());
        });
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.password_manager_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_settings) {


            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettingsDialog() {

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(new String[]{"Enable Biometric Unlock"}, (dialog, which) -> {
                if (which == 0) {
                    enableBiometric();
                }
            })
            .show();
    }

    private void enableBiometric() {
        if (!juloo.keyboard2.passwordmanager.AuthManager.getInstance().isAuthenticated()) {
             android.widget.Toast.makeText(this, "Please login first", android.widget.Toast.LENGTH_SHORT).show();
             return;
        }



        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Confirm Master Password");

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Setup Biometric")
            .setView(input)
            .setPositiveButton("Enable", (d, w) -> {
                String pass = input.getText().toString();
                if (juloo.keyboard2.passwordmanager.AuthManager.getInstance().login(this, pass)) {
                    promotePendingNotes();
                    juloo.keyboard2.passwordmanager.BiometricHelper.enableBiometric(this, pass, new juloo.keyboard2.passwordmanager.BiometricHelper.BiometricAuthCallback() {
                        @Override
                        public void onSuccess(String masterPassword) {
                            android.widget.Toast.makeText(PasswordManagerActivity.this, "Biometric Enabled", android.widget.Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void onError(String error) {
                            android.widget.Toast.makeText(PasswordManagerActivity.this, error, android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    android.widget.Toast.makeText(this, "Wrong Password", android.widget.Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.d("VaultLifecycle", "onResume - isLaunchingExternalActivity: " + isLaunchingExternalActivity);
        // We only reset it on resume if we were actually launching one,
        // to handle cases where we return from a picker.
        isLaunchingExternalActivity = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        android.util.Log.d("VaultLifecycle", "onStop - isLaunchingExternalActivity: " + isLaunchingExternalActivity + ", isChangingConfigurations: " + isChangingConfigurations());
        if (!isChangingConfigurations() && !isLaunchingExternalActivity) {
             android.util.Log.d("VaultLifecycle", "Logging out in onStop");
             AuthManager.getInstance().logout();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            AuthManager.getInstance().logout();
        }
    }
}
