package com.example.object_detection_app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.button.MaterialButton;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ObjectDetectionPrefs";
    private static final String KEY_VOICE_FEEDBACK = "voice_feedback";
    private static final String KEY_SENSITIVITY = "sensitivity";
    private static final String KEY_VOLUME = "volume";

    private SwitchCompat switchVoice;
    private RadioGroup radioSensitivity;
    private SeekBar volumeSeekBar;
    private TextView tvVolumeValue;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initializeViews();
        initializeSharedPreferences();
        loadSavedSettings();
        setupListeners();
    }

    private void initializeViews() {
        switchVoice = findViewById(R.id.switchVoice);
        radioSensitivity = findViewById(R.id.radioSensitivity);
        volumeSeekBar = findViewById(R.id.volumeSeekBar);
        tvVolumeValue = findViewById(R.id.tvVolumeValue);
        MaterialButton btnBack = findViewById(R.id.btnBackSettings);
        MaterialButton btnReset = findViewById(R.id.btnReset);

        // Back button
        btnBack.setOnClickListener(v -> {
            saveCurrentSettings();
            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
            finish();
        });

        // Reset button
        btnReset.setOnClickListener(v -> showResetConfirmationDialog());
    }

    private void initializeSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void loadSavedSettings() {
        // Load voice feedback setting
        boolean isVoiceEnabled = sharedPreferences.getBoolean(KEY_VOICE_FEEDBACK, true);
        switchVoice.setChecked(isVoiceEnabled);

        // Load sensitivity setting
        String sensitivity = sharedPreferences.getString(KEY_SENSITIVITY, "Low");
        switch (sensitivity) {
            case "Medium":
                radioSensitivity.check(R.id.radioMedium);
                break;
            case "High":
                radioSensitivity.check(R.id.radioHigh);
                break;
            default:
                radioSensitivity.check(R.id.radioLow);
        }

        // Load volume setting
        int volume = sharedPreferences.getInt(KEY_VOLUME, 70);
        volumeSeekBar.setProgress(volume);
        updateVolumeText(volume);
    }

    private void setupListeners() {
        // Voice feedback switch
        switchVoice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSetting(KEY_VOICE_FEEDBACK, isChecked);
            showToast(isChecked ? "Voice feedback enabled" : "Voice feedback disabled");
        });

        // Sensitivity radio group
        radioSensitivity.setOnCheckedChangeListener((group, checkedId) -> {
            String level = getSensitivityLevel(checkedId);
            saveSetting(KEY_SENSITIVITY, level);
            showToast("Sensitivity set to: " + level);
        });

        // Volume seekbar
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVolumeText(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Optional: Show some feedback
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int volume = seekBar.getProgress();
                saveSetting(KEY_VOLUME, volume);
                showToast("Volume set to " + volume + "%");
            }
        });
    }

    private String getSensitivityLevel(int checkedId) {
        if (checkedId == R.id.radioMedium) return "Medium";
        if (checkedId == R.id.radioHigh) return "High";
        return "Low";
    }

    private void updateVolumeText(int volume) {
        tvVolumeValue.setText(volume + "%");

        // Change color based on volume level
        if (volume < 33) {
            tvVolumeValue.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else if (volume < 66) {
            tvVolumeValue.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        } else {
            tvVolumeValue.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    private void showResetConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Settings")
                .setMessage("Are you sure you want to reset all settings to default values?")
                .setPositiveButton("Reset", (dialog, which) -> resetToDefault())
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void resetToDefault() {
        // Reset to default values
        switchVoice.setChecked(true);
        radioSensitivity.check(R.id.radioLow);
        volumeSeekBar.setProgress(70);
        updateVolumeText(70);

        // Save defaults
        saveSetting(KEY_VOICE_FEEDBACK, true);
        saveSetting(KEY_SENSITIVITY, "Low");
        saveSetting(KEY_VOLUME, 70);

        showToast("Settings reset to default values");
    }

    private void saveSetting(String key, Object value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        }

        editor.apply();
    }

    private void saveCurrentSettings() {
        // Save voice feedback
        saveSetting(KEY_VOICE_FEEDBACK, switchVoice.isChecked());

        // Save sensitivity
        String sensitivity = getSensitivityLevel(radioSensitivity.getCheckedRadioButtonId());
        saveSetting(KEY_SENSITIVITY, sensitivity);

        // Save volume
        saveSetting(KEY_VOLUME, volumeSeekBar.getProgress());
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Auto-save when leaving activity
        saveCurrentSettings();
    }

    // Static utility methods for other activities
    public static boolean isVoiceEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_VOICE_FEEDBACK, true);
    }

    public static String getSensitivity(SharedPreferences prefs) {
        return prefs.getString(KEY_SENSITIVITY, "Low");
    }

    public static int getVolume(SharedPreferences prefs) {
        return prefs.getInt(KEY_VOLUME, 70);
    }

    public static float getSensitivityValue(SharedPreferences prefs) {
        String sensitivity = getSensitivity(prefs);
        switch (sensitivity) {
            case "Medium":
                return 0.6f;
            case "High":
                return 0.3f;
            default:
                return 0.8f;
        }
    }

    @Override
    public void onBackPressed() {
        saveCurrentSettings();
        super.onBackPressed();
    }
}