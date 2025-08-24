package com.example.smartattendancesystem;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button; // Import Button
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class DisplayDetailActivity extends AppCompatActivity {

    private TextView facultyDetail, sectionDetail, timeDetail;
    private Button btnNext; // Declare the Button

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display_detail); // Ensure this matches your redesigned layout filename

        // Initialize TextViews
        facultyDetail = findViewById(R.id.facultyDetail);
        sectionDetail = findViewById(R.id.sectionDetail);
        timeDetail = findViewById(R.id.timeDetail);
        btnNext = findViewById(R.id.btn_next); // Initialize the Button

        // Get the passed data from the Intent
        String faculty = getIntent().getStringExtra("FACULTY");
        String section = getIntent().getStringExtra("SECTION");
        String time = getIntent().getStringExtra("TIME");

        // Display the data in the TextViews
        if (faculty != null) {
            facultyDetail.setText("Faculty: " + faculty);
        }
        if (section != null) {
            sectionDetail.setText("Section: " + section);
        }
        if (time != null) {
            timeDetail.setText("Time: " + time);
        }

        // Set OnClickListener for the Next Button
        btnNext.setOnClickListener(v -> {
            // Navigate to QR Scan Activity
            Intent qrScanIntent = new Intent(DisplayDetailActivity.this, QrScanActivity.class);
            startActivity(qrScanIntent);
        });
    }
}
