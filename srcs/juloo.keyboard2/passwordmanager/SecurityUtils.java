package juloo.keyboard2.passwordmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class SecurityUtils {

    private static final String KEY_ALIAS = "ziaistan_vault_key";
    private static final int ITERATIONS = 100000;
    private static final int KEY_LENGTH = 256;
    private static final String SALT_PREF_KEY = "vault_salt";
    private static final String HASH_PREF_KEY = "master_hash";


    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";


    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;


    public static boolean isMasterPasswordSet(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs.contains(HASH_PREF_KEY);
    }


    public static void setMasterPassword(Context context, String password) throws GeneralSecurityException {
        byte[] salt = generateSalt();
        String saltStr = Base64.encodeToString(salt, Base64.NO_WRAP);


        String hash = hashPassword(password, salt);

        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putString(SALT_PREF_KEY, saltStr);
        editor.putString(HASH_PREF_KEY, hash);
        editor.apply();
    }


    public static boolean verifyMasterPassword(Context context, String password) {
        SharedPreferences prefs = getPrefs(context);
        String saltStr = prefs.getString(SALT_PREF_KEY, null);
        String storedHash = prefs.getString(HASH_PREF_KEY, null);

        if (saltStr == null || storedHash == null) return false;

        byte[] salt = Base64.decode(saltStr, Base64.NO_WRAP);
        try {
            String computedHash = hashPassword(password, salt);
            return computedHash.equals(storedHash);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            return false;
        }
    }


    public static SecretKey deriveKey(Context context, String password) throws GeneralSecurityException {
        SharedPreferences prefs = getPrefs(context);
        String saltStr = prefs.getString(SALT_PREF_KEY, null);
        if (saltStr == null) throw new GeneralSecurityException("Salt not found");

        byte[] salt = Base64.decode(saltStr, Base64.NO_WRAP);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static String encrypt(String data, SecretKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));


        String ivStr = Base64.encodeToString(iv, Base64.NO_WRAP);
        String dataStr = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
        return ivStr + ":" + dataStr;
    }

    public static String decrypt(String encryptedData, SecretKey key) throws GeneralSecurityException {
        String[] parts = encryptedData.split(":");
        if (parts.length != 2) throw new GeneralSecurityException("Invalid encrypted data format");

        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] data = Base64.decode(parts[1], Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] decryptedBytes = cipher.doFinal(data);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    private static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static String hashPassword(String password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] hash = skf.generateSecret(spec).getEncoded();
        return Base64.encodeToString(hash, Base64.NO_WRAP);
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences("secure_vault_prefs", Context.MODE_PRIVATE);
    }
}
