package juloo.keyboard2.passwordmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class BiometricHelper {

    private static final String KEY_NAME = "ziaistan_biometric_key";
    private static final String PREF_ENCRYPTED_PASS = "encrypted_master_pass_bio";
    private static final String PREF_IV = "biometric_iv";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    public interface BiometricAuthCallback {
        void onSuccess(String masterPassword);
        void onError(String error);
    }

    public static boolean isBiometricEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("secure_vault_prefs", Context.MODE_PRIVATE);
        return prefs.contains(PREF_ENCRYPTED_PASS);
    }

    public static void enableBiometric(FragmentActivity activity, String masterPassword, BiometricAuthCallback callback) {
        try {

            generateSecretKey();


            Cipher cipher = getCipher();
            SecretKey secretKey = getSecretKey();
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);






            showBiometricPrompt(activity, cipher, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    try {
                        Cipher cipher = result.getCryptoObject().getCipher();
                        byte[] encrypted = cipher.doFinal(masterPassword.getBytes(StandardCharsets.UTF_8));
                        byte[] iv = cipher.getIV();

                        SharedPreferences.Editor editor = activity.getSharedPreferences("secure_vault_prefs", Context.MODE_PRIVATE).edit();
                        editor.putString(PREF_ENCRYPTED_PASS, Base64.encodeToString(encrypted, Base64.NO_WRAP));
                        editor.putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP));
                        editor.apply();

                        callback.onSuccess(masterPassword);
                    } catch (Exception e) {
                        e.printStackTrace();
                        callback.onError("Encryption failed: " + e.getMessage());
                    }
                }

                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    callback.onError(errString.toString());
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            callback.onError("Setup failed: " + e.getMessage());
        }
    }

    public static void unlockWithBiometric(FragmentActivity activity, BiometricAuthCallback callback) {
        try {
            SharedPreferences prefs = activity.getSharedPreferences("secure_vault_prefs", Context.MODE_PRIVATE);
            String ivStr = prefs.getString(PREF_IV, null);
            String encPassStr = prefs.getString(PREF_ENCRYPTED_PASS, null);

            if (ivStr == null || encPassStr == null) {
                callback.onError("Biometric not set up.");
                return;
            }

            byte[] iv = Base64.decode(ivStr, Base64.NO_WRAP);

            Cipher cipher = getCipher();
            SecretKey secretKey = getSecretKey();
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));

            showBiometricPrompt(activity, cipher, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    try {
                        Cipher cipher = result.getCryptoObject().getCipher();
                        byte[] decrypted = cipher.doFinal(Base64.decode(encPassStr, Base64.NO_WRAP));
                        String masterPassword = new String(decrypted, StandardCharsets.UTF_8);
                        callback.onSuccess(masterPassword);
                    } catch (Exception e) {
                        e.printStackTrace();

                        callback.onError("Decryption failed. Fingerprint might have changed.");
                    }
                }

                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    callback.onError(errString.toString());
                }
            });

        } catch (Exception e) {

            e.printStackTrace();
            callback.onError("Authentication failed or Key Invalidated: " + e.getMessage());
        }
    }

    private static void generateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_NAME)) {
            keyStore.deleteEntry(KEY_NAME);
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true);

        keyGenerator.init(builder.build());
        keyGenerator.generateKey();
    }

    private static SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return (SecretKey) keyStore.getKey(KEY_NAME, null);
    }

    private static Cipher getCipher() throws Exception {
        return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_GCM + "/"
                + KeyProperties.ENCRYPTION_PADDING_NONE);
    }

    private static void showBiometricPrompt(FragmentActivity activity, Cipher cipher, BiometricPrompt.AuthenticationCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, callback);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Confirm your identity")
                .setNegativeButtonText("Use Password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
    }
}
