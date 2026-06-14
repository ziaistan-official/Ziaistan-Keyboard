package juloo.keyboard2.passwordmanager.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import juloo.keyboard2.R;
import juloo.keyboard2.passwordmanager.BackupManager;
import juloo.keyboard2.passwordmanager.PasswordEntry;

public class VaultFragment extends Fragment {

    private VaultViewModel viewModel;
    private PasswordAdapter adapter;
    private java.util.Set<Integer> selectedIds = new java.util.HashSet<>();
    private boolean isSelectionMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_vault, container, false);
    }

    private final androidx.activity.result.ActivityResultLauncher<String> importCsvLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    try {
                        InputStream is = requireContext().getContentResolver().openInputStream(uri);
                        BackupManager.importFromCsv(requireContext(), is);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), "Failed to open CSV", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(VaultViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PasswordAdapter();
        recyclerView.setAdapter(adapter);

        View bulkActions = view.findViewById(R.id.bulk_actions_container);
        view.findViewById(R.id.btn_delete_selected).setOnClickListener(v -> deleteSelected());
        view.findViewById(R.id.btn_export_selected).setOnClickListener(v -> exportSelected());

        FloatingActionButton fab = view.findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> {
             if (getActivity() instanceof PasswordManagerActivity) {
                ((PasswordManagerActivity) getActivity()).navigateTo(new EditEntryFragment());
            }
        });

        view.findViewById(R.id.btn_backup).setOnClickListener(v -> showBackupDialog());
        view.findViewById(R.id.btn_restore).setOnClickListener(v -> showRestoreDialog());
        view.findViewById(R.id.btn_generator).setOnClickListener(v -> {
             if (getActivity() instanceof PasswordManagerActivity) {
                ((PasswordManagerActivity) getActivity()).navigateTo(new PasswordGeneratorFragment());
            }
        });

        view.findViewById(R.id.btn_notes).setOnClickListener(v -> {
             if (getActivity() instanceof PasswordManagerActivity) {
                ((PasswordManagerActivity) getActivity()).navigateTo(new SecureNotesFragment());
            }
        });

        view.findViewById(R.id.btn_import_csv).setOnClickListener(v -> {
            if (getActivity() instanceof PasswordManagerActivity) {
                ((PasswordManagerActivity) getActivity()).setLaunchingExternalActivity(true);
            }
            importCsvLauncher.launch("text/*");
        });

        viewModel.getAllPasswords().observe(getViewLifecycleOwner(), entries -> {
            adapter.setEntries(entries);
            autoFetchMissingIcons(entries);
        });
    }

    private void autoFetchMissingIcons(List<PasswordEntry> entries) {
        for (PasswordEntry entry : entries) {
            if (entry.customIcon == null && entry.url != null && (entry.url.startsWith("http") || entry.url.contains("."))) {
                juloo.keyboard2.passwordmanager.FaviconHelper.loadFavicon(entry.url, new juloo.keyboard2.passwordmanager.FaviconHelper.FaviconCallback() {
                    @Override
                    public void onFaviconLoaded(android.graphics.Bitmap bitmap) {
                        if (bitmap != null) {
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos);
                            entry.customIcon = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.DEFAULT);
                            viewModel.update(entry);
                        }
                    }
                    @Override
                    public void onError(Exception e) {}
                });
            }
        }
    }

    private void deleteSelected() {
        if (selectedIds.isEmpty()) return;
        new android.app.AlertDialog.Builder(getContext())
            .setTitle("Delete Selected")
            .setMessage("Are you sure you want to delete " + selectedIds.size() + " entries?")
            .setPositiveButton("Delete", (d, w) -> {
                for (PasswordEntry entry : adapter.entries) {
                    if (selectedIds.contains(entry.id)) {
                        viewModel.delete(entry);
                    }
                }
                exitSelectionMode();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void exportSelected() {
        if (selectedIds.isEmpty()) return;

        List<PasswordEntry> toExport = new ArrayList<>();
        for (PasswordEntry entry : adapter.entries) {
            if (selectedIds.contains(entry.id)) {
                toExport.add(entry);
            }
        }

        if (toExport.isEmpty()) return;

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Export Selected");
        final android.widget.EditText input = new android.widget.EditText(getContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Encryption Password");
        builder.setView(input);

        builder.setPositiveButton("Export", (dialog, which) -> {
            String password = input.getText().toString();
            if (!android.text.TextUtils.isEmpty(password)) {

                BackupManager.exportBackup(getContext(), password);
                exitSelectionMode();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedIds.clear();
        adapter.notifyDataSetChanged();
        if (getView() != null) {
            getView().findViewById(R.id.bulk_actions_container).setVisibility(View.GONE);
            getView().findViewById(R.id.fab_add).setVisibility(View.VISIBLE);
        }
    }

    private void showBackupDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Backup Vault");
        final android.widget.EditText input = new android.widget.EditText(getContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Enter Master Password to Encrypt Backup");
        builder.setView(input);

        builder.setPositiveButton("Backup", (dialog, which) -> {
            String password = input.getText().toString();
            if (!android.text.TextUtils.isEmpty(password)) {
                BackupManager.exportBackup(getContext(), password);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showRestoreDialog() {
        File backupDir = new File(android.os.Environment.getExternalStorageDirectory(), "/Download/ziaistan_keyboard_backup");
        if (!backupDir.exists() || !backupDir.isDirectory()) {
            Toast.makeText(getContext(), "No backup directory found at " + backupDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return;
        }

        File[] files = backupDir.listFiles((dir, name) -> name.endsWith(".zkb"));
        if (files == null || files.length == 0) {
            Toast.makeText(getContext(), "No backup files found in " + backupDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return;
        }

        String[] fileNames = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            fileNames[i] = files[i].getName();
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Select Backup to Restore");
        builder.setItems(fileNames, (dialog, which) -> {
            File selectedFile = files[which];
            askPasswordAndRestore(selectedFile);
        });
        builder.show();
    }

    private void askPasswordAndRestore(File file) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Decrypt Backup");
        final android.widget.EditText input = new android.widget.EditText(getContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Enter Backup Password");
        builder.setView(input);

        builder.setPositiveButton("Restore", (dialog, which) -> {
            String password = input.getText().toString();
            if (!android.text.TextUtils.isEmpty(password)) {
                BackupManager.importBackup(getContext(), file, password);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
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
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_password_entry, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PasswordEntry entry = entries.get(position);
            holder.siteName.setText(entry.siteName);
            holder.username.setText(entry.username);

            boolean isSelected = selectedIds.contains(entry.id);
            holder.itemView.setActivated(isSelected);
            holder.itemView.setBackgroundColor(isSelected ? 0x330000FF : android.graphics.Color.TRANSPARENT);

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
                        holder.icon.setImageResource(R.drawable.ic_launcher_foreground);
                    }
                } catch (Exception e) {
                    holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            }

            holder.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(entry.id);
                } else {
                     if (getActivity() instanceof PasswordManagerActivity) {
                        EditEntryFragment fragment = new EditEntryFragment();
                        Bundle args = new Bundle();
                        args.putInt("entry_id", entry.id);
                        fragment.setArguments(args);
                        ((PasswordManagerActivity) getActivity()).navigateTo(fragment);
                    }
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode) {
                    isSelectionMode = true;
                    if (getView() != null) {
                        getView().findViewById(R.id.bulk_actions_container).setVisibility(View.VISIBLE);
                        getView().findViewById(R.id.fab_add).setVisibility(View.GONE);
                    }
                }
                toggleSelection(entry.id);
                return true;
            });
        }

        private void toggleSelection(int id) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id);
            } else {
                selectedIds.add(id);
            }
            if (selectedIds.isEmpty()) {
                exitSelectionMode();
            } else {
                notifyDataSetChanged();
            }
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView siteName;
            TextView username;
            android.widget.ImageView icon;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                siteName = itemView.findViewById(R.id.site_name);
                username = itemView.findViewById(R.id.username);
                icon = itemView.findViewById(R.id.app_icon);
            }
        }
    }
}
