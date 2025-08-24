package com.example.smartattendancesystem;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.squareup.picasso.Picasso;

public class LecturerActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ImageView profileImageView;
    private TextView usernameTextView;
    private Button generateQRButton, viewDataButton;

    private static final String TAG = "LecturerActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lecturer);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI components
        profileImageView = findViewById(R.id.profileImageView);
        usernameTextView = findViewById(R.id.usernameTextView);
        generateQRButton = findViewById(R.id.generateQRButton);
        viewDataButton = findViewById(R.id.viewDataButton);

        // Load user details from Firestore
        loadUserDetails();

        // Setup back, home, and profile navigation buttons
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            // This might go back to LoginActivity or a main selection if the app has one
            Intent loginIntent = new Intent(LecturerActivity.this, LoginActivity.class);
            startActivity(loginIntent);
            finish();
        });

        ImageButton homeButton = findViewById(R.id.homeButton);
        homeButton.setOnClickListener(v -> {
            // Navigate to MainActivity (home screen for all users, or LecturerActivity itself if this is the main screen)
            Intent intent = new Intent(LecturerActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        ImageButton profileButton = findViewById(R.id.profileButton);
        profileButton.setOnClickListener(v -> {
            Intent profileIntent = new Intent(LecturerActivity.this, LecturerProfileActivity.class);
            startActivity(profileIntent);
        });

        // Button actions
        generateQRButton.setOnClickListener(v -> {
            // Navigate to LecturerChooseClassActivity to select details and generate QR
            Intent chooseClassIntent = new Intent(LecturerActivity.this, LecturerChooseClassActivity.class);
            startActivity(chooseClassIntent);
        });

        viewDataButton.setOnClickListener(v -> {
            // Navigate to the new ViewAttendanceActivity
            Intent viewDataIntent = new Intent(LecturerActivity.this, ViewAttendanceActivity.class);
            startActivity(viewDataIntent);
        });
    }

    private void loadUserDetails() {
        String userId = null;
        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        }

        final String finalUserId = userId;

        if (finalUserId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "User not logged in, cannot load details.");
            profileImageView.setImageResource(R.drawable.ic_profile_default);
            return;
        }

        DocumentReference userRef = db.collection("users").document(finalUserId);
        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                String username = task.getResult().getString("username");
                String profileImageUrl = task.getResult().getString("profileImageUrl");
                String lecturerId = task.getResult().getString("matricOrStaffId");
                String email = task.getResult().getString("email");

                usernameTextView.setText(username);
                TextView lecturerIdOrEmail = findViewById(R.id.lecturerIdOrEmail);
                if (lecturerId != null && email != null) {
                    lecturerIdOrEmail.setText(lecturerId + " / " + email);
                } else if (lecturerId != null) {
                    lecturerIdOrEmail.setText(lecturerId);
                } else if (email != null) {
                    lecturerIdOrEmail.setText(email);
                }

                if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                    Picasso.get().load(profileImageUrl)
                            .placeholder(R.drawable.ic_profile_default)
                            .error(R.drawable.ic_profile_default)
                            .into(profileImageView);
                } else {
                    profileImageView.setImageResource(R.drawable.ic_profile_default);
                }
            } else {
                if (!task.isSuccessful()) {
                    Log.e(TAG, "Error loading user data for ID " + finalUserId, task.getException());
                } else {
                    Log.w(TAG, "User document for ID " + finalUserId + " not found or does not exist.");
                }
                Toast.makeText(LecturerActivity.this, "User data not found or error.", Toast.LENGTH_SHORT).show();
                profileImageView.setImageResource(R.drawable.ic_profile_default);
            }
        });
    }
}