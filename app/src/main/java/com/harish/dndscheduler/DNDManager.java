package com.harish.dndscheduler;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

public class DNDManager {

    private Context context;
    private AlarmManager alarmManager;
    private NotificationManager notificationManager;
    private static final String TAG = "DNDManager";

    public DNDManager(Context context) {
        this.context = context;
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Schedule DND ON/OFF for all class time slots.
     */
    public void scheduleDndForClasses() {
        try {
            List<ClassTimeSlot> classSlots = TimetableStore.getClassTimeSlots(context);

            if (classSlots == null || classSlots.isEmpty()) {
                Log.d(TAG, "No timetable data found");
                Toast.makeText(context, "No timetable data found.", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "Scheduling DND for " + classSlots.size() + " class slots");

            for (int i = 0; i < classSlots.size(); i++) {
                ClassTimeSlot slot = classSlots.get(i);
                scheduleDnd(slot.getStartMillis(), slot.getEndMillis(), i);
            }

            Toast.makeText(context, "DND scheduled for " + classSlots.size() + " classes", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling DND for classes", e);
            Toast.makeText(context, "Error scheduling DND: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Cancel all scheduled DND ON/OFF alarms.
     */
    public void cancelDndSchedules() {
        try {
            List<ClassTimeSlot> classSlots = TimetableStore.getClassTimeSlots(context);
            if (classSlots == null) {
                Log.d(TAG, "No class slots to cancel");
                return;
            }

            Log.d(TAG, "Canceling DND schedules for " + classSlots.size() + " class slots");

            for (int i = 0; i < classSlots.size(); i++) {
                ClassTimeSlot slot = classSlots.get(i);
                cancelAlarm(slot.getStartMillis(), "TURN_ON_DND", i);
                cancelAlarm(slot.getEndMillis(), "TURN_OFF_DND", i);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error canceling DND schedules", e);
        }
    }

    /**
     * Turn DND ON immediately.
     */
    public void setDndOn() {
        try {
            if (hasDndAccess()) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                Log.d(TAG, "DND turned ON");
            } else {
                Log.w(TAG, "No DND access - requesting permission");
                requestDndAccess();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error turning DND ON", e);
        }
    }

    /**
     * Turn DND OFF immediately.
     */
    public void setDndOff() {
        try {
            if (hasDndAccess()) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                Log.d(TAG, "DND turned OFF");
            } else {
                Log.w(TAG, "No DND access - requesting permission");
                requestDndAccess();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error turning DND OFF", e);
        }
    }

    /**
     * Internal: Schedule TURN_ON_DND and TURN_OFF_DND alarms.
     */
    private void scheduleDnd(long startMillis, long endMillis, int slotIndex) {
        scheduleAlarm(startMillis, "TURN_ON_DND", slotIndex);
        scheduleAlarm(endMillis, "TURN_OFF_DND", slotIndex);
    }

    /**
     * Internal: Schedule alarm for a specific action.
     * Uses safer request code generation.
     */
    private void scheduleAlarm(long triggerAtMillis, String action, int slotIndex) {
        try {
            Intent intent = new Intent(context, DNDReceiver.class);
            intent.setAction(action);

            // Generate safe request code using slot index and action type
            int requestCode = generateSafeRequestCode(slotIndex, action);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Check if alarm manager is available
            if (alarmManager != null) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                Log.d(TAG, "Scheduled alarm: " + action + " at " + triggerAtMillis + " with request code " + requestCode);
            } else {
                Log.e(TAG, "AlarmManager is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling alarm for action: " + action, e);
        }
    }

    /**
     * Internal: Cancel alarm for a specific timestamp and action.
     */
    private void cancelAlarm(long triggerAtMillis, String action, int slotIndex) {
        try {
            Intent intent = new Intent(context, DNDReceiver.class);
            intent.setAction(action);

            int requestCode = generateSafeRequestCode(slotIndex, action);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
                Log.d(TAG, "Canceled alarm: " + action + " with request code " + requestCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error canceling alarm for action: " + action, e);
        }
    }

    /**
     * Generate a safe request code that won't overflow.
     */
    private int generateSafeRequestCode(int slotIndex, String action) {
        // Use slot index and action type to generate unique but safe request codes
        int actionCode = "TURN_ON_DND".equals(action) ? 1 : 2;
        return (slotIndex * 10) + actionCode;
    }

    /**
     * Check if app has DND policy access.
     */
    private boolean hasDndAccess() {
        try {
            return notificationManager != null && notificationManager.isNotificationPolicyAccessGranted();
        } catch (Exception e) {
            Log.e(TAG, "Error checking DND access", e);
            return false;
        }
    }

    /**
     * Request user to grant DND policy access.
     */
    private void requestDndAccess() {
        try {
            Toast.makeText(context, "Grant Do Not Disturb access in settings.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error requesting DND access", e);
            Toast.makeText(context, "Please manually grant DND access in Settings > Apps", Toast.LENGTH_LONG).show();
        }
    }
}