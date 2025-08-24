package com.example.smartattendancesystem;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log; // Import Log for debugging
import android.view.View; // Import View for visibility control
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar; // Import ProgressBar
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.List; // Import List for face embedding

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity"; // Tag for logging

    private EditText edtMatricOrStaffId, edtPassword;
    private Button btnLogin, btnFaceScan; // Declare the Face Scan button
    private ProgressBar loadingSpinner;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private Bitmap capturedPhotoBitmap; // To temporarily hold the captured bitmap
    private String loggedInUserMatricOrStaffId; // To store the matric or staff ID of the logged-in user

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        edtMatricOrStaffId = findViewById(R.id.edt_matric_or_staff_id);
        edtPassword = findViewById(R.id.edt_password);
        btnLogin = findViewById(R.id.btn_login);
        btnFaceScan = findViewById(R.id.btn_face_scan); // Initialize the Face Scan button
        loadingSpinner = findViewById(R.id.loading_spinner_login);

        // --- Logic for standard 'Login' button (password-only or prompts face scan) ---
        btnLogin.setOnClickListener(v -> {
            String inputMatricOrStaffId = edtMatricOrStaffId.getText().toString().trim();
            String password = edtPassword.getText().toString();

            if (inputMatricOrStaffId.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Please enter both fields", Toast.LENGTH_SHORT).show();
            } else {
                showLoading(true); // Show spinner and disable fields

                // Query Firestore to check if the user has a registered face image
                db.collection("users")
                        .whereEqualTo("matricOrStaffId", inputMatricOrStaffId)
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            if (!queryDocumentSnapshots.isEmpty()) {
                                QueryDocumentSnapshot document = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                                String email = document.getString("email");
                                loggedInUserMatricOrStaffId = document.getString("matricOrStaffId");

                                // Check for face embedding existence
                                List<Double> faceEmbedding = (List<Double>) document.get("faceEmbedding"); // Primary face
                                List<Double> faceEmbeddingForLogin = (List<Double>) document.get("faceEmbeddingForLogin"); // Face used for login

                                if ((faceEmbedding != null && !faceEmbedding.isEmpty()) || (faceEmbeddingForLogin != null && !faceEmbeddingForLogin.isEmpty())) {
                                    // User has face data, prompt them to use the face scan login
                                    showLoading(false); // Hide spinner
                                    Toast.makeText(LoginActivity.this, "This account requires 'Login with Face Scan'. Please use that button.", Toast.LENGTH_LONG).show();
                                } else {
                                    // User does NOT have face data, proceed with password-only login
                                    if (email != null && !email.isEmpty()) {
                                        mAuth.signInWithEmailAndPassword(email, password)
                                                .addOnCompleteListener(LoginActivity.this, authTask -> {
                                                    if (authTask.isSuccessful()) {
                                                        Log.d(TAG, "Password login successful for user without face data.");
                                                        // Directly navigate to role-specific dashboard as no face scan is required
                                                        navigateToRoleSpecificDashboard(loggedInUserMatricOrStaffId);
                                                        // No need to showLoading(false) here as activity will finish
                                                    } else {
                                                        showLoading(false); // Hide spinner on auth failure
                                                        Toast.makeText(LoginActivity.this, "Authentication Failed: " + authTask.getException().getMessage(), Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                    } else {
                                        showLoading(false); // Hide spinner if email not found
                                        Toast.makeText(LoginActivity.this, "Email not found for this user in database.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            } else {
                                showLoading(false); // Hide spinner if user not found
                                Toast.makeText(LoginActivity.this, "User not found with this Matric/Staff ID.", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(e -> {
                            showLoading(false); // Hide spinner on Firestore query failure
                            Toast.makeText(LoginActivity.this, "Error retrieving user information: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Firestore query failed: " + e.getMessage());
                        });
            }
        });

        // --- Logic for 'Login with Face Scan' button ---
        btnFaceScan.setOnClickListener(v -> {
            String inputMatricOrStaffId = edtMatricOrStaffId.getText().toString().trim();
            String password = edtPassword.getText().toString();

            if (inputMatricOrStaffId.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Please enter both fields", Toast.LENGTH_SHORT).show();
            } else {
                showLoading(true); // Show spinner and disable fields

                // Query Firestore to check if the user has a registered face image for face scan login
                db.collection("users")
                        .whereEqualTo("matricOrStaffId", inputMatricOrStaffId)
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            if (!queryDocumentSnapshots.isEmpty()) {
                                QueryDocumentSnapshot document = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                                String email = document.getString("email");
                                loggedInUserMatricOrStaffId = document.getString("matricOrStaffId");

                                // Check for face embedding existence for face scan path
                                List<Double> faceEmbedding = (List<Double>) document.get("faceEmbedding"); // Primary face
                                List<Double> faceEmbeddingForLogin = (List<Double>) document.get("faceEmbeddingForLogin"); // Face used for login

                                if ((faceEmbedding == null || faceEmbedding.isEmpty()) && (faceEmbeddingForLogin == null || faceEmbeddingForLogin.isEmpty())) {
                                    // User does NOT have face data, prompt them to use the standard login
                                    showLoading(false); // Hide spinner
                                    Toast.makeText(LoginActivity.this, "No face registered for this account. Please use the 'Login' button or register a face.", Toast.LENGTH_LONG).show();
                                } else {
                                    // User HAS face data, proceed with password auth then face scan
                                    if (email != null && !email.isEmpty()) {
                                        mAuth.signInWithEmailAndPassword(email, password)
                                                .addOnCompleteListener(LoginActivity.this, authTask -> {
                                                    if (authTask.isSuccessful()) {
                                                        Log.d(TAG, "Password authentication successful for face scan login.");
                                                        openCamera(); // Proceed to camera for face verification
                                                        // No need to showLoading(false) here as activity will finish
                                                    } else {
                                                        showLoading(false); // Hide spinner on auth failure
                                                        Toast.makeText(LoginActivity.this, "Authentication Failed: " + authTask.getException().getMessage(), Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                    } else {
                                        showLoading(false); // Hide spinner if email not found
                                        Toast.makeText(LoginActivity.this, "Email not found for this user in database.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            } else {
                                showLoading(false); // Hide spinner if user not found
                                Toast.makeText(LoginActivity.this, "User not found with this Matric/Staff ID.", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(e -> {
                            showLoading(false); // Hide spinner on Firestore query failure
                            Toast.makeText(LoginActivity.this, "Error retrieving user information: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Firestore query failed: " + e.getMessage());
                        });
            }
        });
    }

    // Helper method to control loading spinner visibility and button state
    private void showLoading(boolean show) {
        if (show) {
            loadingSpinner.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(false);
            btnFaceScan.setEnabled(false); // Disable face scan button too
            edtMatricOrStaffId.setEnabled(false);
            edtPassword.setEnabled(false);
        } else {
            loadingSpinner.setVisibility(View.GONE);
            btnLogin.setEnabled(true);
            btnFaceScan.setEnabled(true); // Enable face scan button
            edtMatricOrStaffId.setEnabled(true);
            edtPassword.setEnabled(true);
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, 100);
        } else {
            showLoading(false); // Hide spinner if camera app not found
            Toast.makeText(this, "No camera app found.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No camera app found for capture.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null && data.getExtras() != null) {
            capturedPhotoBitmap = (Bitmap) data.getExtras().get("data");
            if (capturedPhotoBitmap != null) {
                // Loading continues, saveFaceImageToStorage will keep spinner visible
                saveFaceImageToStorage(capturedPhotoBitmap);
            } else {
                showLoading(false); // Hide spinner if image capture failed
                Toast.makeText(this, "Failed to capture image data.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Captured photo bitmap is null in onActivityResult.");
            }
        } else if (requestCode == 100 && resultCode == RESULT_CANCELED) {
            showLoading(false); // Hide spinner if face capture cancelled
            Toast.makeText(this, "Face capture cancelled. Login requires face verification.", Toast.LENGTH_LONG).show();
            mAuth.signOut(); // Sign out as face verification is mandatory for this path
            Log.d(TAG, "Face capture cancelled, user signed out.");
        } else {
            showLoading(false); // Hide spinner on other capture failures
            Toast.makeText(this, "Image capture failed or cancelled.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Image capture failed or cancelled. Request code: " + requestCode + ", Result code: " + resultCode);
        }
    }

    private void saveFaceImageToStorage(Bitmap photoToSave) {
        if (mAuth.getCurrentUser() == null) {
            showLoading(false); // Hide spinner if user not logged in (shouldn't happen here)
            Toast.makeText(this, "User not logged in to save image.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "saveFaceImageToStorage: Current user is null.");
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        photoToSave.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] data = baos.toByteArray();

        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("face_images_login/" + mAuth.getCurrentUser().getUid() + ".jpg");

        storageRef.putBytes(data)
                .addOnSuccessListener(taskSnapshot -> {
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        saveFaceImageUrlToFirestore(uri.toString(), photoToSave);
                        Log.d(TAG, "Image uploaded to Storage. URI: " + uri.toString());
                    }).addOnFailureListener(e -> {
                        showLoading(false); // Hide spinner on URL retrieval failure
                        Toast.makeText(LoginActivity.this, "Failed to get image URL from Firebase Storage: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to get image URL: " + e.getMessage());
                    });
                })
                .addOnFailureListener(e -> {
                    showLoading(false); // Hide spinner on storage upload failure
                    Toast.makeText(LoginActivity.this, "Error saving face image to storage: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error saving face image to storage: " + e.getMessage());
                });
    }

    private void saveFaceImageUrlToFirestore(String faceImageUrl, Bitmap capturedPhoto) {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId)
                .update("faceImageLoginUrl", faceImageUrl)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(LoginActivity.this, "Face image captured and saved successfully.", Toast.LENGTH_SHORT).show();
                    // Navigation will happen, so loading state can be maintained until VerifyActivity takes over
                    navigateToVerify(capturedPhoto);
                    Log.d(TAG, "faceImageLoginUrl updated in Firestore.");
                    // No need to showLoading(false) here, as LoginActivity will finish
                })
                .addOnFailureListener(e -> {
                    showLoading(false); // Hide spinner on Firestore update failure
                    Toast.makeText(LoginActivity.this, "Error saving face image URL to Firestore: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error saving faceImageLoginUrl to Firestore: " + e.getMessage());
                });
    }

    /**
     * Navigates to the VerifyActivity, passing the captured photo and user's matric/staff ID.
     * This path is taken *only* by the "Login with Face Scan" button flow.
     */
    private void navigateToVerify(Bitmap capturedPhoto) {
        Intent intent = new Intent(LoginActivity.this, VerifyActivity.class);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        capturedPhoto.compress(Bitmap.CompressFormat.JPEG, 90, stream);
        byte[] byteArray = stream.toByteArray();

        intent.putExtra("LIVE_FACE_BITMAP_BYTE_ARRAY", byteArray);
        intent.putExtra("USER_MATRIC_OR_STAFF_ID", loggedInUserMatricOrStaffId);

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish(); // Finish LoginActivity
        Log.d(TAG, "Navigating to VerifyActivity.");
    }

    /**
     * Navigates to the role-specific dashboard directly after successful password-only login.
     * This path is taken *only* by the standard "Login" button flow (for users without face data).
     */
    private void navigateToRoleSpecificDashboard(String matricOrStaffId) {
        Intent intent;

        if (matricOrStaffId != null && !matricOrStaffId.isEmpty()) {
            char userType = matricOrStaffId.charAt(0);
            if (userType == 'B' || userType == 'b') {
                intent = new Intent(LoginActivity.this, StudentActivity.class);
            } else if (userType == 'S' || userType == 's') {
                intent = new Intent(LoginActivity.this, LecturerActivity.class);
            } else if (userType == 'A' || userType == 'a') {
                intent = new Intent(LoginActivity.this, AdminActivity.class);
            } else {
                Toast.makeText(this, "Unknown user type. Navigating to default student page.", Toast.LENGTH_SHORT).show();
                intent = new Intent(LoginActivity.this, StudentActivity.class); // Fallback
            }
        } else {
            Toast.makeText(this, "User role information missing. Navigating to default student page.", Toast.LENGTH_SHORT).show();
            intent = new Intent(LoginActivity.this, StudentActivity.class); // Fallback
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish(); // Finish LoginActivity
        Log.d(TAG, "Navigating to role-specific dashboard: " + intent.getComponent().getClassName());
    }
}
