package com.harish.dndscheduler;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
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
    private LinearLayout btnDndMenu;
    private TextView tvDndMenuStatus;
    private LinearLayout layoutSaturdayDropdown;
    private TextView tvSaturdaySelection;
    private RecyclerView rvTodayClasses;
    private TextView tvNoClasses;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Spinner spinnerSaturdaySchedule;

    private SharedPreferences prefs;
    private DNDManager dndManager;
    private CaptchaRefreshManager captchaRefreshManager;
    private Handler updateHandler;
    private Runnable updateRunnable;
    private android.app.Dialog dndAccessDialog;
    private boolean isRedirectingToSettings = false;
    private ActivityResultLauncher<Intent> loginActivityLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize login activity launcher for refresh functionality
        setupLoginActivityLauncher();
        
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

    private void setupLoginActivityLauncher() {
        loginActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    // Stop the refresh animation
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    
                    if (result.getResultCode() == RESULT_OK) {
                        // Login was successful, refresh the UI and data
                        Log.d("MainActivity", "Login successful, refreshing data");
                        checkCurrentDndStatus(false);
                        updateUI();
                        Toast.makeText(MainActivity.this, "Timetable refreshed successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        // Login was cancelled or failed
                        Log.d("MainActivity", "Login cancelled or failed");
                        // Removed "Refresh cancelled" toast message
                    }
                }
            }
        );
    }

    private void initializeViews() {
        tvServiceStatus = findViewById(R.id.tv_service_status);
        btnDndMenu = findViewById(R.id.btn_dnd_menu);
        tvDndMenuStatus = findViewById(R.id.tv_dnd_menu_status);
        layoutSaturdayDropdown = findViewById(R.id.layout_saturday_dropdown);
        tvSaturdaySelection = findViewById(R.id.tv_saturday_selection);
        rvTodayClasses = findViewById(R.id.rv_today_classes);
        tvNoClasses = findViewById(R.id.tv_no_classes);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        spinnerSaturdaySchedule = findViewById(R.id.spinner_saturday_schedule);

        // Add null checks
        if (btnDndMenu == null) {
            Log.e("MainActivity", "CRITICAL: btnDndMenu is null - check layout file R.id.btn_dnd_menu");
            return;
        }
        
        if (tvDndMenuStatus == null) {
            Log.e("MainActivity", "CRITICAL: tvDndMenuStatus is null - check layout file R.id.tv_dnd_menu_status");
            return;
        }
        
        Log.d("MainActivity", "Setting up button click listeners");
        btnDndMenu.setOnClickListener(v -> {
            Log.d("MainActivity", "DND Menu button clicked");
            showDndMenu();
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
        captchaRefreshManager = new CaptchaRefreshManager(this);
        
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

    private void showDndMenu() {
        // Inflate custom dialog layout
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_dnd_menu, null);
        
        // Get dialog components
        Switch switchAutoSilent = dialogView.findViewById(R.id.switch_auto_silent);
        RadioGroup radioGroupMode = dialogView.findViewById(R.id.radio_group_mode);
        RadioButton radioDnd = dialogView.findViewById(R.id.radio_dnd);
        RadioButton radioVibrate = dialogView.findViewById(R.id.radio_vibrate);
        RadioButton radioSilent = dialogView.findViewById(R.id.radio_silent);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnApply = dialogView.findViewById(R.id.btn_apply);
        
        // Store original states for cancel functionality
        final boolean originalSchedulingEnabled = dndManager.isDndSchedulingEnabled();
        final String originalMode = dndManager.getSilentModeType();
        
        // Set current states
        switchAutoSilent.setChecked(originalSchedulingEnabled);
        
        // Auto Silent toggle - changes immediately, no Apply needed
        switchAutoSilent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                enableDNDScheduling();
                prefs.edit().putBoolean("dnd_scheduling_enabled", true).apply();
            } else {
                disableDNDScheduling();
                prefs.edit().putBoolean("dnd_scheduling_enabled", false).apply();
            }
            updateUI();
        });
        
        // Set current mode selection - no immediate changes, only visual
        switch (originalMode) {
            case "dnd":
                radioDnd.setChecked(true);
                break;
            case "vibrate":
                radioVibrate.setChecked(true);
                break;
            case "silent":
                radioSilent.setChecked(true);
                break;
        }
        
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(true);
        
        AlertDialog dialog = builder.create();
        
        // No immediate changes - just UI feedback
        // Remove the listeners that immediately apply changes
        
        // Handle buttons
        btnCancel.setOnClickListener(v -> {
            // Revert any changes (though nothing should be applied yet)
            dialog.dismiss();
        });
        
        btnApply.setOnClickListener(v -> {
            // Only apply mode selection changes (Auto Silent is handled immediately)
            String selectedMode = "vibrate"; // default
            if (radioDnd.isChecked()) {
                selectedMode = "dnd";
            } else if (radioVibrate.isChecked()) {
                selectedMode = "vibrate";
            } else if (radioSilent.isChecked()) {
                selectedMode = "silent";
            }
            
            if (!selectedMode.equals(originalMode)) {
                dndManager.setSilentModeType(selectedMode);
            }
            
            updateUI();
            dialog.dismiss();
        });
        
        dialog.show();
    }

    private void showCaptchaRefreshDialog() {
        // Check if credentials are available
        if (!captchaRefreshManager.hasStoredCredentials()) {
            // Fall back to full login if no credentials stored
            showFullLoginFallback();
            return;
        }

        // Inflate custom dialog layout
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_captcha_refresh, null);
        
        // Get dialog components
        ImageView imgCaptcha = dialogView.findViewById(R.id.img_captcha);
        EditText etCaptcha = dialogView.findViewById(R.id.et_captcha);
        ImageButton btnRefreshCaptcha = dialogView.findViewById(R.id.btn_refresh_captcha);
        Button btnSubmit = dialogView.findViewById(R.id.btn_submit);
        LinearLayout tvFullLogin = dialogView.findViewById(R.id.tv_full_login);
        
        // Create dialog with Material 3 styling
        AlertDialog.Builder builder = new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog);
        builder.setView(dialogView);
        builder.setCancelable(true);
        
        AlertDialog dialog = builder.create();
        
        // Stop refresh loading if dialog is dismissed by back gesture or outside tap
        dialog.setOnDismissListener(dialogInterface -> {
            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
        
        // Apply window styling for modern look
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
        
        // Setup captcha refresh callback
        CaptchaRefreshManager.CaptchaRefreshCallback callback = new CaptchaRefreshManager.CaptchaRefreshCallback() {
            @Override
            public void onCaptchaFetched(Bitmap captchaBitmap) {
                imgCaptcha.setImageBitmap(captchaBitmap);
                btnRefreshCaptcha.setEnabled(true);
                btnSubmit.setEnabled(true);
                // Reset button state in case it was in processing mode
                btnSubmit.setText("Submit");
                btnSubmit.setAlpha(1.0f);
            }

            @Override
            public void onRefreshSuccess() {
                // Reset button state
                btnSubmit.setText("Submit");
                btnSubmit.setAlpha(1.0f);
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(MainActivity.this, "Timetable refreshed successfully", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                updateUI(); // Refresh the UI with new data
            }

            @Override
            public void onRefreshError(String error) {
                // Reset button state
                btnSubmit.setText("Submit");
                btnSubmit.setAlpha(1.0f);
                swipeRefreshLayout.setRefreshing(false);
                btnRefreshCaptcha.setEnabled(true);
                btnSubmit.setEnabled(true);
                
                // Clear captcha input on error
                etCaptcha.setText("");
                
                // Show specific error message
                if (error.contains("Invalid captcha")) {
                    Toast.makeText(MainActivity.this, "Invalid captcha. Please try again.", Toast.LENGTH_SHORT).show();
                    // Fetch new captcha for retry
                    fetchCaptchaForDialog(imgCaptcha, btnRefreshCaptcha, btnSubmit);
                } else if (error.contains("credentials")) {
                    Toast.makeText(MainActivity.this, "Please use full login to update credentials.", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    showFullLoginFallback();
                } else {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                    // Fetch new captcha for retry on network errors
                    fetchCaptchaForDialog(imgCaptcha, btnRefreshCaptcha, btnSubmit);
                }
            }

            @Override
            public void onCredentialsRequired() {
                swipeRefreshLayout.setRefreshing(false);
                dialog.dismiss();
                showFullLoginFallback();
            }
        };
        
        // Function to fetch captcha
        Runnable fetchCaptcha = () -> {
            btnRefreshCaptcha.setEnabled(false);
            btnSubmit.setEnabled(false);
            captchaRefreshManager.fetchCaptcha(callback);
        };
        
        // Handle refresh captcha button
        btnRefreshCaptcha.setOnClickListener(v -> {
            etCaptcha.setText(""); // Clear previous captcha input
            fetchCaptcha.run();
        });
        
        // Handle submit button
        btnSubmit.setOnClickListener(v -> {
            String captcha = etCaptcha.getText().toString().trim();
            if (captcha.isEmpty()) {
                etCaptcha.setError("Please enter captcha");
                return;
            }
            
            // Show visual feedback that processing is happening
            btnRefreshCaptcha.setEnabled(false);
            btnSubmit.setEnabled(false);
            btnSubmit.setText("Processing...");
            btnSubmit.setAlpha(0.7f); // Make it slightly transparent to show it's disabled
            
            captchaRefreshManager.performRefreshWithCaptcha(captcha, callback);
        });
        
        // Handle full login option
        tvFullLogin.setOnClickListener(v -> {
            swipeRefreshLayout.setRefreshing(false);
            dialog.dismiss();
            showFullLoginFallback();
        });
        
        dialog.show();
        
        // Fetch initial captcha
        fetchCaptcha.run();
    }
    
    private void fetchCaptchaForDialog(ImageView imgCaptcha, ImageButton btnRefreshCaptcha, 
                                     Button btnSubmit) {
        btnRefreshCaptcha.setEnabled(false);
        btnSubmit.setEnabled(false);
        
        captchaRefreshManager.fetchCaptcha(new CaptchaRefreshManager.CaptchaRefreshCallback() {
            @Override
            public void onCaptchaFetched(Bitmap captchaBitmap) {
                imgCaptcha.setImageBitmap(captchaBitmap);
                btnRefreshCaptcha.setEnabled(true);
                btnSubmit.setEnabled(true);
                // Reset button state in case it was in processing mode
                btnSubmit.setText("Submit");
                btnSubmit.setAlpha(1.0f);
            }

            @Override
            public void onRefreshSuccess() {
                // This won't be called for captcha-only fetch
            }

            @Override
            public void onRefreshError(String error) {
                btnRefreshCaptcha.setEnabled(true);
                btnSubmit.setEnabled(true);
                Toast.makeText(MainActivity.this, "Failed to load captcha", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCredentialsRequired() {
                // This won't be called for captcha-only fetch
            }
        });
    }
    
    private void showFullLoginFallback() {
        swipeRefreshLayout.setRefreshing(false);
        
        // Launch LoginActivity for result
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("is_refresh", true);
        loginActivityLauncher.launch(intent);
    }

    private void toggleModeType() {
        String currentMode = dndManager.getSilentModeType();
        String newMode = "dnd".equals(currentMode) ? "vibrate" : "dnd";
        
        dndManager.setSilentModeType(newMode);
        
        Toast.makeText(this, 
            "vibrate".equals(newMode) ? "Switched to Vibrate Mode" : "Switched to Do Not Disturb Mode", 
            Toast.LENGTH_SHORT).show();
        
        updateUI();
    }

    private void refreshTimetable() {
        Log.d("MainActivity", "Refresh timetable triggered");
        
        // Show loading state
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }
        
        // Try captcha-only refresh first, fall back to full login if needed
        showCaptchaRefreshDialog();
    }

    private void updateUI() {
        boolean isSchedulingEnabled = dndManager.isDndSchedulingEnabled();
        boolean isDndCurrentlyOn = dndManager.isDndCurrentlyOn();
        
        Log.d("MainActivity", "Updating UI - Scheduling Enabled: " + isSchedulingEnabled + ", DND Currently On: " + isDndCurrentlyOn);

        // Update service status and DND menu button
        if (tvServiceStatus != null) {
            if (isSchedulingEnabled) {
                tvServiceStatus.setText("✓ Background service active");
                tvServiceStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                tvServiceStatus.setText("✗ Background service inactive");
                tvServiceStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        } else {
            Log.e("MainActivity", "tvServiceStatus is null in updateUI");
        }

        // Update DND Menu button label based on current mode
        if (tvDndMenuStatus != null) {
            String currentMode = dndManager.getSilentModeType();
            String modeText = currentMode.equals("dnd") ? "DND" : 
                             currentMode.equals("vibrate") ? "VIBRATE" : "SILENT";
            tvDndMenuStatus.setText(modeText);
            Log.d("MainActivity", "Updated DND Menu status to: " + modeText);
        } else {
            Log.e("MainActivity", "tvDndMenuStatus is null in updateUI");
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
            
            String timeRange = String.format(Locale.ROOT, "%02d:%02d-%02d:%02d",
                startCal.get(Calendar.HOUR_OF_DAY), startCal.get(Calendar.MINUTE),
                endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE));
            String dayName = getDayNameFromCalendar(startCal.get(Calendar.DAY_OF_WEEK));
            
            Log.d("UI_DEBUG", "Slot " + i + ": " + dayName + " " + timeRange + " - " + slot.getSubject());
        }
        
        List<ClassTimeSlot> todaySlots = getTodaySlots(allSlots);
        Log.d("UI_DEBUG", "Filtered to " + todaySlots.size() + " slots for today's display");
        Log.d("UI_DEBUG", "=== End UI data retrieval ===");
        
        if (todaySlots.isEmpty()) {
            // Show "No classes today" message
            rvTodayClasses.setVisibility(View.GONE);
            tvNoClasses.setVisibility(View.VISIBLE);
        } else {
            // Show classes list
            rvTodayClasses.setVisibility(View.VISIBLE);
            tvNoClasses.setVisibility(View.GONE);
            rvTodayClasses.setAdapter(new ClassSlotAdapter(todaySlots));
        }
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
            boolean canSchedule = alarmManager.canScheduleExactAlarms();
            if (!canSchedule) {
                showExactAlarmRationale();
            }
            return canSchedule;
        }
        return true; // Always available on older versions
    }

    private void showExactAlarmRationale() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Precise Scheduling Required")
                .setMessage("This app needs precise alarm scheduling to:\n\n" +
                           "• Turn DND on/off at exact class times\n" +
                           "• Ensure reliable silent mode during lectures\n" +
                           "• Sync with your university timetable\n\n" +
                           "Without this, DND might activate late or miss your classes.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Toast.makeText(this, "Scheduling may be less reliable", Toast.LENGTH_SHORT).show();
                })
                .show();
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
        // Show beautiful tutorial-style card instead of plain dialog
        showDndAccessCard();
    }

    private void showDndAccessCard() {
        // Inflate the custom DND access card layout
        View cardView = getLayoutInflater().inflate(R.layout.dnd_access_card, null);
        
        // Create a dialog with the custom layout
        dndAccessDialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dndAccessDialog.setContentView(cardView);
        dndAccessDialog.setCancelable(false);
        
        // Set up button click listeners
        TextView btnNotNow = cardView.findViewById(R.id.btn_not_now);
        TextView btnGrantAccess = cardView.findViewById(R.id.btn_grant_access);
        
        btnNotNow.setOnClickListener(v -> {
            dndAccessDialog.dismiss();
            dndAccessDialog = null;
            Toast.makeText(this, "DND scheduling will be disabled", Toast.LENGTH_SHORT).show();
        });
        
        btnGrantAccess.setOnClickListener(v -> {
            // Show loading state but keep dialog open
            btnGrantAccess.setText("Redirecting to Settings...");
            btnGrantAccess.setEnabled(false);
            btnNotNow.setEnabled(false);
            
            // Set flag to indicate we're redirecting to settings
            isRedirectingToSettings = true;
            
            // Start settings activity
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        });
        
        // Show the dialog
        dndAccessDialog.show();
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
        
        // Clean up dialog state when returning from settings
        if (isRedirectingToSettings) {
            isRedirectingToSettings = false;
            if (dndAccessDialog != null && dndAccessDialog.isShowing()) {
                dndAccessDialog.dismiss();
                dndAccessDialog = null;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateHandler.removeCallbacks(updateRunnable);
        
        // Dismiss DND access dialog only if we're redirecting to settings
        if (isRedirectingToSettings && dndAccessDialog != null && dndAccessDialog.isShowing()) {
            // Add a small delay to ensure settings screen is actually loading
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (dndAccessDialog != null && dndAccessDialog.isShowing()) {
                    dndAccessDialog.dismiss();
                    dndAccessDialog = null;
                }
                isRedirectingToSettings = false;
            }, 1000); // 1 second delay to ensure smooth transition
        }
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
        if (captchaRefreshManager != null) {
            captchaRefreshManager.shutdown();
        }
    }
}