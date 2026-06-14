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

import juloo.keyboard2.R;
import juloo.keyboard2.passwordmanager.AuthManager;

public class LoginFragment extends Fragment {

    private EditText passwordInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        passwordInput = view.findViewById(R.id.password_input);
        Button loginButton = view.findViewById(R.id.login_button);

        loginButton.setOnClickListener(v -> {
            String pass = passwordInput.getText().toString();

            if (TextUtils.isEmpty(pass)) {
                passwordInput.setError("Password cannot be empty");
                return;
            }

            if (AuthManager.getInstance().login(requireContext(), pass)) {
                if (getActivity() instanceof PasswordManagerActivity) {
                    PasswordManagerActivity activity = (PasswordManagerActivity) getActivity();
                    activity.promotePendingNotes();
                    activity.replaceFragment(new VaultFragment());
                }
            } else {
                Toast.makeText(getContext(), "Incorrect password", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
