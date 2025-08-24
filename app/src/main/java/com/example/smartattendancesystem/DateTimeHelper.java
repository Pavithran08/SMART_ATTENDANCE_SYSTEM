// DateTimeHelper.java
package com.example.smartattendancesystem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Calendar;

/**
 * Helper class for retrieving current date and time in specific formats.
 */
public class DateTimeHelper {

    /**
     * Gets the current date formatted as "yyyy-MM-dd".
     * @return Current date string.
     */
    public static String getCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * Gets the current time formatted as "HH:mm" (24-hour format).
     * @return Current time string.
     */
    public static String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * Gets a Calendar instance representing the current time.
     * @return A Calendar instance.
     */
    public static Calendar getCurrentCalendar() {
        return Calendar.getInstance();
    }
}