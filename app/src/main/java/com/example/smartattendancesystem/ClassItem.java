package com.example.smartattendancesystem; // Your package name

public class ClassItem {
    private String faculty;
    private String course;
    private String section;
    private String time;
    private String enrollmentId;
    private String classLocationName; // NEW: Field for the specific class location name

    // UPDATED Constructor: Now includes classLocationName
    public ClassItem(String faculty, String course, String section, String time, String enrollmentId, String classLocationName) {
        this.faculty = faculty;
        this.course = course;
        this.section = section;
        this.time = time;
        this.enrollmentId = enrollmentId;
        this.classLocationName = classLocationName; // Initialize the new field
    }

    // Getters
    public String getFaculty() { return faculty; }
    public String getCourse() { return course; }
    public String getSection() { return section; }
    public String getTime() { return time; }
    public String getEnrollmentId() { return enrollmentId; }

    // NEW Getter for classLocationName
    public String getClassLocationName() { return classLocationName; }

    // Setters (optional, if you need to modify ClassItem objects)
    public void setFaculty(String faculty) { this.faculty = faculty; }
    public void setCourse(String course) { this.course = course; }
    public void setSection(String section) { this.section = section; }
    public void setTime(String time) { this.time = time; }
    public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }

    // NEW Setter for classLocationName
    public void setClassLocationName(String classLocationName) { this.classLocationName = classLocationName; }

    @Override
    public String toString() {
        // Updated toString to potentially include location for debugging or simple display
        return course + " - " + section + " (" + time + ") at " + classLocationName;
    }
}