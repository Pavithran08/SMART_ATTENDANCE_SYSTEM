package com.example.smartattendancesystem;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.squareup.picasso.Picasso;

public class StudentActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private ImageView profileImageView;
    private TextView usernameTextView;
    private TextView userMatricOrEmailTextView;
    private TextView registeredFacultyTextView;
    private TextView registeredCourseTextView;
    private TextView registeredYearTextView;

    private Button scanQrButton; // Declare the button here to enable/disable it

    // Store registered details and user's matric/staff ID locally
    private String userRegisteredFaculty = "N/A";
    private String userRegisteredCourse = "N/A";
    private String userRegisteredYear = "N/A";
    private String currentUserMatric = null;
    private String currentUserName = null;

    private static final String TAG = "StudentActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.student);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize UI components
        profileImageView = findViewById(R.id.profileImage);
        usernameTextView = findViewById(R.id.usernameText);
        userMatricOrEmailTextView = findViewById(R.id.userMatricOrEmail);
        registeredFacultyTextView = findViewById(R.id.registeredFacultyText);
        registeredCourseTextView = findViewById(R.id.registeredCourseText);
        registeredYearTextView = findViewById(R.id.registeredYearText);

        scanQrButton = findViewById(R.id.scanQrButton); // Initialize the button
        scanQrButton.setEnabled(false); // Disable by default until data loads
        Log.d(TAG, "onCreate: Scan QR Button - Initial state: DISABLED"); // LOG ADDED

        // Fetch current user's details for the profile card and registered info
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId != null) {
            fetchUserDetails(userId);
        } else {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            // Redirect to login if no user is logged in
            startActivity(new Intent(StudentActivity.this, LoginActivity.class));
            finish();
        }

        // "Scan QR" button listener
        scanQrButton.setOnClickListener(v -> {
            Log.d(TAG, "Scan QR button clicked. Performing pre-check."); // LOG ADDED
            Log.d(TAG, "  currentUserMatric: '" + currentUserMatric + "'"); // LOG ADDED
            Log.d(TAG, "  currentUserName: '" + currentUserName + "'");     // LOG ADDED
            Log.d(TAG, "  userRegisteredFaculty: '" + userRegisteredFaculty + "'"); // LOG ADDED

            // This check is now less critical as the button is disabled, but still good as a safeguard
            if (currentUserMatric == null || currentUserName == null || userRegisteredFaculty.equals("N/A")) {
                Toast.makeText(StudentActivity.this, "User data still loading. Please wait a moment.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Scan QR: Pre-check failed. Displaying 'User data still loading' toast."); // LOG ADDED
                // Optionally, you could try to re-fetch if this state is reached unexpectedly:
                // fetchUserDetails(userId);
                return;
            }

            Log.d(TAG, "Scan QR: Pre-check passed. Starting QrScanActivity."); // LOG ADDED
            Intent scannerIntent = new Intent(StudentActivity.this, QrScanActivity.class);
            scannerIntent.putExtra("REGISTERED_FACULTY", userRegisteredFaculty);
            scannerIntent.putExtra("REGISTERED_COURSE", userRegisteredCourse);
            scannerIntent.putExtra("REGISTERED_YEAR", userRegisteredYear);
            scannerIntent.putExtra("USER_ID", userId);
            scannerIntent.putExtra("USER_MATRIC", currentUserMatric);
            scannerIntent.putExtra("USER_NAME", currentUserName);
            startActivity(scannerIntent);
        });

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId != null) {
            Log.d(TAG, "onResume: Re-fetching user details for ID: " + userId); // LOG ADDED
            // Re-fetch details in onResume to ensure fresh data and re-enable button
            fetchUserDetails(userId);
        } else {
            // If user somehow became null during onPause/onStop, handle it
            scanQrButton.setEnabled(false);
            Log.w(TAG, "onResume: User ID is null. Disabling QR button and redirecting to Login."); // LOG ADDED
            Toast.makeText(this, "User session expired. Please log in.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(StudentActivity.this, LoginActivity.class));
            finish();
        }
    }

    private void setupBottomNavigation() {
        ImageButton backButton = findViewById(R.id.backButton);
        ImageButton homeButton = findViewById(R.id.homeButton);
        ImageButton profileButton = findViewById(R.id.profileButton);

        backButton.setOnClickListener(v -> {
            onBackPressed();
        });

        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(StudentActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        profileButton.setOnClickListener(v -> {
            Intent profileIntent = new Intent(StudentActivity.this, ProfileActivity.class);
            startActivity(profileIntent);
        });
    }

    private void fetchUserDetails(String userId) {
        DocumentReference userRef = db.collection("users").document(userId);
        Log.d(TAG, "fetchUserDetails: Attempting to fetch user details for: " + userId); // LOG ADDED

        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    String username = document.getString("username");
                    String profileImageUrl = document.getString("profileImageUrl");
                    String matricOrStaffId = document.getString("matricOrStaffId");
                    String email = document.getString("email");

                    String faculty = document.getString("faculty");
                    String course = document.getString("course");
                    String year = document.getString("year");

                    // Update local variables
                    currentUserName = username;
                    currentUserMatric = matricOrStaffId;
                    userRegisteredFaculty = (faculty != null && !faculty.isEmpty()) ? faculty : "N/A";
                    userRegisteredCourse = (course != null && !course.isEmpty()) ? course : "N/A";
                    userRegisteredYear = (year != null && !year.isEmpty()) ? year : "N/A";

                    // Update UI elements
                    usernameTextView.setText(username);
                    if (userMatricOrEmailTextView != null) {
                        userMatricOrEmailTextView.setText(currentUserMatric + " / " + email);
                    }

                    if (registeredFacultyTextView != null) {
                        registeredFacultyTextView.setText("Faculty: " + userRegisteredFaculty);
                        registeredFacultyTextView.setVisibility(View.VISIBLE);
                    }
                    if (registeredCourseTextView != null) {
                        registeredCourseTextView.setText("Course: " + userRegisteredCourse);
                        registeredCourseTextView.setVisibility(View.VISIBLE);
                    }
                    if (registeredYearTextView != null) {
                        registeredYearTextView.setText("Year: " + userRegisteredYear);
                        registeredYearTextView.setVisibility(View.VISIBLE);
                    }

                    if (profileImageView != null && profileImageUrl != null && !profileImageUrl.isEmpty()) {
                        Picasso.get().load(profileImageUrl).into(profileImageView);
                    } else if (profileImageView != null) {
                        profileImageView.setImageResource(R.drawable.ic_profile_default); // Assuming you have a default drawable
                    }

                    // Enable the scan QR button once data is loaded
                    scanQrButton.setEnabled(true);
                    Log.d(TAG, "fetchUserDetails: Data loaded successfully. Scan QR Button - ENABLED."); // LOG ADDED
                    Toast.makeText(StudentActivity.this, "Profile loaded successfully.", Toast.LENGTH_SHORT).show();

                } else {
                    Log.w(TAG, "fetchUserDetails: No such user document exists for ID: " + userId); // LOG ADDED
                    Toast.makeText(StudentActivity.this, "User data not found!", Toast.LENGTH_LONG).show();
                    scanQrButton.setEnabled(false); // Keep disabled if data not found
                    Log.w(TAG, "fetchUserDetails: Scan QR Button - DISABLED due to missing document."); // LOG ADDED
                }
            } else {
                Log.e(TAG, "fetchUserDetails: Error getting user details", task.getException()); // LOG ADDED
                Toast.makeText(StudentActivity.this, "Error loading user data: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                scanQrButton.setEnabled(false); // Keep disabled on error
                Log.e(TAG, "fetchUserDetails: Scan QR Button - DISABLED due to fetch error."); // LOG ADDED
            }
        });
    }
}