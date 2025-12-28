package com.example.object_detection_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    private static final int PERMISSION_REQUEST_CAMERA = 101;

    private PreviewView previewView;
    private TextView resultTextView;
    private Button toggleButton;
    private Button backButton;
    private Button debugButton;
    private TextView statusTextView;

    private ExecutorService cameraExecutor;
    private ObjectDetector objectDetector;
    private TextToSpeech textToSpeech;

    private boolean isDetecting = false;
    private String lastSpokenResult = "";
    private List<String> detectionHistory = new ArrayList<>();
    private int detectionCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        initializeViews();
        setupClickListeners();

        // Initialize Object Detector
        initializeObjectDetector();

        // Initialize Text-to-Speech
        initializeTextToSpeech();

        // Check camera permission
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestCameraPermission();
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void initializeViews() {
        previewView = findViewById(R.id.preview_view);
        resultTextView = findViewById(R.id.result_text);
        toggleButton = findViewById(R.id.toggle_button);
        backButton = findViewById(R.id.back_button);
        debugButton = findViewById(R.id.debug_button);
        statusTextView = findViewById(R.id.status_text);

        // Set initial text
        statusTextView.setText("Camera initializing...");
        resultTextView.setText("Press 'Start Detection' to begin");
    }

    private void setupClickListeners() {
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDetection();
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        debugButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDebugInfo();
            }
        });
    }

    private void initializeObjectDetector() {
        try {
            objectDetector = new ObjectDetector(this);
            statusTextView.setText("✅ Model loaded successfully");
            speak("Object detection model loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize object detector", e);
            statusTextView.setText("❌ Failed to load model");
            Toast.makeText(this, "Failed to load detection model", Toast.LENGTH_LONG).show();
            speak("Failed to load detection model. Please check your model files.");
        }
    }

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.US);
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "Text-to-Speech language not supported");
                        statusTextView.setText("⚠️ TTS language not supported");
                    } else {
                        Log.d(TAG, "Text-to-Speech initialized successfully");
                    }
                } else {
                    Log.e(TAG, "Text-to-Speech initialization failed");
                    statusTextView.setText("⚠️ TTS initialization failed");
                }
            }
        });
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                PERMISSION_REQUEST_CAMERA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
                speak("Camera permission granted. Starting camera.");
            } else {
                statusTextView.setText("❌ Camera permission denied");
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
                speak("Camera permission is required to use this app.");
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview
                Preview preview = new Preview.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Camera selector (use back camera)
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Image analysis for object detection
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @OptIn(markerClass = ExperimentalGetImage.class)
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        if (!isDetecting || objectDetector == null) {
                            imageProxy.close();
                            return;
                        }

                        @SuppressLint("UnsafeOptInUsageError")
                        android.media.Image image = imageProxy.getImage();
                        if (image != null) {
                            Bitmap bitmap = ImageUtils.imageToBitmap(image);
                            if (bitmap != null) {
                                // Run object detection
                                List<ObjectDetector.Recognition> recognitions =
                                        objectDetector.recognizeImage(bitmap);

                                // Process results on UI thread
                                runOnUiThread(() -> {
                                    handleDetectionResults(recognitions);
                                    updateDetectionCount();
                                });
                            }
                        }
                        imageProxy.close();
                    }
                });

                // Bind use cases to camera
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis);

                statusTextView.setText("✅ Camera ready");
                speak("Camera ready. Point at objects and press start detection.");

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed", e);
                statusTextView.setText("❌ Camera initialization failed");
                speak("Failed to initialize camera.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleDetection() {
        isDetecting = !isDetecting;
        if (isDetecting) {
            toggleButton.setText("Stop Detection");
            toggleButton.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            statusTextView.setText("🔍 Detecting objects...");
            speak("Object detection started. Point camera at objects.");

            // Reset detection history
            detectionHistory.clear();
            detectionCount = 0;
            resultTextView.setText("Starting detection...\n");
        } else {
            toggleButton.setText("Start Detection");
            toggleButton.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
            statusTextView.setText("⏸️ Detection paused");
            resultTextView.setText("Detection paused\n" + resultTextView.getText());
            speak("Detection stopped. Detected " + detectionCount + " objects.");
        }
    }

    private void handleDetectionResults(List<ObjectDetector.Recognition> recognitions) {
        if (recognitions.isEmpty()) {
            if (detectionCount == 0) {
                resultTextView.setText("No objects detected\nPoint camera at objects");
            }
            return;
        }

        // Sort by confidence (highest first)
        Collections.sort(recognitions, new Comparator<ObjectDetector.Recognition>() {
            @Override
            public int compare(ObjectDetector.Recognition r1, ObjectDetector.Recognition r2) {
                return Float.compare(r2.getConfidence(), r1.getConfidence());
            }
        });

        // Build display text
        StringBuilder displayText = new StringBuilder();
        StringBuilder speechText = new StringBuilder();

        displayText.append("🎯 Detected Objects:\n\n");
        speechText.append("Detected ");

        int count = 0;
        float totalConfidence = 0;

        for (int i = 0; i < Math.min(5, recognitions.size()); i++) {
            ObjectDetector.Recognition recognition = recognitions.get(i);
            float confidence = recognition.getConfidence();

            if (confidence >= 0.15f) { // 15% confidence threshold
                String objectName = recognition.getTitle();
                float confidencePercent = confidence * 100;

                displayText.append(String.format("• %s: %.1f%%\n", objectName, confidencePercent));

                if (count > 0) {
                    speechText.append(", ");
                }
                speechText.append(objectName);

                // Add to history (avoid duplicates)
                if (!detectionHistory.contains(objectName)) {
                    detectionHistory.add(objectName);
                }

                totalConfidence += confidence;
                count++;
            }
        }

        if (count == 0) {
            displayText.append("No confident detections\n(confidence < 15%)");
            return;
        }

        // Add statistics
        float avgConfidence = totalConfidence / count;
        displayText.append(String.format("\n📊 Detected %d objects (Avg confidence: %.1f%%)",
                count, avgConfidence));

        // Add history
        if (!detectionHistory.isEmpty()) {
            displayText.append("\n\n📋 History: ");
            for (int i = 0; i < Math.min(5, detectionHistory.size()); i++) {
                displayText.append(detectionHistory.get(i));
                if (i < Math.min(5, detectionHistory.size()) - 1) {
                    displayText.append(", ");
                }
            }
        }

        resultTextView.setText(displayText.toString());

        // Speak results if different from last time
        String currentResult = speechText.toString();
        if (!currentResult.equals(lastSpokenResult) && count > 0) {
            String speech = speechText + ". " + count + " objects detected.";
            speak(speech);
            lastSpokenResult = currentResult;
        }

        detectionCount += count;
    }

    private void updateDetectionCount() {
        String status = isDetecting ?
                "🔍 Detecting... | Total: " + detectionCount + " objects" :
                "⏸️ Paused | Total: " + detectionCount + " objects";
        statusTextView.setText(status);
    }

    private void showDebugInfo() {
        String debugInfo = "📱 Object Detection Debug Info\n\n" +
                "Model: COCO SSD MobileNet V1\n" +
                "Input Size: 300x300\n" +
                "Labels: " + (objectDetector != null ? objectDetector.getLabelsCount() : "Unknown") + " classes\n" +
                "Detection Mode: " + (isDetecting ? "Active" : "Inactive") + "\n" +
                "Total Detections: " + detectionCount + "\n" +
                "Unique Objects: " + detectionHistory.size() + "\n" +
                "\nCommon objects to detect:\n" +
                "• Person\n• Cell Phone\n• Bottle\n• Laptop\n• Chair\n• Book\n• TV";

        new android.app.AlertDialog.Builder(this)
                .setTitle("Debug Information")
                .setMessage(debugInfo)
                .setPositiveButton("OK", null)
                .show();

        speak("Showing debug information. " + detectionCount + " total detections, " +
                detectionHistory.size() + " unique objects.");
    }

    private void speak(String text) {
        if (textToSpeech != null && !textToSpeech.isSpeaking()) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        if (isDetecting) {
            isDetecting = false;
            toggleButton.setText("Start Detection");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (objectDetector != null) {
            objectDetector.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}