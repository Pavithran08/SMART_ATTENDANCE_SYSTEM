package com.example.smartattendancesystem;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder> {

    private List<Attendance> attendanceList;

    public AttendanceAdapter(List<Attendance> attendanceList) {
        this.attendanceList = attendanceList;
    }

    // Method to update the list and notify adapter (useful when filtering)
    public void setAttendanceList(List<Attendance> newAttendanceList) {
        this.attendanceList = newAttendanceList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AttendanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance, parent, false);
        return new AttendanceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttendanceViewHolder holder, int position) {
        Attendance attendance = attendanceList.get(position);
        holder.bind(attendance);
    }

    @Override
    public int getItemCount() {
        return attendanceList.size();
    }

    public static class AttendanceViewHolder extends RecyclerView.ViewHolder {
        TextView tvMatricNo;
        TextView tvName;
        TextView tvProgram; // Now refers to 'course'
        TextView tvSection;
        TextView tvTimestamp;
        TextView tvStatus; // New TextView for Status

        public AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMatricNo = itemView.findViewById(R.id.tvMatricNo);
            tvName = itemView.findViewById(R.id.tvName);
            tvProgram = itemView.findViewById(R.id.tvProgram); // Still refers to the TextView in layout
            tvSection = itemView.findViewById(R.id.tvSection);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvStatus = itemView.findViewById(R.id.tvStatus); // Initialize the new TextView
        }

        public void bind(Attendance attendance) {
            // Update these calls to use the new getter names from the Attendance model
            tvMatricNo.setText(attendance.getStudentMatric()); // Changed from getMatricNumber()
            tvName.setText(attendance.getStudentName());     // Changed from getName()
            tvProgram.setText(attendance.getCourse());       // Changed from getProgram()
            tvSection.setText(attendance.getSection());

            // Format timestamp nicely
            if (attendance.getTimestamp() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                tvTimestamp.setText(sdf.format(attendance.getTimestamp()));
            } else {
                tvTimestamp.setText("N/A");
            }

            // Set the Status
            tvStatus.setText("Status: " + (attendance.getStatus() != null ? attendance.getStatus() : "N/A"));
        }
    }
}