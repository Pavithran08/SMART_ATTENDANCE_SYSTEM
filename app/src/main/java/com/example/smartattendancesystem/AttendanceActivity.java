package com.example.smartattendancesystem;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView; // Import TextView

import androidx.appcompat.app.AppCompatActivity;

public class AttendanceActivity extends AppCompatActivity {

    private Button btnNext; // Changed to btnNext for clarity, as per XML
    private ImageButton backButton, homeButton, profileButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.attendance_success); // Make sure this matches your XML file name

        // Initialize views
        btnNext = findViewById(R.id.btn_scan); // This button is named btn_scan in your XML
        backButton = findViewById(R.id.backButton);
        homeButton = findViewById(R.id.homeButton);
        profileButton = findViewById(R.id.profileButton);

        // Set up the "Next" button (originally btn_scan in XML) to go back to StudentActivity
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AttendanceActivity.this, StudentActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK); // Clears activity stack
                startActivity(intent);
                finish(); // Finish this activity
            }
        });

        // Set up bottom navigation buttons (similar logic for all)
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed(); // Go back to the previous activity in the stack
            }
        });

        homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AttendanceActivity.this, StudentActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });

        profileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Assuming ProfileActivity is your profile screen
                Intent intent = new Intent(AttendanceActivity.this, StudentActivity.class); // Or ProfileActivity.class if you have one
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });

        // You can get data from the intent if you want to display dynamic messages
        // For example:
        // TextView successMessage = findViewById(R.id.success_message);
        // String message = getIntent().getStringExtra("message");
        // if (message != null) {
        //    successMessage.setText(message);
        // }
    }
}