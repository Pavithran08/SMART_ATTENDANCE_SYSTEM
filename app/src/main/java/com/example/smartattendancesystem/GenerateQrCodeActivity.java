package com.example.smartattendancesystem;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class GenerateQrCodeActivity extends AppCompatActivity {

    private static final String TAG = "GenerateQrCodeActivity";

    private TextView qrDetailsTextView;
    private ImageView qrCodeImageView;
    private Button downloadQrButton, uploadQrButton;

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseAuth mAuth;

    private Bitmap generatedQrBitmap;

    // Data passed from previous activity
    private String faculty, course, section, time, enrollmentId;
    private String classLocationName; // NEW: To store the class location name

    private String lecturerFirebaseUid;
    private String lecturerId;
    private String lecturerUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_qr_code);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        mAuth = FirebaseAuth.getInstance();
        lecturerFirebaseUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        qrDetailsTextView = findViewById(R.id.qrDetailsTextView);
        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        downloadQrButton = findViewById(R.id.downloadQrButton);
        uploadQrButton = findViewById(R.id.uploadQrButton);

        // Retrieve data passed from previous activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            faculty = extras.getString("faculty");
            course = extras.getString("course");
            section = extras.getString("section");
            time = extras.getString("time");
            enrollmentId = extras.getString("enrollmentId");
            classLocationName = extras.getString("classLocationName"); // NEW: Retrieve location name

            // Fetch lecturer's staffId and username (if needed, otherwise they should be passed as well)
            fetchLecturerDetailsForUpload();

            // Construct the string to be encoded into the QR code
            // This data should be unique and sufficient for attendance verification
            // Now includes the selected class location name
            String qrCodeContent = String.format("FACULTY:%s;COURSE:%s;SECTION:%s;TIME:%s;ENROLLMENT_ID:%s;LOCATION_NAME:%s",
                    faculty, course, section, time, enrollmentId, classLocationName); // UPDATED FORMAT

            // Display the received data (WITHOUT Enrollment ID on screen, but INCLUDING Location)
            String displayDetails = "Faculty: " + faculty + "\n" +
                    "Course: " + course + "\n" +
                    "Section: " + section + "\n" +
                    "Time: " + time + "\n" +
                    "Location: " + classLocationName; // Display location name
            qrDetailsTextView.setText(displayDetails);

            // Generate and display the QR code
            generateAndDisplayQrCode(qrCodeContent);

        } else {
            qrDetailsTextView.setText("No class details received.");
            Toast.makeText(this, "Error: No class details.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No extras found in Intent for QR code generation.");
        }

        // Set listeners for new buttons
        downloadQrButton.setOnClickListener(v -> downloadQrCode());
        uploadQrButton.setOnClickListener(v -> uploadQrCode());

        // Setup bottom navigation buttons
        setupBottomNavigation();
    }

    private void fetchLecturerDetailsForUpload() {
        if (lecturerFirebaseUid == null) {
            Log.e(TAG, "Lecturer Firebase UID is null. Cannot fetch details for upload.");
            return;
        }

        db.collection("users").document(lecturerFirebaseUid)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        lecturerId = task.getResult().getString("userID"); // Assuming userID is the staff ID
                        lecturerUsername = task.getResult().getString("username");
                        Log.d(TAG, "Fetched lecturer details: ID=" + lecturerId + ", Username=" + lecturerUsername);
                    } else {
                        Log.e(TAG, "Failed to retrieve lecturer profile for QR upload: " + task.getException());
                        Toast.makeText(GenerateQrCodeActivity.this, "Could not retrieve lecturer profile for QR upload.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void generateAndDisplayQrCode(String text) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, "No data to generate QR code.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Attempted to generate QR with empty text.");
            return;
        }

        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = multiFormatWriter.encode(text, BarcodeFormat.QR_CODE, 500, 500, hints);

            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            generatedQrBitmap = barcodeEncoder.createBitmap(bitMatrix);

            qrCodeImageView.setImageBitmap(generatedQrBitmap);

        } catch (WriterException e) {
            Log.e(TAG, "Error generating QR code: " + e.getMessage(), e);
            Toast.makeText(this, "Error generating QR code: " + e.getMessage(), Toast.LENGTH_LONG).show();
            qrCodeImageView.setImageResource(R.drawable.ic_error_qr);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in QR code generation: " + e.getMessage(), e);
            Toast.makeText(this, "An unexpected error occurred while generating QR code.", Toast.LENGTH_LONG).show();
            qrCodeImageView.setImageResource(R.drawable.ic_error_qr);
        }
    }

    private void downloadQrCode() {
        if (generatedQrBitmap == null) {
            Toast.makeText(this, "QR Code not generated yet!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Include location name in the filename for better identification
        String fileName = String.format("QR_%s_%s_%s_%s_%s.png", faculty, course, section, time, classLocationName)
                .replace(" ", "_") // Replace spaces for valid filename
                .replace("/", "-"); // Replace slashes if any

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SmartAttendanceQR");

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        generatedQrBitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                        Toast.makeText(this, "QR Code downloaded to Gallery!", Toast.LENGTH_LONG).show();
                        Log.d(TAG, "QR Code saved to " + uri.toString());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error saving QR Code to gallery: " + e.getMessage(), e);
                    Toast.makeText(this, "Failed to download QR Code: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Failed to create media file.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Download not supported on this Android version without specific permissions.", Toast.LENGTH_LONG).show();
            Log.w(TAG, "QR download for API < 29 not implemented or permission missing.");
        }
    }

    private void uploadQrCode() {
        if (generatedQrBitmap == null) {
            Toast.makeText(this, "QR Code not generated yet!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (enrollmentId == null || enrollmentId.isEmpty()) {
            Toast.makeText(this, "Enrollment ID missing for upload.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Enrollment ID is null/empty, cannot upload QR code.");
            return;
        }
        if (lecturerFirebaseUid == null || lecturerId == null || lecturerUsername == null) {
            Toast.makeText(this, "Lecturer details incomplete for upload. Please try again.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Lecturer details (UID, ID, Username) are null. Cannot upload QR.");
            return;
        }
        if (classLocationName == null || classLocationName.isEmpty()) { // NEW: Validate location name
            Toast.makeText(this, "Class location name missing for upload.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Class location name is null/empty, cannot upload QR code metadata.");
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        generatedQrBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] data = baos.toByteArray();

        StorageReference qrCodeRef = storage.getReference()
                .child("qrcodes/" + enrollmentId + ".png");

        qrCodeRef.putBytes(data)
                .addOnSuccessListener(taskSnapshot -> {
                    qrCodeRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();
                        saveQrCodeMetadataToFirestore(downloadUrl);
                    }).addOnFailureListener(e -> {
                        Toast.makeText(GenerateQrCodeActivity.this, "Failed to get QR download URL.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to get QR download URL: " + e.getMessage(), e);
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(GenerateQrCodeActivity.this, "Failed to upload QR code: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Failed to upload QR code to Storage: " + e.getMessage(), e);
                });
    }

    private void saveQrCodeMetadataToFirestore(String downloadUrl) {
        Map<String, Object> qrCodeData = new HashMap<>();
        qrCodeData.put("enrollmentId", enrollmentId);
        qrCodeData.put("qrCodeImageUrl", downloadUrl);
        qrCodeData.put("generatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        qrCodeData.put("faculty", faculty);
        qrCodeData.put("course", course);
        qrCodeData.put("section", section);
        qrCodeData.put("time", time);
        qrCodeData.put("classLocationName", classLocationName); // NEW: Save location name to Firestore
        qrCodeData.put("lecturerFirebaseUid", lecturerFirebaseUid);
        qrCodeData.put("lecturerId", lecturerId);
        qrCodeData.put("lecturerUsername", lecturerUsername);

        db.collection("qrcodes").document(enrollmentId)
                .set(qrCodeData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(GenerateQrCodeActivity.this, "QR Code uploaded successfully!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "QR Code metadata saved to Firestore for enrollment: " + enrollmentId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(GenerateQrCodeActivity.this, "Failed to save QR metadata: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Failed to save QR code metadata to Firestore: " + e.getMessage(), e);
                });
    }

    private void setupBottomNavigation() {
        ImageButton backButton = findViewById(R.id.backButton);
        ImageButton homeButton = findViewById(R.id.homeButton);
        ImageButton profileButton = findViewById(R.id.profileButton);

        backButton.setOnClickListener(v -> finish());

        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(GenerateQrCodeActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(GenerateQrCodeActivity.this, LecturerProfileActivity.class);
            startActivity(intent);
            finish();
        });
    }
}