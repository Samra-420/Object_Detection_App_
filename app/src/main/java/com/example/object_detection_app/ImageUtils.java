package com.example.object_detection_app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ImageUtils {

    private static final String TAG = "ImageUtils";

    /**
     * Convert ImageProxy to Bitmap
     * Handles YUV_420_888 format (standard for CameraX)
     */
    @ExperimentalGetImage
    public static Bitmap imageProxyToBitmap(@NonNull ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) {
                return null;
            }

            // Check image format
            int format = image.getFormat();

            if (format == ImageFormat.YUV_420_888) {
                return convertYUV420ToBitmap(image, imageProxy.getImageInfo().getRotationDegrees());
            } else {
                Log.w(TAG, "Unsupported image format: " + format);
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert ImageProxy to Bitmap", e);
            return null;
        }
    }

    /**
     * Convert YUV_420_888 Image to Bitmap with rotation handling
     */
    private static Bitmap convertYUV420ToBitmap(@NonNull Image image, int rotationDegrees) {
        try {
            Image.Plane[] planes = image.getPlanes();

            if (planes.length < 3) {
                Log.e(TAG, "Invalid YUV image planes: " + planes.length);
                return null;
            }

            // Get Y, U, and V planes
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            // Create NV21 byte array
            byte[] nv21 = new byte[ySize + uSize + vSize];

            // Copy Y plane
            yBuffer.get(nv21, 0, ySize);

            // For NV21 format: YYYY... UVUV...
            // CameraX provides Y, U, V separately, we need to interleave U and V

            int width = image.getWidth();
            int height = image.getHeight();

            // Get the UV pixel stride
            int uvPixelStride = planes[1].getPixelStride();
            int uvRowStride = planes[1].getRowStride();

            // Process U and V planes
            byte[] u = new byte[uSize];
            byte[] v = new byte[vSize];
            uBuffer.get(u);
            vBuffer.get(v);

            // Interleave U and V for NV21 format
            int uvOffset = ySize;
            if (uvPixelStride == 1) {
                // UV is already interleaved in some cases
                for (int i = 0; i < uSize; i++) {
                    nv21[uvOffset++] = v[i];
                    nv21[uvOffset++] = u[i];
                }
            } else {
                // Handle different UV formats
                for (int row = 0; row < height / 2; row++) {
                    for (int col = 0; col < width / 2; col++) {
                        int uvIndex = row * uvRowStride + col * uvPixelStride;
                        nv21[uvOffset++] = v[uvIndex];
                        nv21[uvOffset++] = u[uvIndex];
                    }
                }
            }

            // Create YuvImage
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);

            // Convert to JPEG bytes
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Rect rect = new Rect(0, 0, width, height);

            yuvImage.compressToJpeg(rect, 90, outputStream); // 90% quality
            byte[] jpegBytes = outputStream.toByteArray();

            // Decode JPEG to Bitmap
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);

            // Apply rotation if needed
            if (rotationDegrees != 0 && bitmap != null) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                bitmap = Bitmap.createBitmap(
                        bitmap,
                        0, 0,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        matrix,
                        true
                );
            }

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "YUV to Bitmap conversion failed", e);
            return null;
        }
    }

    /**
     * Alternative: Direct conversion without NV21 intermediate (more efficient)
     */
    @ExperimentalGetImage
    public static Bitmap imageProxyToBitmapDirect(@NonNull ImageProxy imageProxy) {
        try {
            Bitmap bitmap = imageProxy.toBitmap();

            // Apply rotation if needed
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            if (rotation != 0 && bitmap != null) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                bitmap = Bitmap.createBitmap(
                        bitmap,
                        0, 0,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        matrix,
                        true
                );
            }

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "Direct conversion failed", e);
            return null;
        }
    }

    /**
     * Rotate bitmap if needed
     */
    public static Bitmap rotateBitmap(Bitmap source, float angle) {
        if (source == null) return null;

        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(
                source,
                0, 0,
                source.getWidth(),
                source.getHeight(),
                matrix,
                true
        );
    }

    /**
     * Scale bitmap while maintaining aspect ratio
     */
    public static Bitmap scaleBitmap(Bitmap source, int maxWidth, int maxHeight) {
        if (source == null) return null;

        int width = source.getWidth();
        int height = source.getHeight();

        // Calculate scaling factor
        float scale = Math.min(
                (float) maxWidth / width,
                (float) maxHeight / height
        );

        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);

        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
    }
}