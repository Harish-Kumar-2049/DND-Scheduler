package com.harish.dndscheduler;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvDndStatus;
    private TextView tvCurrentTime;
    private TextView tvNextClass;
    private TextView tvTimetableStatus;
    private TextView tvServiceStatus;
    private Button btnToggleDnd;
    private Button btnRefreshTimetable;
    private Button btnCheckNow;
    private RecyclerView rvTodayClasses;

    private SharedPreferences prefs;
    private DNDManager dndManager;
    private Handler updateHandler;
    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupManagers();
        setupUpdateHandler();

        rvTodayClasses.setLayoutManager(new LinearLayoutManager(this));
        checkCurrentDndStatus();

        // Request battery optimization exemption on first run
        requestBatteryOptimizationExemption();
    }

    private void initializeViews() {
        tvDndStatus = findViewById(R.id.tv_dnd_status);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvNextClass = findViewById(R.id.tv_next_class);
        tvTimetableStatus = findViewById(R.id.tv_timetable_status);
        tvServiceStatus = findViewById(R.id.tv_service_status);
        btnToggleDnd = findViewById(R.id.btn_toggle_dnd);
        btnRefreshTimetable = findViewById(R.id.btn_refresh_timetable);
        btnCheckNow = findViewById(R.id.btn_check_now);
        rvTodayClasses = findViewById(R.id.rv_today_classes);

        btnToggleDnd.setOnClickListener(v -> toggleDndScheduling());
        btnRefreshTimetable.setOnClickListener(v -> refreshTimetable());
        btnCheckNow.setOnClickListener(v -> checkCurrentDndStatus());
    }

    private void setupManagers() {
        prefs = getSharedPreferences("dnd_prefs", MODE_PRIVATE);
        dndManager = new DNDManager(this);
    }

    private void setupUpdateHandler() {
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentTimeDisplay();
                updateUI();
                updateHandler.postDelayed(this, 30000);
            }
        };
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                try {
                    startActivity(intent);
                    Toast.makeText(this, "Please allow app to run in background for reliable DND scheduling", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e("MainActivity", "Error requesting battery optimization exemption", e);
                }
            }
        }
    }

    private void enableDNDScheduling() {
        // Request battery optimization exemption
        requestBatteryOptimizationExemption();

        // Schedule DND for classes
        dndManager.scheduleDndForClasses();

        // Start foreground service for reliable background operation
        DNDService.startService(this);


        Toast.makeText(this, "DND scheduling enabled with background service", Toast.LENGTH_SHORT).show();
    }

    private void disableDNDScheduling() {
        // Cancel all DND schedules
        dndManager.cancelDndSchedules();

        // Stop foreground service
        DNDService.stopService(this);

        Toast.makeText(this, "DND scheduling disabled", Toast.LENGTH_SHORT).show();
    }

    private void checkCurrentDndStatus() {
        if (!hasDndAccess()) {
            showDndAccessRequired();
            return;
        }

        List<ClassTimeSlot> allSlots = TimetableStore.getClassTimeSlots(this);
        List<ClassTimeSlot> classSlots = getTodaySlots(allSlots);

        for (ClassTimeSlot slot : classSlots) {
            String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(slot.getStartMillis())) +
                    " - " + new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(slot.getEndMillis()));
            Log.d("TodaySlotDetail", time + " => " + slot.getSubject());
        }

        if (classSlots.isEmpty()) {
            showNoTimetableData();
            return;
        }

        dndManager.checkAndSetCurrentDndStatus(classSlots);
        updateUI();
        updateTimetableStatus(classSlots);
    }

    private void toggleDndScheduling() {
        if (!hasDndAccess()) {
            showDndAccessRequired();
            return;
        }

        boolean isCurrentlyEnabled = dndManager.isDndSchedulingEnabled();

        List<ClassTimeSlot> allSlots = TimetableStore.getClassTimeSlots(this);
        List<ClassTimeSlot> classSlots = getTodaySlots(allSlots);

        if (classSlots.isEmpty()) {
            showNoTimetableData();
            return;
        }

        if (isCurrentlyEnabled) {
            disableDNDScheduling();
        } else {
            enableDNDScheduling();
        }

        updateUI();
    }

    private void refreshTimetable() {
        Toast.makeText(this, "Redirecting to login for fresh timetable...", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void updateUI() {
        boolean isSchedulingEnabled = dndManager.isDndSchedulingEnabled();
        boolean isDndCurrentlyOn = dndManager.isDndCurrentlyOn();

        if (isDndCurrentlyOn) {
            tvDndStatus.setText("DND is ON");
            tvDndStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else {
            tvDndStatus.setText("DND is OFF");
            tvDndStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }

        if (isSchedulingEnabled) {
            btnToggleDnd.setText("Disable Auto DND");
            btnToggleDnd.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
            tvServiceStatus.setText("✓ Background service active");
            tvServiceStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            btnToggleDnd.setText("Enable Auto DND");
            btnToggleDnd.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
            tvServiceStatus.setText("✗ Background service inactive");
            tvServiceStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        updateNextClassInfo();
        updateBatteryOptimizationStatus();

        List<ClassTimeSlot> allSlots = TimetableStore.getClassTimeSlots(this);
        List<ClassTimeSlot> todaySlots = getTodaySlots(allSlots);
        rvTodayClasses.setAdapter(new ClassSlotAdapter(todaySlots));
    }

    private void updateBatteryOptimizationStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(getPackageName());

            if (!isIgnoringBatteryOptimizations) {
                Toast.makeText(this, "⚠️ Battery optimization is enabled. DND scheduling may not work reliably.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateCurrentTimeDisplay() {
        Calendar now = Calendar.getInstance();
        String currentTime = String.format("Current Time: %02d:%02d",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        tvCurrentTime.setText(currentTime);
    }

    private void updateNextClassInfo() {
        List<ClassTimeSlot> allSlots = TimetableStore.getClassTimeSlots(this);
        List<ClassTimeSlot> classSlots = getTodaySlots(allSlots);

        if (classSlots.isEmpty()) {
            tvNextClass.setText("No timetable data available");
            return;
        }

        Calendar now = Calendar.getInstance();
        int currentTimeInMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        String currentClassInfo = getCurrentClassInfo(classSlots, currentTimeInMinutes);
        if (!currentClassInfo.isEmpty()) {
            tvNextClass.setText("Current Class: " + currentClassInfo);
            tvNextClass.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            return;
        }

        String nextClassInfo = getNextClassInfo(classSlots, currentTimeInMinutes);
        if (!nextClassInfo.isEmpty()) {
            tvNextClass.setText("Next Class: " + nextClassInfo);
            tvNextClass.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        } else {
            tvNextClass.setText("No more classes today");
            tvNextClass.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    private String getCurrentClassInfo(List<ClassTimeSlot> classSlots, int currentTimeInMinutes) {
        for (ClassTimeSlot slot : classSlots) {
            Calendar startCal = Calendar.getInstance();
            startCal.setTimeInMillis(slot.getStartMillis());
            int startTimeInMinutes = startCal.get(Calendar.HOUR_OF_DAY) * 60 + startCal.get(Calendar.MINUTE);

            Calendar endCal = Calendar.getInstance();
            endCal.setTimeInMillis(slot.getEndMillis());
            int endTimeInMinutes = endCal.get(Calendar.HOUR_OF_DAY) * 60 + endCal.get(Calendar.MINUTE);

            if (currentTimeInMinutes >= startTimeInMinutes && currentTimeInMinutes <= endTimeInMinutes) {
                return String.format("%02d:%02d - %02d:%02d",
                        startCal.get(Calendar.HOUR_OF_DAY), startCal.get(Calendar.MINUTE),
                        endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE));
            }
        }
        return "";
    }

    private String getNextClassInfo(List<ClassTimeSlot> classSlots, int currentTimeInMinutes) {
        int nextStartTime = Integer.MAX_VALUE;
        String nextClassInfo = "";

        for (ClassTimeSlot slot : classSlots) {
            Calendar startCal = Calendar.getInstance();
            startCal.setTimeInMillis(slot.getStartMillis());
            int startTimeInMinutes = startCal.get(Calendar.HOUR_OF_DAY) * 60 + startCal.get(Calendar.MINUTE);

            Calendar endCal = Calendar.getInstance();
            endCal.setTimeInMillis(slot.getEndMillis());

            if (startTimeInMinutes > currentTimeInMinutes && startTimeInMinutes < nextStartTime) {
                nextStartTime = startTimeInMinutes;
                nextClassInfo = String.format("%02d:%02d - %02d:%02d",
                        startCal.get(Calendar.HOUR_OF_DAY), startCal.get(Calendar.MINUTE),
                        endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE));
            }
        }

        return nextClassInfo;
    }

    private void updateTimetableStatus(List<ClassTimeSlot> classSlots) {
        long fetchTime = prefs.getLong("timetable_fetch_time", 0);

        if (fetchTime == 0) {
            tvTimetableStatus.setText("No timetable data");
            tvTimetableStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else {
            Calendar fetchCal = Calendar.getInstance();
            fetchCal.setTimeInMillis(fetchTime);

            String statusText = String.format("Timetable: %d classes found\nLast updated: %02d:%02d",
                    classSlots.size(),
                    fetchCal.get(Calendar.HOUR_OF_DAY),
                    fetchCal.get(Calendar.MINUTE));

            tvTimetableStatus.setText(statusText);
            tvTimetableStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }
    }

    private void showDndAccessRequired() {
        Toast.makeText(this, "Grant Do Not Disturb access in settings.", Toast.LENGTH_LONG).show();
        tvDndStatus.setText("⚠️ DND Access Required");
        tvDndStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    private void showNoTimetableData() {
        Toast.makeText(this, "No timetable data found. Please refresh timetable.", Toast.LENGTH_LONG).show();
        tvTimetableStatus.setText("❌ No timetable data available");
        tvTimetableStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
    }

    private boolean hasDndAccess() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return nm.isNotificationPolicyAccessGranted();
    }

    private List<ClassTimeSlot> getTodaySlots(List<ClassTimeSlot> allSlots) {
        List<ClassTimeSlot> todaySlots = new ArrayList<>();
        Calendar today = Calendar.getInstance();
        int todayDay = today.get(Calendar.DAY_OF_WEEK);

        for (ClassTimeSlot slot : allSlots) {
            Calendar slotCal = Calendar.getInstance();
            slotCal.setTimeInMillis(slot.getStartMillis());
            if (slotCal.get(Calendar.DAY_OF_WEEK) == todayDay) {
                todaySlots.add(slot);
            }
        }

        Log.d("TodaySlots", "Found " + todaySlots.size() + " slots for today");
        return todaySlots;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHandler.post(updateRunnable);
        checkCurrentDndStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateHandler.removeCallbacks(updateRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateHandler != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }
}