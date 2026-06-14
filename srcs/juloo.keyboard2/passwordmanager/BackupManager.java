package juloo.keyboard2.passwordmanager;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class BackupManager {

    private static final String BACKUP_DIR = "/Download/ziaistan_keyboard_backup";


    private static class BackupData {


        List<PortablePasswordEntry> passwords;
        List<PortableSecureNote> notes;

        BackupData(List<PortablePasswordEntry> passwords, List<PortableSecureNote> notes) {
            this.passwords = passwords;
            this.notes = notes;
        }
    }


    private static class PortablePasswordEntry {
        String siteName;
        String url;
        String username;
        String password;
        String notes;
        String customIcon;
        long createdAt;
        long modifiedAt;
        String category;
        boolean isFavorite;
    }

    private static class PortableSecureNote {
        String title;
        String content;
        long createdAt;
        long modifiedAt;
    }

    public static void exportBackup(Context context, String masterPassword) {
        PasswordDatabase.databaseWriteExecutor.execute(() -> {
            try {

                SecretKey sessionKey = AuthManager.getInstance().getSessionKey();
                if (sessionKey == null) {






                    if (!AuthManager.getInstance().isAuthenticated()) {









                         throw new IOException("Vault is locked. Please unlock first.");
                    }
                }

                PasswordDatabase db = PasswordDatabase.getDatabase(context);
                List<PasswordEntry> dbPasswords = db.passwordDao().getAllPasswordsSync();
                List<SecureNote> dbNotes = db.noteDao().getAllNotesSync();


                List<PortablePasswordEntry> portablePasswords = new ArrayList<>();
                for (PasswordEntry entry : dbPasswords) {
                    PortablePasswordEntry p = new PortablePasswordEntry();
                    p.siteName = entry.siteName;
                    p.url = entry.url;
                    p.username = entry.username;
                    try {
                        p.password = SecurityUtils.decrypt(entry.encryptedPassword, sessionKey);
                        if (entry.encryptedNotes != null) {
                            p.notes = SecurityUtils.decrypt(entry.encryptedNotes, sessionKey);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();

                        continue;
                    }
                    p.createdAt = entry.createdAt;
                    p.modifiedAt = entry.modifiedAt;
                    p.category = entry.category;
                    p.isFavorite = entry.isFavorite;
                    p.customIcon = entry.customIcon;
                    portablePasswords.add(p);
                }

                List<PortableSecureNote> portableNotes = new ArrayList<>();
                for (SecureNote note : dbNotes) {
                    PortableSecureNote n = new PortableSecureNote();
                    n.title = note.title;
                    try {
                        n.content = SecurityUtils.decrypt(note.encryptedContent, sessionKey);
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                    n.createdAt = note.createdAt;
                    n.modifiedAt = note.modifiedAt;
                    portableNotes.add(n);
                }

                BackupData data = new BackupData(portablePasswords, portableNotes);
                String json = new Gson().toJson(data);


                byte[] fileSalt = new byte[16];
                new SecureRandom().nextBytes(fileSalt);
                SecretKey fileKey = deriveKey(masterPassword, fileSalt);

                byte[] iv = new byte[12];
                new SecureRandom().nextBytes(iv);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, fileKey, new GCMParameterSpec(128, iv));
                byte[] encryptedBytes = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));

                String saltStr = Base64.encodeToString(fileSalt, Base64.NO_WRAP);
                String ivStr = Base64.encodeToString(iv, Base64.NO_WRAP);
                String dataStr = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);

                String finalOutput = saltStr + ":" + ivStr + ":" + dataStr;


                File dir = new File(Environment.getExternalStorageDirectory(), BACKUP_DIR);
                if (!dir.exists()) dir.mkdirs();

                String filename = "password_vault_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".zkb";
                File file = new File(dir, filename);

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(finalOutput.getBytes(StandardCharsets.UTF_8));
                }

                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Backup saved to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show()
                );

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    public static void importBackup(Context context, File backupFile, String masterPassword) {
        try {
             importBackupFromStream(context, new FileInputStream(backupFile), masterPassword);
        } catch (IOException e) {
             e.printStackTrace();
             Toast.makeText(context, "File Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void importBackupFromStream(Context context, InputStream inputStream, String masterPassword) {
        PasswordDatabase.databaseWriteExecutor.execute(() -> {
            try {

                SecretKey sessionKey = AuthManager.getInstance().getSessionKey();
                if (sessionKey == null) {


                     if (!AuthManager.getInstance().isAuthenticated()) {





                         throw new IOException("Please unlock the vault before restoring.");
                     }
                }


                StringBuilder sb = new StringBuilder();
                try (InputStream fis = inputStream) {
                    int content;
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                    }
                }
                String fileContent = sb.toString();


                String[] parts = fileContent.split(":", 3);
                if (parts.length != 3) throw new IOException("Invalid backup format");

                byte[] fileSalt = Base64.decode(parts[0], Base64.NO_WRAP);
                byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
                byte[] encryptedBytes = Base64.decode(parts[2], Base64.NO_WRAP);


                SecretKey fileKey = deriveKey(masterPassword, fileSalt);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, fileKey, new GCMParameterSpec(128, iv));
                byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
                String json = new String(decryptedBytes, StandardCharsets.UTF_8);


                BackupData data = new Gson().fromJson(json, BackupData.class);

                if (data == null) throw new IOException("Failed to parse data");

                PasswordDatabase db = PasswordDatabase.getDatabase(context);


                if (data.passwords != null) {
                    for (PortablePasswordEntry p : data.passwords) {
                        if (isDuplicate(context, p, sessionKey)) continue;

                        PasswordEntry entry = new PasswordEntry();
                        entry.siteName = p.siteName;
                        entry.url = p.url;
                        entry.username = p.username;
                        entry.createdAt = p.createdAt;
                        entry.modifiedAt = p.modifiedAt;
                        entry.category = p.category;
                        entry.isFavorite = p.isFavorite;
                        entry.customIcon = p.customIcon;

                        try {
                            entry.encryptedPassword = SecurityUtils.encrypt(p.password, sessionKey);
                            if (p.notes != null) {
                                entry.encryptedNotes = SecurityUtils.encrypt(p.notes, sessionKey);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            continue;
                        }

                        db.passwordDao().insert(entry);
                    }
                }

                if (data.notes != null) {
                    for (PortableSecureNote n : data.notes) {
                        if (isDuplicateNote(context, n, sessionKey)) continue;

                        SecureNote note = new SecureNote();
                        note.title = n.title;
                        note.createdAt = n.createdAt;
                        note.modifiedAt = n.modifiedAt;
                        try {
                            note.encryptedContent = SecurityUtils.encrypt(n.content, sessionKey);
                        } catch (Exception e) {
                            e.printStackTrace();
                            continue;
                        }
                        db.noteDao().insert(note);
                    }
                }

                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Restore Successful!", Toast.LENGTH_LONG).show()
                );

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    public static void importFromCsv(Context context, InputStream inputStream) {
        PasswordDatabase.databaseWriteExecutor.execute(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream))) {
                SecretKey sessionKey = AuthManager.getInstance().getSessionKey();
                if (sessionKey == null) {
                    throw new IOException("Vault is locked. Please unlock first.");
                }

                PasswordDatabase db = PasswordDatabase.getDatabase(context);
                String line;
                String[] header = null;

                db.runInTransaction(() -> {
                    try {
                        String l;
                        String[] h = null;
                        while ((l = reader.readLine()) != null) {
                            if (l.trim().isEmpty()) continue;

                            if (l.startsWith("\ufeff")) {
                                l = l.substring(1);
                            }

                            String[] values = l.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                            if (h == null) {

                                boolean hasName = false;
                                for (String v : values) {
                                    String lower = v.trim().toLowerCase().replace("\"", "");
                                    if (lower.equals("name") || lower.equals("title") || lower.contains("site") || lower.contains("url") || lower.contains("username") || lower.contains("login") || lower.contains("password")) {
                                        hasName = true;
                                        break;
                                    }
                                }

                                if (hasName) {
                                    h = values;
                                    for (int i = 0; i < h.length; i++) h[i] = h[i].trim().toLowerCase().replace("\"", "");
                                    continue;
                                } else {

                                    h = new String[]{"name", "url", "username", "password", "note"};
                                }
                            }

                            PasswordEntry entry = new PasswordEntry();
                            for (int i = 0; i < values.length && i < h.length; i++) {
                                String key = h[i];
                                String val = values[i].trim();
                                if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                                    val = val.substring(1, val.length() - 1).replace("\"\"", "\"");
                                }

                                if (key.equals("name") || key.equals("title") || key.contains("site")) entry.siteName = val;
                                else if (key.equals("url") || key.contains("link")) entry.url = val;
                                else if (key.equals("username") || key.equals("login") || key.contains("user") || key.contains("email")) entry.username = val;
                                else if (key.equals("password") || key.contains("pass")) {
                                     try {
                                         entry.encryptedPassword = SecurityUtils.encrypt(val, sessionKey);
                                     } catch (Exception e) { e.printStackTrace(); }
                                } else if (key.contains("note")) {
                                     try {
                                         entry.encryptedNotes = SecurityUtils.encrypt(val, sessionKey);
                                     } catch (Exception e) { e.printStackTrace(); }
                                }
                            }

                            if (entry.siteName == null && entry.url != null) entry.siteName = entry.url;
                            if (entry.siteName != null && entry.encryptedPassword != null) {
                                if (!isDuplicate(context, entry, sessionKey)) {
                                    db.passwordDao().insert(entry);
                                }
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "CSV Import Successful!", Toast.LENGTH_LONG).show()
                );
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "CSV Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isDuplicate(Context context, PortablePasswordEntry p, SecretKey sessionKey) {
        PasswordDatabase db = PasswordDatabase.getDatabase(context);
        String pSite = normalize(p.siteName);
        String pUser = normalize(p.username);
        List<PasswordEntry> potentials = db.passwordDao().findPotentialDuplicates(pSite, pUser);
        for (PasswordEntry existing : potentials) {
            try {
                String existingPass = SecurityUtils.decrypt(existing.encryptedPassword, sessionKey);
                String existingUrl = normalize(existing.url);
                String incomingUrl = normalize(p.url);

                if (normalize(existingPass).equals(normalize(p.password)) && existingUrl.equals(incomingUrl)) {
                    String existingNotes = existing.encryptedNotes != null ? SecurityUtils.decrypt(existing.encryptedNotes, sessionKey) : "";
                    String incomingNotes = p.notes != null ? p.notes : "";
                    if (normalize(existingNotes).equals(normalize(incomingNotes))) {
                        return true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean isDuplicateNote(Context context, PortableSecureNote n, SecretKey sessionKey) {
        PasswordDatabase db = PasswordDatabase.getDatabase(context);
        List<SecureNote> potentials = db.noteDao().findPotentialDuplicates(normalize(n.title));
        for (SecureNote existing : potentials) {
            try {
                String existingContent = SecurityUtils.decrypt(existing.encryptedContent, sessionKey);
                if (normalize(existingContent).equals(normalize(n.content))) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean isDuplicateNote(Context context, SecureNote incoming, SecretKey sessionKey) {
        PasswordDatabase db = PasswordDatabase.getDatabase(context);
        List<SecureNote> potentials = db.noteDao().findPotentialDuplicates(normalize(incoming.title));
        for (SecureNote existing : potentials) {
            try {
                String existingContent = SecurityUtils.decrypt(existing.encryptedContent, sessionKey);
                String incomingContent = SecurityUtils.decrypt(incoming.encryptedContent, sessionKey);
                if (normalize(existingContent).equals(normalize(incomingContent))) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean isDuplicate(Context context, PasswordEntry incoming, SecretKey sessionKey) {
        PasswordDatabase db = PasswordDatabase.getDatabase(context);
        String iSite = normalize(incoming.siteName);
        String iUser = normalize(incoming.username);
        List<PasswordEntry> potentials = db.passwordDao().findPotentialDuplicates(iSite, iUser);
        for (PasswordEntry existing : potentials) {
            try {
                String existingPass = SecurityUtils.decrypt(existing.encryptedPassword, sessionKey);
                String incomingPass = SecurityUtils.decrypt(incoming.encryptedPassword, sessionKey);

                String existingUrl = normalize(existing.url);
                String incomingUrl = normalize(incoming.url);

                if (normalize(existingPass).equals(normalize(incomingPass)) && existingUrl.equals(incomingUrl)) {
                    String existingNotes = existing.encryptedNotes != null ? SecurityUtils.decrypt(existing.encryptedNotes, sessionKey) : "";
                    String incomingNotes = incoming.encryptedNotes != null ? SecurityUtils.decrypt(incoming.encryptedNotes, sessionKey) : "";
                    if (normalize(existingNotes).equals(normalize(incomingNotes))) {
                        return true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 100000, 256);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }
}
