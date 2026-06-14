package juloo.keyboard2.passwordmanager;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PasswordDao {
    @Insert
    long insert(PasswordEntry entry);

    @Update
    void update(PasswordEntry entry);

    @Delete
    void delete(PasswordEntry entry);

    @Query("DELETE FROM passwords WHERE id = :id")
    void deleteById(int id);

    @Query("SELECT * FROM passwords ORDER BY siteName ASC")
    LiveData<List<PasswordEntry>> getAllPasswords();

    @Query("SELECT * FROM passwords WHERE id = :id")
    PasswordEntry getPasswordById(int id);

    @Query("SELECT * FROM passwords WHERE siteName LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%'")
    LiveData<List<PasswordEntry>> searchPasswords(String query);

    @Query("SELECT * FROM passwords")
    List<PasswordEntry> getAllPasswordsSync();

    @Query("SELECT * FROM passwords WHERE siteName = :siteName AND (username = :username OR (username IS NULL AND :username IS NULL))")
    List<PasswordEntry> findPotentialDuplicates(String siteName, String username);
}
