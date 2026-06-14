package juloo.keyboard2.passwordmanager.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.security.GeneralSecurityException;
import java.util.concurrent.Executors;

import javax.crypto.SecretKey;

import juloo.keyboard2.R;
import juloo.keyboard2.passwordmanager.AuthManager;
import juloo.keyboard2.passwordmanager.PasswordDatabase;
import juloo.keyboard2.passwordmanager.SecureNote;
import juloo.keyboard2.passwordmanager.SecurityUtils;

public class EditNoteFragment extends Fragment {

    private EditText titleInput;
    private EditText contentInput;
    private int noteId = -1;
    private SecureNote currentNote;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_note, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        titleInput = view.findViewById(R.id.title_input);
        contentInput = view.findViewById(R.id.content_input);
        Button saveButton = view.findViewById(R.id.save_button);
        Button deleteButton = view.findViewById(R.id.delete_button);

        if (getArguments() != null && getArguments().containsKey("note_id")) {
            noteId = getArguments().getInt("note_id");
            deleteButton.setVisibility(View.VISIBLE);
            loadNote(noteId);
        } else {
            deleteButton.setVisibility(View.GONE);
        }

        saveButton.setOnClickListener(v -> saveNote());
        deleteButton.setOnClickListener(v -> deleteNote());
    }

    private void loadNote(int id) {
        Executors.newSingleThreadExecutor().execute(() -> {
            SecureNote note = PasswordDatabase.getDatabase(requireContext()).noteDao().getNoteById(id);
            if (note != null) {
                currentNote = note;
                getActivity().runOnUiThread(() -> {
                    titleInput.setText(note.title);
                    try {
                        SecretKey key = AuthManager.getInstance().getSessionKey();
                        if (key != null) {
                            String decrypted = SecurityUtils.decrypt(note.encryptedContent, key);
                            contentInput.setText(decrypted);
                        }
                    } catch (GeneralSecurityException e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), "Failed to decrypt note", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void saveNote() {
        String title = titleInput.getText().toString();
        String content = contentInput.getText().toString();

        if (TextUtils.isEmpty(title)) {
            titleInput.setError("Title required");
            return;
        }

        try {
            SecretKey key = AuthManager.getInstance().getSessionKey();
            if (key == null) return;

            String encryptedContent = SecurityUtils.encrypt(content, key);

            if (currentNote == null) {
                currentNote = new SecureNote();
            }

            currentNote.title = title;
            currentNote.encryptedContent = encryptedContent;
            currentNote.modifiedAt = System.currentTimeMillis();

            PasswordDatabase.databaseWriteExecutor.execute(() -> {
                if (noteId == -1) {
                    if (juloo.keyboard2.passwordmanager.BackupManager.isDuplicateNote(requireContext(), currentNote, key)) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Duplicate note already exists", Toast.LENGTH_SHORT).show());
                        return;
                    }
                    PasswordDatabase.getDatabase(getContext()).noteDao().insert(currentNote);
                } else {
                    PasswordDatabase.getDatabase(getContext()).noteDao().update(currentNote);
                }
                getActivity().runOnUiThread(() -> getParentFragmentManager().popBackStack());
            });

        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Encryption failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteNote() {
        if (currentNote != null) {
            PasswordDatabase.databaseWriteExecutor.execute(() -> {
                PasswordDatabase.getDatabase(getContext()).noteDao().delete(currentNote);
                getActivity().runOnUiThread(() -> getParentFragmentManager().popBackStack());
            });
        }
    }
}
