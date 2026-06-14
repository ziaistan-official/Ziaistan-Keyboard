package juloo.keyboard2.passwordmanager;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {PasswordEntry.class, SecureNote.class, PendingNote.class}, version = 4, exportSchema = false)
public abstract class PasswordDatabase extends RoomDatabase {

    public abstract PasswordDao passwordDao();
    public abstract NoteDao noteDao();
    public abstract PendingNoteDao pendingNoteDao();

    private static volatile PasswordDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE passwords ADD COLUMN customIcon TEXT");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `pending_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `content` TEXT, `createdAt` INTEGER NOT NULL)");
        }
    };

    public static PasswordDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (PasswordDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    PasswordDatabase.class, "password_vault_db")
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
