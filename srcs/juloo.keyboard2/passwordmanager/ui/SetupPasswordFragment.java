package juloo.keyboard2.passwordmanager.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.security.GeneralSecurityException;

import juloo.keyboard2.R;
import juloo.keyboard2.passwordmanager.AuthManager;

public class SetupPasswordFragment extends Fragment {

    private EditText passwordInput;
    private EditText confirmInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setup_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        passwordInput = view.findViewById(R.id.password_input);
        confirmInput = view.findViewById(R.id.confirm_input);
        Button saveButton = view.findViewById(R.id.save_button);

        saveButton.setOnClickListener(v -> {
            String pass = passwordInput.getText().toString();
            String confirm = confirmInput.getText().toString();

            if (TextUtils.isEmpty(pass)) {
                passwordInput.setError("Password cannot be empty");
                return;
            }
            if (!pass.equals(confirm)) {
                confirmInput.setError("Passwords do not match");
                return;
            }

            try {
                AuthManager.getInstance().setMasterPassword(requireContext(), pass);
                if (getActivity() instanceof PasswordManagerActivity) {
                    ((PasswordManagerActivity) getActivity()).replaceFragment(new VaultFragment());
                }
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Error setting password", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
