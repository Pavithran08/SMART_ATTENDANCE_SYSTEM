package com.example.smartattendancesystem;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.FieldValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Import the new helper classes
import com.example.smartattendancesystem.DateTimeHelper;
import com.example.smartattendancesystem.ClassTimeChecker;


public class UlearnRegistrationActivity extends AppCompatActivity {

    private static final String TAG = "UlearnRegistration";

    private Spinner ulearnFacultySpinner;
    private Spinner ulearnCourseSpinner;
    private Spinner ulearnSectionSpinner;
    private Spinner ulearnTimeSpinner;
    private Spinner ulearnLocationSpinner;
    private Button btnEnrollCourse;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private String selectedFaculty;
    private String selectedCourse;
    private String selectedSection;
    private String selectedTime; // This will hold "HH:mm - HH:mm" string
    private String selectedLocationName;

    // Variables to store actual start and end times parsed from selectedTime
    private String classStartTime = null;
    private String classEndTime = null;

    private String lecturerFirebaseUid;
    private String lecturerId;
    private String lecturerUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ulearn_registration);

        Log.d(TAG, "UlearnRegistrationActivity: onCreate started.");

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        lecturerFirebaseUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        if (lecturerFirebaseUid == null) {
            Log.e(TAG, "ERROR: lecturerFirebaseUid is NULL in onCreate! User might not be logged in or session expired.");
            Toast.makeText(this, "Lecturer not logged in. Please log in again.", Toast.LENGTH_LONG).show();
            // It's usually better to finish here if the user is not logged in
            // finish();
            // return;
        }

        fetchLecturerDetails();

        ulearnFacultySpinner = findViewById(R.id.ulearn_facultySpinner);
        ulearnCourseSpinner = findViewById(R.id.ulearn_courseSpinner);
        ulearnSectionSpinner = findViewById(R.id.ulearn_sectionSpinner);
        ulearnTimeSpinner = findViewById(R.id.ulearn_timeSpinner);
        ulearnLocationSpinner = findViewById(R.id.ulearn_locationSpinner);
        btnEnrollCourse = findViewById(R.id.btn_enroll_course);

        setupSpinners();
        setupBackButton();

        // The button click listener now calls a method that includes the time check
        btnEnrollCourse.setOnClickListener(v -> handleClassOfferingCreation());
        Log.d(TAG, "UlearnRegistrationActivity: onCreate finished.");
    }

    // Add lifecycle logging
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "UlearnRegistrationActivity: onResume()");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "UlearnRegistrationActivity: onPause()");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "UlearnRegistrationActivity: onStop()");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "UlearnRegistrationActivity: onDestroy()");
    }

    private void fetchLecturerDetails() {
        if (lecturerFirebaseUid == null) {
            Log.w(TAG, "fetchLecturerDetails: lecturerFirebaseUid is null, skipping fetch.");
            return;
        }

        db.collection("users").document(lecturerFirebaseUid)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        lecturerId = task.getResult().getString("userID");
                        lecturerUsername = task.getResult().getString("username");
                        Log.d(TAG, "Fetched lecturer details: ID=" + lecturerId + ", Username=" + lecturerUsername);
                    } else {
                        Log.e(TAG, "Failed to retrieve lecturer profile: " + task.getException());
                        Toast.makeText(UlearnRegistrationActivity.this, "Could not retrieve lecturer profile. Class creation might be incomplete.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setupSpinners() {
        fetchFacultiesFromFirestore();

        setSpinnerAdapter(ulearnCourseSpinner, new ArrayList<>(Collections.singletonList("Select Course")));
        setSpinnerAdapter(ulearnSectionSpinner, new ArrayList<>(Collections.singletonList("Select Section")));
        setSpinnerAdapter(ulearnTimeSpinner, new ArrayList<>(Collections.singletonList("Select Time")));
        setSpinnerAdapter(ulearnLocationSpinner, new ArrayList<>(Collections.singletonList("Select Location")));

        ulearnFacultySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object selectedObj = parent.getItemAtPosition(position);
                selectedFaculty = selectedObj != null ? selectedObj.toString() : null;
                Log.d(TAG, "Faculty selected: " + selectedFaculty);

                if (selectedFaculty != null &&
                        !selectedFaculty.startsWith("Select") &&
                        !selectedFaculty.startsWith("No") &&
                        !selectedFaculty.startsWith("Error")) {

                    fetchCoursesForFaculty(selectedFaculty);
                    fetchLocationsForFaculty(selectedFaculty);
                } else {
                    setSpinnerAdapter(ulearnCourseSpinner, new ArrayList<>(Collections.singletonList("Select Course")));
                    setSpinnerAdapter(ulearnSectionSpinner, new ArrayList<>(Collections.singletonList("Select Section")));
                    setSpinnerAdapter(ulearnTimeSpinner, new ArrayList<>(Collections.singletonList("Select Time")));
                    setSpinnerAdapter(ulearnLocationSpinner, new ArrayList<>(Collections.singletonList("Select Location")));
                }
                // Reset time variables when faculty changes
                classStartTime = null;
                classEndTime = null;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ulearnCourseSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object selectedCourseObj = parent.getItemAtPosition(position);
                selectedCourse = selectedCourseObj != null ? selectedCourseObj.toString() : null;
                Log.d(TAG, "Course selected: " + selectedCourse);

                Object facultyObj = ulearnFacultySpinner.getSelectedItem();
                String currentFaculty = facultyObj != null ? facultyObj.toString() : null;

                if (currentFaculty == null ||
                        currentFaculty.startsWith("Select") || currentFaculty.startsWith("No") || currentFaculty.startsWith("Error")) {
                    Log.d(TAG, "Current faculty not valid, cannot fetch courses/sections.");
                    setSpinnerAdapter(ulearnSectionSpinner, new ArrayList<>(Collections.singletonList("Select Section")));
                    setSpinnerAdapter(ulearnTimeSpinner, new ArrayList<>(Collections.singletonList("Select Time")));
                    return;
                }

                if (selectedCourse != null &&
                        !selectedCourse.startsWith("Select") &&
                        !selectedCourse.startsWith("No") &&
                        !selectedCourse.startsWith("Error")) {
                    fetchSectionsForCourse(currentFaculty, selectedCourse);
                } else {
                    setSpinnerAdapter(ulearnSectionSpinner, new ArrayList<>(Collections.singletonList("Select Section")));
                    setSpinnerAdapter(ulearnTimeSpinner, new ArrayList<>(Collections.singletonList("Select Time")));
                }
                // Reset time variables when course changes
                classStartTime = null;
                classEndTime = null;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ulearnSectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object sectionObj = parent.getItemAtPosition(position);
                selectedSection = sectionObj != null ? sectionObj.toString() : null;
                Log.d(TAG, "Section selected: " + selectedSection);

                Object facultyObj = ulearnFacultySpinner.getSelectedItem();
                Object courseObj = ulearnCourseSpinner.getSelectedItem();
                String currentFaculty = facultyObj != null ? facultyObj.toString() : null;
                String currentCourse = courseObj != null ? courseObj.toString() : null;

                if (currentFaculty == null || currentCourse == null ||
                        currentFaculty.startsWith("Select") || currentFaculty.startsWith("No") || currentFaculty.startsWith("Error") ||
                        currentCourse.startsWith("Select") || currentCourse.startsWith("No") || currentCourse.startsWith("Error")) {
                    Log.d(TAG, "Current faculty or course not valid, cannot fetch times.");
                    setSpinnerAdapter(ulearnTimeSpinner, new ArrayList<>(Collections.singletonList("Select Time")));
                    return;
                }

                if (selectedSection != null &&
                        !selectedSection.startsWith("Select") &&
                        !selectedSection.startsWith("No") &&
                        !selectedSection.startsWith("Error")) {
                    fetchTimeForSection(currentFaculty, currentCourse, selectedSection);
                } else {
                    setSpinnerAdapter(ulearnTimeSpinner, new ArrayList<>(Collections.singletonList("Select Time")));
                }
                // Reset time variables when section changes
                classStartTime = null;
                classEndTime = null;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ulearnTimeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object timeObj = parent.getItemAtPosition(position);
                selectedTime = timeObj != null ? timeObj.toString() : null;
                Log.d(TAG, "Time selected: " + selectedTime);

                // Parse selectedTime into classStartTime and classEndTime (e.g., "09:00 - 11:00")
                if (selectedTime != null && selectedTime.contains(" - ")) {
                    String[] times = selectedTime.split(" - ");
                    if (times.length == 2) {
                        classStartTime = times[0].trim();
                        classEndTime = times[1].trim();
                        Log.d(TAG, "Parsed classStartTime: " + classStartTime + ", classEndTime: " + classEndTime);
                    } else {
                        Log.w(TAG, "Selected time string format unexpected: " + selectedTime);
                        classStartTime = null;
                        classEndTime = null;
                    }
                } else {
                    classStartTime = null;
                    classEndTime = null;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ulearnLocationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object locationObj = parent.getItemAtPosition(position);
                selectedLocationName = locationObj != null ? locationObj.toString() : null;
                Log.d(TAG, "Location selected: " + selectedLocationName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void fetchFacultiesFromFirestore() {
        // Change the collection reference to "faculties"
        Log.d(TAG, "Fetching faculties from 'faculties' collection...");
        db.collection("faculties") // <-- CHANGED THIS LINE
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<String> facultyList = new ArrayList<>();
                        // Iterate through each document found in the "faculties" collection
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // The document ID itself is the faculty name (e.g., "FTKEK", "FTMK")
                            String facultyName = document.getId();
                            if (facultyName != null && !facultyName.isEmpty()) {
                                facultyList.add(facultyName);
                            }
                        }

                        if (facultyList.isEmpty()) {
                            Log.w(TAG, "No documents found in 'faculties' collection.");
                            setSpinnerAdapter(ulearnFacultySpinner, new ArrayList<>(Collections.singletonList("No Faculties Found")));
                            Toast.makeText(UlearnRegistrationActivity.this, "No faculties found.", Toast.LENGTH_SHORT).show();
                            return; // Exit if no faculties
                        }

                        Collections.sort(facultyList);
                        facultyList.add(0, "Select Faculty"); // Add "Select Faculty" at the beginning
                        setSpinnerAdapter(ulearnFacultySpinner, facultyList);
                        Log.d(TAG, "Faculties fetched successfully from 'faculties' collection: " + facultyList.size() + " items.");
                    } else {
                        Log.w(TAG, "Error getting faculties from 'faculties' collection.", task.getException());
                        Toast.makeText(UlearnRegistrationActivity.this, "Error loading faculties.", Toast.LENGTH_SHORT).show();
                        setSpinnerAdapter(ulearnFacultySpinner, new ArrayList<>(Collections.singletonList("Error loading faculties")));
                    }
                });
    }

    private void fetchCoursesForFaculty(String facultyName) {
        // Log the new fetch path
        Log.d(TAG, "Fetching courses for faculty: " + facultyName + " from faculties/" + facultyName + "/courses...");
        // Query the 'courses' subcollection under the specific faculty document
        db.collection("faculties")
                .document(facultyName) // Use the selected facultyName as the document ID
                .collection("courses") // Access the 'courses' subcollection
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<String> courseList = new ArrayList<>();
                        if (task.getResult().isEmpty()) {
                            Log.d(TAG, "No documents found in 'courses' sub-collection for faculty: " + facultyName);
                        } else {
                            // Iterate through each document found in the 'courses' subcollection
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                // The document ID itself is the course name (e.g., "BITS", "MIT")
                                String courseName = document.getId();
                                if (courseName != null && !courseName.isEmpty()) {
                                    courseList.add(courseName);
                                }
                            }
                        }

                        if (courseList.isEmpty()) {
                            // If no courses were found after iterating, display appropriate message
                            setSpinnerAdapter(ulearnCourseSpinner, new ArrayList<>(Collections.singletonList("No Courses Found")));
                            Toast.makeText(UlearnRegistrationActivity.this, "No courses found for " + facultyName + ".", Toast.LENGTH_SHORT).show();
                        } else {
                            Collections.sort(courseList);
                            courseList.add(0, "Select Course"); // Add "Select Course" at the beginning
                            setSpinnerAdapter(ulearnCourseSpinner, courseList);
                            Log.d(TAG, "Courses fetched for " + facultyName + ": " + courseList.size() + " items.");
                        }
                    } else {
                        Log.w(TAG, "Error getting courses for " + facultyName + ": ", task.getException());
                        Toast.makeText(UlearnRegistrationActivity.this, "Error loading courses for " + facultyName + ".", Toast.LENGTH_SHORT).show();
                        setSpinnerAdapter(ulearnCourseSpinner, new ArrayList<>(Collections.singletonList("Error loading courses")));
                    }
                });
    }

    private void fetchSectionsForCourse(String facultyName, String courseName) {
        Log.d(TAG, "Fetching sections for " + facultyName + "/" + courseName + " from faculties/F/courses/C/sections...");
        db.collection("faculties")
                .document(facultyName)
                .collection("courses")
                .document(courseName)
                .collection("sections")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<String> sectionList = new ArrayList<>();
                        if (task.getResult().isEmpty()) {
                            Log.d(TAG, "No documents in 'sections' sub-collection for " + courseName + " under " + facultyName);
                        } else {
                            for (DocumentSnapshot document : task.getResult()) {
                                String sectionName = document.getId();
                                if (sectionName != null && !sectionName.isEmpty()) {
                                    sectionList.add(sectionName);
                                }
                            }
                        }
                        Collections.sort(sectionList);
                        sectionList.add(0, "Select Section");
                        setSpinnerAdapter(ulearnSectionSpinner, sectionList);
                        Log.d(TAG, "Sections fetched for " + courseName + ": " + sectionList.size() + " items.");
                    } else {
                        Log.w(TAG, "Error getting sections for " + facultyName + "/" + courseName, task.getException());
                        Toast.makeText(UlearnRegistrationActivity.this, "Error loading sections.", Toast.LENGTH_SHORT).show();
                        setSpinnerAdapter(ulearnSectionSpinner, new ArrayList<>(Collections.singletonList("Error loading sections")));
                    }
                });
    }

    private void fetchTimeForSection(String facultyName, String courseName, String sectionName) {
        Log.d(TAG, "Fetching times for " + facultyName + "/" + courseName + "/" + sectionName + " from faculties/F/courses/C/sections/S/class_time...");
        db.collection("faculties")
                .document(facultyName)
                .collection("courses")
                .document(courseName)
                .collection("sections")
                .document(sectionName)
                .collection("class_time")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            List<String> timeList = new ArrayList<>();
                            if (task.getResult().isEmpty()) {
                                Log.d(TAG, "No documents in class_time sub-collection for " + sectionName);
                            } else {
                                for (DocumentSnapshot document : task.getResult()) {
                                    String timeString = document.getId(); // Assuming time string is the document ID
                                    if (timeString != null && !timeString.isEmpty()) {
                                        timeList.add(timeString);
                                    }
                                }
                            }
                            Collections.sort(timeList);
                            timeList.add(0, "Select Time");
                            setSpinnerAdapter(ulearnTimeSpinner, timeList);
                            Log.d(TAG, "Times fetched for " + sectionName + ": " + timeList.size() + " items.");
                        } else {
                            Log.w(TAG, "Error getting class times for " + sectionName + ": ", task.getException());
                            Toast.makeText(UlearnRegistrationActivity.this, "Error loading times.", Toast.LENGTH_SHORT).show();
                            setSpinnerAdapter(ulearnTimeSpinner, new ArrayList<>(Collections.singletonList("Error loading times")));
                        }
                    }
                });
    }

    private void fetchLocationsForFaculty(String facultyName) {
        Log.d(TAG, "Fetching locations for faculty: " + facultyName + " from locations/facultyname/specific_locations...");
        // Convert faculty name to lowercase for location path as per your previous code
        db.collection("locations").document(facultyName.toLowerCase())
                .collection("specific_locations")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<String> locationList = new ArrayList<>();
                        if (task.getResult().isEmpty()) {
                            Log.d(TAG, "No specific locations found for faculty: " + facultyName);
                        } else {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String locationName = document.getId(); // Assuming location name is the document ID
                                Log.d(TAG, "Found location: " + locationName);
                                if (locationName != null && !locationName.isEmpty()) {
                                    locationList.add(locationName);
                                }
                            }
                        }
                        Collections.sort(locationList);
                        locationList.add(0, "Select Location");
                        setSpinnerAdapter(ulearnLocationSpinner, locationList);
                        Log.d(TAG, "Locations fetched for " + facultyName + ": " + locationList.size() + " items.");
                    } else {
                        Log.w(TAG, "Error getting locations for faculty " + facultyName + ": ", task.getException());
                        Toast.makeText(UlearnRegistrationActivity.this, "Error loading locations.", Toast.LENGTH_SHORT).show();
                        setSpinnerAdapter(ulearnLocationSpinner, new ArrayList<>(Collections.singletonList("Error loading locations")));
                    }
                });
    }

    private void setSpinnerAdapter(Spinner spinner, List<String> dataList) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                dataList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void handleClassOfferingCreation() {
        // Validation for spinner selections
        if (selectedFaculty == null || selectedFaculty.startsWith("Select") || selectedFaculty.startsWith("No") || selectedFaculty.startsWith("Error") ||
                selectedCourse == null || selectedCourse.startsWith("Select") || selectedCourse.startsWith("No") || selectedCourse.startsWith("Error") ||
                selectedSection == null || selectedSection.startsWith("Select") || selectedSection.startsWith("No") || selectedSection.startsWith("Error") ||
                selectedTime == null || selectedTime.startsWith("Select") || selectedTime.startsWith("No") || selectedTime.startsWith("Error") ||
                selectedLocationName == null || selectedLocationName.startsWith("Select") || selectedLocationName.startsWith("No") || selectedLocationName.startsWith("Error")) {

            Toast.makeText(this, "Please select all class details, including location.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validation for lecturer details
        if (lecturerFirebaseUid == null || lecturerId == null || lecturerUsername == null) {
            Toast.makeText(this, "Lecturer details not loaded. Please try again.", Toast.LENGTH_LONG).show();
            fetchLecturerDetails(); // Attempt to re-fetch, though it's async
            return;
        }

        // Validate that classStartTime and classEndTime were successfully parsed
        if (classStartTime == null || classEndTime == null) {
            Toast.makeText(this, "Could not parse class time. Please re-select the time.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "classStartTime or classEndTime is null after time spinner selection.");
            return;
        }

        // --- NEW: Time and Date Check before creating class offering ---
        String currentDate = DateTimeHelper.getCurrentDate(); // e.g., "2025-06-24"

        // For a lecturer creating a class offering, they should be able to create it
        // regardless of whether the class is currently active or in the past.
        // The check `isClassCurrentlyActive` is primarily for the *student* attendance marking.
        //
        // If the lecturer should ONLY be able to create a class for the current day
        // AND within the active class time, then uncomment the following block:
        /*
        if (!ClassTimeChecker.isClassCurrentlyActive(currentDate, classStartTime, classEndTime)) {
            Toast.makeText(this, "You can only create class offerings for the current date and time range.", Toast.LENGTH_LONG).show();
            Log.w(TAG, "Attempted to create class outside of active time: Current Date=" + currentDate + ", Class Time=" + selectedTime);
            return;
        }
        */
        // However, typically, a lecturer should be able to schedule classes for future dates/times too.
        // If "createClassOffering" means "schedule a class for *now*," then use the check.
        // If it means "schedule a class for *any time*", then don't use the check here.
        // For now, I'll assume a lecturer can schedule any time, and this check is more for students.
        // If you intended to limit class creation to current active classes, uncomment the above block.
        // --- END NEW TIME CHECK CONSIDERATION ---


        createClassOffering(); // Proceed with class creation if all validations pass
    }

    private void createClassOffering() {
        String enrollmentId = UUID.randomUUID().toString();

        Map<String, Object> classOffering = new HashMap<>();
        classOffering.put("enrollmentId", enrollmentId);
        classOffering.put("faculty", selectedFaculty.trim());
        classOffering.put("course", selectedCourse.trim());
        classOffering.put("section", selectedSection.trim());
        classOffering.put("time", selectedTime.trim()); // Store the full time string "HH:mm - HH:mm"
        classOffering.put("classStartTime", classStartTime); // Store parsed start time
        classOffering.put("classEndTime", classEndTime);     // Store parsed end time
        classOffering.put("classDate", DateTimeHelper.getCurrentDate()); // Store the current date as the class date
        classOffering.put("classLocationName", selectedLocationName.trim());
        classOffering.put("lecturerFirebaseUid", lecturerFirebaseUid);
        classOffering.put("lecturerId", lecturerId.trim());
        classOffering.put("lecturerUsername", lecturerUsername.trim());
        classOffering.put("createdAt", FieldValue.serverTimestamp());
        classOffering.put("status", "active"); // You might want a 'scheduled' status initially

        // Save to central collection
        db.collection("classOfferings").document(enrollmentId)
                .set(classOffering)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Class offering added to central collection with ID: " + enrollmentId);

                    // Optional: Save to lecturer's own collection (good for quick lookup by lecturer)
                    db.collection("lecturers")
                            .document(lecturerId)
                            .collection("classOfferings")
                            .document(enrollmentId)
                            .set(classOffering) // Using the same map
                            .addOnSuccessListener(innerVoid -> {
                                Log.d(TAG, "Class offering also added under lecturer's own sub-collection.");
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to save in lecturer's sub-collection: " + e.getMessage(), e);
                            });

                    Toast.makeText(UlearnRegistrationActivity.this, "Class offering created successfully!", Toast.LENGTH_LONG).show();
                    finish(); // Go back to previous activity
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(UlearnRegistrationActivity.this, "Error creating class offering: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error adding class offering: " + e.getMessage(), e);
                });
    }

    private void setupBackButton() {
        ImageButton backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }
}