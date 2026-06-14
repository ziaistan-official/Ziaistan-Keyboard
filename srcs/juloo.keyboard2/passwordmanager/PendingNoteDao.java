package juloo.keyboard2.passwordmanager;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PendingNoteDao {
    @Insert
    void insert(PendingNote note);

    @Query("SELECT * FROM pending_notes")
    List<PendingNote> getAllPendingNotes();

    @Delete
    void delete(PendingNote note);
}
