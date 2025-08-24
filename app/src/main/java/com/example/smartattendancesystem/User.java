package com.example.smartattendancesystem;

import com.google.firebase.firestore.DocumentId;
import java.util.List;

public class User {

    private String email;
    private List<Double> faceEmbedding;
    private String faceImageUrl;
    private String faceImageLoginUrl;
    private String matricOrStaffId;
    private String password; // **Important: Consider security implications of storing passwords here. Firebase Auth handles passwords securely. It's generally not recommended to store raw passwords in Firestore.**
    private String profileImageUrl;
    private String role;

    @DocumentId // This field will hold the Firestore Document ID (e.g., the auto-generated key)
    private String id; // This maps to the actual Firestore document ID (Firebase Auth UID)

    private String userID; // This is YOUR CUSTOM user ID field, stored within the document (e.g., matric/staff ID)
    private String username;

    // --- New Fields for Student Information ---
    private String faculty;
    private String course;
    private String year;
    // ------------------------------------------

    public User() {
        // Required empty public constructor for Firestore
    }

    // Constructor with all fields, including the new ones
    public User(String email, List<Double> faceEmbedding, String faceImageUrl, String faceImageLoginUrl,
                String matricOrStaffId, String password, String profileImageUrl, String role,
                String id, String userID, String username, // Existing fields
                String faculty, String course, String year) { // New fields
        this.email = email;
        this.faceEmbedding = faceEmbedding;
        this.faceImageUrl = faceImageUrl;
        this.faceImageLoginUrl = faceImageLoginUrl;
        this.matricOrStaffId = matricOrStaffId;
        this.password = password;
        this.profileImageUrl = profileImageUrl;
        this.role = role;
        this.id = id;
        this.userID = userID;
        this.username = username;
        this.faculty = faculty; // Initialize new field
        this.course = course;   // Initialize new field
        this.year = year;       // Initialize new field
    }

    // --- Getters for all fields ---

    public String getEmail() { return email; }
    public List<Double> getFaceEmbedding() { return faceEmbedding; }
    public String getFaceImageUrl() { return faceImageUrl; }
    public String getFaceImageLoginUrl() { return faceImageLoginUrl; }
    public String getMatricOrStaffId() { return matricOrStaffId; }
    public String getPassword() { return password; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public String getRole() { return role; }
    public String getId() { return id; } // Firestore Document ID
    public String getUserID() { return userID; } // Your custom user ID
    public String getUsername() { return username; }
    public String getFaculty() { return faculty; } // New getter
    public String getCourse() { return course; }   // New getter
    public String getYear() { return year; }       // New getter

    // --- Setters for all fields ---

    public void setEmail(String email) { this.email = email; }
    public void setFaceEmbedding(List<Double> faceEmbedding) { this.faceEmbedding = faceEmbedding; }
    public void setFaceImageUrl(String faceImageUrl) { this.faceImageUrl = faceImageUrl; }
    public void setFaceImageLoginUrl(String faceImageLoginUrl) { this.faceImageLoginUrl = faceImageLoginUrl; }
    public void setMatricOrStaffId(String matricOrStaffId) { this.matricOrStaffId = matricOrStaffId; }
    public void setPassword(String password) { this.password = password; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public void setRole(String role) { this.role = role; }
    public void setId(String id) { this.id = id; }
    public void setUserID(String userID) { this.userID = userID; }
    public void setUsername(String username) { this.username = username; }
    public void setFaculty(String faculty) { this.faculty = faculty; } // New setter
    public void setCourse(String course) { this.course = course; }   // New setter
    public void setYear(String year) { this.year = year; }       // New setter
}