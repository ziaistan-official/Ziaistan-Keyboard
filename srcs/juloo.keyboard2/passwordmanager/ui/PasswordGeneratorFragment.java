package juloo.keyboard2.passwordmanager.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import juloo.keyboard2.R;
import juloo.keyboard2.passwordmanager.PasswordGenerator;

public class PasswordGeneratorFragment extends Fragment {

    private TextView passwordPreview;
    private SeekBar lengthSeekBar;
    private TextView lengthLabel;
    private CheckBox checkLower;
    private CheckBox checkUpper;
    private CheckBox checkNumbers;
    private CheckBox checkSymbols;
    private CheckBox checkAmbiguous;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_password_generator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        passwordPreview = view.findViewById(R.id.password_preview);
        lengthSeekBar = view.findViewById(R.id.length_seekbar);
        lengthLabel = view.findViewById(R.id.length_label);
        checkLower = view.findViewById(R.id.check_lower);
        checkUpper = view.findViewById(R.id.check_upper);
        checkNumbers = view.findViewById(R.id.check_numbers);
        checkSymbols = view.findViewById(R.id.check_symbols);
        checkAmbiguous = view.findViewById(R.id.check_ambiguous);
        Button generateButton = view.findViewById(R.id.btn_generate);
        Button copyButton = view.findViewById(R.id.btn_copy);


        lengthSeekBar.setMax(100);
        lengthSeekBar.setProgress(40);
        updateLengthLabel(40);

        lengthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 4) progress = 4;
                updateLengthLabel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        generateButton.setOnClickListener(v -> generatePassword());
        copyButton.setOnClickListener(v -> copyPassword());

        generatePassword();
    }

    private void updateLengthLabel(int length) {
        lengthLabel.setText("Length: " + length);
    }

    private void generatePassword() {
        PasswordGenerator.Options options = new PasswordGenerator.Options();
        options.length = Math.max(4, lengthSeekBar.getProgress());
        options.useLowercase = checkLower.isChecked();
        options.useUppercase = checkUpper.isChecked();
        options.useNumbers = checkNumbers.isChecked();
        options.useSymbols = checkSymbols.isChecked();
        options.excludeAmbiguous = checkAmbiguous.isChecked();

        String password = PasswordGenerator.generatePassword(options);
        passwordPreview.setText(password);
    }

    private void copyPassword() {
        String pass = passwordPreview.getText().toString();
        if (pass.isEmpty()) return;

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Generated Password", pass);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "Password copied", Toast.LENGTH_SHORT).show();
    }
}
