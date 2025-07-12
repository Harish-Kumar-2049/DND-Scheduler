package com.harish.dndscheduler;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;

public class DNDManager {

    private final Context context;
    private final AlarmManager alarmManager;
    private final NotificationManager notificationManager;
    private final SharedPreferences prefs;
    private static final String TAG = "DNDManager";

    public DNDManager(Context context) {
        this.context = context;
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.prefs = context.getSharedPreferences("dnd_prefs", Context.MODE_PRIVATE);
    }

    public void scheduleDndForClasses() {
        List<ClassTimeSlot> slots = TimetableStore.getClassTimeSlots(context);

        if (slots == null || slots.isEmpty()) {
            Toast.makeText(context, "No timetable data available.", Toast.LENGTH_SHORT).show();
            return;
        }

        // First, cancel all existing alarms to avoid duplicates
        cancelAllAlarms();

        for (ClassTimeSlot slot : slots) {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(slot.getStartMillis());

            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(slot.getEndMillis());

            int dayOfWeek = start.get(Calendar.DAY_OF_WEEK);

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

        // Schedule a periodic check alarm every 10 minutes
        schedulePeriodicCheck();

        prefs.edit().putBoolean("dnd_scheduling_enabled", true).apply();
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

    private void schedulePeriodicCheck() {
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

        for (ClassTimeSlot slot : classSlots) {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(slot.getStartMillis());

            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(slot.getEndMillis());

            if (start.get(Calendar.DAY_OF_WEEK) != today) continue;

            int startMin = start.get(Calendar.HOUR_OF_DAY) * 60 + start.get(Calendar.MINUTE);
            int endMin = end.get(Calendar.HOUR_OF_DAY) * 60 + end.get(Calendar.MINUTE);

            if (nowMinutes >= startMin && nowMinutes < endMin) {
                inClass = true;
                // Assuming ClassTimeSlot has a getClassName() method
                // currentClassName = slot.getClassName();
                Log.d(TAG, "Currently in class: " + startMin + "-" + endMin + " (now: " + nowMinutes + ")");
                break;
            }
        }

        boolean currentDndStatus = isDndCurrentlyOn();
        Log.d(TAG, "DND Status Check - InClass: " + inClass + ", CurrentDND: " + currentDndStatus);

        if (inClass && !currentDndStatus) {
            setDndOn();
            Log.d(TAG, "In class: Turning DND ON");
        } else if (!inClass && currentDndStatus && wasDndSetByApp()) {
            setDndOff();
            Log.d(TAG, "Not in class: Turning DND OFF");
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
        cancelAllAlarms();
        if (wasDndSetByApp()) {
            setDndOff();
        }
        prefs.edit().putBoolean("dnd_scheduling_enabled", false).apply();
        Log.d(TAG, "Cancelled all DND alarms");
    }

    private void cancelAllAlarms() {
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

                cancelAlarm(startRequestCode, "TURN_ON_DND");
                cancelAlarm(endRequestCode, "TURN_OFF_DND");
            }
        }

        // Cancel periodic check
        cancelAlarm(9999, "PERIODIC_CHECK");
    }

    private void cancelAlarm(int requestCode, String action) {
        Intent intent = new Intent(context, DNDReceiver.class);
        intent.setAction(action);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pi);
        Log.d(TAG, "Cancelled alarm with request code: " + requestCode);
    }

    public void setDndOn() {
        if (hasDndAccess()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
            prefs.edit().putBoolean("dnd_currently_on", true).apply();
            prefs.edit().putBoolean("dnd_set_by_app", true).apply();
            Log.d(TAG, "DND turned ON");
        } else {
            requestDndAccess();
        }
    }

    public void setDndOff() {
        if (hasDndAccess()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            prefs.edit().putBoolean("dnd_currently_on", false).apply();
            prefs.edit().putBoolean("dnd_set_by_app", false).apply();
            Log.d(TAG, "DND turned OFF");
        } else {
            requestDndAccess();
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

    private boolean hasDndAccess() {
        boolean hasAccess = notificationManager.isNotificationPolicyAccessGranted();
        if (!hasAccess) {
            Log.w(TAG, "DND access not granted");
        }
        return hasAccess;
    }

    private void requestDndAccess() {
        Toast.makeText(context, "Please grant Do Not Disturb access in settings", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}