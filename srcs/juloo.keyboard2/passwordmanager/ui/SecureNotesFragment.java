package juloo.keyboard2.passwordmanager.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import juloo.keyboard2.R;
import juloo.keyboard2.passwordmanager.PasswordDatabase;
import juloo.keyboard2.passwordmanager.SecureNote;

public class SecureNotesFragment extends Fragment {

    private RecyclerView recyclerView;
    private NotesAdapter adapter;
    private android.widget.EditText searchInput;
    private List<SecureNote> allNotes = new ArrayList<>();
    private java.util.Set<Integer> selectedIds = new java.util.HashSet<>();
    private boolean isSelectionMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_secure_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_view);
        searchInput = view.findViewById(R.id.search_input);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotesAdapter();
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                filter(s.toString());
            }
        });

        view.findViewById(R.id.btn_delete_selected).setOnClickListener(v -> deleteSelected());

        FloatingActionButton fab = view.findViewById(R.id.fab_add_note);
        fab.setOnClickListener(v -> {
             if (getActivity() instanceof PasswordManagerActivity) {
                ((PasswordManagerActivity) getActivity()).navigateTo(new EditNoteFragment());
            }
        });

        PasswordDatabase.getDatabase(requireContext()).noteDao().getAllNotes().observe(getViewLifecycleOwner(), notes -> {
            this.allNotes = notes;
            filter(searchInput.getText().toString());
        });
    }

    private void filter(String query) {
        if (query.isEmpty()) {
            adapter.setNotes(allNotes);
            return;
        }
        String q = query.toLowerCase();
        List<SecureNote> filtered = new ArrayList<>();
        juloo.keyboard2.passwordmanager.AuthManager auth = juloo.keyboard2.passwordmanager.AuthManager.getInstance();
        javax.crypto.SecretKey key = auth.getSessionKey();

        for (SecureNote note : allNotes) {
            boolean match = (note.title != null && note.title.toLowerCase().contains(q));
            if (!match && key != null) {
                try {
                    String decrypted = juloo.keyboard2.passwordmanager.SecurityUtils.decrypt(note.encryptedContent, key);
                    if (decrypted != null && decrypted.toLowerCase().contains(q)) {
                        match = true;
                    }
                } catch (Exception e) {}
            }
            if (match) filtered.add(note);
        }
        adapter.setNotes(filtered);
    }

    private void deleteSelected() {
        if (selectedIds.isEmpty()) return;
        new android.app.AlertDialog.Builder(getContext())
            .setTitle("Delete Selected")
            .setMessage("Are you sure you want to delete " + selectedIds.size() + " notes?")
            .setPositiveButton("Delete", (d, w) -> {
                for (SecureNote note : adapter.notes) {
                    if (selectedIds.contains(note.id)) {
                        PasswordDatabase.databaseWriteExecutor.execute(() -> {
                            PasswordDatabase.getDatabase(getContext()).noteDao().delete(note);
                        });
                    }
                }
                exitSelectionMode();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedIds.clear();
        adapter.notifyDataSetChanged();
        if (getView() != null) {
            getView().findViewById(R.id.bulk_actions_container).setVisibility(View.GONE);
            getView().findViewById(R.id.fab_add_note).setVisibility(View.VISIBLE);
        }
    }

    private class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.ViewHolder> {
        private List<SecureNote> notes = new ArrayList<>();

        public void setNotes(List<SecureNote> notes) {
            this.notes = notes;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_secure_note, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SecureNote note = notes.get(position);
            holder.title.setText(note.title != null && !note.title.isEmpty() ? note.title : "Untitled Note");

            boolean isSelected = selectedIds.contains(note.id);
            holder.itemView.setBackgroundColor(isSelected ? 0x330000FF : android.graphics.Color.TRANSPARENT);

            holder.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(note.id);
                } else {
                     if (getActivity() instanceof PasswordManagerActivity) {
                        EditNoteFragment fragment = new EditNoteFragment();
                        Bundle args = new Bundle();
                        args.putInt("note_id", note.id);
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
                        getView().findViewById(R.id.fab_add_note).setVisibility(View.GONE);
                    }
                }
                toggleSelection(note.id);
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
            return notes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.note_title);
            }
        }
    }
}
