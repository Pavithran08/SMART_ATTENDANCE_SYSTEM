// ClassTimeChecker.java
package com.example.smartattendancesystem;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import android.util.Log; // Import Log for debugging

/**
 * Helper class to check if the current time falls within a given class's scheduled time.
 */
public class ClassTimeChecker {

    private static final String TAG = "ClassTimeChecker";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String TIME_FORMAT = "HH:mm"; // Assumes 24-hour format (e.g., "09:00", "14:30")

    /**
     * Checks if the current date and time fall within the specified class schedule.
     *
     * @param classDateStr The date of the class in "yyyy-MM-dd" format.
     * @param classStartTimeStr The start time of the class in "HH:mm" format (24-hour).
     * @param classEndTimeStr The end time of the class in "HH:mm" format (24-hour).
     * @return true if the current time is on the class date and within the class start/end times, false otherwise.
     */
    public static boolean isClassCurrentlyActive(String classDateStr, String classStartTimeStr, String classEndTimeStr) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());

            // --- Parse Class Schedule ---
            // Create Calendar instances for class start and end to include both date and time
            Calendar classStartCal = Calendar.getInstance();
            classStartCal.setTime(dateFormat.parse(classDateStr)); // Set date first
            Date parsedClassStartTime = timeFormat.parse(classStartTimeStr);
            classStartCal.set(Calendar.HOUR_OF_DAY, parsedClassStartTime.getHours());
            classStartCal.set(Calendar.MINUTE, parsedClassStartTime.getMinutes());
            classStartCal.set(Calendar.SECOND, 0);
            classStartCal.set(Calendar.MILLISECOND, 0);

            Calendar classEndCal = Calendar.getInstance();
            classEndCal.setTime(dateFormat.parse(classDateStr)); // Set date first
            Date parsedClassEndTime = timeFormat.parse(classEndTimeStr);
            classEndCal.set(Calendar.HOUR_OF_DAY, parsedClassEndTime.getHours());
            classEndCal.set(Calendar.MINUTE, parsedClassEndTime.getMinutes());
            classEndCal.set(Calendar.SECOND, 0);
            classEndCal.set(Calendar.MILLISECOND, 0);

            // Add a grace period to end time (e.g., 5 minutes)
            classEndCal.add(Calendar.MINUTE, 5); // Example: 5-minute grace period

            // --- Get Current Time ---
            Calendar currentCal = Calendar.getInstance();
            currentCal.set(Calendar.SECOND, 0);
            currentCal.set(Calendar.MILLISECOND, 0);

            Log.d(TAG, "Class Start: " + classStartCal.getTime());
            Log.d(TAG, "Class End (with grace): " + classEndCal.getTime());
            Log.d(TAG, "Current Time: " + currentCal.getTime());

            // Check if current time is after class start and before class end (with grace period)
            boolean isActive = currentCal.after(classStartCal) && currentCal.before(classEndCal);
            Log.d(TAG, "Is class currently active? " + isActive);
            return isActive;

        } catch (ParseException e) {
            Log.e(TAG, "Error parsing date or time: " + e.getMessage(), e);
            return false; // Safely assume cannot mark attendance if there's a parse error
        }
    }
}