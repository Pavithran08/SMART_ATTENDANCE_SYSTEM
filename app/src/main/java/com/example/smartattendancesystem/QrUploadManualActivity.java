package com.example.smartattendancesystem;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QrUploadManualActivity extends AppCompatActivity {

    private static final String TAG = "QrUploadManualActivity";
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int READ_EXTERNAL_STORAGE_PERMISSION_CODE = 2;

    private EditText facultyEditText, courseEditText, sectionEditText, timeEditText;
    private ImageView selectedQrImageView;
    private Button selectImageButton, uploadManualButton, closeManualUploadButton;

    private Uri selectedImageUri; // To store the URI of the selected image

    // Firebase
    private FirebaseStorage firebaseStorage;
    private StorageReference storageReference;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_upload_manual); // Ensure this matches your XML file name

        // Initialize UI elements
        facultyEditText = findViewById(R.id.facultyEditText);
        courseEditText = findViewById(R.id.courseEditText);
        sectionEditText = findViewById(R.id.sectionEditText);
        timeEditText = findViewById(R.id.timeEditText);
        selectedQrImageView = findViewById(R.id.selectedQrImageView);
        selectImageButton = findViewById(R.id.selectImageButton);
        uploadManualButton = findViewById(R.id.uploadManualButton);
        closeManualUploadButton = findViewById(R.id.closeManualUploadButton);

        // Initialize Firebase
        firebaseStorage = FirebaseStorage.getInstance();
        storageReference = firebaseStorage.getReference();
        db = FirebaseFirestore.getInstance();

        setupListeners();
    }

    private void setupListeners() {
        selectImageButton.setOnClickListener(v -> {
            requestGalleryPermissionAndOpen();
        });

        uploadManualButton.setOnClickListener(v -> {
            uploadDataAndImage();
        });

        closeManualUploadButton.setOnClickListener(v -> onBackPressed()); // Go back to previous activity
    }

    // --- GALLERY IMAGE SELECTION ---
    private void requestGalleryPermissionAndOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // No explicit storage permission needed for Android 13 (API 33) and above
            // for media access when using ACTION_PICK.
            openImageChooser();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        READ_EXTERNAL_STORAGE_PERMISSION_CODE);
            } else {
                openImageChooser();
            }
        }
    }

    private void openImageChooser() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImageChooser();
            } else {
                Toast.makeText(this, "Permission denied to read storage. Cannot select image.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            try {
                // Display the selected image
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                selectedQrImageView.setImageBitmap(bitmap);
                Toast.makeText(this, "Image selected!", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(TAG, "Error loading image: " + e.getMessage());
                Toast.makeText(this, "Failed to load image: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- UPLOAD DATA AND IMAGE TO FIREBASE ---
    private void uploadDataAndImage() {
        String faculty = facultyEditText.getText().toString().trim();
        String course = courseEditText.getText().toString().trim();
        String section = sectionEditText.getText().toString().trim();
        String time = timeEditText.getText().toString().trim();

        // Basic validation
        if (faculty.isEmpty() || course.isEmpty() || section.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, "Please fill all class details.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select a QR code image first.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Uploading QR data and image...", Toast.LENGTH_LONG).show();

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] imageData = baos.toByteArray();

            String imagePath = "manual_qrcodes/" + UUID.randomUUID().toString() + ".png";
            StorageReference imageRef = storageReference.child(imagePath);

            UploadTask uploadTask = imageRef.putBytes(imageData);
            uploadTask.addOnSuccessListener(taskSnapshot -> {
                imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String imageUrl = uri.toString();
                    Log.d(TAG, "Image uploaded. URL: " + imageUrl);
                    saveQrDetailsToFirestore(faculty, course, section, time, imageUrl);
                }).addOnFailureListener(e -> {
                    Toast.makeText(QrUploadManualActivity.this, "Failed to get image URL.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to get image download URL: " + e.getMessage());
                });
            }).addOnFailureListener(e -> {
                Toast.makeText(QrUploadManualActivity.this, "Image upload failed.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Image upload to Firebase Storage failed: " + e.getMessage());
            });

        } catch (IOException e) {
            Toast.makeText(this, "Error processing image for upload.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error converting image URI to bitmap: " + e.getMessage());
        }
    }

    private void saveQrDetailsToFirestore(String faculty, String course, String section, String time, String imageUrl) {
        Map<String, Object> qrData = new HashMap<>();
        qrData.put("faculty", faculty);
        qrData.put("course", course);
        qrData.put("section", section);
        qrData.put("class_time", time);
        qrData.put("qr_image_url", imageUrl);
        qrData.put("uploaded_at", FieldValue.serverTimestamp()); // Timestamp of upload

        // You might want to add a field to distinguish between generated and manually uploaded QRs, e.g.,
        // qrData.put("source", "manual_upload");

        db.collection("qrcode").add(qrData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(QrUploadManualActivity.this, "QR details saved successfully!", Toast.LENGTH_LONG).show();
                    Log.d(TAG, "QR data saved to Firestore with ID: " + documentReference.getId());
                    // Optionally, clear fields or close activity after successful upload
                    clearFields();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(QrUploadManualActivity.this, "Error saving QR details to database.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error saving QR data to Firestore: " + e.getMessage());
                });
    }

    private void clearFields() {
        facultyEditText.setText("");
        courseEditText.setText("");
        sectionEditText.setText("");
        timeEditText.setText("");
        selectedQrImageView.setImageResource(android.R.color.transparent); // Clear image view
        selectedImageUri = null;
    }
}