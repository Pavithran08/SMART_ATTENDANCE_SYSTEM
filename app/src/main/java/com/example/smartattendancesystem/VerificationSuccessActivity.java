package com.example.smartattendancesystem;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast; // Import Toast
import androidx.appcompat.app.AppCompatActivity;

public class VerificationSuccessActivity extends AppCompatActivity {

    private static final long DELAY_MILLISECONDS = 3000; // 3 seconds
    private String userMatricOrStaffId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification_success); // Set the new layout

        // Retrieve the matric or staff ID passed from VerifyActivity
        userMatricOrStaffId = getIntent().getStringExtra("USER_MATRIC_OR_STAFF_ID");

        // Display a message (optional, as you have a layout for success)
        Toast.makeText(this, "Face Verification Successful!", Toast.LENGTH_SHORT).show();

        // Use a Handler to post a delayed action
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateToNextPage, DELAY_MILLISECONDS);
    }

    private void navigateToNextPage() {
        Intent intent;

        // Determine the target activity based on the first character of matricOrStaffId
        if (userMatricOrStaffId != null && !userMatricOrStaffId.isEmpty()) {
            char userType = userMatricOrStaffId.charAt(0);
            if (userType == 'B' || userType == 'b') {
                intent = new Intent(VerificationSuccessActivity.this, StudentActivity.class);
            } else if (userType == 'S' || userType == 's') {
                intent = new Intent(VerificationSuccessActivity.this, LecturerActivity.class);
            } else if (userType == 'A' || userType == 'a') {
                intent = new Intent(VerificationSuccessActivity.this, AdminActivity.class);
            } else {
                // Default to a general student page if type is unknown
                Toast.makeText(this, "Unknown user type. Navigating to default student page.", Toast.LENGTH_SHORT).show();
                intent = new Intent(VerificationSuccessActivity.this, StudentActivity.class);
            }
        } else {
            // Fallback if matricOrStaffId is not available, go to StudentActivity
            Toast.makeText(this, "User role information missing. Navigating to default student page.", Toast.LENGTH_SHORT).show();
            intent = new Intent(VerificationSuccessActivity.this, StudentActivity.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK); // Clear back stack
        startActivity(intent);
        finish(); // Finish this activity so user can't go back to it
    }
}
