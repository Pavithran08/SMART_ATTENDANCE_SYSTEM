package com.example.smartattendancesystem;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner; // Import Spinner
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";
    private static final int REQUEST_IMAGE_CAPTURE = 100;

    private EditText edtUsername, edtEmail, edtPassword, edtConfirmPassword, edtMatricOrStaffId;
    private Button btnRegister;
    private Spinner spinnerFaculty, spinnerCourse, spinnerYear; // Declare Spinners

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private String role;

    private FaceDetector faceDetector;
    private FaceRecognitionHelper faceRecognitionHelper;
    private ExecutorService executorService;

    // Data for spinners
    private List<String> facultyList = new ArrayList<>();
    private Map<String, List<String>> coursesByFaculty = new HashMap<>(); // To store courses mapped to faculties
    private List<String> yearList = new ArrayList<>();

    private ArrayAdapter<String> facultyAdapter;
    private ArrayAdapter<String> courseAdapter;
    private ArrayAdapter<String> yearAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        faceDetector = FaceDetection.getClient(options);

        try {
            faceRecognitionHelper = new FaceRecognitionHelper(this, "output_model.tflite");
            Log.d(TAG, "FaceRecognitionHelper initialized successfully.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize FaceRecognitionHelper: " + e.getMessage());
            Toast.makeText(this, "Error: Face recognition model not loaded.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        executorService = Executors.newSingleThreadExecutor();

        edtUsername = findViewById(R.id.edt_username);
        edtEmail = findViewById(R.id.edt_email);
        edtPassword = findViewById(R.id.edt_password);
        edtConfirmPassword = findViewById(R.id.edt_confirm_password);
        edtMatricOrStaffId = findViewById(R.id.edt_matric_or_staff_id);
        btnRegister = findViewById(R.id.btn_register);

        // Initialize Spinners
        spinnerFaculty = findViewById(R.id.spinner_faculty);
        spinnerCourse = findViewById(R.id.spinner_course);
        spinnerYear = findViewById(R.id.spinner_year);

        setupSpinners(); // Call method to set up adapters and listeners
        loadSpinnerDataFromFirestore(); // Load data for spinners

        btnRegister.setOnClickListener(v -> {
            String username = edtUsername.getText().toString().trim();
            String email = edtEmail.getText().toString().trim();
            String password = edtPassword.getText().toString().trim();
            String confirmPassword = edtConfirmPassword.getText().toString().trim();
            String matricOrStaffId = edtMatricOrStaffId.getText().toString().trim();

            // Get selected values from Spinners
            String selectedFaculty = "N/A"; // Default for non-students
            String selectedCourse = "N/A";  // Default for non-students
            String selectedYear = "N/A";    // Default for non-students

            // Determine the role based on the matric/staff ID input
            role = getRoleFromMatric(matricOrStaffId);

            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || matricOrStaffId.isEmpty()) {
                Toast.makeText(RegisterActivity.this, "Please enter all fields", Toast.LENGTH_SHORT).show();
            } else if (!password.equals(confirmPassword)) {
                Toast.makeText(RegisterActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            } else if (role == null) { // Check for valid matric/staff ID prefix
                Toast.makeText(RegisterActivity.this, "Invalid matric number or staff ID prefix (B, S, or A)", Toast.LENGTH_SHORT).show();
            } else {
                // If the user is a student, validate spinner selections
                if (role.equals("Student")) {
                    selectedFaculty = spinnerFaculty.getSelectedItem().toString();
                    selectedCourse = spinnerCourse.getSelectedItem().toString();
                    selectedYear = spinnerYear.getSelectedItem().toString();

                    if (selectedFaculty.equals("Select Faculty") || selectedCourse.equals("Select Course") || selectedYear.equals("Select Year")) {
                        Toast.makeText(RegisterActivity.this, "Students must select Faculty, Course, and Year", Toast.LENGTH_SHORT).show();
                        return; // Stop registration if student fields are not selected
                    }
                }
                // Store selected values temporarily to be used after face capture
                getApplication().getSharedPreferences("reg_data", MODE_PRIVATE).edit()
                        .putString("selectedFaculty", selectedFaculty)
                        .putString("selectedCourse", selectedCourse)
                        .putString("selectedYear", selectedYear)
                        .apply();

                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Firebase Auth registration successful.");
                                captureFace(); // Proceed to face capture
                            } else {
                                Log.e(TAG, "Firebase Auth registration failed: " + task.getException().getMessage());
                                Toast.makeText(RegisterActivity.this, "Registration failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });
    }

    /**
     * Sets up the adapters for the spinners and their listeners.
     */
    private void setupSpinners() {
        // Initialize adapters with a default "Select..." item
        facultyList.add("Select Faculty");
        facultyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, facultyList);
        facultyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFaculty.setAdapter(facultyAdapter);

        // Course spinner starts empty (except for hint) and will be populated based on faculty selection
        List<String> initialCourseList = new ArrayList<>();
        initialCourseList.add("Select Course");
        courseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, initialCourseList);
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCourse.setAdapter(courseAdapter);

        yearList.add("Select Year");
        yearAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, yearList);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(yearAdapter);

        // Listener for Faculty Spinner to update Course Spinner
        spinnerFaculty.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedFaculty = parent.getItemAtPosition(position).toString();
                updateCoursesSpinner(selectedFaculty);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Add a TextWatcher to edtMatricOrStaffId to dynamically show/hide spinners
        edtMatricOrStaffId.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String currentRole = getRoleFromMatric(s.toString().trim());
                if ("Student".equals(currentRole)) {
                    spinnerFaculty.setVisibility(View.VISIBLE);
                    spinnerCourse.setVisibility(View.VISIBLE);
                    spinnerYear.setVisibility(View.VISIBLE);
                } else {
                    spinnerFaculty.setVisibility(View.GONE);
                    spinnerCourse.setVisibility(View.GONE);
                    spinnerYear.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Initially hide them until a role is determined (or show them if default is student)
        spinnerFaculty.setVisibility(View.GONE);
        spinnerCourse.setVisibility(View.GONE);
        spinnerYear.setVisibility(View.GONE);
    }

    /**
     * Loads faculty, course, and year data from Firestore.
     */
    private void loadSpinnerDataFromFirestore() {
        // Load Faculties
        db.collection("app_data").document("faculties")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("names")) {
                        List<String> names = (List<String>) documentSnapshot.get("names");
                        if (names != null) {
                            facultyList.clear();
                            facultyList.add("Select Faculty"); // Re-add hint
                            facultyList.addAll(names);
                            facultyAdapter.notifyDataSetChanged();
                            Log.d(TAG, "Faculties loaded: " + facultyList);
                        }
                    } else {
                        Log.d(TAG, "Faculties document not found or 'names' field is missing.");
                        Toast.makeText(this, "Error loading faculties.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading faculties: " + e.getMessage());
                    Toast.makeText(this, "Error loading faculties.", Toast.LENGTH_SHORT).show();
                });

        // Load Courses (all at once and store in map for dynamic loading)
        db.collection("app_data").document("courses")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> data = documentSnapshot.getData();
                        if (data != null) {
                            for (Map.Entry<String, Object> entry : data.entrySet()) {
                                String facultyName = entry.getKey();
                                List<String> courses = (List<String>) entry.getValue();
                                if (courses != null) {
                                    coursesByFaculty.put(facultyName, courses);
                                }
                            }
                            Log.d(TAG, "Courses data loaded: " + coursesByFaculty.size() + " faculties mapped.");
                            // Update course spinner based on initial faculty selection (or hint)
                            if (!facultyList.isEmpty()) {
                                updateCoursesSpinner(facultyList.get(0)); // Update with "Select Faculty" initially
                            }
                        }
                    } else {
                        Log.d(TAG, "Courses document not found.");
                        Toast.makeText(this, "Error loading courses.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading courses: " + e.getMessage());
                    Toast.makeText(this, "Error loading courses.", Toast.LENGTH_SHORT).show();
                });

        // Load Years
        db.collection("app_data").document("years")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("values")) {
                        List<String> values = (List<String>) documentSnapshot.get("values");
                        if (values != null) {
                            yearList.clear();
                            yearList.add("Select Year"); // Re-add hint
                            yearList.addAll(values);
                            yearAdapter.notifyDataSetChanged();
                            Log.d(TAG, "Years loaded: " + yearList);
                        }
                    } else {
                        Log.d(TAG, "Years document not found or 'values' field is missing.");
                        Toast.makeText(this, "Error loading years.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading years: " + e.getMessage());
                    Toast.makeText(this, "Error loading years.", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Updates the course spinner based on the selected faculty.
     * @param selectedFaculty The name of the selected faculty.
     */
    private void updateCoursesSpinner(String selectedFaculty) {
        courseAdapter.clear();
        courseAdapter.add("Select Course"); // Always add the hint first

        List<String> courses = coursesByFaculty.get(selectedFaculty);
        if (courses != null) {
            courseAdapter.addAll(courses);
        }
        courseAdapter.notifyDataSetChanged();
        Log.d(TAG, "Courses updated for faculty: " + selectedFaculty);
    }


    /**
     * Determines the user's role based on the prefix of their matric or staff ID.
     * @param matricOrStaffId The matric number or staff ID.
     * @return "Student" if prefix is "B", "Lecturer" if "S", "Admin" if "A", otherwise null.
     */
    private String getRoleFromMatric(String matricOrStaffId) {
        if (matricOrStaffId.startsWith("B")) {
            return "Student";
        } else if (matricOrStaffId.startsWith("S")) {
            return "Lecturer";
        } else if (matricOrStaffId.startsWith("A")) {
            return "Admin";
        }
        return null;
    }

    private void captureFace() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, REQUEST_IMAGE_CAPTURE);
            Log.d(TAG, "Camera intent launched.");
        } else {
            Toast.makeText(this, "No camera app found.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No camera app found.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK && data != null && data.getExtras() != null) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            if (photo != null) {
                Log.d(TAG, "Photo captured. Starting face processing...");
                processAndSaveFaceData(photo);
            } else {
                Toast.makeText(this, "Failed to capture image. Photo is null.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Captured photo bitmap is null.");
            }
        }
    }

    private void processAndSaveFaceData(Bitmap photo) {
        InputImage image = InputImage.fromBitmap(photo, 0);

        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    if (!faces.isEmpty()) {
                        Face face = faces.get(0);
                        Log.d(TAG, "Face detected. Bounding Box: " + face.getBoundingBox().toShortString());

                        Bitmap croppedFaceBitmap = cropFaceFromBitmap(photo, face.getBoundingBox());

                        if (croppedFaceBitmap != null) {
                            executorService.execute(() -> {
                                Log.d(TAG, "Generating face embedding...");
                                float[] faceEmbedding = faceRecognitionHelper.getFaceEmbedding(croppedFaceBitmap);

                                if (faceEmbedding != null) {
                                    Log.d(TAG, "Face embedding generated successfully. Size: " + faceEmbedding.length);
                                    runOnUiThread(() -> saveFaceImageAndEmbedding(photo, faceEmbedding));
                                } else {
                                    runOnUiThread(() -> Toast.makeText(RegisterActivity.this, "Failed to generate face embedding.", Toast.LENGTH_SHORT).show());
                                }
                            });
                        } else {
                            Toast.makeText(RegisterActivity.this, "Failed to crop face from image.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(RegisterActivity.this, "No face detected in the image. Please try again.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(RegisterActivity.this, "Face detection failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private Bitmap cropFaceFromBitmap(Bitmap sourceBitmap, android.graphics.Rect boundingBox) {
        int x = boundingBox.left;
        int y = boundingBox.top;
        int width = boundingBox.width();
        int height = boundingBox.height();

        // Ensure the bounding box is within the bitmap's bounds to prevent IllegalArgumentException
        x = Math.max(0, x);
        y = Math.max(0, y);
        width = Math.min(width, sourceBitmap.getWidth() - x);
        height = Math.min(height, sourceBitmap.getHeight() - y);

        if (width <= 0 || height <= 0) {
            Log.e(TAG, "Invalid crop dimensions: width=" + width + ", height=" + height + ". Original Bitmap: " + sourceBitmap.getWidth() + "x" + sourceBitmap.getHeight());
            return null; // Return null or handle this case appropriately
        }
        return Bitmap.createBitmap(sourceBitmap, x, y, width, height);
    }


    private void saveFaceImageAndEmbedding(Bitmap photo, float[] faceEmbedding) {
        String userId = mAuth.getCurrentUser().getUid(); // Firebase Auth UID will be the Firestore Document ID

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        photo.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] imageData = baos.toByteArray();

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference().child("face_images/" + userId + ".jpg");

        storageRef.putBytes(imageData)
                .addOnSuccessListener(taskSnapshot -> {
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String faceImageUrl = uri.toString();
                        saveUserDataToFirestore(userId, faceImageUrl, faceEmbedding);
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(RegisterActivity.this, "Error saving face image to storage: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Optional: Delete Firebase Auth user if image upload fails
                    mAuth.getCurrentUser().delete().addOnCompleteListener(deleteTask -> {
                        if (deleteTask.isSuccessful()) {
                            Log.d(TAG, "Firebase user deleted due to face image upload failure.");
                        } else {
                            Log.e(TAG, "Failed to delete Firebase user after image error: " + deleteTask.getException().getMessage());
                        }
                    });
                });
    }

    private void saveUserDataToFirestore(String userId, String faceImageUrl, float[] faceEmbedding) {
        // Retrieve selected values from SharedPreferences
        // These values were stored just before calling captureFace()
        String selectedFaculty = getApplication().getSharedPreferences("reg_data", MODE_PRIVATE).getString("selectedFaculty", "N/A");
        String selectedCourse = getApplication().getSharedPreferences("reg_data", MODE_PRIVATE).getString("selectedCourse", "N/A");
        String selectedYear = getApplication().getSharedPreferences("reg_data", MODE_PRIVATE).getString("selectedYear", "N/A");

        // Convert faceEmbedding (float[]) to List<Double> for Firestore compatibility
        List<Double> embeddingList = new ArrayList<>();
        for (float f : faceEmbedding) {
            embeddingList.add((double) f);
        }

        String customUserIdFromInput = edtMatricOrStaffId.getText().toString().trim();
        String currentRole = getRoleFromMatric(customUserIdFromInput); // Re-determine role for safety

        // Prepare the user object to save to Firestore
        User user = new User(
                edtEmail.getText().toString(),
                embeddingList,
                faceImageUrl,
                faceImageUrl, // faceImageLoginUrl (using same as general face image for now)
                customUserIdFromInput, // matricOrStaffId
                edtPassword.getText().toString(), // password (still here, but strongly consider security implications)
                "", // profileImageUrl (empty for now)
                currentRole, // role
                userId, // id (Firestore Document ID - Firebase Auth UID)
                customUserIdFromInput, // userID (Your custom user ID, duplicated from input matricOrStaffId)
                edtUsername.getText().toString(),
                selectedFaculty, // Pass the selected faculty
                selectedCourse,  // Pass the selected course
                selectedYear     // Pass the selected year
        );

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(userId) // Use Firebase Auth UID as the document ID
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_LONG).show();
                    // Clear SharedPreferences data after successful save
                    getApplication().getSharedPreferences("reg_data", MODE_PRIVATE).edit().clear().apply();
                    // Navigate to success screen or another activity
                    Intent intent = new Intent(RegisterActivity.this, SuccessActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(RegisterActivity.this, "Error saving user data to Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Optional: Delete Firebase Auth user if Firestore save fails
                    mAuth.getCurrentUser().delete().addOnCompleteListener(deleteTask -> {
                        if (deleteTask.isSuccessful()) {
                            Log.d(TAG, "Firebase user deleted due to Firestore save failure.");
                        } else {
                            Log.e(TAG, "Failed to delete Firebase user after Firestore error: " + deleteTask.getException().getMessage());
                        }
                    });
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceRecognitionHelper != null) {
            faceRecognitionHelper.close();
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}