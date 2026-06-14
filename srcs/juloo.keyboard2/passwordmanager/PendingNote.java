package juloo.keyboard2.passwordmanager;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_notes")
public class PendingNote {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String title;
    public String content;
    public long createdAt;

    public PendingNote() {
        this.createdAt = System.currentTimeMillis();
    }
}
