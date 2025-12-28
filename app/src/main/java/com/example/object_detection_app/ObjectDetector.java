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

    // COCO has 91 classes (including background at index 0)
    private static final int NUM_CLASSES = 91;
    private static final int NUM_DETECTIONS = 10;

    // Confidence threshold - LOW for testing
    private static final float MIN_CONFIDENCE = 0.15f;

    private Interpreter tflite;
    private List<String> labels = new ArrayList<>();

    // Output buffers
    private float[][][] outputLocations;
    private float[][] outputClasses;
    private float[][] outputScores;
    private float[] numDetections;

    private ByteBuffer imgData;

    public ObjectDetector(Context context) throws IOException {
        // Load model
        MappedByteBuffer modelBuffer = loadModelFile(context);

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        tflite = new Interpreter(modelBuffer, options);

        // Load labels
        labels = loadLabelList(context);

        // Initialize input buffer (quantized model: uint8)
        imgData = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3);
        imgData.order(ByteOrder.nativeOrder());

        // Initialize output arrays
        outputLocations = new float[1][NUM_DETECTIONS][4];
        outputClasses = new float[1][NUM_DETECTIONS];
        outputScores = new float[1][NUM_DETECTIONS];
        numDetections = new float[1];

        Log.d(TAG, "✅ ObjectDetector initialized");
        Log.d(TAG, "📊 Total labels: " + labels.size());

        // Test: Print some important labels with indices
        int[] importantIndices = {0, 1, 14, 56, 60, 62, 63, 67, 73};
        for (int idx : importantIndices) {
            if (idx < labels.size()) {
                Log.d(TAG, String.format("Label [%d]: %s", idx, labels.get(idx)));
            }
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabelList(Context context) throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(LABEL_FILE)));
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
                labelList.add(line);
            }
        }
        reader.close();

        // COCO has 91 classes. If we have fewer, pad with placeholder names
        if (labelList.size() < NUM_CLASSES) {
            Log.w(TAG, "⚠️ Label file has only " + labelList.size() + " labels, expected " + NUM_CLASSES);
            for (int i = labelList.size(); i < NUM_CLASSES; i++) {
                labelList.add("class_" + i);
            }
        }

        return labelList;
    }

    public List<Recognition> recognizeImage(Bitmap bitmap) {
        // Resize to model input
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                bitmap, INPUT_SIZE, INPUT_SIZE, true);

        // Convert to byte buffer
        convertBitmapToByteBuffer(resizedBitmap);

        // Run inference
        long startTime = SystemClock.elapsedRealtime();
        runInference();
        long endTime = SystemClock.elapsedRealtime();
        Log.d(TAG, "⚡ Inference time: " + (endTime - startTime) + " ms");

        return getRecognitions();
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

        // DEBUG: Log ALL detections
        int detections = Math.min(NUM_DETECTIONS, (int) numDetections[0]);
        Log.d(TAG, "🔍 Model returned " + detections + " detections");

        for (int i = 0; i < detections; i++) {
            float confidence = outputScores[0][i];
            int classId = (int) outputClasses[0][i];
            String labelName = "unknown";
            if (classId >= 0 && classId < labels.size()) {
                labelName = labels.get(classId);
            }

            if (confidence > 0.05f) { // Log all >5% confidence
                Log.d(TAG, String.format("  [%d] Class=%d (%s) Conf=%.1f%%",
                        i, classId, labelName, confidence * 100));
            }
        }
    }

    private List<Recognition> getRecognitions() {
        List<Recognition> recognitions = new ArrayList<>();

        int numDetectionsValue = Math.min(NUM_DETECTIONS, (int) numDetections[0]);

        for (int i = 0; i < numDetectionsValue; i++) {
            float confidence = outputScores[0][i];

            if (confidence > MIN_CONFIDENCE) {
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

                // Skip invalid boxes
                if (xmax <= xmin || ymax <= ymin) {
                    continue;
                }

                // Get class
                int classId = (int) outputClasses[0][i];
                String label = "unknown";
                if (classId >= 0 && classId < labels.size()) {
                    label = labels.get(classId);
                }

                // Skip "???" and background
                if (label.equals("???") || classId == 0) {
                    continue;
                }

                // Convert to pixels
                float left = xmin * INPUT_SIZE;
                float top = ymin * INPUT_SIZE;
                float right = xmax * INPUT_SIZE;
                float bottom = ymax * INPUT_SIZE;

                RectF location = new RectF(left, top, right, bottom);
                Recognition recognition = new Recognition(
                        String.valueOf(i), label, confidence, location);

                recognitions.add(recognition);

                Log.d(TAG, String.format("✅ %s (%.1f%%) [ClassId=%d]",
                        label, confidence * 100, classId));
            }
        }

        // Sort by confidence
        Collections.sort(recognitions, new Comparator<Recognition>() {
            @Override
            public int compare(Recognition r1, Recognition r2) {
                return Float.compare(r2.getConfidence(), r1.getConfidence());
            }
        });

        Log.d(TAG, "📊 Final detections: " + recognitions.size());
        return recognitions;
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
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

        public String getId() { return id; }
        public String getTitle() { return title; }
        public float getConfidence() { return confidence; }
        public RectF getLocation() { return new RectF(location); }

        @Override
        public String toString() {
            return String.format("%s (%.1f%%)", title, confidence * 100);
        }
    }
}