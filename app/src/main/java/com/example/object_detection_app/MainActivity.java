package com.example.object_detection_app;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private MaterialCardView btnStart;
    private TextToSpeech textToSpeech;
    private boolean isSpeaking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech.setLanguage(Locale.US);
                    // Set utterance listener to detect when speech finishes
                    textToSpeech.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            isSpeaking = true;
                        }

                        @Override
                        public void onDone(String utteranceId) {
                            isSpeaking = false;
                            // Navigate to CameraActivity AFTER speech completes
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                                    startActivity(intent);
                                }
                            });
                        }

                        @Override
                        public void onError(String utteranceId) {
                            isSpeaking = false;
                            // Navigate even if there's an error
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                                    startActivity(intent);
                                }
                            });
                        }
                    });
                }
            }
        });

        // Setup button click listener
        setupButtonListeners();
    }

    private void setupButtonListeners() {
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Speak the message
                speak("Detection started. Point your camera.");
            }
        });

        // Optional: Long press for instructions
        btnStart.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // Different message for long press
                speak("Welcome to Vision Assistant. Tap once to start object detection.");
                return true;
            }
        });
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            // Stop any ongoing speech first
            textToSpeech.stop();
            // Speak the text with utterance ID
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speech_complete");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (textToSpeech != null) {
            textToSpeech.stop();
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