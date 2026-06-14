package juloo.keyboard2.passwordmanager.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import juloo.keyboard2.passwordmanager.PasswordDatabase;
import juloo.keyboard2.passwordmanager.PasswordEntry;

public class VaultViewModel extends AndroidViewModel {

    private final LiveData<List<PasswordEntry>> allPasswords;
    private final PasswordDatabase db;

    public VaultViewModel(@NonNull Application application) {
        super(application);
        db = PasswordDatabase.getDatabase(application);
        allPasswords = db.passwordDao().getAllPasswords();
    }

    public LiveData<List<PasswordEntry>> getAllPasswords() {
        return allPasswords;
    }

    public void insert(PasswordEntry entry) {
        PasswordDatabase.databaseWriteExecutor.execute(() -> {
            db.passwordDao().insert(entry);
        });
    }

    public void update(PasswordEntry entry) {
        PasswordDatabase.databaseWriteExecutor.execute(() -> {
            db.passwordDao().update(entry);
        });
    }

    public void delete(PasswordEntry entry) {
        PasswordDatabase.databaseWriteExecutor.execute(() -> {
            db.passwordDao().delete(entry);
        });
    }

    public PasswordEntry getEntryById(int id) {






        return db.passwordDao().getPasswordById(id);
    }
}
