package com.example.object_detection_app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OptIn(markerClass = ExperimentalGetImage.class)
public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    private static final int PERMISSION_REQUEST_CAMERA = 101;

    // UI Components
    private PreviewView previewView;
    private TextView resultTextView;
    private TextView statusTextView;
    private TextView historyTextView;
    private Button toggleButton;
    private Button backButton;
    private Button debugButton;

    // Camera & Detection
    private ExecutorService cameraExecutor;
    private ObjectDetector objectDetector;

    // TTS
    private TextToSpeech textToSpeech;
    private boolean isVoiceEnabled = true;
    private boolean isTTSReady = false;

    // State
    private boolean isDetecting = false;
    private Set<String> detectedObjects = new HashSet<>();
    private int detectionCount = 0;
    private long lastDetectionTime = 0;
    private static final long DETECTION_COOLDOWN = 1500; // 1.5 seconds
    private float confidenceThreshold = 0.50f;

    // Voice control
    private String lastSpokenObject = "";
    private long lastSpeechTime = 0;
    private static final long SPEECH_COOLDOWN = 3000; // 3 seconds between same object announcement

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        initializeViews();
        setupButtons();
        initializeTTS();
        checkPermissions();

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void initializeViews() {
        previewView = findViewById(R.id.preview_view);
        resultTextView = findViewById(R.id.result_text);
        statusTextView = findViewById(R.id.status_text);
        historyTextView = findViewById(R.id.history_text);
        toggleButton = findViewById(R.id.toggle_button);
        backButton = findViewById(R.id.back_button);
        debugButton = findViewById(R.id.debug_button);
    }

    private void setupButtons() {
        backButton.setOnClickListener(v -> finish());
        debugButton.setOnClickListener(v -> showDebugInfo());
        toggleButton.setOnClickListener(v -> toggleDetection());
    }

    private void initializeTTS() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS language not supported");
                    isVoiceEnabled = false;
                    isTTSReady = false;
                } else {
                    isTTSReady = true;
                    // Set speech rate slightly faster for better experience
                    textToSpeech.setSpeechRate(1.0f);
                    Log.d(TAG, "✅ TTS initialized successfully");
                }
            } else {
                Log.e(TAG, "TTS initialization failed");
                isVoiceEnabled = false;
                isTTSReady = false;
            }
        });
    }

    private void checkPermissions() {
        if (allPermissionsGranted()) {
            initializeDetector();
        } else {
            requestCameraPermission();
        }
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
                initializeDetector();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void initializeDetector() {
        try {
            statusTextView.setText("Loading model...");
            objectDetector = new ObjectDetector(this);
            statusTextView.setText("Model loaded • Ready to detect");
            startCamera();
        } catch (IOException e) {
            statusTextView.setText("Model load failed");
            Log.e(TAG, "Model load failed", e);
            Toast.makeText(this, "Failed to load detection model", Toast.LENGTH_LONG).show();
        }
    }

    private void startCamera() {
        if (objectDetector == null) {
            statusTextView.setText("Model not loaded");
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCamera(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera init failed", e);
                statusTextView.setText("Camera init failed");
                Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(640, 480))
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            statusTextView.setText("Camera ready • Press Start Detection");
        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed", e);
            statusTextView.setText("Camera binding failed");
        }
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        try {
            if (!isDetecting || objectDetector == null) {
                imageProxy.close();
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastDetectionTime < DETECTION_COOLDOWN) {
                imageProxy.close();
                return;
            }

            Image image = imageProxy.getImage();
            if (image != null) {
                Bitmap bitmap = imageToBitmap(image);
                if (bitmap != null) {
                    // Resize bitmap to match model input size (300x300)
                    Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 300, 300, true);

                    try {
                        List<ObjectDetector.Recognition> recognitions =
                                objectDetector.recognizeImage(resizedBitmap);
                        lastDetectionTime = now;
                        runOnUiThread(() -> handleDetectionResults(recognitions));
                    } catch (Exception e) {
                        Log.e(TAG, "Detection failed", e);
                        runOnUiThread(() -> statusTextView.setText("Detection error"));
                    } finally {
                        if (resizedBitmap != bitmap) {
                            resizedBitmap.recycle();
                        }
                        bitmap.recycle();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Image analysis error", e);
        } finally {
            imageProxy.close();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        try {
            if (image.getFormat() == ImageFormat.YUV_420_888) {
                ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
                ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
                ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

                int ySize = yBuffer.remaining();
                int uSize = uBuffer.remaining();
                int vSize = vBuffer.remaining();

                byte[] nv21 = new byte[ySize + uSize + vSize];
                yBuffer.get(nv21, 0, ySize);
                vBuffer.get(nv21, ySize, vSize);
                uBuffer.get(nv21, ySize + vSize, uSize);

                YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                        image.getWidth(), image.getHeight(), null);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(),
                        image.getHeight()), 90, out);
                byte[] bytes = out.toByteArray();
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "Image conversion error", e);
        }
        return null;
    }

    private void toggleDetection() {
        if (objectDetector == null) {
            Toast.makeText(this, "Model not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        isDetecting = !isDetecting;
        if (isDetecting) {
            toggleButton.setText("Stop Detection");
            toggleButton.setBackgroundTintList(
                    getResources().getColorStateList(android.R.color.holo_red_dark));
            statusTextView.setText("🔴 Detecting...");
            detectedObjects.clear();
            detectionCount = 0;
            lastSpokenObject = "";
            resultTextView.setText("Scanning for objects...");
            historyTextView.setText("");
        } else {
            toggleButton.setText("Start Detection");
            toggleButton.setBackgroundTintList(
                    getResources().getColorStateList(android.R.color.holo_green_dark));
            statusTextView.setText("⏸️ Detection paused");
        }
    }

    private void handleDetectionResults(List<ObjectDetector.Recognition> recognitions) {
        if (objectDetector == null) {
            return;
        }

        if (recognitions == null || recognitions.isEmpty()) {
            resultTextView.setText("No objects detected\n\nPoint camera at common objects like:\n• Person\n• Chair, Table\n• Laptop, Phone\n• Cup, Bottle");
            return;
        }

        // Sort by confidence (highest first)
        Collections.sort(recognitions, (r1, r2) ->
                Float.compare(r2.getConfidence(), r1.getConfidence()));

        // Get the MOST CONFIDENT object only
        ObjectDetector.Recognition topRecognition = recognitions.get(0);

        if (topRecognition.getConfidence() < confidenceThreshold) {
            resultTextView.setText("No confident detections\n\nMove closer or improve lighting");
            return;
        }

        // Build display text with TOP object highlighted
        StringBuilder displayText = new StringBuilder();
        displayText.append("🎯 PRIMARY DETECTION:\n\n");

        String topObjectName = topRecognition.getTitle().replace("_", " ");
        displayText.append(String.format("📍 %s\n   Confidence: %.1f%%\n\n",
                topObjectName.toUpperCase(),
                topRecognition.getConfidence() * 100));

        // Add other detections
        if (recognitions.size() > 1) {
            displayText.append("Other objects:\n");
            for (int i = 1; i < Math.min(recognitions.size(), 4); i++) {
                ObjectDetector.Recognition rec = recognitions.get(i);
                if (rec.getConfidence() >= confidenceThreshold) {
                    String name = rec.getTitle().replace("_", " ");
                    displayText.append(String.format("  • %s (%.1f%%)\n",
                            name, rec.getConfidence() * 100));
                }
            }
        }

        resultTextView.setText(displayText.toString());

        // Update statistics
        for (ObjectDetector.Recognition rec : recognitions) {
            if (rec.getConfidence() >= confidenceThreshold) {
                detectedObjects.add(rec.getTitle());
                detectionCount++;
            }
        }
        historyTextView.setText("Total: " + detectedObjects.size() + " unique | " +
                detectionCount + " detections");

        // VOICE ANNOUNCEMENT - Only speak the TOP object
        speakTopObject(topObjectName, topRecognition.getConfidence());
    }

    private void speakTopObject(String objectName, float confidence) {
        if (!isVoiceEnabled || !isTTSReady || textToSpeech == null) {
            return;
        }

        long now = System.currentTimeMillis();

        // Only speak if:
        // 1. Different object than last time OR
        // 2. Same object but enough time has passed (to avoid spam)
        if (!objectName.equals(lastSpokenObject) ||
                (now - lastSpeechTime) > SPEECH_COOLDOWN) {

            // Create natural speech
            String speech = objectName;

            // Add confidence level for very high confidence
            if (confidence >= 0.80f) {
                speech = objectName + " detected";
            } else if (confidence >= 0.60f) {
                speech = "I see " + objectName;
            }

            // Speak it
            textToSpeech.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null);

            lastSpokenObject = objectName;
            lastSpeechTime = now;

            Log.d(TAG, "🔊 Speaking: " + speech);
        }
    }

    private void showDebugInfo() {
        String modelStatus = (objectDetector != null) ? "Loaded" : "Not Loaded";
        String ttsStatus = isTTSReady ? "Ready" : "Not Ready";
        String debug = "Model: " + modelStatus + "\n" +
                "TTS: " + ttsStatus + "\n" +
                "Detection: " + (isDetecting ? "Active" : "Inactive") + "\n" +
                "Unique Objects: " + detectedObjects.size() + "\n" +
                "Total Detections: " + detectionCount + "\n" +
                "Confidence: " + (confidenceThreshold * 100) + "%" + "\n" +
                "Last Spoken: " + (lastSpokenObject.isEmpty() ? "None" : lastSpokenObject);
        Toast.makeText(this, debug, Toast.LENGTH_LONG).show();
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

    @Override
    protected void onPause() {
        super.onPause();
        if (isDetecting) {
            toggleDetection();
        }
        // Stop any ongoing speech
        if (textToSpeech != null && isTTSReady) {
            textToSpeech.stop();
        }
    }
}