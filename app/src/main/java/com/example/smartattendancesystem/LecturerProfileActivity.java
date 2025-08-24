package com.example.smartattendancesystem;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import java.io.IOException;

public class LecturerProfileActivity extends AppCompatActivity {

    private static final String TAG = "LecturerProfileActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private EditText nameField, lecturerIdField, emailField, passwordField;
    private ImageView profileImageView;
    private TextView usernameTextView, lecturerIdOrEmailTextView;
    private Button updateButton, ulearnRegistrationButton;
    private ImageButton logoutCornerButton;

    private ImageButton backButton, homeButton, profileButton;

    private Uri selectedImageUri;
    private static final int PICK_IMAGE_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lecturer_profile);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // UI Components
        nameField = findViewById(R.id.nameField);
        lecturerIdField = findViewById(R.id.lecturerIdField);
        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        updateButton = findViewById(R.id.btn_update);
        ulearnRegistrationButton = findViewById(R.id.btn_ulearn_registration);
        profileImageView = findViewById(R.id.profileImage);
        usernameTextView = findViewById(R.id.usernameTextView);
        lecturerIdOrEmailTextView = findViewById(R.id.lecturerIdOrEmail);
        logoutCornerButton = findViewById(R.id.btn_logout_corner); // New ImageButton

        // Bottom Navigation Buttons
        setupBottomNavigation();

        // Load profile
        loadUserDetails();

        // Update button logic
        updateButton.setOnClickListener(v -> updateUserDetails());

        // Open gallery on profile image click
        profileImageView.setOnClickListener(v -> openGallery());

        // Ulearn Registration Button
        ulearnRegistrationButton.setOnClickListener(v -> {
            Log.d(TAG, "Launching UlearnRegistrationActivity.");
            startActivity(new Intent(this, UlearnRegistrationActivity.class));
            finish();
        });

        // Logout ImageButton at top corner
        logoutCornerButton.setOnClickListener(v -> {
            Log.d(TAG, "Logging out user.");
            mAuth.signOut();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void setupBottomNavigation() {
        backButton = findViewById(R.id.backButton);
        homeButton = findViewById(R.id.homeButton);
        profileButton = findViewById(R.id.profileButton);

        backButton.setOnClickListener(v -> {
            Log.d(TAG, "Back to LecturerActivity.");
            Intent intent = new Intent(this, LecturerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        homeButton.setOnClickListener(v -> {
            Log.d(TAG, "Back to MainActivity.");
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        profileButton.setOnClickListener(v -> {
            Toast.makeText(this, "You are already on the Profile screen.", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadUserDetails() {
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference userRef = db.collection("users").document(userId);

        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DocumentSnapshot doc = task.getResult();
                String email = doc.getString("email");
                String username = doc.getString("username");
                String userID = doc.getString("userID");

                nameField.setText(username);
                lecturerIdField.setText(userID);
                emailField.setText(email);
                passwordField.setText("");
                passwordField.setHint("Enter new password to change");

                usernameTextView.setText(username);
                lecturerIdOrEmailTextView.setText(userID + " / " + email);

                String profileImageUrl = doc.getString("profileImageUrl");
                if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                    Picasso.get().load(profileImageUrl).into(profileImageView);
                } else {
                    profileImageView.setImageResource(R.drawable.ic_profile_default);
                }

            } else {
                Toast.makeText(this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "No such document or error: ", task.getException());
            }
        });
    }

    private void updateUserDetails() {
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = emailField.getText().toString().trim();
        String newPassword = passwordField.getText().toString().trim();
        String username = nameField.getText().toString().trim();
        String userID = lecturerIdField.getText().toString().trim();

        if (username.isEmpty() || userID.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference userRef = db.collection("users").document(userId);

        if (!email.equals(mAuth.getCurrentUser().getEmail())) {
            mAuth.getCurrentUser().updateEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            performFirestoreUpdateAndPasswordChange(userRef, username, userID, email, newPassword);
                        } else {
                            Toast.makeText(this, "Failed to update email.", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            performFirestoreUpdateAndPasswordChange(userRef, username, userID, email, newPassword);
        }

        if (selectedImageUri != null) {
            uploadProfileImage(selectedImageUri);
        }
    }

    private void performFirestoreUpdateAndPasswordChange(DocumentReference userRef, String username, String userID, String email, String newPassword) {
        userRef.update("email", email, "username", username, "userID", userID)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show();
                    if (!newPassword.isEmpty()) {
                        updateFirebasePassword(newPassword);
                    } else {
                        loadUserDetails();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error updating profile.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Firestore update error", e);
                });
    }

    private void updateFirebasePassword(String newPassword) {
        if (newPassword.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.getCurrentUser().updatePassword(newPassword)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                        passwordField.setText("");
                        loadUserDetails();
                    } else {
                        Toast.makeText(this, "Failed to update password.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Password update failed", task.getException());
                    }
                });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                profileImageView.setImageBitmap(bitmap);
            } catch (IOException e) {
                Log.e(TAG, "Image load failed", e);
                Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void uploadProfileImage(Uri imageUri) {
        String userId = mAuth.getCurrentUser().getUid();
        StorageReference imageRef = storage.getReference().child("profile_images/" + userId + ".jpg");

        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String imageUrl = uri.toString();
                    db.collection("users").document(userId).update("profileImageUrl", imageUrl)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Image updated!", Toast.LENGTH_SHORT).show();
                                loadUserDetails();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Image URL update failed", e);
                                Toast.makeText(this, "Failed to update image URL.", Toast.LENGTH_SHORT).show();
                            });
                }))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Image upload failed", e);
                    Toast.makeText(this, "Image upload failed.", Toast.LENGTH_SHORT).show();
                });
    }
}
