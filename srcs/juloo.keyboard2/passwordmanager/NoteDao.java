package juloo.keyboard2.passwordmanager;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NoteDao {
    @Insert
    long insert(SecureNote note);

    @Update
    void update(SecureNote note);

    @Delete
    void delete(SecureNote note);

    @Query("SELECT * FROM secure_notes ORDER BY modifiedAt DESC")
    LiveData<List<SecureNote>> getAllNotes();

    @Query("SELECT * FROM secure_notes ORDER BY modifiedAt DESC")
    List<SecureNote> getAllNotesSync();

    @Query("SELECT * FROM secure_notes WHERE id = :id")
    SecureNote getNoteById(int id);

    @Query("SELECT * FROM secure_notes WHERE title = :title")
    List<SecureNote> findPotentialDuplicates(String title);
}
