package com.harish.dndscheduler;
 
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class DNDManager {

    private static DNDManager instance;
    private final Context context;
    private final AlarmManager alarmManager;
    private final NotificationManager notificationManager;
    private final AudioManager audioManager;
    private final SharedPreferences prefs;
    private static final String TAG = "DNDManager";
    private boolean isRequestingDndAccess = false; 

    private DNDManager(Context context) {
        this.context = context.getApplicationContext(); // Use app context to prevent leaks
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.prefs = context.getSharedPreferences("dnd_prefs", Context.MODE_PRIVATE);
    }

    public static synchronized DNDManager getInstance(Context context) {
        if (instance == null) {
            instance = new DNDManager(context);
        }
        return instance;
    }

    public void scheduleDndForClasses() {
        List<ClassTimeSlot> slots = TimetableStore.getClassTimeSlots(context);

        if (slots == null || slots.isEmpty()) {
            // No timetable data available
            return;
        }

        // First, cancel all existing alarms to avoid duplicates
        cancelAllAlarms();

        // Get Saturday configuration from MainActivity
        String saturdayFollows = getSaturdayFollowsDay();
        Log.d(TAG, "Saturday follows: " + saturdayFollows);

        for (ClassTimeSlot slot : slots) {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(slot.getStartMillis());

            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(slot.getEndMillis());

            int dayOfWeek = start.get(Calendar.DAY_OF_WEEK);

            // Schedule for the original day
            scheduleSlotForDay(slot, start, end, dayOfWeek);

            // Handle Saturday compensation
            if (!saturdayFollows.equals("None (Holiday)")) {
                int saturdayTargetDay = getDayOfWeekFromString(saturdayFollows);
                if (dayOfWeek == saturdayTargetDay) {
                    // This day's schedule should also apply to Saturday
                    scheduleSlotForDay(slot, start, end, Calendar.SATURDAY);
                    Log.d(TAG, "Scheduled Saturday to follow " + saturdayFollows + " for " + slot.getSubject());
                }
            }
        }

        // Schedule a periodic check alarm every 10 minutes
        schedulePeriodicCheck();

        prefs.edit().putBoolean("dnd_scheduling_enabled", true).apply();
    }

    /**
     * Schedule alarms for a specific slot and day
     */
    private void scheduleSlotForDay(ClassTimeSlot slot, Calendar start, Calendar end, int dayOfWeek) {
        // Generate unique request codes to avoid collisions
        int startRequestCode = generateRequestCode(dayOfWeek, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), true);
        int endRequestCode = generateRequestCode(dayOfWeek, end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE), false);

        // Schedule exact alarms for each class
        scheduleExactRecurringAlarm(dayOfWeek, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE),
                "TURN_ON_DND", startRequestCode);

        scheduleExactRecurringAlarm(dayOfWeek, end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE),
                "TURN_OFF_DND", endRequestCode);

        Log.d(TAG, "Scheduled exact alarms for day " + dayOfWeek + ": ON at " +
                start.get(Calendar.HOUR_OF_DAY) + ":" + start.get(Calendar.MINUTE));
    }

    /**
     * Get which day Saturday should follow from preferences
     */
    private String getSaturdayFollowsDay() {
        return MainActivity.getSaturdayFollowsDayStatic(context);
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

    private int generateRequestCode(int dayOfWeek, int hour, int minute, boolean isStart) {
        // Generate unique request code: dayOfWeek(1) + hour(2) + minute(2) + type(1)
        return dayOfWeek * 10000 + hour * 100 + minute * 10 + (isStart ? 1 : 0);
    }

    private void scheduleExactRecurringAlarm(int dayOfWeek, int hour, int minute, String action, int requestCode) {
        Intent intent = new Intent(context, DNDReceiver.class);
        intent.setAction(action);
        intent.putExtra("day_of_week", dayOfWeek);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);
        intent.putExtra("request_code", requestCode);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Schedule for the next occurrence of this day/time
        Calendar nextOccurrence = getNextOccurrence(dayOfWeek, hour, minute);

        // Use setExactAndAllowWhileIdle for better reliability
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextOccurrence.getTimeInMillis(),
                    pi
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    nextOccurrence.getTimeInMillis(),
                    pi
            );
        }

        Log.d(TAG, "Scheduled exact alarm for " + action + " at " +
                nextOccurrence.getTime().toString() + " (RequestCode: " + requestCode + ")");
    }

    private Calendar getNextOccurrence(int dayOfWeek, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Calculate days until next occurrence
        int currentDay = cal.get(Calendar.DAY_OF_WEEK);
        int daysUntilTarget = (dayOfWeek - currentDay + 7) % 7;

        // If it's today but time has passed, schedule for next week
        if (daysUntilTarget == 0 && cal.getTimeInMillis() <= System.currentTimeMillis()) {
            daysUntilTarget = 7;
        }

        cal.add(Calendar.DAY_OF_YEAR, daysUntilTarget);
        return cal;
    }

    public void schedulePeriodicCheck() {
        Intent intent = new Intent(context, DNDReceiver.class);
        intent.setAction("PERIODIC_CHECK");

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                9999, // Unique request code for periodic check
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 1); // Start in 1 minute

        // Use setRepeating for periodic check (less critical timing)
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                10 * 60 * 1000, // 10 minutes instead of 15 for better responsiveness
                pi
        );

        Log.d(TAG, "Scheduled periodic DND check every 10 minutes");
    }

    public void checkAndSetCurrentDndStatus(List<ClassTimeSlot> classSlots) {
        if (classSlots == null) {
            classSlots = TimetableStore.getClassTimeSlots(context);
        }

        if (classSlots == null || classSlots.isEmpty()) {
            Log.w(TAG, "No class slots available for DND check");
            return;
        }

        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_WEEK);
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        boolean inClass = false;
        String currentClassName = "";

        // Handle Saturday compensation
        String saturdayFollows = getSaturdayFollowsDay();
        boolean isSaturday = (today == Calendar.SATURDAY);
        int targetDay = -1;
        if (isSaturday && !saturdayFollows.equals("None (Holiday)")) {
            targetDay = getDayOfWeekFromString(saturdayFollows);
            Log.d(TAG, "Saturday following " + saturdayFollows + " schedule (target day: " + targetDay + ")");
        } else if (isSaturday) {
            Log.d(TAG, "Saturday is a holiday - checking if DND should be turned off");
            // Saturday is a holiday, but we still need to check if DND is currently on and turn it off
            boolean currentDndStatus = isDndCurrentlyOn();
            if (currentDndStatus && wasDndSetByApp()) {
                setDndOff();
                Log.d(TAG, "Saturday is holiday and DND was on - turning DND OFF");
            }
            return; // No classes scheduled for Saturday holiday
        }

        for (ClassTimeSlot slot : classSlots) {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(slot.getStartMillis());

            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(slot.getEndMillis());

            int slotDay = start.get(Calendar.DAY_OF_WEEK);

            // Check if this slot applies to today
            boolean slotApplies = false;
            if (isSaturday && !saturdayFollows.equals("None (Holiday)")) {
                // On Saturday, check if this slot is from the day Saturday is following
                slotApplies = (slotDay == targetDay);
            } else {
                // Normal day, check if slot matches today
                slotApplies = (slotDay == today);
            }

            if (!slotApplies) continue;

            int startMin = start.get(Calendar.HOUR_OF_DAY) * 60 + start.get(Calendar.MINUTE);
            int endMin = end.get(Calendar.HOUR_OF_DAY) * 60 + end.get(Calendar.MINUTE);

            if (nowMinutes >= startMin && nowMinutes < endMin) {
                inClass = true;
                currentClassName = slot.getSubject();
                Log.d(TAG, "Currently in class: " + currentClassName + " (" + startMin + "-" + endMin + ", now: " + nowMinutes + ")");
                break;
            }
        }

        boolean currentDndStatus = isDndCurrentlyOn();
        Log.d(TAG, "DND Status Check - InClass: " + inClass + ", CurrentDND: " + currentDndStatus);

        if (inClass && !currentDndStatus) {
            setDndOn();
            Log.d(TAG, "In class: Turning DND ON immediately");
        } else if (!inClass && currentDndStatus && wasDndSetByApp()) {
            setDndOff();
            Log.d(TAG, "Not in class: Turning DND OFF immediately");
        } else {
            Log.d(TAG, "No DND change needed - InClass: " + inClass + ", CurrentDND: " + currentDndStatus + ", SetByApp: " + wasDndSetByApp());
        }
    }

    private boolean wasDndSetByApp() {
        // Check if DND was set by this app (not manually by user)
        return prefs.getBoolean("dnd_set_by_app", false);
    }

    public void rescheduleAlarmAfterTrigger(int requestCode, String action) {
        // Extract day, hour, minute from request code
        int type = requestCode % 10;
        int minute = (requestCode / 10) % 100;
        int hour = (requestCode / 1000) % 100;
        int dayOfWeek = requestCode / 10000;

        Log.d(TAG, "Rescheduling alarm - Day: " + dayOfWeek + ", Hour: " + hour + ", Minute: " + minute);

        // Reschedule for next week
        scheduleExactRecurringAlarm(dayOfWeek, hour, minute, action, requestCode);
    }

    public void cancelDndSchedules() {
        Log.d(TAG, "=== Starting to cancel all DND schedules ===");
        
        // First, try the comprehensive approach
        cancelAllAlarms();
        
        // Nuclear option: try to cancel with different intent patterns
        cancelAlarmsWithDifferentPatterns();
        
        if (wasDndSetByApp()) {
            setDndOff();
            Log.d(TAG, "DND was set by app, turning it OFF");
        }
        prefs.edit().putBoolean("dnd_scheduling_enabled", false).apply();
        Log.d(TAG, "Set dnd_scheduling_enabled to false");
        Log.d(TAG, "Cancelled all DND alarms");
        Log.d(TAG, "=== Finished cancelling all DND schedules ===");
    }

    /**
     * Nuclear option: try different intent patterns to ensure all alarms are cancelled
     */
    private void cancelAlarmsWithDifferentPatterns() {
        Log.d(TAG, "Using nuclear cancellation approach...");
        
        List<ClassTimeSlot> slots = TimetableStore.getClassTimeSlots(context);
        if (slots != null) {
            for (ClassTimeSlot slot : slots) {
                Calendar start = Calendar.getInstance();
                start.setTimeInMillis(slot.getStartMillis());
                Calendar end = Calendar.getInstance();
                end.setTimeInMillis(slot.getEndMillis());
                int dayOfWeek = start.get(Calendar.DAY_OF_WEEK);
                
                int startRequestCode = generateRequestCode(dayOfWeek, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), true);
                int endRequestCode = generateRequestCode(dayOfWeek, end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE), false);
                
                // Try cancelling with minimal intent (old pattern)
                cancelAlarmMinimal(startRequestCode, "TURN_ON_DND");
                cancelAlarmMinimal(endRequestCode, "TURN_OFF_DND");
                
                // Try cancelling backup alarms
                cancelAlarmMinimal(startRequestCode + 10000, "TURN_ON_DND_BACKUP");
                cancelAlarmMinimal(endRequestCode + 10000, "TURN_OFF_DND_BACKUP");
            }
        }
    }

    /**
     * Cancel alarm with minimal intent (without extras) - for backwards compatibility
     */
    private void cancelAlarmMinimal(int requestCode, String action) {
        Intent intent = new Intent(context, DNDReceiver.class);
        intent.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pi);
        Log.d(TAG, "Nuclear cancelled alarm: " + requestCode + " (" + action + ")");
    }

    private void cancelAllAlarms() {
        List<ClassTimeSlot> slots = TimetableStore.getClassTimeSlots(context);

        if (slots != null) {
            String saturdayFollows = getSaturdayFollowsDay();
            
            for (ClassTimeSlot slot : slots) {
                Calendar start = Calendar.getInstance();
                start.setTimeInMillis(slot.getStartMillis());

                Calendar end = Calendar.getInstance();
                end.setTimeInMillis(slot.getEndMillis());

                int dayOfWeek = start.get(Calendar.DAY_OF_WEEK);
                
                // Cancel alarms for the original day
                int startRequestCode = generateRequestCode(dayOfWeek, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), true);
                int endRequestCode = generateRequestCode(dayOfWeek, end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE), false);

                cancelAlarm(startRequestCode, "TURN_ON_DND");
                cancelAlarm(endRequestCode, "TURN_OFF_DND");
                
                // Cancel backup alarms with correct request codes (primary + 10000)
                cancelAlarm(startRequestCode + 10000, "TURN_ON_DND_BACKUP");
                cancelAlarm(endRequestCode + 10000, "TURN_OFF_DND_BACKUP");

                // Cancel Saturday compensation alarms if they exist
                if (!saturdayFollows.equals("None (Holiday)")) {
                    int saturdayTargetDay = getDayOfWeekFromString(saturdayFollows);
                    if (dayOfWeek == saturdayTargetDay) {
                        // Cancel Saturday alarms for this slot
                        int satStartRequestCode = generateRequestCode(Calendar.SATURDAY, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), true);
                        int satEndRequestCode = generateRequestCode(Calendar.SATURDAY, end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE), false);
                        
                        cancelAlarm(satStartRequestCode, "TURN_ON_DND");
                        cancelAlarm(satEndRequestCode, "TURN_OFF_DND");
                        
                        // Cancel Saturday backup alarms with correct request codes (primary + 10000)
                        cancelAlarm(satStartRequestCode + 10000, "TURN_ON_DND_BACKUP");
                        cancelAlarm(satEndRequestCode + 10000, "TURN_OFF_DND_BACKUP");
                        
                        Log.d(TAG, "Cancelled Saturday compensation alarms for " + slot.getSubject());
                    }
                }
            }
        }

        // Cancel periodic check
        cancelAlarm(9999, "PERIODIC_CHECK");
        
        // Cancel any possible backup periodic checks
        cancelAlarm(9999 + 10000, "PERIODIC_CHECK_BACKUP");
    }

    /**
     * Cancel all possible Saturday alarms for all weekdays
     * This is used when Saturday setting changes to ensure clean slate
     */
    public void cancelAllSaturdayAlarms() {
        List<ClassTimeSlot> slots = TimetableStore.getClassTimeSlots(context);
        if (slots == null) return;

        Log.d(TAG, "Cancelling all possible Saturday alarms...");

        // Cancel Saturday alarms for all possible time slots
        for (ClassTimeSlot slot : slots) {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(slot.getStartMillis());

            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(slot.getEndMillis());

            // Generate Saturday alarm request codes for this time slot
            int satStartRequestCode = generateRequestCode(Calendar.SATURDAY, 
                start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), true);
            int satEndRequestCode = generateRequestCode(Calendar.SATURDAY, 
                end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE), false);
            
            cancelAlarm(satStartRequestCode, "TURN_ON_DND");
            cancelAlarm(satEndRequestCode, "TURN_OFF_DND");
        }

        Log.d(TAG, "All Saturday alarms cancelled");
    }

    /**
     * Force an immediate DND status check and update
     * Useful when settings change and we need immediate effect
     */
    public void forceImmediateDndStatusCheck() {
        Log.d(TAG, "Forcing immediate DND status check...");
        
        // Get all slots and check current status
        List<ClassTimeSlot> slots = TimetableStore.getClassTimeSlots(context);
        if (slots != null && !slots.isEmpty()) {
            checkAndSetCurrentDndStatus(slots);
        } else {
            Log.w(TAG, "No slots available for immediate DND check");
        }
    }

    private void cancelAlarm(int requestCode, String action) {
        // Create intent that exactly matches the one used for scheduling
        Intent intent = new Intent(context, DNDReceiver.class);
        intent.setAction(action);
        
        // Extract day, hour, minute from request code for proper matching
        int type = requestCode % 10;
        int minute = (requestCode / 10) % 100;
        int hour = (requestCode / 1000) % 100;
        int dayOfWeek = requestCode / 10000;
        
        // Add the same extras that were used during scheduling
        intent.putExtra("day_of_week", dayOfWeek);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);
        intent.putExtra("request_code", requestCode);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pi);
        Log.d(TAG, "Cancelled alarm with request code: " + requestCode + " (Day: " + dayOfWeek + ", Time: " + hour + ":" + minute + ", Action: " + action + ")");
    }

    public boolean setSilentModeOn() {
        if (!isDndSchedulingEnabled()) {
            Log.d(TAG, "setSilentModeOn() called but scheduling is disabled. Ignoring.");
            return false;
        }
        
        String silentModeType = getSilentModeType();
        
        if ("vibrate".equals(silentModeType)) {
            return setVibrateMode();
        } else if ("silent".equals(silentModeType)) {
            return setSilentMode();
        } else {
            return setDndMode();
        }
    }

    public boolean setSilentModeOff() {
        if (!isDndSchedulingEnabled()) {
            Log.d(TAG, "setSilentModeOff() called but scheduling is disabled. Ignoring.");
            return false;
        }
        
        String silentModeType = getSilentModeType();
        
        if ("vibrate".equals(silentModeType) || "silent".equals(silentModeType)) {
            return restoreNormalMode();
        } else {
            return setDndOff();
        }
    }

    private boolean setVibrateMode() {
        // Store current ringer mode to restore later (only once per day)
        storeOriginalModeIfNeeded();
        
        // Set to vibrate mode
        audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        prefs.edit().putBoolean("dnd_currently_on", true).apply();
        prefs.edit().putBoolean("dnd_set_by_app", true).apply();
        Log.d(TAG, "Vibrate mode turned ON");
        return true;
    }

    private boolean setSilentMode() {
        // Store current ringer mode to restore later (only once per day)
        storeOriginalModeIfNeeded();
        
        // Set to silent mode
        audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        prefs.edit().putBoolean("dnd_currently_on", true).apply();
        prefs.edit().putBoolean("dnd_set_by_app", true).apply();
        Log.d(TAG, "Silent mode turned ON");
        return true;
    }

    /**
     * Store the original ringer mode only once per day to avoid issues with consecutive classes
     */
    private void storeOriginalModeIfNeeded() {
        String currentDate = java.text.DateFormat.getDateInstance().format(new java.util.Date());
        String storedDate = prefs.getString("original_mode_date", "");
        
        // Only store if we haven't stored for today or if no mode is stored at all
        if (!currentDate.equals(storedDate) || !prefs.getBoolean("ringer_mode_stored", false)) {
            int currentRingerMode = audioManager.getRingerMode();
            prefs.edit()
                    .putInt("original_ringer_mode", currentRingerMode)
                    .putBoolean("ringer_mode_stored", true)
                    .putString("original_mode_date", currentDate)
                    .apply();
            Log.d(TAG, "Stored original ringer mode: " + currentRingerMode + " for date: " + currentDate);
        }
    }

    /**
     * Store the original DND state only once per day for true DND mode
     */
    private void storeOriginalDndStateIfNeeded() {
        String currentDate = DateFormat.getDateInstance().format(new Date());
        String storedDate = prefs.getString("original_dnd_date", "");
        
        // Only store if we haven't stored for today or if no state is stored at all
        if (!currentDate.equals(storedDate) || !prefs.getBoolean("dnd_state_stored", false)) {
            int currentFilter = notificationManager.getCurrentInterruptionFilter();
            prefs.edit()
                    .putInt("original_dnd_filter", currentFilter)
                    .putBoolean("dnd_state_stored", true)
                    .putString("original_dnd_date", currentDate)
                    .apply();
            Log.d(TAG, "Stored original DND filter: " + currentFilter + " for date: " + currentDate);
        }
    }

    private boolean restoreNormalMode() {
        // Restore original ringer mode
        int originalRingerMode = prefs.getInt("original_ringer_mode", AudioManager.RINGER_MODE_NORMAL);
        audioManager.setRingerMode(originalRingerMode);
        
        // Only clear DND status but keep the original mode stored for consecutive classes
        prefs.edit()
                .putBoolean("dnd_currently_on", false)
                .putBoolean("dnd_set_by_app", false)
                .apply();
        
        Log.d(TAG, "Restored to original ringer mode: " + originalRingerMode);
        return true;
    }

    private boolean setDndMode() {
        if (hasDndAccess()) {
            // Store the original DND state before changing it
            storeOriginalDndStateIfNeeded();
            
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
            prefs.edit().putBoolean("dnd_currently_on", true).apply();
            prefs.edit().putBoolean("dnd_set_by_app", true).apply();
            Log.d(TAG, "DND turned ON");
            return true;
        } else {
            requestDndAccessSafely();
            return false;
        }
    }

    // Keep the old methods for backward compatibility
    public boolean setDndOn() {
        return setSilentModeOn();
    }

    public boolean setDndOff() {
        if (!isDndSchedulingEnabled()) {
            Log.d(TAG, "setDndOff() called but DND scheduling is disabled. Ignoring.");
            return false;
        }
        if (hasDndAccess()) {
            // Restore original DND filter instead of just setting to ALL
            int originalFilter = prefs.getInt("original_dnd_filter", NotificationManager.INTERRUPTION_FILTER_ALL);
            notificationManager.setInterruptionFilter(originalFilter);
            prefs.edit().putBoolean("dnd_currently_on", false).apply();
            prefs.edit().putBoolean("dnd_set_by_app", false).apply();
            Log.d(TAG, "DND turned OFF, restored to filter: " + originalFilter);
            return true;
        } else {
            requestDndAccessSafely();
            return false;
        }
    }

    public boolean isDndCurrentlyOn() {
        if (!hasDndAccess()) return false;
        int filter = notificationManager.getCurrentInterruptionFilter();
        boolean isOn = filter == NotificationManager.INTERRUPTION_FILTER_NONE;
        Log.d(TAG, "DND Status: " + isOn + " (Filter: " + filter + ")");
        return isOn;
    }

    public boolean isDndSchedulingEnabled() {
        return prefs.getBoolean("dnd_scheduling_enabled", false);
    }

    public String getSilentModeType() {
        return prefs.getString("silent_mode_type", "vibrate"); // Default to Vibrate mode (options: "dnd", "vibrate", "silent")
    }

    public void setSilentModeType(String modeType) {
        prefs.edit().putString("silent_mode_type", modeType).apply();
        Log.d(TAG, "Silent mode type set to: " + modeType);
    }

    public boolean isVibrateMode() {
        return "vibrate".equals(getSilentModeType());
    }

    public boolean hasDndAccess() {
        boolean hasAccess = notificationManager.isNotificationPolicyAccessGranted();
        if (!hasAccess) {
            Log.w(TAG, "DND access not granted");
        }
        return hasAccess;
    }

    // New method to safely request DND access without duplicates
    private synchronized void requestDndAccessSafely() {
        // Check if we're already showing the permission screen
        if (!isRequestingDndAccess) {
            isRequestingDndAccess = true;
            Log.d(TAG, "Requesting DND access - first request");
            
            // Only show toast, let MainActivity handle opening settings to avoid duplicates
            Toast.makeText(context, "Please grant Do Not Disturb access in settings", Toast.LENGTH_LONG).show();
            
            // Reset flag after delay to allow future requests if needed
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                isRequestingDndAccess = false;
                Log.d(TAG, "DND permission request flag reset");
            }, 10000); // 10 second cooldown
        } else {
            Log.d(TAG, "Skipping duplicate DND permission request");
        }
    }
    
    // Keep the old method for backward compatibility but make it call the safe version
    private void requestDndAccess() {
        requestDndAccessSafely();
    }
}