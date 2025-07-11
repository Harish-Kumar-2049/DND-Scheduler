package com.harish.dndscheduler;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TextView tvDndStatus;
    private Button btnToggleDnd;
    private Button btnDebugTimetable;
    private TextView tvTimetableInfo;
    private boolean isDndOn = false;

    private SharedPreferences prefs;
    private DNDManager dndManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            initializeViews();
            loadPreferences();
            setupDNDManager();
            updateUI();
            processIntentData();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeViews() {
        tvDndStatus = findViewById(R.id.tv_dnd_status);
        btnToggleDnd = findViewById(R.id.btn_toggle_dnd);

        // Optional: Add debug button to your layout
        btnDebugTimetable = findViewById(R.id.btn_debug_timetable);
        tvTimetableInfo = findViewById(R.id.tv_timetable_info);

        if (btnDebugTimetable != null) {
            btnDebugTimetable.setOnClickListener(v -> debugTimetable());
        }

        btnToggleDnd.setOnClickListener(v -> toggleDnd());
    }

    private void loadPreferences() {
        prefs = getSharedPreferences("dnd_prefs", MODE_PRIVATE);
        isDndOn = prefs.getBoolean("is_dnd_on", false);
        Log.d(TAG, "Loaded DND state: " + isDndOn);
    }

    private void setupDNDManager() {
        dndManager = new DNDManager(this);
    }

    private void processIntentData() {
        try {
            Intent intent = getIntent();
            if (intent != null) {
                String timetableData = intent.getStringExtra("timetable_data");
                boolean hasValidTimetable = intent.getBooleanExtra("has_valid_timetable", false);
                int parsedSlotsCount = intent.getIntExtra("parsed_slots_count", 0);
                String timetableError = intent.getStringExtra("timetable_error");

                Log.d(TAG, "Intent data - Valid timetable: " + hasValidTimetable + ", Slots: " + parsedSlotsCount);

                if (timetableError != null) {
                    Log.w(TAG, "Timetable error from LoginActivity: " + timetableError);
                    showTimetableInfo("Error: " + timetableError);
                } else if (hasValidTimetable && parsedSlotsCount > 0) {
                    showTimetableInfo("Found " + parsedSlotsCount + " class slots");
                } else {
                    showTimetableInfo("No valid timetable found");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing intent data", e);
        }
    }

    private void toggleDnd() {
        try {
            if (!hasDndAccess()) {
                Toast.makeText(this, "Grant Do Not Disturb access in settings.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                return;
            }

            // Check if we have timetable data
            List<ClassTimeSlot> slots = TimetableStore.getClassTimeSlots(this);
            if (slots.isEmpty()) {
                Toast.makeText(this, "No timetable data available. Please login again.", Toast.LENGTH_LONG).show();
                // Optionally, redirect to LoginActivity
                startActivity(new Intent(this, LoginActivity.class));
                return;
            }

            isDndOn = !isDndOn;

            if (isDndOn) {
                // Schedule DND ON/OFF based on timetable
                dndManager.scheduleDndForClasses();
                Toast.makeText(this, "DND auto scheduling enabled for " + slots.size() + " classes.", Toast.LENGTH_SHORT).show();
            } else {
                // Cancel scheduled DND and turn off if currently on
                dndManager.cancelDndSchedules();
                dndManager.setDndOff();
                Toast.makeText(this, "DND auto scheduling disabled.", Toast.LENGTH_SHORT).show();
            }

            // Save state
            prefs.edit().putBoolean("is_dnd_on", isDndOn).apply();
            updateUI();

        } catch (Exception e) {
            Log.e(TAG, "Error toggling DND", e);
            Toast.makeText(this, "Error toggling DND: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateUI() {
        try {
            if (isDndOn) {
                tvDndStatus.setText("DND Auto-Scheduling is ON");
                btnToggleDnd.setText("Turn OFF DND Auto-Scheduling");
            } else {
                tvDndStatus.setText("DND Auto-Scheduling is OFF");
                btnToggleDnd.setText("Turn ON DND Auto-Scheduling");
            }

            // Update timetable info
            updateTimetableInfo();
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI", e);
        }
    }

    private void updateTimetableInfo() {
        try {
            List<ClassTimeSlot> slots = TimetableStore.getClassTimeSlots(this);
            String info = "Classes found: " + slots.size();

            if (slots.isEmpty()) {
                info += "\nNo timetable data available";
            }

            showTimetableInfo(info);
        } catch (Exception e) {
            Log.e(TAG, "Error updating timetable info", e);
            showTimetableInfo("Error loading timetable info");
        }
    }

    private void showTimetableInfo(String info) {
        if (tvTimetableInfo != null) {
            tvTimetableInfo.setText(info);
        }
    }

    private void debugTimetable() {
        try {
            TimetableStore.debugTimetableHtml(this);
            Toast.makeText(this, "Check logs for timetable debug info", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error in debug timetable", e);
            Toast.makeText(this, "Error debugging timetable: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasDndAccess() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.isNotificationPolicyAccessGranted();
        } catch (Exception e) {
            Log.e(TAG, "Error checking DND access", e);
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            // Refresh DND access status when returning to activity
            updateUI();
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }
}