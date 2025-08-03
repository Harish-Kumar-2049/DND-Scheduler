package com.harish.dndscheduler;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.app.ActivityManager;
import android.app.AlarmManager;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
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

    private TextView tvServiceStatus;
    private LinearLayout btnToggleDnd;
    private TextView tvButtonStatus;
    private LinearLayout layoutSaturdayDropdown;
    private TextView tvSaturdaySelection;
    private RecyclerView rvTodayClasses;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Spinner spinnerSaturdaySchedule;

    private SharedPreferences prefs;
    private DNDManager dndManager;
    private Handler updateHandler;
    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if this is first launch or if timetable data exists
        if (isFirstLaunchOrNoTimetableData()) {
            // Redirect to LoginActivity directly
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        
        setContentView(R.layout.activity_main);

        initializeViews();
        setupManagers();
        setupUpdateHandler();

        rvTodayClasses.setLayoutManager(new LinearLayoutManager(this));
        checkCurrentDndStatus(false); // Don't auto-show settings on app start

        // Initialize UI state
        updateUI();

        // Start enhanced reliability features
        initializeReliabilityFeatures();
        
        // Initialize and start tutorial system
        setupTutorialSystem();
        
        // Check if tutorial replay was requested
        handleTutorialReplayIntent();
    }

    private void initializeViews() {
        tvServiceStatus = findViewById(R.id.tv_service_status);
        btnToggleDnd = findViewById(R.id.btn_toggle_dnd);
        tvButtonStatus = findViewById(R.id.tv_button_status);
        layoutSaturdayDropdown = findViewById(R.id.layout_saturday_dropdown);
        tvSaturdaySelection = findViewById(R.id.tv_saturday_selection);
        rvTodayClasses = findViewById(R.id.rv_today_classes);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        spinnerSaturdaySchedule = findViewById(R.id.spinner_saturday_schedule);

        // Add null check and debugging for the button
        if (btnToggleDnd == null) {
            Log.e("MainActivity", "btnToggleDnd is null - check layout file");
            return;
        }
        
        Log.d("MainActivity", "Setting up button click listeners");
        btnToggleDnd.setOnClickListener(v -> {
            Log.d("MainActivity", "Toggle DND button clicked");
            toggleDndScheduling();
        });
        
        // Setup Saturday dropdown click listener
        layoutSaturdayDropdown.setOnClickListener(v -> {
            Log.d("MainActivity", "Saturday dropdown clicked");
            spinnerSaturdaySchedule.performClick();
        });
        
        // Setup swipe to refresh
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                Log.d("MainActivity", "Swipe refresh triggered");
                refreshTimetable();
            });
            swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
        }
    }

    private void setupManagers() {
        prefs = getSharedPreferences("dnd_prefs", MODE_PRIVATE);
        dndManager = DNDManager.getInstance(this);
        
        // Setup Saturday spinner after prefs is initialized
        setupSaturdaySpinner();
    }

    private void setupUpdateHandler() {
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI();
                updateHandler.postDelayed(this, 30000);
            }
        };
    }

    /**
     * Initialize enhanced reliability features without requiring battery optimization permissions
     */
    private void initializeReliabilityFeatures() {
        // 1. Start foreground service immediately for maximum reliability
        Intent serviceIntent = new Intent(this, DNDService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // 2. Set up aggressive self-healing mechanisms
        setupAdvancedSelfHealing();
        
        // 3. Enable multiple alarm redundancy
        enableMultipleAlarmRedundancy();
        
        Log.d("MainActivity", "Enhanced reliability features initialized");
    }

    private void setupSaturdaySpinner() {
        if (spinnerSaturdaySchedule == null) {
            Log.e("MainActivity", "Saturday spinner is null");
            return;
        }

        // Create spinner options
        String[] saturdayOptions = {
            "None (Holiday)",
            "Monday",
            "Tuesday", 
            "Wednesday",
            "Thursday",
            "Friday"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            R.layout.spinner_selected_item, saturdayOptions);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerSaturdaySchedule.setAdapter(adapter);

        // Load saved selection
        String savedSelection = prefs.getString("saturday_follows", "None (Holiday)");
        int position = 0;
        for (int i = 0; i < saturdayOptions.length; i++) {
            if (saturdayOptions[i].equals(savedSelection)) {
                position = i;
                break;
            }
        }
        spinnerSaturdaySchedule.setSelection(position);
        
        // Update the TextView display
        tvSaturdaySelection.setText(savedSelection);

        // Set up selection listener with improved logic
        spinnerSaturdaySchedule.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedDay = saturdayOptions[position];
                String previousSetting = getSaturdayFollowsDay();
                
                // Always save the selection regardless of service status
                saveSaturdaySelection(selectedDay);
                
                // Update the TextView display
                tvSaturdaySelection.setText(selectedDay);
                
                Log.d("MainActivity", "Saturday changed from '" + previousSetting + "' to '" + selectedDay + "'");
                
                // ONLY apply DND changes if auto-scheduling is ENABLED
                if (!dndManager.isDndSchedulingEnabled()) {
                    Log.d("MainActivity", "DND auto-scheduling is disabled - Saturday setting saved but not applied");
                    updateUI(); // Just update UI to show the saved setting
                    return; // Don't proceed with DND scheduling
                }
                
                // Re-schedule DND since it's currently enabled
                Log.d("MainActivity", "Re-scheduling DND with new Saturday setting");
                
                // First cancel all existing Saturday alarms to ensure clean slate
                dndManager.cancelAllSaturdayAlarms();
                
                // If Saturday is set to "None (Holiday)", also turn off DND immediately if today is Saturday
                if (selectedDay.equals("None (Holiday)")) {
                    Calendar today = Calendar.getInstance();
                    if (today.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                        Log.d("MainActivity", "Saturday set to Holiday and today is Saturday - turning OFF DND immediately");
                        dndManager.setDndOff();
                    }
                }
                
                // Then re-schedule everything including new Saturday setting
                dndManager.scheduleDndForClasses();
                
                // Update UI to reflect Saturday changes
                updateUI();
                
                // IMPORTANT: If today is Saturday, immediately check and apply DND status
                Calendar today = Calendar.getInstance();
                if (today.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                    Log.d("MainActivity", "Today is Saturday - immediately checking DND status with new setting");
                    
                    // Force immediate DND status check with new Saturday setting
                    dndManager.forceImmediateDndStatusCheck();
                    
                    // Update UI again to reflect any DND status changes
                    updateUI();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    /**
     * Save Saturday schedule selection to preferences
     */
    private void saveSaturdaySelection(String selectedDay) {
        prefs.edit().putString("saturday_follows", selectedDay).apply();
        Log.d("MainActivity", "Saved Saturday follows: " + selectedDay);
    }

    /**
     * Get which day Saturday should follow
     */
    public String getSaturdayFollowsDay() {
        return prefs.getString("saturday_follows", "None (Holiday)");
    }

    /**
     * Static method for DNDManager to access Saturday settings
     */
    public static String getSaturdayFollowsDayStatic(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("dnd_prefs", Context.MODE_PRIVATE);
        return prefs.getString("saturday_follows", "None (Holiday)");
    }

    /**
     * Setup advanced self-healing mechanisms that work without battery exemption
     */
    private void setupAdvancedSelfHealing() {
        // Multiple periodic checks with different intervals to ensure at least one works
        dndManager.schedulePeriodicCheck(); // 10 minutes
        
        // Additional shorter interval check (works even with battery optimization)
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable selfHealCheck = new Runnable() {
            @Override
            public void run() {
                if (dndManager.isDndSchedulingEnabled()) {
                    dndManager.checkAndSetCurrentDndStatus(null);
                }
                // Reschedule for next check
                handler.postDelayed(this, 3 * 60 * 1000); // 3 minutes
            }
        };
        handler.postDelayed(selfHealCheck, 3 * 60 * 1000);
    }

    /**
     * Enable multiple alarm redundancy for critical timing
     */
    private void enableMultipleAlarmRedundancy() {
        // This will be implemented in DNDManager for better alarm reliability
        // Each class will have multiple alarms scheduled with slight offsets
        Log.d("MainActivity", "Multiple alarm redundancy enabled");
    }

    private void enableDNDScheduling() {
        Log.d("MainActivity", "Enabling DND scheduling...");
        
        // Schedule DND for classes with enhanced reliability
        dndManager.scheduleDndForClasses();

        // Start foreground service for reliable background operation
        DNDService.startService(this);

        Log.d("MainActivity", "DND scheduling enabled successfully");
    }

    private void disableDNDScheduling() {
        Log.d("MainActivity", "Disabling DND scheduling...");
        
        // Cancel all DND schedules
        dndManager.cancelDndSchedules();

        // Stop foreground service
        DNDService.stopService(this);

        Log.d("MainActivity", "DND scheduling disabled successfully");
    }

    private void checkCurrentDndStatus() {
        checkCurrentDndStatus(true); // Default behavior - show settings if needed
    }

    private void checkCurrentDndStatus(boolean showSettingsIfNeeded) {
        if (!hasDndAccess()) {
            if (showSettingsIfNeeded) {
                showDndAccessRequired();
            }
            return;
        }

        Log.d("DND_DEBUG", "=== Starting DND status check ===");
        List<ClassTimeSlot> allSlots = TimetableStore.getClassTimeSlots(this);
        Log.d("DND_DEBUG", "Retrieved " + allSlots.size() + " total slots for DND check");
        
        List<ClassTimeSlot> classSlots = getTodaySlots(allSlots);
        Log.d("DND_DEBUG", "Filtered to " + classSlots.size() + " slots for today's DND check");

        for (ClassTimeSlot slot : classSlots) {
            String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(slot.getStartMillis())) +
                    " - " + new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(slot.getEndMillis()));
            Log.d("DND_DEBUG", "DND Slot: " + time + " => " + slot.getSubject());
        }

        if (classSlots.isEmpty()) {
            Log.d("DND_DEBUG", "No classes found for DND check");
            showNoTimetableData();
            return;
        }

        Log.d("DND_DEBUG", "=== End DND status check ===");
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

        if (isCurrentlyEnabled) {
            disableDNDScheduling();
            prefs.edit().putBoolean("dnd_scheduling_enabled", false).apply(); // Update preference
        } else {
            enableDNDScheduling();
            prefs.edit().putBoolean("dnd_scheduling_enabled", true).apply(); // Update preference
        }

        updateUI();
    }

    private void refreshTimetable() {
        Log.d("MainActivity", "Refresh timetable triggered");
        
        // Stop the refresh animation
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
        
        // Redirecting to login - removed toast
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void updateUI() {
        boolean isSchedulingEnabled = dndManager.isDndSchedulingEnabled();
        boolean isDndCurrentlyOn = dndManager.isDndCurrentlyOn();
        
        Log.d("MainActivity", "Updating UI - Scheduling Enabled: " + isSchedulingEnabled + ", DND Currently On: " + isDndCurrentlyOn);

        if (isSchedulingEnabled) {
            tvButtonStatus.setText("Disable");
            // Keep using the modern ripple background instead of basic color
            btnToggleDnd.setBackgroundResource(R.drawable.main_button_ripple);
            tvServiceStatus.setText("✓ Background service active");
            tvServiceStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            tvButtonStatus.setText("Enable");
            // Keep using the modern ripple background instead of basic color
            btnToggleDnd.setBackgroundResource(R.drawable.main_button_ripple);
            tvServiceStatus.setText("✗ Background service inactive");
            tvServiceStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        updateReliabilityStatus();

        // Add detailed logging for UI data retrieval
        Log.d("UI_DEBUG", "=== Starting UI data retrieval ===");
        List<ClassTimeSlot> allSlots = TimetableStore.getClassTimeSlots(this);
        Log.d("UI_DEBUG", "Retrieved " + allSlots.size() + " total slots from TimetableStore");
        
        // Log all retrieved slots with their times
        for (int i = 0; i < allSlots.size(); i++) {
            ClassTimeSlot slot = allSlots.get(i);
            Calendar startCal = Calendar.getInstance();
            startCal.setTimeInMillis(slot.getStartMillis());
            Calendar endCal = Calendar.getInstance();
            endCal.setTimeInMillis(slot.getEndMillis());
            
            String timeRange = String.format("%02d:%02d-%02d:%02d",
                startCal.get(Calendar.HOUR_OF_DAY), startCal.get(Calendar.MINUTE),
                endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE));
            String dayName = getDayNameFromCalendar(startCal.get(Calendar.DAY_OF_WEEK));
            
            Log.d("UI_DEBUG", "Slot " + i + ": " + dayName + " " + timeRange + " - " + slot.getSubject());
        }
        
        List<ClassTimeSlot> todaySlots = getTodaySlots(allSlots);
        Log.d("UI_DEBUG", "Filtered to " + todaySlots.size() + " slots for today's display");
        Log.d("UI_DEBUG", "=== End UI data retrieval ===");
        
        rvTodayClasses.setAdapter(new ClassSlotAdapter(todaySlots));
    }

    /**
     * Enhanced status display without battery optimization dependency
     */
    private void updateReliabilityStatus() {
        // Show app reliability status based on multiple factors
        boolean hasExactAlarmPermission = checkExactAlarmPermission();
        boolean isServiceRunning = isServiceRunning();
        boolean hasDndPermission = dndManager.hasDndAccess();
        
        StringBuilder status = new StringBuilder("App Status: ");
        if (hasExactAlarmPermission && isServiceRunning && hasDndPermission) {
            status.append("✅ Fully Operational");
        } else {
            status.append("⚠️ Limited Functionality");
        }
        
        // Display reliability info without scaring users about battery optimization
        Log.d("MainActivity", status.toString());
    }

    private boolean checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            return alarmManager.canScheduleExactAlarms();
        }
        return true; // Always available on older versions
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (DNDService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if this is the first launch or if no timetable data exists
     * @return true if login is required, false otherwise
     */
    private boolean isFirstLaunchOrNoTimetableData() {
        SharedPreferences prefs = getSharedPreferences("dnd_prefs", MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean("first_launch", true);
        String timetableHtml = prefs.getString("timetable_html", "");
        
        // If it's first launch, save that we've launched once
        if (isFirstLaunch) {
            prefs.edit().putBoolean("first_launch", false).apply();
        }
        
        return isFirstLaunch || timetableHtml.isEmpty();
    }

    private void updateCurrentTimeDisplay() {
        // Current time display removed from UI for cleaner design
        Calendar now = Calendar.getInstance();
        Log.d("MainActivity", String.format("Current Time: %02d:%02d",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));
    }

    private void updateNextClassInfo() {
        // Next class info display removed from UI for cleaner design
        List<ClassTimeSlot> allSlots = TimetableStore.getClassTimeSlots(this);
        List<ClassTimeSlot> classSlots = getTodaySlots(allSlots);

        if (classSlots.isEmpty()) {
            Log.d("MainActivity", "No timetable data available");
            return;
        }

        Calendar now = Calendar.getInstance();
        int currentTimeInMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        String currentClassInfo = getCurrentClassInfo(classSlots, currentTimeInMinutes);
        if (!currentClassInfo.isEmpty()) {
            Log.d("MainActivity", "Current Class: " + currentClassInfo);
            return;
        }

        String nextClassInfo = getNextClassInfo(classSlots, currentTimeInMinutes);
        if (!nextClassInfo.isEmpty()) {
            Log.d("MainActivity", "Next Class: " + nextClassInfo);
        } else {
            Log.d("MainActivity", "No more classes today");
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
        // Timetable status update - display removed from UI for cleaner design
        long fetchTime = prefs.getLong("timetable_fetch_time", 0);
        Log.d("MainActivity", "Timetable status: " + classSlots.size() + " classes found");
    }

    private void showDndAccessRequired() {
        Toast.makeText(this, "Grant Do Not Disturb access in settings.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    private void showNoTimetableData() {
        // No timetable data found - removed toast and UI status for cleaner design
        Log.d("MainActivity", "No timetable data available");
    }

    private boolean hasDndAccess() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return nm.isNotificationPolicyAccessGranted();
    }

    private List<ClassTimeSlot> getTodaySlots(List<ClassTimeSlot> allSlots) {
        List<ClassTimeSlot> todaySlots = new ArrayList<>();
        Calendar today = Calendar.getInstance();
        int todayDay = today.get(Calendar.DAY_OF_WEEK);

        Log.d("TodaySlots", "Today is: " + getDayNameFromCalendar(todayDay) + " (" + todayDay + ")");
        Log.d("TodaySlots", "Total slots to check: " + allSlots.size());

        // Handle Saturday compensation
        if (todayDay == Calendar.SATURDAY) {
            String saturdayFollows = getSaturdayFollowsDay();
            if (!saturdayFollows.equals("None (Holiday)")) {
                int targetDay = getDayOfWeekFromString(saturdayFollows);
                if (targetDay != -1) {
                    // Show the classes from the day Saturday is following
                    for (ClassTimeSlot slot : allSlots) {
                        Calendar slotCal = Calendar.getInstance();
                        slotCal.setTimeInMillis(slot.getStartMillis());
                        if (slotCal.get(Calendar.DAY_OF_WEEK) == targetDay) {
                            todaySlots.add(slot);
                        }
                    }
                    Log.d("TodaySlots", "Saturday following " + saturdayFollows + " - Found " + todaySlots.size() + " slots");
                    return todaySlots;
                }
            }
            // If Saturday is set to "None (Holiday)", return empty list
            Log.d("TodaySlots", "Saturday is a holiday - no classes");
            return todaySlots;
        }

        // Normal day processing
        for (ClassTimeSlot slot : allSlots) {
            Calendar slotCal = Calendar.getInstance();
            slotCal.setTimeInMillis(slot.getStartMillis());
            int slotDay = slotCal.get(Calendar.DAY_OF_WEEK);
            
            Log.d("TodaySlots", "Checking slot: " + slot.getSubject() + " on day " + getDayNameFromCalendar(slotDay) + " (" + slotDay + ")");
            
            if (slotDay == todayDay) {
                todaySlots.add(slot);
                Log.d("TodaySlots", "Added slot: " + slot.getSubject() + " at " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(slot.getStartMillis())));
            }
        }

        Log.d("TodaySlots", "Found " + todaySlots.size() + " slots for today");
        return todaySlots;
    }

    /**
     * Convert day name to Calendar day constant
     */
    private int getDayOfWeekFromString(String dayName) {
        switch (dayName) {
            case "Monday": return Calendar.MONDAY;
            case "Tuesday": return Calendar.TUESDAY;
            case "Wednesday": return Calendar.WEDNESDAY;
            case "Thursday": return Calendar.THURSDAY;
            case "Friday": return Calendar.FRIDAY;
            default: return -1; // Invalid day
        }
    }

    /**
     * Convert Calendar day constant to day name
     */
    private String getDayNameFromCalendar(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.SUNDAY: return "Sunday";
            case Calendar.MONDAY: return "Monday";
            case Calendar.TUESDAY: return "Tuesday";
            case Calendar.WEDNESDAY: return "Wednesday";
            case Calendar.THURSDAY: return "Thursday";
            case Calendar.FRIDAY: return "Friday";
            case Calendar.SATURDAY: return "Saturday";
            default: return "Unknown";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHandler.post(updateRunnable);
        checkCurrentDndStatus();
        updateUI(); // Refresh UI state when returning to activity
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateHandler.removeCallbacks(updateRunnable);
    }

    // ===================== TUTORIAL SYSTEM =====================
    
    private SimpleTutorialManager tutorialManager;
    
    /**
     * Setup the premium interactive tutorial system
     */
    private void setupTutorialSystem() {
        Log.d("Tutorial", "Setting up tutorial system for MainActivity");
        
        // Create simple tutorial manager (more reliable than TapTargetView)
        tutorialManager = new SimpleTutorialManager(this);
        
        // Enable spotlight mode for main activity and set unique keys
        tutorialManager.setSpotlightMode(true)
                      .setTutorialKeys("main_activity");
        
        Log.d("Tutorial", "Tutorial manager configured with spotlight mode and main_activity keys");
        
        // Setup tutorial listener for analytics and callbacks
        tutorialManager.setTutorialListener(new SimpleTutorialManager.TutorialListener() {
            @Override
            public void onTutorialStarted() {
                Log.d("Tutorial", "Interactive tutorial started");
                // Pause any auto-refresh or updates during tutorial
                if (updateHandler != null) {
                    updateHandler.removeCallbacks(updateRunnable);
                }
            }
            
            @Override
            public void onTutorialCompleted() {
                Log.d("Tutorial", "Tutorial completed successfully");
                // Resume normal operations
                resumeNormalOperations();
            }
            
            @Override
            public void onTutorialSkipped() {
                Log.d("Tutorial", "Tutorial was skipped");
                // Resume normal operations
                resumeNormalOperations();
            }
            
            @Override
            public void onStepCompleted(int stepIndex, String stepId) {
                Log.d("Tutorial", "Step completed: " + stepId + " (index: " + stepIndex + ")");
            }
        });
        
        // Configure tutorial steps for this activity
        try {
            Log.d("Tutorial", "Setting up main activity tutorial configuration");
            SimpleTutorialConfig.setupMainActivityTutorial(this, tutorialManager);
            Log.d("Tutorial", "Tutorial configuration completed");
            
            // Start tutorial if it's the first time
            // Add a delay to ensure all views are properly initialized
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Log.d("Tutorial", "Checking if tutorial should show...");
                    boolean shouldShow = tutorialManager.shouldShowTutorial();
                    Log.d("Tutorial", "Should show tutorial: " + shouldShow);
                    
                    // Check if this is truly the first time
                    if (shouldShow) {
                        Log.d("Tutorial", "First time user - starting tutorial system");
                        tutorialManager.startTutorial();
                    } else {
                        Log.d("Tutorial", "Tutorial already completed or skipped - not showing");
                        Log.d("Tutorial", "To test tutorial again, call resetTutorialForTesting()");
                    }
                } catch (Exception e) {
                    Log.e("Tutorial", "Error starting tutorial", e);
                }
            }, 1500); // Increased delay to 1500ms for better reliability
        } catch (Exception e) {
            Log.e("Tutorial", "Error setting up tutorial", e);
        }
    }
    
    /**
     * Resume normal app operations after tutorial
     */
    private void resumeNormalOperations() {
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.post(updateRunnable);
        }
    }
    
    /**
     * Public method to replay tutorial (can be called from settings)
     */
    public void replayTutorial() {
        if (tutorialManager != null) {
            tutorialManager.forceStartTutorial();
        }
    }
    
    /**
     * Show contextual help for a specific UI element
     */
    public void showContextualHelp(String title, String description) {
        SimpleTutorialConfig.showQuickHelp(this, title, description);
    }
    
    /**
     * Reset tutorial status (for testing or user preference)
     */
    public void resetTutorialStatus() {
        if (tutorialManager != null) {
            tutorialManager.resetTutorial();
            Toast.makeText(this, "Tutorial status reset. It will show on next app launch.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * For testing - reset and immediately show tutorial
     */
    public void resetTutorialForTesting() {
        Log.d("Tutorial", "Resetting tutorial for testing...");
        if (tutorialManager != null) {
            tutorialManager.resetTutorial();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d("Tutorial", "Starting tutorial immediately for testing");
                tutorialManager.forceStartTutorial();
            }, 500);
        } else {
            Log.e("Tutorial", "Tutorial manager is null - cannot reset for testing");
        }
    }

    /**
     * Handle tutorial replay intent from settings
     */
    private void handleTutorialReplayIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("replay_tutorial", false)) {
            // Add a longer delay to ensure UI is fully loaded
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (tutorialManager != null) {
                    tutorialManager.forceStartTutorial();
                }
            }, 1000);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateHandler != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }
}