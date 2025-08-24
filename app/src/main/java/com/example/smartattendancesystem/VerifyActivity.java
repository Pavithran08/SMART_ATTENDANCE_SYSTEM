package com.example.smartattendancesystem;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.os.Handler; // Import Handler
import android.os.Looper; // Import Looper
import android.util.Log;
import android.view.View; // Import View
import android.widget.Button;
import android.widget.ProgressBar; // Import ProgressBar
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VerifyActivity extends AppCompatActivity {

    private static final String TAG = "VerifyActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private PreviewView previewView;
    private TextView txtVerificationResult;
    private Button btnCaptureAndVerify;
    private ProgressBar loadingSpinner; // Declare ProgressBar

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor; // For CameraX ImageAnalysis
    private ExecutorService mlKitExecutorService; // For ML Kit face detection & TFLite inference
    private FaceRecognitionHelper faceRecognitionHelper;
    private String currentUserId;
    private String userMatricOrStaffId; // To store the matric/staff ID received from LoginActivity
    private float[] storedFaceEmbedding; // The reference embedding for comparison
    private boolean isVerifying = false; // Flag to control live verification process
    private boolean isInitialFaceProcessingDone = false; // Flag for initial password-login face processing

    private boolean hasNavigatedToSuccess = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Get UserId from FirebaseAuth if available, otherwise from intent (as fallback)
        if (mAuth.getCurrentUser() != null) {
            currentUserId = mAuth.getCurrentUser().getUid();
            Log.d(TAG, "User ID from FirebaseAuth: " + currentUserId);
        } else {
            currentUserId = getIntent().getStringExtra("USER_ID");
            if (currentUserId == null || currentUserId.isEmpty()) {
                Toast.makeText(this, "User ID not available. Please log in again.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            Log.d(TAG, "User ID received from intent: " + currentUserId);
        }

        userMatricOrStaffId = getIntent().getStringExtra("USER_MATRIC_OR_STAFF_ID");
        if (userMatricOrStaffId == null || userMatricOrStaffId.isEmpty()) {
            Toast.makeText(this, "User role information not available. Please log in again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Log.d(TAG, "User Matric/Staff ID received: " + userMatricOrStaffId);

        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .build();
        faceDetector = FaceDetection.getClient(options);

        try {
            faceRecognitionHelper = new FaceRecognitionHelper(this, "output_model.tflite");
            Log.d(TAG, "FaceRecognitionHelper initialized successfully.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize FaceRecognitionHelper: " + e.getMessage(), e);
            Toast.makeText(this, "Error: Face recognition model not loaded. Cannot perform verification.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        previewView = findViewById(R.id.preview_view);
        txtVerificationResult = findViewById(R.id.txt_verification_result);
        btnCaptureAndVerify = findViewById(R.id.btn_verify);
        loadingSpinner = findViewById(R.id.loading_spinner_verify); // Initialize ProgressBar

        cameraExecutor = Executors.newSingleThreadExecutor();
        mlKitExecutorService = Executors.newSingleThreadExecutor();

        btnCaptureAndVerify.setOnClickListener(v -> {
            if (storedFaceEmbedding == null && !isInitialFaceProcessingDone) {
                Toast.makeText(this, "User face data is still being loaded or processed. Please wait.", Toast.LENGTH_LONG).show();
                return;
            }
            if (!isVerifying) {
                isVerifying = true; // Set flag to true to start processing frames
                txtVerificationResult.setText("Scanning for face...");
                txtVerificationResult.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                Toast.makeText(this, "Verification started. Look at the camera.", Toast.LENGTH_SHORT).show();
                showLoading(true); // Show spinner when verification starts
            } else {
                Toast.makeText(this, "Verification already in progress. Please wait.", Toast.LENGTH_SHORT).show();
            }
        });

        byte[] liveFaceBitmapByteArray = getIntent().getByteArrayExtra("LIVE_FACE_BITMAP_BYTE_ARRAY");
        if (liveFaceBitmapByteArray != null && liveFaceBitmapByteArray.length > 0) {
            Toast.makeText(this, "Processing captured face from login...", Toast.LENGTH_SHORT).show();
            isInitialFaceProcessingDone = false;
            showLoading(true); // Show spinner while processing initial login face
            processAndStoreLiveFaceFromLogin(liveFaceBitmapByteArray);
        } else {
            // If no live face bitmap from login, load the existing stored embedding from Firestore.
            retrieveStoredFaceEmbedding(currentUserId);
        }

        requestCameraPermission();
    }

    // Helper method to control loading spinner visibility and button state
    private void showLoading(boolean show) {
        runOnUiThread(() -> { // Ensure UI updates are on the main thread
            if (show) {
                loadingSpinner.setVisibility(View.VISIBLE);
                btnCaptureAndVerify.setEnabled(false);
            } else {
                loadingSpinner.setVisibility(View.GONE);
                btnCaptureAndVerify.setEnabled(true);
            }
        });
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required for face verification.", Toast.LENGTH_LONG).show();
                showLoading(false); // Hide spinner if camera permission denied
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e(TAG, "Error starting camera: " + e.getMessage(), e);
                Toast.makeText(this, "Error starting camera.", Toast.LENGTH_SHORT).show();
                showLoading(false); // Hide spinner if camera fails to start
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (hasNavigatedToSuccess) {
                imageProxy.close();
                return;
            }

            if (isVerifying && storedFaceEmbedding != null) {
                Bitmap bitmap = imageProxyToBitmap(imageProxy);
                if (bitmap == null) {
                    return;
                }

                InputImage inputImage = InputImage.fromBitmap(bitmap, imageProxy.getImageInfo().getRotationDegrees());

                faceDetector.process(inputImage)
                        .addOnSuccessListener(faces -> {
                            if (hasNavigatedToSuccess) {
                                if (bitmap != null && !bitmap.isRecycled()) {
                                    bitmap.recycle();
                                }
                                return;
                            }

                            if (!faces.isEmpty()) {
                                Face liveFace = faces.get(0);
                                if (isLive(liveFace)) {
                                    Bitmap croppedLiveFace = cropFaceFromBitmap(bitmap, liveFace.getBoundingBox());
                                    if (croppedLiveFace != null) {
                                        mlKitExecutorService.execute(() -> {
                                            float[] liveFaceEmbedding = faceRecognitionHelper.getFaceEmbedding(croppedLiveFace);
                                            if (croppedLiveFace != null && !croppedLiveFace.isRecycled()) {
                                                croppedLiveFace.recycle();
                                            }
                                            runOnUiThread(() -> {
                                                if (hasNavigatedToSuccess) {
                                                    return;
                                                }
                                                if (liveFaceEmbedding != null) {
                                                    compareFaceEmbeddings(liveFaceEmbedding);
                                                } else {
                                                    Log.e(TAG, "Failed to get live face embedding.");
                                                    updateVerificationResult("Failed to process face data.");
                                                    isVerifying = false;
                                                    showLoading(false); // Hide spinner on failure
                                                }
                                            });
                                        });
                                    } else {
                                        Log.e(TAG, "Failed to crop live face.");
                                        updateVerificationResult("Failed to process face.");
                                        // Keep verifying, no immediate stop
                                    }
                                } else {
                                    updateVerificationResult("Please show a live face (e.g., blink or move slightly).");
                                    // Keep verifying
                                }
                            } else {
                                updateVerificationResult("No face detected. Please center your face.");
                                // Keep verifying
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "ML Kit Face Detection error: " + e.getMessage(), e);
                            updateVerificationResult("Error detecting face.");
                            isVerifying = false;
                            showLoading(false); // Hide spinner on ML Kit error
                            if (bitmap != null && !bitmap.isRecycled()) {
                                bitmap.recycle();
                            }
                        });
            } else {
                imageProxy.close();
            }
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
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

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 90, out);
            byte[] imageBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap: " + e.getMessage(), e);
            return null;
        } finally {
            image.close();
        }
    }

    private Bitmap cropFaceFromBitmap(Bitmap sourceBitmap, Rect boundingBox) {
        int x = Math.max(0, boundingBox.left);
        int y = Math.max(0, boundingBox.top);
        int width = Math.min(boundingBox.width(), sourceBitmap.getWidth() - x);
        int height = Math.min(boundingBox.height(), sourceBitmap.getHeight() - y);

        if (width <= 0 || height <= 0) {
            Log.e(TAG, "Invalid bounding box dimensions for cropping. BBox: " + boundingBox.toString() +
                    ", Bitmap size: " + sourceBitmap.getWidth() + "x" + sourceBitmap.getHeight());
            return null;
        }

        try {
            return Bitmap.createBitmap(sourceBitmap, x, y, width, height);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error cropping bitmap: " + e.getMessage(), e);
            return null;
        }
    }

    private boolean isLive(Face face) {
        Float leftEyeOpenProb = face.getLeftEyeOpenProbability();
        Float rightEyeOpenProb = face.getRightEyeOpenProbability();
        boolean eyesAppearOpen = (leftEyeOpenProb == null || leftEyeOpenProb > 0.15) &&
                (rightEyeOpenProb == null || rightEyeOpenProb > 0.15);

        Float headEulerY = face.getHeadEulerAngleY();
        Float headEulerZ = face.getHeadEulerAngleZ();

        boolean headWithinReasonableRange = (headEulerY == null || Math.abs(headEulerY) < 30) &&
                (headEulerZ == null || Math.abs(headEulerZ) < 30);

        return eyesAppearOpen && headWithinReasonableRange;
    }

    private void updateVerificationResult(String message) {
        runOnUiThread(() -> txtVerificationResult.setText(message));
    }

    private void compareFaceEmbeddings(float[] liveFaceEmbedding) {
        if (hasNavigatedToSuccess) {
            return;
        }

        if (storedFaceEmbedding == null) {
            updateVerificationResult("Error: Stored face data not available for comparison.");
            isVerifying = false;
            showLoading(false); // Hide spinner on error
            return;
        }
        if (liveFaceEmbedding == null) {
            updateVerificationResult("Error: Live face embedding not generated.");
            isVerifying = false;
            showLoading(false); // Hide spinner on error
            return;
        }
        if (faceRecognitionHelper == null) {
            updateVerificationResult("Error: Face recognition helper not initialized.");
            isVerifying = false;
            showLoading(false); // Hide spinner on error
            return;
        }

        double similarity = faceRecognitionHelper.calculateCosineSimilarity(liveFaceEmbedding, storedFaceEmbedding);
        Log.d(TAG, "Cosine Similarity: " + similarity);

        final double THRESHOLD = 0.02;

        if (similarity > THRESHOLD) {
            updateVerificationResult("Faces Match! Similarity: " + String.format("%.2f", similarity) + "\nVerification Successful!");
            txtVerificationResult.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            isVerifying = false;
            navigateToSuccessActivity(); // Will set hasNavigatedToSuccess = true
        } else {
            updateVerificationResult("Faces Do Not Match! Similarity: " + String.format("%.2f", similarity) + "\nPlease try again.");
            txtVerificationResult.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            isVerifying = false;
            showLoading(false); // Hide spinner on mismatch, allow retry
        }
    }

    private void navigateToSuccessActivity() {
        hasNavigatedToSuccess = true; // Set the flag immediately

        Intent intent = new Intent(VerifyActivity.this, VerificationSuccessActivity.class);
        intent.putExtra("USER_MATRIC_OR_STAFF_ID", userMatricOrStaffId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void processAndStoreLiveFaceFromLogin(byte[] liveFaceBitmapByteArray) {
        Bitmap liveBitmap = BitmapFactory.decodeByteArray(liveFaceBitmapByteArray, 0, liveFaceBitmapByteArray.length);
        if (liveBitmap == null) {
            Toast.makeText(this, "Failed to decode live face image from login.", Toast.LENGTH_SHORT).show();
            isInitialFaceProcessingDone = true;
            showLoading(false); // Hide spinner on decode failure
            finish();
            return;
        }

        InputImage image = InputImage.fromBitmap(liveBitmap, 0);

        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    if (!faces.isEmpty()) {
                        Face face = faces.get(0);
                        Bitmap croppedLoginFace = cropFaceFromBitmap(liveBitmap, face.getBoundingBox());
                        if (croppedLoginFace != null) {
                            mlKitExecutorService.execute(() -> {
                                float[] newLoginFaceEmbedding = faceRecognitionHelper.getFaceEmbedding(croppedLoginFace);
                                if (croppedLoginFace != null && !croppedLoginFace.isRecycled()) {
                                    croppedLoginFace.recycle();
                                }
                                runOnUiThread(() -> {
                                    if (newLoginFaceEmbedding != null) {
                                        storedFaceEmbedding = newLoginFaceEmbedding;
                                        isInitialFaceProcessingDone = true;
                                        Log.d(TAG, "New login face embedding set as storedFaceEmbedding.");
                                        Toast.makeText(VerifyActivity.this, "Your login face has been updated. Click VERIFY to proceed.", Toast.LENGTH_LONG).show();

                                        saveLiveFaceDataToFirestoreAndStorage(liveBitmap, currentUserId, newLoginFaceEmbedding);
                                        if (!liveBitmap.isRecycled()) {
                                            liveBitmap.recycle();
                                        }
                                        showLoading(false); // Hide spinner after initial processing completes
                                    } else {
                                        Toast.makeText(VerifyActivity.this, "Failed to generate embedding for login face.", Toast.LENGTH_SHORT).show();
                                        Log.e(TAG, "Failed to generate embedding for login face after capture.");
                                        isInitialFaceProcessingDone = true;
                                        if (!liveBitmap.isRecycled()) {
                                            liveBitmap.recycle();
                                        }
                                        showLoading(false); // Hide spinner on embedding failure
                                        finish();
                                    }
                                });
                            });
                        } else {
                            Toast.makeText(this, "Failed to crop face from login image.", Toast.LENGTH_SHORT).show();
                            isInitialFaceProcessingDone = true;
                            if (!liveBitmap.isRecycled()) {
                                liveBitmap.recycle();
                            }
                            showLoading(false); // Hide spinner on crop failure
                            finish();
                        }
                    } else {
                        Toast.makeText(this, "No face detected in login image. Please try again from login.", Toast.LENGTH_LONG).show();
                        isInitialFaceProcessingDone = true;
                        if (!liveBitmap.isRecycled()) {
                            liveBitmap.recycle();
                        }
                        showLoading(false); // Hide spinner if no face detected
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "ML Kit Face Detection failed for login image: " + e.getMessage(), e);
                    Toast.makeText(this, "Error processing login face for verification.", Toast.LENGTH_SHORT).show();
                    isInitialFaceProcessingDone = true;
                    if (!liveBitmap.isRecycled()) {
                        liveBitmap.recycle();
                    }
                    showLoading(false); // Hide spinner on ML Kit processing failure
                    finish();
                });
    }

    private void saveLiveFaceDataToFirestoreAndStorage(Bitmap photo, String userId, float[] embedding) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        photo.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] data = baos.toByteArray();

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("users/" + userId + "/face_login_image.jpg");

        storageRef.putBytes(data)
                .addOnSuccessListener(taskSnapshot -> {
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();
                        Log.d(TAG, "Face login image uploaded to Storage: " + downloadUrl);

                        List<Double> embeddingList = new ArrayList<>();
                        for (float val : embedding) {
                            embeddingList.add((double) val);
                        }

                        db.collection("users").document(userId)
                                .update("faceImageLoginUrl", downloadUrl, "faceEmbeddingForLogin", embeddingList)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Firestore updated with new login face data.");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error updating Firestore with login face data: " + e.getMessage(), e);
                                    Toast.makeText(VerifyActivity.this, "Failed to save login face data to Firestore.", Toast.LENGTH_SHORT).show();
                                    showLoading(false); // Hide spinner on Firestore update failure
                                });
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get download URL for login face image: " + e.getMessage(), e);
                        Toast.makeText(VerifyActivity.this, "Failed to get image URL from Storage.", Toast.LENGTH_SHORT).show();
                        showLoading(false); // Hide spinner on URL retrieval failure
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload login face image to Storage: " + e.getMessage(), e);
                    Toast.makeText(VerifyActivity.this, "Failed to upload login image to Storage.", Toast.LENGTH_SHORT).show();
                    showLoading(false); // Hide spinner on storage upload failure
                });
    }

    private void retrieveStoredFaceEmbedding(String userId) {
        showLoading(true); // Show spinner while retrieving stored embedding
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<Double> embeddingList = (List<Double>) documentSnapshot.get("faceEmbeddingForLogin");
                        if (embeddingList == null) {
                            embeddingList = (List<Double>) documentSnapshot.get("faceEmbedding");
                            if (embeddingList != null) {
                                Log.w(TAG, "Using 'faceEmbedding' as 'faceEmbeddingForLogin' was not found for user: " + userId);
                                Toast.makeText(this, "Using existing primary face data. Consider updating login face.", Toast.LENGTH_LONG).show();
                            }
                        }

                        if (embeddingList != null && !embeddingList.isEmpty()) {
                            storedFaceEmbedding = new float[embeddingList.size()];
                            for (int i = 0; i < embeddingList.size(); i++) {
                                storedFaceEmbedding[i] = embeddingList.get(i).floatValue();
                            }
                            Log.d(TAG, "Stored face embedding loaded successfully for user: " + userId);
                            Toast.makeText(this, "User face data loaded. Click VERIFY to proceed.", Toast.LENGTH_SHORT).show();
                            isInitialFaceProcessingDone = true;
                            showLoading(false); // Hide spinner after loading embedding
                        } else {
                            Log.e(TAG, "No face embedding found in Firestore for user: " + userId + ". Cannot perform verification.");
                            Toast.makeText(this, "No registered face data found. Cannot verify.", Toast.LENGTH_LONG).show();
                            showLoading(false); // Hide spinner on no embedding found
                            finish();
                        }
                    } else {
                        Toast.makeText(this, "User data not found in Firestore. Please register or log in.", Toast.LENGTH_LONG).show();
                        showLoading(false); // Hide spinner if user data not found
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error retrieving user data from Firestore: " + e.getMessage(), e);
                    Toast.makeText(this, "Error retrieving user data for verification: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    showLoading(false); // Hide spinner on retrieval error
                    finish();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (mlKitExecutorService != null) {
            mlKitExecutorService.shutdown();
        }
        if (faceDetector != null) {
            faceDetector.close();
        }
        if (faceRecognitionHelper != null) {
            faceRecognitionHelper.close();
        }
    }
}
