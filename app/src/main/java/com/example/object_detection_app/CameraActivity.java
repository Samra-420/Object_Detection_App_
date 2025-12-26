package com.example.object_detection_app;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class CameraActivity extends AppCompatActivity {

    private TextToSpeech textToSpeech;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        tvStatus = findViewById(R.id.tvStatus);

        // Initialize Text-to-Speech
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech.setLanguage(Locale.US);

                    // Speak welcome message
                    String message = "Object detection screen opened. " +
                            "This app will detect objects using AI. " +
                            "Camera implementation in progress.";
                    speak(message);

                    // Update status
                    tvStatus.setText("Object Detection Ready\n\n" +
                            "Demo Mode Active\n" +
                            "Detecting objects...");

                    // Start object detection simulation
                    startDetectionSimulation();

                } else {
                    Toast.makeText(CameraActivity.this,
                            "Text-to-Speech failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void startDetectionSimulation() {
        // Array of objects to detect
        final String[] objects = {
                "Person", "Chair", "Table", "Laptop",
                "Mobile Phone", "Book", "Bottle", "Cup"
        };

        // Simulate detection every 3 seconds
        new android.os.Handler().postDelayed(new Runnable() {
            int count = 0;

            @Override
            public void run() {
                // Get random object
                String detectedObject = objects[count % objects.length];
                int confidence = 75 + (int)(Math.random() * 20); // 75-95% confidence

                // Update UI
                String statusText = "Detected: " + detectedObject +
                        "\nConfidence: " + confidence + "%" +
                        "\n\nDetection active...";
                tvStatus.setText(statusText);

                // Speak detection (every other detection)
                if (count % 2 == 0) {
                    speak(detectedObject + " detected ahead");
                }

                count++;

                // Repeat every 3 seconds
                new android.os.Handler().postDelayed(this, 3000);
            }
        }, 1000);
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}