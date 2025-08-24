package com.example.smartattendancesystem;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class LecturerChooseClassActivity extends AppCompatActivity {

    private static final String TAG = "LecturerChooseClass";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private RecyclerView enrolledCoursesRecyclerView;
    private ProgressBar progressBar;
    private TextView noCoursesTextView;
    private EnrolledCourseAdapter adapter;
    private List<EnrolledCourse> enrolledCoursesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lecturer_choose_class);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        enrolledCoursesRecyclerView = findViewById(R.id.enrolledCoursesRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        noCoursesTextView = findViewById(R.id.noCoursesTextView);

        enrolledCoursesList = new ArrayList<>();
        adapter = new EnrolledCourseAdapter(enrolledCoursesList);
        enrolledCoursesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        enrolledCoursesRecyclerView.setAdapter(adapter);

        setupBottomNavigation();

        fetchEnrolledCourses();
    }

    private void setupBottomNavigation() {
        ImageButton backButton = findViewById(R.id.backButton);
        ImageButton homeButton = findViewById(R.id.homeButton);
        ImageButton profileButton = findViewById(R.id.profileButton);

        backButton.setOnClickListener(v -> finish());

        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(LecturerChooseClassActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(LecturerChooseClassActivity.this, LecturerProfileActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void fetchEnrolledCourses() {
        String currentLecturerFirebaseUid = null;
        if (mAuth.getCurrentUser() != null) {
            currentLecturerFirebaseUid = mAuth.getCurrentUser().getUid();
        }

        if (currentLecturerFirebaseUid == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            noCoursesTextView.setText("User not logged in. Please log in again.");
            noCoursesTextView.setVisibility(View.VISIBLE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        noCoursesTextView.setVisibility(View.GONE);
        enrolledCoursesRecyclerView.setVisibility(View.GONE);

        String finalCurrentLecturerFirebaseUid = currentLecturerFirebaseUid;

        // Assuming your 'enrollments' collection now stores 'classLocationName'
        db.collection("classOfferings")
                .whereEqualTo("lecturerFirebaseUid", finalCurrentLecturerFirebaseUid)
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        enrolledCoursesList.clear();
                        if (task.getResult().isEmpty()) {
                            noCoursesTextView.setText("You have not enrolled in any courses yet.");
                            noCoursesTextView.setVisibility(View.VISIBLE);
                            enrolledCoursesRecyclerView.setVisibility(View.GONE);
                        } else {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String faculty = document.getString("faculty");
                                String course = document.getString("course");
                                String section = document.getString("section");
                                String time = document.getString("time");
                                String enrollmentId = document.getId(); // Document ID is the enrollmentId
                                // NEW: Retrieve classLocationName
                                String classLocationName = document.getString("classLocationName");

                                // Add the course to the list, including the new location name
                                // Ensure classLocationName is not null, provide a default if it can be missing
                                if (classLocationName == null) {
                                    classLocationName = "Not specified"; // Default value if not found
                                }
                                enrolledCoursesList.add(new EnrolledCourse(faculty, course, section, time, enrollmentId, classLocationName));
                            }
                            adapter.notifyDataSetChanged();
                            enrolledCoursesRecyclerView.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Log.e(TAG, "Error fetching enrolled courses: ", task.getException());
                        Toast.makeText(LecturerChooseClassActivity.this, "Error loading your courses.", Toast.LENGTH_SHORT).show();
                        noCoursesTextView.setText("Failed to load courses. Please try again.");
                        noCoursesTextView.setVisibility(View.VISIBLE);
                    }
                });
    }

    // --- Inner Class: EnrolledCourse (Data Model) ---
    // UPDATED: Added classLocationName field
    public static class EnrolledCourse {
        String faculty;
        String course;
        String section;
        String time;
        String enrollmentId;
        String classLocationName; // NEW: Field for class location name

        // UPDATED: Constructor to include classLocationName
        public EnrolledCourse(String faculty, String course, String section, String time, String enrollmentId, String classLocationName) {
            this.faculty = faculty;
            this.course = course;
            this.section = section;
            this.time = time;
            this.enrollmentId = enrollmentId;
            this.classLocationName = classLocationName; // Initialize new field
        }

        // Existing getters
        public String getFaculty() { return faculty; }
        public String getCourse() { return course; }
        public String getSection() { return section; }
        public String getTime() { return time; }
        public String getEnrollmentId() { return enrollmentId; }
        // NEW: Getter for classLocationName
        public String getClassLocationName() { return classLocationName; }
    }

    // --- Inner Class: EnrolledCourseAdapter (RecyclerView Adapter) ---
    private class EnrolledCourseAdapter extends RecyclerView.Adapter<EnrolledCourseAdapter.CourseViewHolder> {

        private List<EnrolledCourse> courses;

        public EnrolledCourseAdapter(List<EnrolledCourse> courses) {
            this.courses = courses;
        }

        @NonNull
        @Override
        public CourseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_enrolled_course, parent, false);
            return new CourseViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CourseViewHolder holder, int position) {
            EnrolledCourse course = courses.get(position);
            holder.courseDetailsTextView.setText(
                    course.getFaculty() + " - " +
                            course.getCourse() + "\n" +
                            "Section: " + course.getSection() + "\n" +
                            "Time: " + course.getTime() + "\n" +
                            "Location: " + course.getClassLocationName() // Display location
            );

            holder.generateQrButton.setOnClickListener(v -> {
                Log.d(TAG, "Generating QR for Enrollment ID: " + course.getEnrollmentId() +
                        ", Faculty: " + course.getFaculty() +
                        ", Course: " + course.getCourse() +
                        ", Section: " + course.getSection() +
                        ", Time: " + course.getTime() +
                        ", Location: " + course.getClassLocationName()); // Log location

                Intent intent = new Intent(LecturerChooseClassActivity.this, GenerateQrCodeActivity.class);
                intent.putExtra("faculty", course.getFaculty());
                intent.putExtra("course", course.getCourse());
                intent.putExtra("section", course.getSection());
                intent.putExtra("time", course.getTime());
                intent.putExtra("enrollmentId", course.getEnrollmentId());
                intent.putExtra("classLocationName", course.getClassLocationName()); // NEW: Pass location name
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return courses.size();
        }

        public class CourseViewHolder extends RecyclerView.ViewHolder {
            TextView courseDetailsTextView;
            Button generateQrButton;

            public CourseViewHolder(@NonNull View itemView) {
                super(itemView);
                courseDetailsTextView = itemView.findViewById(R.id.courseDetailsTextView);
                generateQrButton = itemView.findViewById(R.id.generateQrButton);
            }
        }
    }
}