package juloo.keyboard2.passwordmanager.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import juloo.keyboard2.R;
import juloo.keyboard2.passwordmanager.PasswordDatabase;
import juloo.keyboard2.passwordmanager.PendingNote;

public class QuickNoteActivity extends AppCompatActivity {

    private EditText titleInput;
    private EditText contentInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_note);

        titleInput = findViewById(R.id.title_input);
        contentInput = findViewById(R.id.content_input);
        Button saveButton = findViewById(R.id.save_button);
        Button cancelButton = findViewById(R.id.cancel_button);

        saveButton.setOnClickListener(v -> saveNote());
        cancelButton.setOnClickListener(v -> finish());
    }

    private void saveNote() {
        String title = titleInput.getText().toString();
        String content = contentInput.getText().toString();

        if (TextUtils.isEmpty(title)) {
            titleInput.setError("Title required");
            return;
        }

        PendingNote note = new PendingNote();
        note.title = title;
        note.content = content;

        PasswordDatabase.databaseWriteExecutor.execute(() -> {
            PasswordDatabase.getDatabase(this).pendingNoteDao().insert(note);
            runOnUiThread(() -> {
                Toast.makeText(this, "Note saved to pending", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
