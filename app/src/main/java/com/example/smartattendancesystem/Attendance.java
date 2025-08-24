package com.example.smartattendancesystem;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

public class Attendance {
    @DocumentId
    private String id; // Firestore Document ID

    // Fields that directly map to your Firestore 'attendance' document
    private String studentFirebaseUid; // New field from Firebase document
    private String studentMatric;      // Renamed from matricNumber to match Firebase
    private String studentName;        // Renamed from name to match Firebase
    private String course;             // Renamed from program to match Firebase
    private String section;            // Already exists, name matches
    private String enrollmentId;       // New field from Firebase document
    private String faculty;            // New field from Firebase document
    private String time;               // New field from Firebase document
    private String classLocationName;  // New field from Firebase document
    private String status;             // New field (and the one that caused the previous error)

    @ServerTimestamp // Automatically populate creation date/time on the server
    private Date timestamp;            // Already exists, name matches

    public Attendance() {
        // Required empty public constructor for Firestore
    }

    // You might want to update your constructor or add new ones as needed for creating objects
    // This constructor now reflects the new fields. Adjust if you have specific creation flows.
    public Attendance(String studentFirebaseUid, String studentMatric, String studentName,
                      String course, String section, String enrollmentId, String faculty,
                      String time, String classLocationName, String status, Date timestamp) {
        this.studentFirebaseUid = studentFirebaseUid;
        this.studentMatric = studentMatric;
        this.studentName = studentName;
        this.course = course;
        this.section = section;
        this.enrollmentId = enrollmentId;
        this.faculty = faculty;
        this.time = time;
        this.classLocationName = classLocationName;
        this.status = status;
        this.timestamp = timestamp;
    }

    // --- Getters ---
    public String getId() {
        return id;
    }

    public String getStudentFirebaseUid() {
        return studentFirebaseUid;
    }

    public String getStudentMatric() { // Renamed getter
        return studentMatric;
    }

    public String getStudentName() { // Renamed getter
        return studentName;
    }

    public String getCourse() { // Renamed getter
        return course;
    }

    public String getSection() {
        return section;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public String getFaculty() {
        return faculty;
    }

    public String getTime() {
        return time;
    }

    public String getClassLocationName() {
        return classLocationName;
    }

    public String getStatus() { // This is the method that was missing!
        return status;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    // --- Setters ---
    public void setId(String id) {
        this.id = id;
    }

    public void setStudentFirebaseUid(String studentFirebaseUid) {
        this.studentFirebaseUid = studentFirebaseUid;
    }

    public void setStudentMatric(String studentMatric) { // Renamed setter
        this.studentMatric = studentMatric;
    }

    public void setStudentName(String studentName) { // Renamed setter
        this.studentName = studentName;
    }

    public void setCourse(String course) { // Renamed setter
        this.course = course;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public void setEnrollmentId(String enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public void setFaculty(String faculty) {
        this.faculty = faculty;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void setClassLocationName(String classLocationName) {
        this.classLocationName = classLocationName;
    }

    public void setStatus(String status) { // New setter
        this.status = status;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}