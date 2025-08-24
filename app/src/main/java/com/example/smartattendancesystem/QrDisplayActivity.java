package com.example.smartattendancesystem;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// Remove Firebase imports if you're not doing direct upload here anymore
// import com.google.firebase.firestore.FirebaseFirestore;
// import com.google.firebase.storage.FirebaseStorage;
// import com.google.firebase.storage.StorageReference;
// import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
// Remove unnecessary Firebase-related imports if not used
// import java.util.HashMap;
// import java.util.Map;
// import java.util.UUID;


public class QrDisplayActivity extends AppCompatActivity {

    private static final String TAG = "QrDisplayActivity";
    private static final int WRITE_EXTERNAL_STORAGE_PERMISSION_CODE = 1;
    private ImageView qrCodeImageView;
    private Button downloadQrButton;
    private Button uploadQrButton; // This button's action will change
    private Button closeQrButton;

    private Bitmap qrBitmap; // Still store the bitmap for download
    // private String qrCodeDataJson; // No longer needed if not directly uploading from here

    // Firebase instances are no longer needed directly in this activity
    // private FirebaseStorage firebaseStorage;
    // private StorageReference storageReference;
    // private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_display);

        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        downloadQrButton = findViewById(R.id.downloadQrButton);
        uploadQrButton = findViewById(R.id.uploadQrButton); // Find the button
        closeQrButton = findViewById(R.id.closeQrButton);

        // Firebase initialization is no longer needed here if direct upload is removed
        // firebaseStorage = FirebaseStorage.getInstance();
        // storageReference = firebaseStorage.getReference();
        // db = FirebaseFirestore.getInstance();

        // Get the bitmap from the intent
        qrBitmap = getIntent().getParcelableExtra("QR_BITMAP");
        // We no longer need qrCodeDataJson here if direct upload is removed
        // qrCodeDataJson = getIntent().getStringExtra("QR_DATA_JSON");

        if (qrBitmap != null) {
            qrCodeImageView.setImageBitmap(qrBitmap);
        } else {
            qrCodeImageView.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            Toast.makeText(this, "QR code image could not be loaded.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "QR_BITMAP was null in QrDisplayActivity intent.");
        }

        setupListeners();
    }

    private void setupListeners() {
        downloadQrButton.setOnClickListener(v -> {
            requestStoragePermissionAndSaveQr();
        });

        // --- CHANGE HERE: Launch new QrUploadManualActivity ---
        uploadQrButton.setOnClickListener(v -> {
            Intent intent = new Intent(QrDisplayActivity.this, QrUploadManualActivity.class);
            startActivity(intent);
        });
        // --- END CHANGE ---

        closeQrButton.setOnClickListener(v -> finish());
    }

    // --- DOWNLOAD QR CODE LOGIC (Remains the same) ---
    private void requestStoragePermissionAndSaveQr() {
        if (qrBitmap == null) {
            Toast.makeText(this, "No QR code image to save.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveQrToDevice(qrBitmap);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_EXTERNAL_STORAGE_PERMISSION_CODE);
            } else {
                saveQrToDevice(qrBitmap);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_EXTERNAL_STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveQrToDevice(qrBitmap);
            } else {
                Toast.makeText(this, "Permission denied. Cannot save QR code.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveQrToDevice(Bitmap bitmap) {
        String filename = "QRCode_" + System.currentTimeMillis() + ".png";
        OutputStream fos = null;
        boolean success = false;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "SmartAttendanceQRs");

                Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                if (imageUri != null) {
                    fos = getContentResolver().openOutputStream(imageUri);
                }
            } else {
                File imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File qrFolder = new File(imagesDir, "SmartAttendanceQRs");
                if (!qrFolder.exists()) {
                    qrFolder.mkdirs();
                }
                File image = new File(qrFolder, filename);
                fos = new FileOutputStream(image);
            }

            if (fos != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                Toast.makeText(this, "QR Code saved to Gallery!", Toast.LENGTH_LONG).show();
                success = true;
            } else {
                Toast.makeText(this, "Failed to get output stream.", Toast.LENGTH_SHORT).show();
            }

        } catch (IOException e) {
            Log.e(TAG, "Error saving QR to device: " + e.getMessage());
            Toast.makeText(this, "Error saving QR code: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream: " + e.getMessage());
                }
            }
        }
        if (success && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + Environment.getExternalStorageDirectory())));
        }
    }

    // --- UPLOAD QR CODE LOGIC (REMOVED FROM HERE - MOVED TO QRUploadManualActivity) ---
    // private void uploadQrToFirebase(...) {}
    // private void saveQrDataToFirestore(...) {}
}