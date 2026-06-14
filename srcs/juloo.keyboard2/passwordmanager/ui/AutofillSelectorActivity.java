package juloo.keyboard2.passwordmanager.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import juloo.keyboard2.R;
import juloo.keyboard2.passwordmanager.AuthManager;
import juloo.keyboard2.passwordmanager.BiometricHelper;
import juloo.keyboard2.passwordmanager.PasswordDatabase;
import juloo.keyboard2.passwordmanager.PasswordEntry;
import juloo.keyboard2.passwordmanager.SecurityUtils;

public class AutofillSelectorActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PasswordAdapter adapter;
    private EditText searchInput;
    private List<PasswordEntry> allEntries = new ArrayList<>();

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            AuthManager.getInstance().logout();
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_autofill_selector);

        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        recyclerView = findViewById(R.id.recycler_view);
        searchInput = findViewById(R.id.search_input);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PasswordAdapter();
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filter(s.toString());
            }
        });

        findViewById(R.id.cancel_button).setOnClickListener(v -> finish());


        AuthManager.getInstance().logout();

        checkAuthAndLoad();
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


                    if (authManager.login(AutofillSelectorActivity.this, masterPassword)) {
                        loadPasswords();
                    } else {

                        showPasswordPrompt();
                    }
                }

                @Override
                public void onError(String error) {


                    Toast.makeText(AutofillSelectorActivity.this, "Biometric Auth Failed: " + error, Toast.LENGTH_SHORT).show();
                    showPasswordPrompt();
                }
            });
        } else {
            showPasswordPrompt();
        }
    }

    private void showPasswordPrompt() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Master Password");

        new android.app.AlertDialog.Builder(this)
            .setTitle("Unlock Vault")
            .setView(input)
            .setPositiveButton("Unlock", (d, w) -> {
                String pass = input.getText().toString();
                if (AuthManager.getInstance().login(this, pass)) {
                    loadPasswords();
                } else {
                    Toast.makeText(this, "Wrong Password", Toast.LENGTH_SHORT).show();
                    finish();
                }
            })
            .setNegativeButton("Cancel", (d, w) -> finish())
            .setCancelable(false)
            .show();
    }

    private void loadPasswords() {
        PasswordDatabase.getDatabase(this).passwordDao().getAllPasswords().observe(this, entries -> {
            this.allEntries = entries;
            filter(searchInput.getText().toString());
        });
    }

    private void filter(String query) {
        if (query.isEmpty()) {
            adapter.setEntries(allEntries);
            return;
        }
        String q = query.toLowerCase();
        List<PasswordEntry> filtered = new ArrayList<>();
        for (PasswordEntry entry : allEntries) {
            boolean match = (entry.siteName != null && entry.siteName.toLowerCase().contains(q)) ||
                            (entry.username != null && entry.username.toLowerCase().contains(q)) ||
                            (entry.url != null && entry.url.toLowerCase().contains(q));
            if (match) filtered.add(entry);
        }
        adapter.setEntries(filtered);
    }

    private void sendToKeyboard(String text) {
        if (text == null) return;
        android.content.Intent intent = new android.content.Intent("juloo.keyboard2.AUTOFILL_RESULT");
        intent.putExtra("text", text);
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        finish();
    }

    private void sendAutofill(String username, String password) {
        android.content.Intent intent = new android.content.Intent("juloo.keyboard2.AUTOFILL_RESULT");
        intent.putExtra("username", username);
        intent.putExtra("password", password);
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        finish();
    }

    private String decryptPassword(PasswordEntry entry) {
        try {
            SecretKey key = AuthManager.getInstance().getSessionKey();
            if (key == null) return null;
            return SecurityUtils.decrypt(entry.encryptedPassword, key);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error decrypting password", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private class PasswordAdapter extends RecyclerView.Adapter<PasswordAdapter.ViewHolder> {
        private List<PasswordEntry> entries = new ArrayList<>();

        public void setEntries(List<PasswordEntry> entries) {
            this.entries = entries;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_password_autofill, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PasswordEntry entry = entries.get(position);
            holder.siteName.setText(entry.siteName != null ? entry.siteName : "Unknown Site");
            holder.username.setText(entry.username != null ? entry.username : "No Username");

            if (entry.customIcon != null) {
                try {
                    byte[] decoded = android.util.Base64.decode(entry.customIcon, android.util.Base64.DEFAULT);
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                    holder.icon.setImageBitmap(bitmap);
                } catch (Exception e) {
                    holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            } else if (entry.url != null && (entry.url.startsWith("http") || entry.url.contains("."))) {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
                juloo.keyboard2.passwordmanager.FaviconHelper.loadFavicon(entry.url, new juloo.keyboard2.passwordmanager.FaviconHelper.FaviconCallback() {
                    @Override
                    public void onFaviconLoaded(android.graphics.Bitmap bitmap) {
                        holder.icon.setImageBitmap(bitmap);
                    }
                    @Override
                    public void onError(Exception e) {}
                });
            } else {
                try {
                    if (entry.url != null) {
                        android.content.pm.PackageManager pm = holder.itemView.getContext().getPackageManager();
                        android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(entry.url, 0);
                        holder.icon.setImageDrawable(pm.getApplicationIcon(appInfo));
                    } else {
                        holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
                    }
                } catch (Exception e) {
                    holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            }

            View.OnClickListener userListener = v -> sendToKeyboard(entry.username);
            holder.username.setOnClickListener(userListener);
            holder.fillUser.setOnClickListener(userListener);

            View.OnClickListener passListener = v -> {
                String pass = decryptPassword(entry);
                if (pass != null) {
                    sendToKeyboard(pass);
                }
            };
            holder.passwordMasked.setOnClickListener(passListener);
            holder.fillPass.setOnClickListener(passListener);

            holder.fillBoth.setOnClickListener(v -> {
                String pass = decryptPassword(entry);
                if (pass != null) {
                    sendAutofill(entry.username, pass);
                }
            });

        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView siteName;
            TextView username;
            TextView usernameLabel;
            TextView passwordMasked;
            TextView passwordLabel;
            android.widget.ImageView icon;
            android.widget.ImageButton fillUser;
            android.widget.ImageButton fillPass;
            android.widget.ImageButton fillBoth;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                siteName = itemView.findViewById(R.id.site_name);
                username = itemView.findViewById(R.id.username);
                passwordMasked = itemView.findViewById(R.id.password_masked);
                icon = itemView.findViewById(R.id.app_icon);
                fillUser = itemView.findViewById(R.id.fill_user);
                fillPass = itemView.findViewById(R.id.fill_pass);
                fillBoth = itemView.findViewById(R.id.fill_both);
            }
        }
    }
}
