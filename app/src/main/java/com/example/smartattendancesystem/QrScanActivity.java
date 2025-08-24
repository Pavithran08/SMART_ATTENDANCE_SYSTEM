package com.example.smartattendancesystem;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.time.LocalTime; // Import for time handling
import java.time.format.DateTimeFormatter; // Import for time formatting
import java.time.format.DateTimeParseException; // Import for parsing exceptions
import java.util.HashMap;
import java.util.Locale; // Import for Locale
import java.util.Map;

public class QrScanActivity extends AppCompatActivity {

    private static final String TAG = "QrScanActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FusedLocationProviderClient fusedLocationClient;

    private String studentFirebaseUid, studentFaculty, studentCourse, studentMatric, studentName;
    private String scannedFaculty, scannedCourse, scannedSection, scannedTime, enrollmentId, scannedLocationName;

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if (result.getContents() == null) {
                    Toast.makeText(this, "Scan cancelled", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    processScannedQrCode(result.getContents());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        studentFirebaseUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        Intent intent = getIntent();

        studentFaculty = intent.getStringExtra("REGISTERED_FACULTY");
        studentCourse = intent.getStringExtra("REGISTERED_COURSE");
        studentMatric = intent.getStringExtra("USER_MATRIC");
        studentName = intent.getStringExtra("USER_NAME");

        Log.d(TAG, "onCreate: QrScanActivity received Intent extras:");
        Log.d(TAG, "  studentFirebaseUid: " + studentFirebaseUid);
        Log.d(TAG, "  studentFaculty: " + studentFaculty);
        Log.d(TAG, "  studentCourse: " + studentCourse);
        Log.d(TAG, "  studentMatric: " + studentMatric);
        Log.d(TAG, "  studentName: " + studentName);


        if (studentFirebaseUid == null || studentFaculty == null || studentCourse == null || studentMatric == null || studentName == null) {
            Log.e(TAG, "onCreate: One or more student profile details are NULL. Displaying toast and finishing.");
            Toast.makeText(this, "Student profile not fully loaded. Please re-login.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.d(TAG, "onCreate: All student profile details loaded. Proceeding with scan.");
        checkLocationPermissionsAndStartScan();
    }

    private void checkLocationPermissionsAndStartScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "checkLocationPermissionsAndStartScan: Location permission already granted. Starting QR scan.");
            startQrScan();
        } else {
            Log.d(TAG, "checkLocationPermissionsAndStartScan: Requesting location permission.");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "onRequestPermissionsResult: Location permission granted. Starting QR scan.");
                startQrScan();
            } else {
                Log.w(TAG, "onRequestPermissionsResult: Location permission denied. Finishing activity.");
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startQrScan() {
        Log.d(TAG, "startQrScan: Launching barcode scanner.");
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan a class QR code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        barcodeLauncher.launch(options);
    }

    private void processScannedQrCode(String qrContent) {
        Log.d(TAG, "processScannedQrCode: QR content received: " + qrContent);
        Map<String, String> qrData = parseQrContent(qrContent);

        scannedFaculty = qrData.get("FACULTY");
        scannedCourse = qrData.get("COURSE");
        scannedSection = qrData.get("SECTION");
        scannedTime = qrData.get("TIME"); // This will be like "08:00 - 10:00"
        enrollmentId = qrData.get("ENROLLMENT_ID");
        scannedLocationName = qrData.get("LOCATION_NAME");

        Log.d(TAG, "processScannedQrCode: Parsed QR data: " + qrData.toString());

        if (scannedFaculty == null || scannedCourse == null || scannedSection == null ||
                scannedTime == null || enrollmentId == null || scannedLocationName == null) {
            Log.e(TAG, "processScannedQrCode: QR code missing required fields. Finishing activity.");
            Toast.makeText(this, "QR code missing required fields.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // --- START: NEW TIME CHECK INTEGRATION ---
        if (!isWithinAttendanceTime(scannedTime)) {
            Log.w(TAG, "processScannedQrCode: Attendance time window has passed or not yet started for " + scannedTime);
            Toast.makeText(this, "Attendance is only open during the class time (and a small grace period).", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // --- END: NEW TIME CHECK INTEGRATION ---


        /*
        // Re-enable this section if you want to enforce class/course matching
        if (!scannedFaculty.equalsIgnoreCase(studentFaculty) || !scannedCourse.equalsIgnoreCase(studentCourse)) {
            Log.w(TAG, "processScannedQrCode: Class mismatch. Scanned Faculty: " + scannedFaculty + ", Student Faculty: " + studentFaculty +
                    "; Scanned Course: " + scannedCourse + ", Student Course: " + studentCourse);
            Toast.makeText(this, "Class does not match your registered course.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        */

        Log.d(TAG, "processScannedQrCode: Class matched and time is valid. Fetching class location.");
        fetchClassLocation(scannedFaculty, scannedLocationName);
    }

    private Map<String, String> parseQrContent(String content) {
        Map<String, String> data = new HashMap<>();
        if (content == null || content.trim().isEmpty()) return data;
        for (String part : content.split(";")) {
            String[] entry = part.split(":", 2);
            if (entry.length == 2) {
                data.put(entry[0].trim().toUpperCase(), entry[1].trim());
            }
        }
        return data;
    }

    // --- START: NEW METHOD FOR TIME VALIDATION ---
    private boolean isWithinAttendanceTime(String scannedTimeString) {
        if (scannedTimeString == null || scannedTimeString.isEmpty()) {
            Log.e(TAG, "isWithinAttendanceTime: scannedTimeString is null or empty.");
            return false;
        }

        try {
            // *** IMPORTANT: Formatter for 24-hour time (e.g., "08:00") ***
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US);

            String[] times = scannedTimeString.split(" - ");
            if (times.length != 2) {
                Log.e(TAG, "isWithinAttendanceTime: Invalid scannedTimeString format (expected 'HH:mm - HH:mm'): " + scannedTimeString);
                Toast.makeText(this, "Invalid QR time format.", Toast.LENGTH_LONG).show();
                return false;
            }

            LocalTime classStartTime = LocalTime.parse(times[0].trim(), formatter);
            LocalTime classEndTime = LocalTime.parse(times[1].trim(), formatter);
            LocalTime currentTime = LocalTime.now(); // Get current device time

            // Define a grace period (e.g., 15 minutes before start, 15 minutes after end)
            int gracePeriodMinutes = 15; // You can make this configurable
            LocalTime effectiveStartTime = classStartTime.minusMinutes(gracePeriodMinutes);
            LocalTime effectiveEndTime = classEndTime.plusMinutes(gracePeriodMinutes);

            // Determine if the class time range crosses midnight (e.g., 22:00 - 00:00)
            boolean crossesMidnight = classEndTime.isBefore(classStartTime);

            boolean isWithin;
            if (crossesMidnight) {
                // If the class crosses midnight (e.g., 22:00 - 02:00), it means it spans two "days" from a LocalTime perspective.
                // We need to check if current time is after the start time (on the first "day") OR before the end time (on the second "day").
                isWithin = (currentTime.isAfter(effectiveStartTime) || currentTime.equals(effectiveStartTime)) ||
                        (currentTime.isBefore(effectiveEndTime) || currentTime.equals(effectiveEndTime));
            } else {
                // Normal case: class does not cross midnight (e.g., 08:00 - 10:00)
                isWithin = !currentTime.isBefore(effectiveStartTime) && !currentTime.isAfter(effectiveEndTime);
            }

            Log.d(TAG, "isWithinAttendanceTime: Class Start Time: " + classStartTime);
            Log.d(TAG, "isWithinAttendanceTime: Class End Time: " + classEndTime);
            Log.d(TAG, "isWithinAttendanceTime: Current Device Time: " + currentTime);
            Log.d(TAG, "isWithinAttendanceTime: Effective Start Time (with grace): " + effectiveStartTime);
            Log.d(TAG, "isWithinAttendanceTime: Effective End Time (with grace): " + effectiveEndTime);
            Log.d(TAG, "isWithinAttendanceTime: Class Crosses Midnight: " + crossesMidnight);
            Log.d(TAG, "isWithinAttendanceTime: Is Current Time Within Window: " + isWithin);

            return isWithin;

        } catch (DateTimeParseException e) {
            Log.e(TAG, "isWithinAttendanceTime: Error parsing time string: " + scannedTimeString, e);
            Toast.makeText(this, "QR code time format is invalid. Please inform lecturer.", Toast.LENGTH_LONG).show();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "isWithinAttendanceTime: An unexpected error occurred during time validation: " + e.getMessage(), e);
            Toast.makeText(this, "An error occurred during time validation.", Toast.LENGTH_LONG).show();
            return false;
        }
    }
    // --- END: NEW METHOD FOR TIME VALIDATION ---

    private void fetchClassLocation(String faculty, String locationName) {
        Log.d(TAG, "fetchClassLocation: Fetching location for faculty: " + faculty + ", location: " + locationName);

        db.collection("locations")
                .document(faculty.toLowerCase())
                .collection("specific_locations")
                .document(locationName)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            Double lat = document.getDouble("latitude");
                            Double lng = document.getDouble("longitude");
                            Long radiusLong = document.getLong("radius");
                            double radius = radiusLong != null ? radiusLong.doubleValue() : 0;

                            Log.d(TAG, "fetchClassLocation: Location data fetched: Lat=" + lat + ", Lng=" + lng + ", Radius=" + radius);

                            if (lat != null && lng != null && radius > 0) {
                                getCurrentLocationAndVerify(lat, lng, radius);
                            } else {
                                Log.e(TAG, "fetchClassLocation: Incomplete location data from Firestore for " + locationName);
                                Toast.makeText(this, "Incomplete location data.", Toast.LENGTH_LONG).show();
                                finish();
                            }
                        } else {
                            Log.e(TAG, "fetchClassLocation: Location document not found for " + locationName);
                            Toast.makeText(this, "Location not found in database.", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    } else {
                        Log.e(TAG, "fetchClassLocation: Error getting location document: ", task.getException());
                        Toast.makeText(this, "Error fetching location data.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }

    private void getCurrentLocationAndVerify(double classLat, double classLng, double classRadius) {
        Log.d(TAG, "getCurrentLocationAndVerify: Verifying current location against class location.");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "getCurrentLocationAndVerify: Location permission not granted during verification.");
            Toast.makeText(this, "Location permission not granted.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        float[] distance = new float[1];
                        Location.distanceBetween(location.getLatitude(), location.getLongitude(), classLat, classLng, distance);
                        Log.d(TAG, "getCurrentLocationAndVerify: User Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude() +
                                ", Class Lat: " + classLat + ", Lng: " + classLng + ", Distance: " + distance[0] + ", Radius: " + classRadius);

                        if (distance[0] <= classRadius) {
                            Log.d(TAG, "getCurrentLocationAndVerify: User is within class location range. Checking if already marked.");
                            checkIfAlreadyMarked();
                        } else {
                            Log.w(TAG, "getCurrentLocationAndVerify: User is outside the allowed class location range. Distance: " + distance[0] + "m, Allowed: " + classRadius + "m.");
                            Toast.makeText(this, "You are outside the allowed class location range.", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    } else {
                        Log.e(TAG, "getCurrentLocationAndVerify: Could not determine user's last known location. Location object is null.");
                        Toast.makeText(this, "Could not determine location.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getCurrentLocationAndVerify: Failed to get last location.", e);
                    Toast.makeText(this, "Failed to get current location: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void checkIfAlreadyMarked() {
        Log.d(TAG, "checkIfAlreadyMarked: Checking attendance record for matric: " + studentMatric + ", enrollmentId: " + enrollmentId);
        db.collection("attendance")
                .whereEqualTo("studentMatric", studentMatric)
                .whereEqualTo("enrollmentId", enrollmentId)
                .get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        Log.d(TAG, "checkIfAlreadyMarked: Attendance already recorded.");
                        Toast.makeText(this, "Attendance already recorded.", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Log.d(TAG, "checkIfAlreadyMarked: Attendance not yet recorded. Proceeding to record.");
                        recordAttendance();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "checkIfAlreadyMarked: Error checking attendance.", e);
                    Toast.makeText(this, "Error checking attendance: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void recordAttendance() {
        Log.d(TAG, "recordAttendance: Recording attendance for student: " + studentName + " (" + studentMatric + ")");
        Map<String, Object> attendance = new HashMap<>();
        attendance.put("studentFirebaseUid", studentFirebaseUid);
        attendance.put("studentMatric", studentMatric);
        attendance.put("studentName", studentName);
        attendance.put("enrollmentId", enrollmentId);
        attendance.put("faculty", scannedFaculty);
        attendance.put("course", scannedCourse);
        attendance.put("section", scannedSection);
        attendance.put("time", scannedTime); // Save the scanned time string
        attendance.put("classLocationName", scannedLocationName);
        attendance.put("timestamp", FieldValue.serverTimestamp()); // Use server timestamp for the actual record time
        attendance.put("status", "present");

        db.collection("attendance")
                .add(attendance)
                .addOnSuccessListener(doc -> {
                    Log.d(TAG, "recordAttendance: Attendance recorded successfully! Document ID: " + doc.getId());
                    Intent intent = new Intent(QrScanActivity.this, AttendanceActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "recordAttendance: Failed to record attendance.", e);
                    Toast.makeText(this, "Failed to record attendance. Please try again: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }
}