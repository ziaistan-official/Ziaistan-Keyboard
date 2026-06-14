package juloo.keyboard2.passwordmanager;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "passwords")
public class PasswordEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String siteName;
    public String url;
    public String username;


    public String encryptedPassword;
    public String encryptedNotes;


    public String iv;
    public long createdAt;
    public long modifiedAt;
    public long lastUsedAt;


    public String category;
    public boolean isFavorite;
    public String customIcon;

    public PasswordEntry() {
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = System.currentTimeMillis();
    }
}
