package com.example.smartattendancesystem;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

public class ClassItemAdapter extends ArrayAdapter<ClassItem> {

    // Constructor for the adapter
    public ClassItemAdapter(@NonNull Context context, @NonNull ArrayList<ClassItem> classList) {
        super(context, 0, classList); // The '0' here means we're not using a predefined layout for the item itself
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        // Get the data item for this position
        ClassItem classItem = getItem(position);

        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            // Inflate the custom layout for a single class item
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.class_item, parent, false);
        }

        // Lookup view for data population
        TextView classDetailsTextView = convertView.findViewById(R.id.classDetailsTextView);
        TextView lecturerNameTextView = convertView.findViewById(R.id.lecturerNameTextView);
        // Add more TextViews if you want to display other details like faculty, section, time separately

        // Populate the data into the template view using the data object
        if (classItem != null) {
            // Combine details for the main display
            String details = classItem.getCourse() + " - " + classItem.getSection() + "\n" +
                    classItem.getTime() + " (" + classItem.getFaculty() + ")";
            classDetailsTextView.setText(details);

            // You might want to display the lecturer's name here if it was passed/available in ClassItem
            // For now, let's assume lecturerNameTextView might show something if available
            // Note: ClassItem as currently defined doesn't hold lecturer name directly, only enrollment details.
            // If you want to show the lecturer's name here, you'd need to fetch it in LecturerChooseClassActivity
            // and pass it to ClassItem or fetch it within the adapter (less efficient).
            // For simplicity, let's just make it visible for potential future use or if you fetch it
            lecturerNameTextView.setVisibility(View.GONE); // Hide it for now, unless you add lecturer name to ClassItem
            // or fetch it here.
        }

        // Return the completed view to render on screen
        return convertView;
    }
}