package com.example.smartattendancesystem;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton; // Make sure this is imported
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAPTURE_LOGIN_FACE_REQUEST = 2;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private EditText nameField, studentMatricField, emailField, passwordField;
    private ImageView profileImageView;
    private TextView studentUsernameText, studentMatricOrEmailText;
    private Button updateButton, btnCaptureLoginFace;
    private ImageButton btnLogoutCorner; // Changed from Button to ImageButton and id name
    private ImageButton backButton, homeButton, profileButton;

    private Uri selectedImageUri;

    private FaceDetector faceDetector;
    private FaceRecognitionHelper faceRecognitionHelper;
    private ExecutorService mlKitExecutorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.student_profile);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // Initialize ML Kit Face Detector
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        faceDetector = FaceDetection.getClient(options);

        // Initialize FaceRecognitionHelper
        try {
            faceRecognitionHelper = new FaceRecognitionHelper(this, "output_model.tflite");
            Log.d(TAG, "FaceRecognitionHelper initialized successfully.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize FaceRecognitionHelper: " + e.getMessage());
            Toast.makeText(this, "Error: Face recognition model not loaded. Face capture disabled.", Toast.LENGTH_LONG).show();
        }

        mlKitExecutorService = Executors.newSingleThreadExecutor();

        // Initialize UI components
        nameField = findViewById(R.id.studentNameField);
        studentMatricField = findViewById(R.id.studentMatricField);
        emailField = findViewById(R.id.studentEmailField);
        passwordField = findViewById(R.id.studentPasswordField);
        updateButton = findViewById(R.id.btn_update_student_profile);
        profileImageView = findViewById(R.id.studentProfileImage);
        studentUsernameText = findViewById(R.id.studentUsernameText);
        studentMatricOrEmailText = findViewById(R.id.studentMatricOrEmail);
        btnCaptureLoginFace = findViewById(R.id.btn_capture_login_face);
        btnLogoutCorner = findViewById(R.id.btn_logout_corner); // Initialize the new corner logout button

        // Setup bottom navigation buttons
        setupBottomNavigation();

        // Load user details from Firestore and display
        loadUserDetails();

        // Handle update button click
        updateButton.setOnClickListener(v -> updateUserDetails());

        // Handle profile image click to select a general profile image from the gallery
        profileImageView.setOnClickListener(v -> openGallery());

        // Handle Capture/Update Login Face button click
        btnCaptureLoginFace.setOnClickListener(v -> captureLoginFace());

        // Handle Logout button click for the corner button
        btnLogoutCorner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performLogout();
            }
        });
    }

    private void setupBottomNavigation() {
        backButton = findViewById(R.id.studentProfileBackButton);
        homeButton = findViewById(R.id.studentProfileHomeButton);
        profileButton = findViewById(R.id.studentProfileProfileButton);

        backButton.setOnClickListener(v -> {
            onBackPressed();
        });

        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, StudentActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        profileButton.setOnClickListener(v -> {
            Toast.makeText(ProfileActivity.this, "You are already on the Profile screen.", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserDetails();
    }

    private void loadUserDetails() {
        String currentUserIdFromAuth = null;
        if (mAuth.getCurrentUser() != null) {
            currentUserIdFromAuth = mAuth.getCurrentUser().getUid();
        }

        final String finalUserId = currentUserIdFromAuth;

        if (finalUserId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        DocumentReference userRef = db.collection("users").document(finalUserId);

        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    String email = document.getString("email");
                    String username = document.getString("username");
                    String matricOrStaffId = document.getString("matricOrStaffId");

                    nameField.setText(username);
                    studentMatricField.setText(matricOrStaffId);
                    emailField.setText(email);

                    passwordField.setText("");
                    passwordField.setHint("Enter new password to change");
                    passwordField.setEnabled(true);

                    studentUsernameText.setText(username);
                    studentMatricOrEmailText.setText(matricOrStaffId + " / " + email);

                    String profileImageUrl = document.getString("profileImageUrl");
                    if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                        Picasso.get().load(profileImageUrl).into(profileImageView);
                    } else {
                        profileImageView.setImageResource(R.drawable.ic_profile_default);
                    }

                    String faceImageLoginUrl = document.getString("faceImageLoginUrl");
                    if (faceImageLoginUrl == null || faceImageLoginUrl.isEmpty()) {
                        btnCaptureLoginFace.setText("Capture Face for Login");
                        Toast.makeText(ProfileActivity.this, "No login face registered. Please capture one.", Toast.LENGTH_LONG).show();
                    } else {
                        btnCaptureLoginFace.setText("Update Login Face");
                    }
                } else {
                    Log.w(TAG, "No such user document for ID: " + finalUserId);
                    Toast.makeText(ProfileActivity.this, "User data not found.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.w(TAG, "Error getting user details.", task.getException());
                Toast.makeText(ProfileActivity.this, "Error loading profile data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUserDetails() {
        String userId = null;
        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        }

        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = emailField.getText().toString().trim();
        String username = nameField.getText().toString().trim();
        String matricOrStaffId = studentMatricField.getText().toString().trim();
        String newPassword = passwordField.getText().toString().trim();

        if (username.isEmpty() || matricOrStaffId.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Username, Matric/Staff ID, and Email are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference userRef = db.collection("users").document(userId);

        if (!email.equals(mAuth.getCurrentUser().getEmail())) {
            mAuth.getCurrentUser().updateEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "User email address updated in Auth.");
                            performFirestoreUpdateAndPasswordChange(userRef, username, matricOrStaffId, email, newPassword);
                        } else {
                            Log.e(TAG, "Error updating email in Auth.", task.getException());
                            Toast.makeText(ProfileActivity.this, "Failed to update email in authentication: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            performFirestoreUpdateAndPasswordChange(userRef, username, matricOrStaffId, email, newPassword);
        }

        if (selectedImageUri != null) {
            uploadProfileImage(selectedImageUri);
        }
    }

    private void performFirestoreUpdateAndPasswordChange(DocumentReference userRef, String username, String matricOrStaffId, String email, String newPassword) {
        userRef.update("email", email, "username", username, "matricOrStaffId", matricOrStaffId)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(ProfileActivity.this, "Profile details updated in Firestore!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Firestore user details updated");

                    if (!newPassword.isEmpty()) {
                        updateFirebasePassword(newPassword);
                    } else {
                        loadUserDetails();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ProfileActivity.this, "Error updating profile details in Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.w(TAG, "Error updating Firestore user details", e);
                });
    }

    private void updateFirebasePassword(String newPassword) {
        if (newPassword.length() < 6) {
            Toast.makeText(ProfileActivity.this, "New password must be at least 6 characters long.", Toast.LENGTH_LONG).show();
            return;
        }

        if (mAuth.getCurrentUser() != null) {
            mAuth.getCurrentUser().updatePassword(newPassword)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "User password updated in Auth.");
                            Toast.makeText(ProfileActivity.this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                            passwordField.setText("");
                            loadUserDetails();
                        } else {
                            Log.e(TAG, "Error updating password in Auth.", task.getException());
                            Toast.makeText(ProfileActivity.this, "Failed to update password: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void captureLoginFace() {
        if (faceRecognitionHelper == null) {
            Toast.makeText(this, "Face recognition features not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAPTURE_LOGIN_FACE_REQUEST);
        } else {
            Toast.makeText(this, "No camera app found.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No camera app found for login face capture.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == PICK_IMAGE_REQUEST) {
                selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                        profileImageView.setImageBitmap(bitmap);
                    } catch (IOException e) {
                        Log.e(TAG, "Error loading general profile image from gallery.", e);
                        Toast.makeText(this, "Error loading image.", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (requestCode == CAPTURE_LOGIN_FACE_REQUEST) {
                Bundle extras = data.getExtras();
                Bitmap capturedBitmap = (Bitmap) extras.get("data");
                if (capturedBitmap != null) {
                    processAndSaveLoginFace(capturedBitmap);
                } else {
                    Toast.makeText(this, "Failed to capture image data for login face.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Captured bitmap is null for login face.");
                }
            }
        } else if (resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "Image capture/selection cancelled.", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadProfileImage(Uri imageUri) {
        String userId = mAuth.getCurrentUser().getUid();
        StorageReference imageRef = storage.getReference().child("profile_images/" + userId + ".jpg");

        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    taskSnapshot.getStorage().getDownloadUrl().addOnSuccessListener(uri -> {
                        String imageUrl = uri.toString();
                        DocumentReference userRef = db.collection("users").document(userId);
                        userRef.update("profileImageUrl", imageUrl)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(ProfileActivity.this, "Profile image updated!", Toast.LENGTH_SHORT).show();
                                    loadUserDetails();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(ProfileActivity.this, "Error updating profile image URL.", Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "Error updating profileImageUrl in Firestore", e);
                                });
                    }).addOnFailureListener(e -> {
                        Toast.makeText(ProfileActivity.this, "Failed to get download URL for profile image.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to get download URL for profile image", e);
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ProfileActivity.this, "Failed to upload profile image.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Profile image upload failed", e);
                });
    }

    private void processAndSaveLoginFace(Bitmap capturedBitmap) {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in. Cannot save face data.", Toast.LENGTH_SHORT).show();
            if (!capturedBitmap.isRecycled()) {
                capturedBitmap.recycle();
            }
            return;
        }

        Toast.makeText(this, "Processing face for login...", Toast.LENGTH_LONG).show();

        mlKitExecutorService.execute(() -> {
            InputImage image = InputImage.fromBitmap(capturedBitmap, 0);

            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (!faces.isEmpty()) {
                            Face face = faces.get(0);
                            Bitmap croppedFaceBitmap = cropFaceFromBitmap(capturedBitmap, face.getBoundingBox());

                            if (croppedFaceBitmap != null) {
                                float[] faceEmbedding = faceRecognitionHelper.getFaceEmbedding(croppedFaceBitmap);
                                if (!croppedFaceBitmap.isRecycled()) {
                                    croppedFaceBitmap.recycle();
                                }

                                if (faceEmbedding != null) {
                                    uploadLoginFaceToFirebaseStorage(capturedBitmap, faceEmbedding);
                                } else {
                                    runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Failed to generate face embedding.", Toast.LENGTH_SHORT).show());
                                    Log.e(TAG, "Failed to generate face embedding from captured bitmap.");
                                    if (!capturedBitmap.isRecycled()) {
                                        capturedBitmap.recycle();
                                    }
                                }
                            } else {
                                runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Failed to crop face from image.", Toast.LENGTH_SHORT).show());
                                Log.e(TAG, "Failed to crop face from captured bitmap.");
                                if (!capturedBitmap.isRecycled()) {
                                    capturedBitmap.recycle();
                                }
                            }
                        } else {
                            runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "No face detected in the picture. Please try again.", Toast.LENGTH_LONG).show());
                            Log.w(TAG, "No face detected in the captured bitmap for login.");
                            if (!capturedBitmap.isRecycled()) {
                                capturedBitmap.recycle();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Face detection failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        Log.e(TAG, "ML Kit Face Detection error for login face: " + e.getMessage(), e);
                        if (!capturedBitmap.isRecycled()) {
                            capturedBitmap.recycle();
                        }
                    });
        });
    }

    private void uploadLoginFaceToFirebaseStorage(Bitmap bitmap, float[] embedding) {
        String userId = mAuth.getCurrentUser().getUid();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] imageData = baos.toByteArray();

        String filename = "face_images_login/" + userId + ".jpg";
        StorageReference storageRef = storage.getReference().child(filename);

        storageRef.putBytes(imageData)
                .addOnSuccessListener(taskSnapshot -> {
                    taskSnapshot.getStorage().getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();
                        Log.d(TAG, "Login face image uploaded to Storage: " + downloadUrl);

                        List<Double> embeddingList = new ArrayList<>();
                        for (float f : embedding) {
                            embeddingList.add((double) f);
                        }

                        DocumentReference userRef = db.collection("users").document(userId);
                        userRef.update("faceImageLoginUrl", downloadUrl, "faceEmbeddingForLogin", embeddingList)
                                .addOnSuccessListener(aVoid -> {
                                    runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Login face updated successfully!", Toast.LENGTH_SHORT).show());
                                    Log.d(TAG, "Firestore updated with new faceImageLoginUrl and faceEmbeddingForLogin.");
                                    loadUserDetails();
                                })
                                .addOnFailureListener(e -> {
                                    runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Error saving login face data to Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                    Log.e(TAG, "Error updating Firestore with login face data", e);
                                });
                    }).addOnFailureListener(e -> {
                        runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Failed to get download URL for login face.", Toast.LENGTH_SHORT).show());
                        Log.e(TAG, "Failed to get download URL for login face", e);
                    });
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Failed to upload login face image.", Toast.LENGTH_SHORT).show());
                    Log.e(TAG, "Login face image upload failed", e);
                })
                .addOnCompleteListener(task -> {
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                });
    }

    private Bitmap cropFaceFromBitmap(Bitmap sourceBitmap, Rect boundingBox) {
        int x = boundingBox.left;
        int y = boundingBox.top;
        int width = boundingBox.width();
        int height = boundingBox.height();
        int padding = (int) (0.1 * Math.min(width, height));
        x = Math.max(0, x - padding);
        y = Math.max(0, y - padding);
        width = Math.min(sourceBitmap.getWidth() - x, width + (2 * padding));
        height = Math.min(sourceBitmap.getHeight() - y, height + (2 * padding));

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

    private void performLogout() {
        mAuth.signOut();
        Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(ProfileActivity.this, MainActivity.class); // Assuming MainActivity is your login screen
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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