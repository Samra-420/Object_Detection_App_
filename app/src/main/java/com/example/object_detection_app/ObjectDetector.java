package com.example.object_detection_app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectDetector {
    private static final String TAG = "ObjectDetector";

    private static final String MODEL_FILE = "detect.tflite";
    private static final String LABEL_FILE = "labelmap.txt";
    private static final int INPUT_SIZE = 300;

    // COCO SSD MobileNet has 90 classes + background (index 0 = "???")
    private static final int NUM_DETECTIONS = 10;

    // Confidence threshold - Lower for better detection
    private static final float MIN_CONFIDENCE = 0.40f;

    private Interpreter tflite;
    private List<String> labels = new ArrayList<>();

    // Output buffers
    private float[][][] outputLocations;
    private float[][] outputClasses;
    private float[][] outputScores;
    private float[] numDetections;

    private ByteBuffer imgData;
    private boolean isReady = false;

    public ObjectDetector(Context context) throws IOException {
        try {
            // Load model
            MappedByteBuffer modelBuffer = loadModelFile(context);

            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);

            // Enable NNAPI for better performance
            try {
                options.setUseNNAPI(true);
                Log.d(TAG, "✅ NNAPI enabled");
            } catch (Exception e) {
                Log.w(TAG, "⚠️ NNAPI not available, using CPU");
            }

            tflite = new Interpreter(modelBuffer, options);

            // Load labels (including "???" at index 0)
            labels = loadLabelList(context);

            // Initialize input buffer (quantized model: uint8)
            imgData = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3);
            imgData.order(ByteOrder.nativeOrder());

            // Initialize output arrays
            outputLocations = new float[1][NUM_DETECTIONS][4];
            outputClasses = new float[1][NUM_DETECTIONS];
            outputScores = new float[1][NUM_DETECTIONS];
            numDetections = new float[1];

            isReady = true;

            Log.d(TAG, "✅ ObjectDetector initialized successfully");
            Log.d(TAG, "📊 Total labels loaded: " + labels.size());
            Log.d(TAG, "🎯 Confidence threshold: " + (MIN_CONFIDENCE * 100) + "%");

            // Log first few labels for verification
            for (int i = 0; i < Math.min(5, labels.size()); i++) {
                Log.d(TAG, "   Label[" + i + "]: " + labels.get(i));
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize ObjectDetector", e);
            throw new IOException("Model initialization failed", e);
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        inputStream.close();
        return buffer;
    }

    private List<String> loadLabelList(Context context) throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(LABEL_FILE)));
        String line;

        // Read ALL lines including "???" at index 0
        while ((line = reader.readLine()) != null) {
            labelList.add(line.trim());
        }
        reader.close();

        Log.d(TAG, "📋 Loaded " + labelList.size() + " labels from file");
        return labelList;
    }

    public List<Recognition> recognizeImage(Bitmap bitmap) {
        if (!isReady || tflite == null) {
            Log.e(TAG, "❌ Detector not ready");
            return new ArrayList<>();
        }

        try {
            // Resize to model input (300x300)
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                    bitmap, INPUT_SIZE, INPUT_SIZE, true);

            // Convert to byte buffer
            convertBitmapToByteBuffer(resizedBitmap);

            // Clean up resized bitmap if different from original
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }

            // Run inference
            long startTime = SystemClock.elapsedRealtime();
            runInference();
            long endTime = SystemClock.elapsedRealtime();
            Log.d(TAG, "⚡ Inference time: " + (endTime - startTime) + " ms");

            return getRecognitions();

        } catch (Exception e) {
            Log.e(TAG, "❌ Recognition failed", e);
            return new ArrayList<>();
        }
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        imgData.rewind();

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        // COCO SSD MobileNet quantized model expects RGB values 0-255
        for (int i = 0; i < INPUT_SIZE * INPUT_SIZE; i++) {
            final int val = pixels[i];
            imgData.put((byte) ((val >> 16) & 0xFF)); // R
            imgData.put((byte) ((val >> 8) & 0xFF));  // G
            imgData.put((byte) (val & 0xFF));         // B
        }
    }

    private void runInference() {
        Object[] inputs = {imgData};
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, outputLocations);
        outputs.put(1, outputClasses);
        outputs.put(2, outputScores);
        outputs.put(3, numDetections);

        tflite.runForMultipleInputsOutputs(inputs, outputs);

        // DEBUG: Log all detections for debugging
        int detections = Math.min(NUM_DETECTIONS, (int) numDetections[0]);
        Log.d(TAG, "🔍 Model returned " + detections + " raw detections");

        for (int i = 0; i < detections; i++) {
            float confidence = outputScores[0][i];
            int classId = (int) outputClasses[0][i];

            // Log all detections above 10% for debugging
            if (confidence > 0.10f) {
                String labelName = "invalid_index";
                if (classId >= 0 && classId < labels.size()) {
                    labelName = labels.get(classId);
                }

                Log.d(TAG, String.format("  [%d] ClassId=%d (%s) Conf=%.1f%%",
                        i, classId, labelName, confidence * 100));
            }
        }
    }

    private List<Recognition> getRecognitions() {
        List<Recognition> recognitions = new ArrayList<>();

        int numDetectionsValue = Math.min(NUM_DETECTIONS, (int) numDetections[0]);

        for (int i = 0; i < numDetectionsValue; i++) {
            float confidence = outputScores[0][i];

            // Only process detections above threshold
            if (confidence >= MIN_CONFIDENCE) {
                int classId = (int) outputClasses[0][i];

                // Validate class ID
                // Index 0 is "???" (background), so skip it
                // Valid classes are 1-90
                if (classId <= 0 || classId >= labels.size()) {
                    Log.w(TAG, "⚠️ Invalid class ID: " + classId);
                    continue;
                }

                String label = labels.get(classId);

                // Skip background class
                if (label.equals("???")) {
                    continue;
                }

                // Bounding box: [ymin, xmin, ymax, xmax]
                float ymin = outputLocations[0][i][0];
                float xmin = outputLocations[0][i][1];
                float ymax = outputLocations[0][i][2];
                float xmax = outputLocations[0][i][3];

                // Clamp to [0,1]
                xmin = Math.max(0, Math.min(1, xmin));
                ymin = Math.max(0, Math.min(1, ymin));
                xmax = Math.max(0, Math.min(1, xmax));
                ymax = Math.max(0, Math.min(1, ymax));

                // Validate bounding box
                if (xmax <= xmin || ymax <= ymin) {
                    Log.w(TAG, "⚠️ Invalid bounding box for " + label);
                    continue;
                }

                // Convert to pixels (for 300x300 input)
                float left = xmin * INPUT_SIZE;
                float top = ymin * INPUT_SIZE;
                float right = xmax * INPUT_SIZE;
                float bottom = ymax * INPUT_SIZE;

                RectF location = new RectF(left, top, right, bottom);
                Recognition recognition = new Recognition(
                        String.valueOf(i), label, confidence, location);

                recognitions.add(recognition);

                Log.d(TAG, String.format("✅ VALID: %s (%.1f%%) [ClassId=%d]",
                        label, confidence * 100, classId));
            }
        }

        // Sort by confidence (highest first)
        Collections.sort(recognitions, new Comparator<Recognition>() {
            @Override
            public int compare(Recognition r1, Recognition r2) {
                return Float.compare(r2.getConfidence(), r1.getConfidence());
            }
        });

        Log.d(TAG, "📊 FINAL RESULT: " + recognitions.size() + " valid detections");
        return recognitions;
    }

    public boolean isReady() {
        return isReady;
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        isReady = false;
        Log.d(TAG, "🔒 ObjectDetector closed");
    }

    public int getLabelsCount() {
        return labels.size();
    }

    public static class Recognition {
        private final String id;
        private final String title;
        private final float confidence;
        private final RectF location;

        public Recognition(String id, String title, float confidence, RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        @Override
        public String toString() {
            return String.format("%s (%.1f%%)", title, confidence * 100);
        }
    }
}