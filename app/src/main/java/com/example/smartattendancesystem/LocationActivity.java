package com.example.smartattendancesystem;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;

public class LocationActivity extends AppCompatActivity {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance(); // Initialize Firebase Auth

        // Call to get and save location
        getCurrentLocationAndSave();
    }

    // Get the current location of the device
    private void getCurrentLocationAndSave() {
        // Check if location permissions are granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request permission if not granted
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            // Location obtained, save it to Firestore
                            saveLocationToDatabase(location);
                        } else {
                            Toast.makeText(LocationActivity.this, "Unable to get location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // Save location to Firebase Firestore
    private void saveLocationToDatabase(Location location) {
        String userId = mAuth.getCurrentUser().getUid();  // Get current user's ID
        String username = mAuth.getCurrentUser().getDisplayName();  // Get the username (ensure this is set in FirebaseAuth)
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        // Create LocationData object to store in Firestore
        LocationData locationData = new LocationData(userId, username, latitude, longitude);

        // Save location data in the "locations" collection
        DocumentReference locationRef = db.collection("locations").document();  // Auto-generate a document ID (timestamp)

        locationRef.set(locationData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(LocationActivity.this, "Location saved successfully", Toast.LENGTH_SHORT).show();
                    finish(); // Close LocationActivity after saving the location
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(LocationActivity.this, "Error saving location", Toast.LENGTH_SHORT).show();
                    finish(); // Close LocationActivity on failure
                });
    }

    // Handle permission request result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocationAndSave();  // Retry getting the location
            } else {
                Toast.makeText(this, "Location permission is required to scan QR code", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Location data class for Firestore storage
    public static class LocationData {
        private String userID;
        private String username;
        private double latitude;
        private double longitude;

        public LocationData() {
            // Empty constructor needed for Firestore
        }

        public LocationData(String userID, String username, double latitude, double longitude) {
            this.userID = userID;
            this.username = username;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getUserID() {
            return userID;
        }

        public String getUsername() {
            return username;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }
}
