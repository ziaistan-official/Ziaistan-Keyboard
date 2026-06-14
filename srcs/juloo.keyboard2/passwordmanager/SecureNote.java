package juloo.keyboard2.passwordmanager;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "secure_notes")
public class SecureNote {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;


    public String encryptedContent;

    public long createdAt;
    public long modifiedAt;

    public SecureNote() {
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = System.currentTimeMillis();
    }
}
