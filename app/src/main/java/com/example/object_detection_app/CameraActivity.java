package com.example.object_detection_app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ExperimentalGetImage
public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private PreviewView previewView;
    private TextView tvStatus;
    private ObjectDetector detector;
    private TextToSpeech tts;
    private ExecutorService cameraExecutor;
    private ExecutorService analysisExecutor;

    private long lastSpeakTime = 0;
    private static final long SPEAK_DELAY = 3000; // 3 seconds
    private static final float CONFIDENCE_THRESHOLD = 0.5f; // 50% confidence
    private static final int MAX_OBJECTS_TO_DISPLAY = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.previewView);
        tvStatus = findViewById(R.id.tvStatus);

        // Create executors
        cameraExecutor = Executors.newSingleThreadExecutor();
        analysisExecutor = Executors.newSingleThreadExecutor();

        // Initialize detector
        detector = new ObjectDetector(this);

        // Initialize TTS
        initTTS();

        // Check and request permissions
        checkCameraPermission();
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS Language not supported");
                    tvStatus.setText("TTS language not supported");
                } else {
                    tts.speak("Vision assistant ready",
                            TextToSpeech.QUEUE_FLUSH, null, "start");
                    Log.d(TAG, "TTS initialized successfully");
                }
            } else {
                Log.e(TAG, "TTS initialization failed");
                tvStatus.setText("TTS failed to initialize");
            }
        });
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    100
            );
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Set up image analysis
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build();

                imageAnalysis.setAnalyzer(analysisExecutor, imageProxy -> {
                    processImage(imageProxy);
                });

                // Select back camera
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind all use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );

                Log.d(TAG, "Camera started successfully");

            } catch (Exception e) {
                Log.e(TAG, "Camera start failed", e);
                runOnUiThread(() ->
                        tvStatus.setText("Camera error: " + e.getMessage())
                );
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processImage(ImageProxy imageProxy) {
        if (detector == null || !detector.isReady()) {
            imageProxy.close();
            return;
        }

        try {
            // Convert ImageProxy to Bitmap
            Bitmap bitmap = ImageUtils.imageProxyToBitmap(imageProxy);

            if (bitmap == null) {
                Log.w(TAG, "Failed to convert image to bitmap");
                imageProxy.close();
                return;
            }

            // Run object detection
            List<ObjectDetector.DetectionResult> results = detector.detect(bitmap);

            // Clean up
            bitmap.recycle();
            imageProxy.close();

            // Update UI with results
            runOnUiThread(() -> handleDetectionResults(results));

        } catch (Exception e) {
            Log.e(TAG, "Image processing error", e);
            if (imageProxy != null) {
                imageProxy.close();
            }
        }
    }

    private void handleDetectionResults(List<ObjectDetector.DetectionResult> results) {
        if (detector == null || !detector.isReady()) {
            tvStatus.setText("⚠️ Model loading...");
            return;
        }

        if (results == null || results.isEmpty()) {
            tvStatus.setText("🔍 No objects detected");
            return;
        }

        // Filter results by confidence threshold and valid labels
        List<ObjectDetector.DetectionResult> validResults = new ArrayList<>();
        for (ObjectDetector.DetectionResult result : results) {
            if (result.confidence >= CONFIDENCE_THRESHOLD &&
                    result.label != null &&
                    !result.label.trim().isEmpty() &&
                    !result.label.equals("background") &&
                    !result.label.contains("???")) {
                validResults.add(result);
            }
        }

        if (validResults.isEmpty()) {
            tvStatus.setText("🔍 Scanning...");
            return;
        }

        // Build status text
        StringBuilder statusBuilder = new StringBuilder();
        int displayCount = Math.min(validResults.size(), MAX_OBJECTS_TO_DISPLAY);

        for (int i = 0; i < displayCount; i++) {
            ObjectDetector.DetectionResult result = validResults.get(i);
            if (i > 0) statusBuilder.append("\n");
            statusBuilder.append("✅ ")
                    .append(result.label)
                    .append(" (")
                    .append(String.format("%.0f", result.confidence * 100))
                    .append("%)");

            // Log for debugging
            Log.d(TAG, "Displaying: " + result.label + " - " + result.confidence);
        }

        // If more objects detected, show count
        if (validResults.size() > MAX_OBJECTS_TO_DISPLAY) {
            statusBuilder.append("\n+")
                    .append(validResults.size() - MAX_OBJECTS_TO_DISPLAY)
                    .append(" more");
        }

        tvStatus.setText(statusBuilder.toString());

        // Speak the highest confidence object
        speakDetection(validResults.get(0));
    }

    private void speakDetection(ObjectDetector.DetectionResult result) {
        if (tts == null || result == null || result.label == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastSpeakTime > SPEAK_DELAY) {
            String speechText = result.label + " detected";

            tts.speak(speechText,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "detection_" + System.currentTimeMillis());

            lastSpeakTime = currentTime;
            Log.d(TAG, "Spoke: " + speechText);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                tvStatus.setText("❌ Camera permission denied");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (detector == null) {
            detector = new ObjectDetector(this);
        }
        if (tts == null) {
            initTTS();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tts != null) {
            tts.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up resources
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        if (detector != null) {
            detector.close();
            detector = null;
        }

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }

        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
            analysisExecutor = null;
        }
    }
}