package com.example.smartattendancesystem;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.DataType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.Arrays;

public class FaceRecognitionHelper {

    private static final String TAG = "FaceRecognitionHelper";
    private Interpreter tflite; // MADE NON-STATIC
    private ImageProcessor imageProcessor; // MADE NON-STATIC
    private int inputImageWidth;
    private int inputImageHeight;
    private int outputEmbeddingSize; // MADE NON-STATIC

    public FaceRecognitionHelper(Context context, String modelPath) throws IOException {
        try {
            MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(context, modelPath);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2); // You can adjust thread count based on device capabilities
            tflite = new Interpreter(tfliteModel, options);

            int[] inputShape = tflite.getInputTensor(0).shape();
            // Input shape is typically [BATCH_SIZE, HEIGHT, WIDTH, CHANNELS]
            // For a single image, BATCH_SIZE will be 1.
            inputImageHeight = inputShape[1]; // Height is at index 1
            inputImageWidth = inputShape[2];  // Width is at index 2

            int[] outputShape = tflite.getOutputTensor(0).shape();
            // Output shape is typically [BATCH_SIZE, EMBEDDING_SIZE]
            outputEmbeddingSize = outputShape[1]; // Embedding size is at index 1

            Log.d(TAG, "TFLite Model Loaded: Input shape = " + Arrays.toString(inputShape) +
                    ", Output shape = " + Arrays.toString(outputShape) +
                    ", Embedding Size = " + outputEmbeddingSize);

            // Image processor for preprocessing the input bitmap
            imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
                    // CHANGE TO THIS:
                    .add(new NormalizeOp(127.5f, 127.5f)) // Normalize from [0, 255] to [-1, 1] as indicated by your model's graph
                    .build();

        } catch (IOException e) {
            Log.e(TAG, "Error loading TFLite model from assets: " + modelPath, e);
            throw e; // Re-throw to indicate critical failure
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TFLite interpreter or image processor.", e);
            throw new IOException("Failed to initialize TFLite components.", e); // Wrap other exceptions
        }
    }

    public float[] getFaceEmbedding(Bitmap faceBitmap) { // MADE NON-STATIC
        if (tflite == null || faceBitmap == null) {
            Log.e(TAG, "TFLite Interpreter is not initialized or input bitmap is null.");
            return null;
        }

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32); // Ensure model input is FLOAT32
        tensorImage.load(faceBitmap);
        tensorImage = imageProcessor.process(tensorImage);

        // Allocate output buffer based on the expected embedding size
        ByteBuffer outputBuffer = ByteBuffer.allocateDirect(4 * outputEmbeddingSize); // 4 bytes per float
        outputBuffer.order(ByteOrder.nativeOrder()); // Use native byte order

        try {
            // Run inference
            tflite.run(tensorImage.getBuffer(), outputBuffer);
        } catch (Exception e) {
            Log.e(TAG, "Error running TFLite inference: " + e.getMessage(), e);
            return null;
        }

        outputBuffer.rewind(); // Reset buffer position to read from the beginning
        float[] embedding = new float[outputEmbeddingSize];
        outputBuffer.asFloatBuffer().get(embedding); // Read floats into the array

        return embedding;
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null; // Set to null after closing
            Log.d(TAG, "TFLite Interpreter closed.");
        }
    }

    /**
     * Calculates the Euclidean distance between two face embeddings.
     * Smaller values indicate higher similarity.
     */
    public double calculateEuclideanDistance(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null) {
            throw new IllegalArgumentException("Embeddings cannot be null.");
        }
        if (embedding1.length != embedding2.length) {
            throw new IllegalArgumentException("Embeddings must have the same dimension for comparison.");
        }
        double sumSquaredDiff = 0;
        for (int i = 0; i < embedding1.length; i++) {
            sumSquaredDiff += Math.pow(embedding1[i] - embedding2[i], 2);
        }
        return Math.sqrt(sumSquaredDiff);
    }

    /**
     * Calculates the Cosine Similarity between two face embeddings.
     * Values closer to 1.0 indicate higher similarity.
     */
    public double calculateCosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null) {
            throw new IllegalArgumentException("Embeddings cannot be null.");
        }
        if (embedding1.length != embedding2.length) {
            throw new IllegalArgumentException("Embeddings must have the same dimension for comparison.");
        }
        double dotProduct = 0.0;
        double normA = 0.0; // Magnitude of embedding1
        double normB = 0.0; // Magnitude of embedding2
        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            normA += Math.pow(embedding1[i], 2);
            normB += Math.pow(embedding2[i], 2);
        }
        // Avoid division by zero
        if (normA == 0.0 || normB == 0.0) {
            return 0.0; // Or handle as an error, depending on desired behavior
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}