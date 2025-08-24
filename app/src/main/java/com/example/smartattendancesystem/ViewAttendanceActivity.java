package com.example.smartattendancesystem;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ViewAttendanceActivity extends AppCompatActivity {

    private static final String TAG = "ViewAttendanceActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private RecyclerView recyclerViewAttendance;
    private AttendanceAdapter attendanceAdapter;
    private List<Attendance> attendanceList;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private TextView tvAttendanceDataTitle; // Added for the title TextView

    private ImageButton backButton, homeButton, profileButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_attendance);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI components
        recyclerViewAttendance = findViewById(R.id.recyclerViewAttendance);
        progressBar = findViewById(R.id.progressBarAttendance);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvAttendanceDataTitle = findViewById(R.id.attendanceDataTitle); // Initialize the title TextView

        // Set up RecyclerView
        attendanceList = new ArrayList<>();
        attendanceAdapter = new AttendanceAdapter(attendanceList);
        recyclerViewAttendance.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewAttendance.setAdapter(attendanceAdapter);

        // Load attendance data
        loadAttendanceData();

        // Setup bottom navigation buttons
        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        backButton = findViewById(R.id.backButton);
        homeButton = findViewById(R.id.homeButton);
        profileButton = findViewById(R.id.profileButton);

        backButton.setOnClickListener(v -> {
            onBackPressed(); // Go back to the previous activity (LecturerActivity)
        });

        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(ViewAttendanceActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(ViewAttendanceActivity.this, LecturerProfileActivity.class);
            startActivity(intent);
        });
    }

    private void loadAttendanceData() {
        showLoading(true); // Show progress bar

        // Fetch attendance data from the "attendance" collection
        db.collection("attendance")
                // Optional: Order by timestamp to show most recent first
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    showLoading(false); // Hide progress bar

                    if (task.isSuccessful()) {
                        attendanceList.clear(); // Clear existing data
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // Convert Firestore document to Attendance object
                            Attendance attendance = document.toObject(Attendance.class);
                            attendanceList.add(attendance);
                        }

                        if (attendanceList.isEmpty()) {
                            tvEmptyState.setVisibility(View.VISIBLE); // Show empty state message
                            recyclerViewAttendance.setVisibility(View.GONE);
                        } else {
                            tvEmptyState.setVisibility(View.GONE);
                            recyclerViewAttendance.setVisibility(View.VISIBLE);
                            attendanceAdapter.setAttendanceList(attendanceList); // Update adapter with new data
                        }
                        Log.d(TAG, "Attendance data loaded successfully. Count: " + attendanceList.size());
                    } else {
                        Log.e(TAG, "Error getting attendance documents: ", task.getException());
                        Toast.makeText(ViewAttendanceActivity.this, "Error loading attendance data.", Toast.LENGTH_SHORT).show();
                        tvEmptyState.setText("Failed to load attendance data.");
                        tvEmptyState.setVisibility(View.VISIBLE);
                        recyclerViewAttendance.setVisibility(View.GONE);
                    }
                });
    }

    private void showLoading(boolean show) {
        if (show) {
            progressBar.setVisibility(View.VISIBLE);
            tvEmptyState.setVisibility(View.GONE);
            recyclerViewAttendance.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
        }
    }
}
