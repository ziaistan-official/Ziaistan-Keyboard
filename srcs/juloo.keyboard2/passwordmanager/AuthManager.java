package juloo.keyboard2.passwordmanager;

import android.content.Context;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;

import javax.crypto.SecretKey;

public class AuthManager {

    private static AuthManager INSTANCE;
    private SecretKey sessionKey;

    private AuthManager() {}

    public static synchronized AuthManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AuthManager();
        }
        return INSTANCE;
    }

    public boolean isMasterPasswordSet(Context context) {
        return SecurityUtils.isMasterPasswordSet(context);
    }

    public void setMasterPassword(Context context, String password) throws GeneralSecurityException {
        SecurityUtils.setMasterPassword(context, password);

        sessionKey = SecurityUtils.deriveKey(context, password);
    }

    public boolean login(Context context, String password) {
        if (SecurityUtils.verifyMasterPassword(context, password)) {
            try {
                sessionKey = SecurityUtils.deriveKey(context, password);
                return true;
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public SecretKey getSessionKey() {
        return sessionKey;
    }

    public void logout() {
        sessionKey = null;
    }

    public boolean isAuthenticated() {
        return sessionKey != null;
    }


    public boolean canAuthenticateWithBiometrics(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
               == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public interface BiometricCallback {
        void onSuccess();
        void onError(String errString);
    }

    public void authenticateWithBiometrics(FragmentActivity activity, BiometricCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                callback.onError(errString.toString());
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);



















                callback.onSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();

            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Use Password")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}
