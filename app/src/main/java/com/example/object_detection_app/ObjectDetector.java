package com.example.object_detection_app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private static final int NUM_DETECTIONS = 10;
    private static final float MIN_CONFIDENCE = 0.5f;
    private static final String MODEL_FILE = "detect.tflite";
    private static final String LABEL_FILE = "labelmap.txt";

    private Interpreter tflite;
    private List<String> labels;
    private int inputSize = 300;
    private boolean isReady = false;

    public static class DetectionResult {
        public String label;
        public float confidence;
        public float[] box;

        public DetectionResult(String label, float confidence, float[] box) {
            this.label = label;
            this.confidence = confidence;
            this.box = box;
        }
    }

    public ObjectDetector(Context context) {
        try {
            labels = loadLabels(context);
            Log.d(TAG, "✅ Loaded " + labels.size() + " labels");

            MappedByteBuffer model = loadModelFile(context, MODEL_FILE);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);

            tflite = new Interpreter(model, options);
            isReady = true;

            Log.d(TAG, "✅ Model loaded successfully");

        } catch (Exception e) {
            Log.e(TAG, "❌ Model load failed", e);
        }
    }

    public boolean isReady() {
        return isReady;
    }

    public List<DetectionResult> detect(Bitmap bitmap) {
        List<DetectionResult> results = new ArrayList<>();
        if (!isReady) {
            Log.e(TAG, "Model not ready");
            return results;
        }

        try {
            // Resize to 300x300
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);
            ByteBuffer inputBuffer = convertBitmapToByteBuffer(resized);
            resized.recycle();

            // Output arrays for SSD MobileNet
            float[][][] outputLocations = new float[1][NUM_DETECTIONS][4];
            float[][] outputClasses = new float[1][NUM_DETECTIONS];
            float[][] outputScores = new float[1][NUM_DETECTIONS];
            float[] numDetections = new float[1];

            // FIXED: Use Map for outputs as required by TensorFlow Lite
            Map<Integer, Object> outputMap = new HashMap<>();
            outputMap.put(0, outputLocations);   // boxes
            outputMap.put(1, outputClasses);     // classes
            outputMap.put(2, outputScores);      // scores
            outputMap.put(3, numDetections);     // num detections

            // FIXED: Use correct method signature
            tflite.runForMultipleInputsOutputs(new Object[]{inputBuffer}, outputMap);

            int detections = Math.min((int) numDetections[0], NUM_DETECTIONS);
            Log.d(TAG, "Raw detections: " + detections);

            for (int i = 0; i < detections; i++) {
                float confidence = outputScores[0][i];

                // Skip low confidence
                if (confidence < MIN_CONFIDENCE) continue;

                int classIndex = (int) outputClasses[0][i];

                // IMPORTANT: COCO SSD MobileNet model outputs:
                // 0 = background (skip this)
                // 1 = person
                // 2 = bicycle
                // 3 = car
                // ... etc

                // Since classIndex 0 is background, we should ignore it
                if (classIndex == 0) continue;

                // Adjust index for our label list (which starts from 0 = person)
                int labelIndex = classIndex - 1;

                // Safety check
                if (labelIndex < 0 || labelIndex >= labels.size()) {
                    Log.w(TAG, "Skipping invalid label index: " + labelIndex + " (classIndex=" + classIndex + ")");
                    continue;
                }

                String label = labels.get(labelIndex);
                float[] box = outputLocations[0][i];

                // Log the raw detection
                Log.d(TAG, "Raw detection - Index: " + classIndex +
                        " -> Label: " + label +
                        " Score: " + confidence +
                        " Box: [" + box[0] + "," + box[1] + "," + box[2] + "," + box[3] + "]");

                // Validate box coordinates
                if (!isValidBox(box)) {
                    Log.w(TAG, "Skipping invalid box");
                    continue;
                }

                results.add(new DetectionResult(label, confidence, box));
            }

            // Sort by confidence (highest first)
            Collections.sort(results, new Comparator<DetectionResult>() {
                @Override
                public int compare(DetectionResult a, DetectionResult b) {
                    return Float.compare(b.confidence, a.confidence);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Detection error", e);
        }

        return results;
    }

    private boolean isValidBox(float[] box) {
        // Check if box has 4 coordinates
        if (box == null || box.length != 4) return false;

        // Box format: [ymin, xmin, ymax, xmax]
        float ymin = box[0];
        float xmin = box[1];
        float ymax = box[2];
        float xmax = box[3];

        // Check for valid ranges
        if (ymin < 0 || ymin > 1) return false;
        if (xmin < 0 || xmin > 1) return false;
        if (ymax < 0 || ymax > 1) return false;
        if (xmax < 0 || xmax > 1) return false;

        // Check if min < max
        if (ymin >= ymax) return false;
        if (xmin >= xmax) return false;

        // Check area (skip very small detections)
        float area = (ymax - ymin) * (xmax - xmin);
        if (area < 0.01) return false; // Skip boxes smaller than 1% of image

        return true;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inputSize * inputSize];
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize);

        for (int pixel : pixels) {
            // COCO SSD MobileNet expects RGB values in range 0-255
            buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
            buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
            buffer.put((byte) (pixel & 0xFF));         // Blue
        }

        buffer.rewind();
        return buffer;
    }

    private List<String> loadLabels(Context context) throws IOException {
        List<String> labelsList = new ArrayList<>();

        InputStream is = context.getAssets().open(LABEL_FILE);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;

        int index = 0;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
                labelsList.add(line);
                Log.d(TAG, "Label " + index + ": " + line);
                index++;
            }
        }
        reader.close();

        // Verify we have exactly 80 classes (COCO has 80 classes)
        if (labelsList.size() != 80) {
            Log.e(TAG, "⚠️ WARNING: Expected 80 labels for COCO, but got " + labelsList.size());
        }

        return labelsList;
    }

    private MappedByteBuffer loadModelFile(Context context, String modelFile) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(modelFile);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel fc = fis.getChannel();
        long startOffset = fd.getStartOffset();
        long declaredLength = fd.getDeclaredLength();
        return fc.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        isReady = false;
    }
}