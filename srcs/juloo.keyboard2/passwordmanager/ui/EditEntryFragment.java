package juloo.keyboard2.passwordmanager.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.concurrent.Executors;

import javax.crypto.SecretKey;

import juloo.keyboard2.R;
import juloo.keyboard2.passwordmanager.AuthManager;
import juloo.keyboard2.passwordmanager.PasswordEntry;
import juloo.keyboard2.passwordmanager.SecurityUtils;

public class EditEntryFragment extends Fragment {

    private EditText siteNameInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private EditText urlInput;
    private ImageView iconPreview;
    private String customIconBase64;
    private VaultViewModel viewModel;
    private int entryId = -1;
    private PasswordEntry currentEntry;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    processSelectedIcon(imageUri);
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_entry, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(VaultViewModel.class);

        siteNameInput = view.findViewById(R.id.site_name_input);
        usernameInput = view.findViewById(R.id.username_input);
        passwordInput = view.findViewById(R.id.password_input);
        urlInput = view.findViewById(R.id.url_input);
        iconPreview = view.findViewById(R.id.icon_preview);
        Button pickIconButton = view.findViewById(R.id.btn_pick_icon);
        Button saveButton = view.findViewById(R.id.save_button);
        Button deleteButton = view.findViewById(R.id.delete_button);

        if (getArguments() != null && getArguments().containsKey("entry_id")) {
            entryId = getArguments().getInt("entry_id");
            loadEntry(entryId);
        } else {
            deleteButton.setVisibility(View.GONE);
        }

        saveButton.setOnClickListener(v -> saveEntry());
        deleteButton.setOnClickListener(v -> deleteEntry());
        pickIconButton.setOnClickListener(v -> {
            if (getActivity() instanceof PasswordManagerActivity) {
                ((PasswordManagerActivity) getActivity()).setLaunchingExternalActivity(true);
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });
    }

    private void processSelectedIcon(Uri uri) {
        try (java.io.InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap != null) {

                Bitmap resized = Bitmap.createScaledBitmap(bitmap, 128, 128, true);
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                resized.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] bytes = baos.toByteArray();
                customIconBase64 = Base64.encodeToString(bytes, Base64.DEFAULT);
                iconPreview.setImageBitmap(resized);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to load icon", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadEntry(int id) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (!isAdded() || getContext() == null) return;
            PasswordEntry entry = juloo.keyboard2.passwordmanager.PasswordDatabase.getDatabase(requireContext()).passwordDao().getPasswordById(id);
            if (entry != null) {
                currentEntry = entry;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        siteNameInput.setText(entry.siteName);
                        usernameInput.setText(entry.username);
                        urlInput.setText(entry.url);
                        if (entry.customIcon != null) {
                            customIconBase64 = entry.customIcon;
                            byte[] decoded = Base64.decode(customIconBase64, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                            iconPreview.setImageBitmap(bitmap);
                        }

                        try {
                            SecretKey key = AuthManager.getInstance().getSessionKey();
                            if (key != null) {
                                String decryptedPass = SecurityUtils.decrypt(entry.encryptedPassword, key);
                                passwordInput.setText(decryptedPass);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        });
    }

    private void saveEntry() {
        String siteName = siteNameInput.getText().toString();
        String username = usernameInput.getText().toString();
        String password = passwordInput.getText().toString();
        String url = urlInput.getText().toString();

        if (siteName.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(), "Site Name and Password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            SecretKey key = AuthManager.getInstance().getSessionKey();
            if (key == null) {
                Toast.makeText(getContext(), "Session expired", Toast.LENGTH_SHORT).show();
                return;
            }
            String encryptedPass = SecurityUtils.encrypt(password, key);

            PasswordEntry entry = (currentEntry != null) ? currentEntry : new PasswordEntry();
            entry.siteName = siteName;
            entry.username = username;
            entry.encryptedPassword = encryptedPass;
            entry.url = url;
            entry.customIcon = customIconBase64;
            entry.modifiedAt = System.currentTimeMillis();
            if (currentEntry == null) entry.createdAt = System.currentTimeMillis();

            if (currentEntry == null) {
                if (juloo.keyboard2.passwordmanager.BackupManager.isDuplicate(requireContext(), entry, key)) {
                    Toast.makeText(getContext(), "Duplicate entry already exists", Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.insert(entry);
            } else {
                viewModel.update(entry);
            }

            if (getActivity() != null) {
                getActivity().onBackPressed();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error saving entry", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteEntry() {
        if (currentEntry != null) {
            viewModel.delete(currentEntry);
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        }
    }
}
