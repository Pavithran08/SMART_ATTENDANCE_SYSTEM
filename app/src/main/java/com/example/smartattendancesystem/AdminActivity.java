package com.example.smartattendancesystem;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException; // Import for checking existing user
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminActivity extends AppCompatActivity implements UserAdapter.OnUserActionListener {

    private static final String TAG = "AdminActivity";

    private RecyclerView recyclerViewUsers;
    private UserAdapter userAdapter;
    private List<User> userList;
    private FloatingActionButton fabAddUser;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // --- NEW: Store current admin's Firebase UID and Role ---
    private String currentAdminUid;
    private String currentAdminRole;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin); // Ensure this matches the XML filename

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check if user is authenticated and is an admin
        // This check is rudimentary; for production, implement Firebase Custom Claims.
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Not logged in. Redirecting to login.", Toast.LENGTH_SHORT).show();
            navigateToLogin();
            return;
        }

        // --- NEW: Get current admin's UID ---
        currentAdminUid = mAuth.getCurrentUser().getUid();

        recyclerViewUsers = findViewById(R.id.recyclerViewUsers);
        fabAddUser = findViewById(R.id.fabAddUser);
        progressBar = findViewById(R.id.progressBarAdmin);

        userList = new ArrayList<>();
        userAdapter = new UserAdapter(this, userList, this); // 'this' for OnUserActionListener
        recyclerViewUsers.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewUsers.setAdapter(userAdapter);

        setupBottomNavigation();

        // --- NEW: Fetch the current user's role to ensure they are admin ---
        // And also to make sure we don't display their own account
        checkAdminRoleAndLoadUsers();

        fabAddUser.setOnClickListener(v -> showAddEditUserDialog(null));
    }

    private void checkAdminRoleAndLoadUsers() {
        if (currentAdminUid == null) {
            Toast.makeText(this, "Admin user not identified. Please re-login.", Toast.LENGTH_SHORT).show();
            navigateToLogin();
            return;
        }

        db.collection("users").document(currentAdminUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentAdminRole = documentSnapshot.getString("role");
                        if ("Admin".equalsIgnoreCase(currentAdminRole)) {
                            Log.d(TAG, "Current user is Admin. Loading non-admin users.");
                            loadUsers(); // Only load users if confirmed admin
                        } else {
                            Log.w(TAG, "Current user is not Admin. Role: " + currentAdminRole);
                            Toast.makeText(AdminActivity.this, "Access Denied: Not an Admin.", Toast.LENGTH_LONG).show();
                            navigateToLogin(); // Redirect if not admin
                        }
                    } else {
                        Log.e(TAG, "Admin user document not found in Firestore.");
                        Toast.makeText(AdminActivity.this, "Admin profile not found. Please re-login.", Toast.LENGTH_LONG).show();
                        navigateToLogin();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching admin role: ", e);
                    Toast.makeText(AdminActivity.this, "Error checking admin status: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    navigateToLogin();
                });
    }


    private void setupBottomNavigation() {
        ImageButton backButton = findViewById(R.id.backButton);
        ImageButton homeButton = findViewById(R.id.homeButton);
        ImageButton profileButton = findViewById(R.id.profileButton);

        backButton.setOnClickListener(v -> onBackPressed());
        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(AdminActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
        profileButton.setOnClickListener(v -> {
            Toast.makeText(AdminActivity.this, "Admin Profile Clicked", Toast.LENGTH_SHORT).show();
            // TODO: Implement navigation to an actual Admin Profile Activity
            // For now, you might just display a simple dialog with admin info
            showAdminProfileDialog();
        });
    }

    // --- NEW: Method to show admin profile details ---
    private void showAdminProfileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Admin Profile");
        String email = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : "N/A";
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "N/A";
        String role = currentAdminRole != null ? currentAdminRole : "Unknown"; // Display fetched role

        builder.setMessage("Email: " + email + "\n" +
                "Firebase UID: " + uid + "\n" +
                "Role: " + role);

        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.show();
    }


    private void loadUsers() {
        showLoading(true);
        db.collection("users")
                // --- MODIFIED QUERY: Filter out users with "Admin" role ---
                // Also, exclude the currently logged-in admin's own UID if it's not already filtered by role
                .whereNotEqualTo("role", "Admin") // Exclude documents where role is "Admin"
                // .whereNotEqualTo(FieldPath.documentId(), currentAdminUid) // This is also an option if you want to explicitly exclude self even if role isn't "Admin"
                .orderBy("matricOrStaffId") // Order by ID for better view
                .addSnapshotListener((snapshots, e) -> {
                    showLoading(false);
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        Toast.makeText(AdminActivity.this, "Error loading users: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }

                    userList.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            User user = doc.toObject(User.class);
                            user.setId(doc.getId()); // Set the document ID from Firestore (the unique path ID)
                            userList.add(user);
                        }
                    }
                    userAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Users loaded (excluding Admins): " + userList.size());

                    if (userList.isEmpty()) {
                        Toast.makeText(AdminActivity.this, "No non-admin users found in database.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showAddEditUserDialog(User userToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_edit_user, null);
        builder.setView(dialogView);

        TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
        TextView tvDocumentIdDisplay = dialogView.findViewById(R.id.tvDocumentIdDisplay); // For displaying Firestore Document ID
        EditText etUserId = dialogView.findViewById(R.id.etUserId);                 // For custom User ID input
        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etMatricStaffId = dialogView.findViewById(R.id.etMatricStaffId); // Matric/Staff ID field
        EditText etRole = dialogView.findViewById(R.id.etRole);                   // Role input field
        EditText etPassword = dialogView.findViewById(R.id.etPassword);           // New: Password input field

        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnSave = dialogView.findViewById(R.id.btnSave);

        if (userToEdit != null) {
            // EDITING EXISTING USER
            dialogTitle.setText("Edit User");
            tvDocumentIdDisplay.setVisibility(View.VISIBLE); // Show Firestore Document ID
            tvDocumentIdDisplay.setText("Document ID: " + userToEdit.getId()); // Use getId() for Firestore Document ID

            etUserId.setVisibility(View.VISIBLE); // Keep custom User ID input visible for editing
            etUserId.setText(userToEdit.getUserID()); // Populate custom User ID
            etUsername.setText(userToEdit.getUsername());
            etEmail.setText(userToEdit.getEmail());
            etMatricStaffId.setText(userToEdit.getMatricOrStaffId());
            etMatricStaffId.setEnabled(true); // Ensure it's editable for existing users
            etRole.setText(userToEdit.getRole()); // Populate Role field
            etRole.setEnabled(true); // Ensure it's editable

            // For existing users, disable password field as Firebase Auth password changes are complex from client-side admin
            etPassword.setHint("Password (not changeable here)");
            etPassword.setEnabled(false);
            etPassword.setText(""); // Clear any previous value to prevent sending old password

            // --- NEW: Prevent editing an Admin user from this dialog (though already filtered by loadUsers) ---
            if ("Admin".equalsIgnoreCase(userToEdit.getRole())) {
                Toast.makeText(this, "Cannot edit Admin users from this interface.", Toast.LENGTH_LONG).show();
                btnSave.setEnabled(false); // Disable save button
                etUsername.setEnabled(false);
                etEmail.setEnabled(false);
                etMatricStaffId.setEnabled(false);
                etRole.setEnabled(false);
            }

        } else {
            // ADDING NEW USER
            dialogTitle.setText("Add New User");
            tvDocumentIdDisplay.setVisibility(View.GONE); // Hide Firestore Document ID display for new users
            etUserId.setVisibility(View.VISIBLE); // Show custom User ID input for new users
            etUserId.setHint("Enter User ID (e.g., B12345)");
            etMatricStaffId.setEnabled(false); // Disable Matric/Staff ID as it will be auto-populated
            etMatricStaffId.setHint("Auto-populated from User ID");
            etRole.setHint("Role (e.g., Student, Lecturer, Admin)"); // Hint for Role
            etRole.setEnabled(true); // Enable role input for new users
            etPassword.setHint("Password (min 6 chars)");
            etPassword.setEnabled(true); // Enable password input for new users
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        // Listen for changes in etUserId to auto-populate etMatricStaffId for new users
        etUserId.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (userToEdit == null) { // Only auto-populate if adding a new user
                    etMatricStaffId.setText(s.toString());
                }
            }
        });


        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            // --- NEW: Re-check if userToEdit is an Admin before saving ---
            if (userToEdit != null && "Admin".equalsIgnoreCase(userToEdit.getRole())) {
                Toast.makeText(AdminActivity.this, "Cannot modify Admin users from this interface.", Toast.LENGTH_LONG).show();
                dialog.dismiss();
                return;
            }

            String customUserId = etUserId.getText().toString().trim(); // This is the user's custom ID
            String username = etUsername.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String matricOrStaffId = etMatricStaffId.getText().toString().trim(); // Get from the field (will be auto-filled for new)
            String role = etRole.getText().toString().trim(); // Get role from input
            String password = etPassword.getText().toString(); // Get password from input

            // Validation logic based on mode
            if (userToEdit == null) { // Adding new user
                if (customUserId.isEmpty() || username.isEmpty() || email.isEmpty() || role.isEmpty() || password.isEmpty()) {
                    Toast.makeText(AdminActivity.this, "Please fill all fields: User ID, Username, Email, Role, and Password.", Toast.LENGTH_LONG).show();
                    return;
                }
                if (password.length() < 6) {
                    Toast.makeText(AdminActivity.this, "Password must be at least 6 characters long.", Toast.LENGTH_LONG).show();
                    return;
                }
                // --- NEW: Prevent creating new 'Admin' role via this dialog if desired ---
                // You might want to reserve admin creation for backend or more secure methods.
                if ("Admin".equalsIgnoreCase(role)) {
                    Toast.makeText(AdminActivity.this, "Creating new 'Admin' accounts is not allowed via this interface.", Toast.LENGTH_LONG).show();
                    return;
                }

                addNewUser(customUserId, email, customUserId, username, role, password); // Pass password to addNewUser
            } else { // Updating existing user
                // Password is not updated here for existing users
                if (username.isEmpty() || email.isEmpty() || matricOrStaffId.isEmpty() || customUserId.isEmpty() || role.isEmpty()) {
                    Toast.makeText(AdminActivity.this, "Please fill all fields.", Toast.LENGTH_SHORT).show();
                    return;
                }
                updateUser(userToEdit.getId(), customUserId, email, matricOrStaffId, username, role); // Pass Firestore Document ID and updated fields
            }
            dialog.dismiss();
        });
    }

    private void addNewUser(String customUserId, String email, String matricOrStaffId, String username, String role, String password) { // Added password parameter
        showLoading(true);

        // First, create the user in Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String firebaseUid = authResult.getUser().getUid(); // Get the UID generated by Firebase Auth
                    Log.d(TAG, "Firebase Auth user created with UID: " + firebaseUid);

                    // Now, save user details to Firestore, using the Firebase UID as the document ID
                    Map<String, Object> newUser = new HashMap<>();
                    newUser.put("email", email);
                    newUser.put("matricOrStaffId", matricOrStaffId);
                    newUser.put("username", username);
                    newUser.put("userID", customUserId); // Store the custom user ID as a field
                    newUser.put("role", role);
                    // Do NOT store password directly in Firestore document for security. Firebase Auth manages it.

                    db.collection("users").document(firebaseUid) // Use Firebase UID as Firestore Document ID
                            .set(newUser) // Use set() to explicitly set the document ID
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(AdminActivity.this, "User " + customUserId + " added successfully!", Toast.LENGTH_LONG).show();
                                Log.d(TAG, "User details saved to Firestore with Document ID: " + firebaseUid);
                                showLoading(false);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(AdminActivity.this, "Error saving user details to Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                Log.e(TAG, "Error saving document to Firestore", e);
                                // Important: If Firestore save fails, clean up the Auth user created earlier
                                authResult.getUser().delete()
                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Auth user deleted due to Firestore error."))
                                        .addOnFailureListener(e1 -> Log.e(TAG, "Failed to delete Auth user: " + e1.getMessage()));
                                showLoading(false);
                            });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        Toast.makeText(AdminActivity.this, "Error: User with this email already exists.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(AdminActivity.this, "Error creating user account: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    Log.e(TAG, "Error creating user in Firebase Auth", e);
                });
    }

    private void updateUser(String documentId, String customUserId, String email, String matricOrStaffId, String username, String role) {
        // --- NEW: Prevent updating the current admin's own account or any other admin account ---
        if ("Admin".equalsIgnoreCase(role)) { // Check the role being updated to
            Toast.makeText(this, "Cannot change a user's role to Admin or modify existing Admin accounts from this interface.", Toast.LENGTH_LONG).show();
            return;
        }
        // If you specifically want to prevent self-modification:
        // if (documentId.equals(currentAdminUid)) {
        //     Toast.makeText(this, "Cannot modify your own admin account.", Toast.LENGTH_LONG).show();
        //     return;
        // }

        showLoading(true);
        Map<String, Object> updatedUser = new HashMap<>();
        updatedUser.put("email", email);
        updatedUser.put("matricOrStaffId", matricOrStaffId);
        updatedUser.put("username", username);
        updatedUser.put("userID", customUserId); // Update customUserID field as well
        updatedUser.put("role", role); // Update role in the user data

        db.collection("users").document(documentId) // Use the Firestore document ID to update
                .update(updatedUser)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(AdminActivity.this, "User updated successfully! (Document ID: " + documentId + ")", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "DocumentSnapshot successfully updated!");
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(AdminActivity.this, "Error updating user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error updating document", e);
                    showLoading(false);
                });
    }

    private void deleteUser(String documentId) {
        // --- NEW: Prevent deleting the current admin's own account or any other admin account ---
        // First, check the role of the user being deleted
        db.collection("users").document(documentId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String roleToDelete = documentSnapshot.getString("role");
                        if ("Admin".equalsIgnoreCase(roleToDelete)) {
                            Toast.makeText(AdminActivity.this, "Cannot delete Admin users from this interface.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (documentId.equals(currentAdminUid)) {
                            Toast.makeText(AdminActivity.this, "You cannot delete your own admin account!", Toast.LENGTH_LONG).show();
                            return;
                        }

                        // If not an admin and not self, proceed with deletion
                        new AlertDialog.Builder(this)
                                .setTitle("Delete User")
                                .setMessage("Are you sure you want to delete this user? This action cannot be undone.")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    showLoading(true);
                                    db.collection("users").document(documentId)
                                            .delete()
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(AdminActivity.this, "User deleted successfully!", Toast.LENGTH_SHORT).show();
                                                Log.d(TAG, "DocumentSnapshot successfully deleted!");
                                                showLoading(false);
                                                // TODO: Also delete the user from Firebase Authentication
                                                // This is tricky client-side, consider Cloud Function for robustness.
                                                // authResult.getUser().delete() if you have the Auth user object.
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(AdminActivity.this, "Error deleting user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                Log.e(TAG, "Error deleting document", e);
                                                showLoading(false);
                                            });
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else {
                        Toast.makeText(AdminActivity.this, "User not found for deletion.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user role for deletion: ", e);
                    Toast.makeText(AdminActivity.this, "Error preparing to delete user.", Toast.LENGTH_LONG).show();
                });
    }


    // Implementation of UserAdapter.OnUserActionListener
    @Override
    public void onEditClick(User user) {
        showAddEditUserDialog(user);
    }

    @Override
    public void onDeleteClick(User user) {
        deleteUser(user.getId()); // Use Firestore Document ID for deletion
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        fabAddUser.setEnabled(!show);
    }

    private void navigateToLogin() {
        Intent intent = new Intent(AdminActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}