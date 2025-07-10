package com.harish.dndscheduler;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

import java.util.List;

public class DNDManager {

    private Context context;
    private AlarmManager alarmManager;
    private NotificationManager notificationManager;

    public DNDManager(Context context) {
        this.context = context;
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Schedule DND ON/OFF for all class time slots.
     * Reads from SharedPreferences or DB depending on your app design.
     */
    public void scheduleDndForClasses() {
        // 🔑 Replace with your actual timetable fetch logic
        List<ClassTimeSlot> classSlots = TimetableStore.getClassTimeSlots(context);

        if (classSlots == null || classSlots.isEmpty()) {
            Toast.makeText(context, "No timetable data found.", Toast.LENGTH_SHORT).show();
            return;
        }

        for (ClassTimeSlot slot : classSlots) {
            scheduleDnd(slot.getStartMillis(), slot.getEndMillis());
        }
    }

    /**
     * Cancel all scheduled DND ON/OFF alarms.
     * Uses unique request codes based on timestamp as used in scheduling.
     */
    public void cancelDndSchedules() {
        List<ClassTimeSlot> classSlots = TimetableStore.getClassTimeSlots(context);
        if (classSlots == null) return;

        for (ClassTimeSlot slot : classSlots) {
            cancelAlarm(slot.getStartMillis(), "TURN_ON_DND");
            cancelAlarm(slot.getEndMillis(), "TURN_OFF_DND");
        }
    }

    /**
     * Turn DND ON immediately.
     */
    public void setDndOn() {
        if (hasDndAccess()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        } else {
            requestDndAccess();
        }
    }

    /**
     * Turn DND OFF immediately.
     */
    public void setDndOff() {
        if (hasDndAccess()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
        } else {
            requestDndAccess();
        }
    }

    /**
     * Internal: Schedule TURN_ON_DND and TURN_OFF_DND alarms.
     */
    private void scheduleDnd(long startMillis, long endMillis) {
        scheduleAlarm(startMillis, "TURN_ON_DND");
        scheduleAlarm(endMillis, "TURN_OFF_DND");
    }

    /**
     * Internal: Schedule alarm for a specific action.
     */
    private void scheduleAlarm(long triggerAtMillis, String action) {
        Intent intent = new Intent(context, DNDReceiver.class);
        intent.setAction(action);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) triggerAtMillis,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
    }

    /**
     * Internal: Cancel alarm for a specific timestamp and action.
     */
    private void cancelAlarm(long triggerAtMillis, String action) {
        Intent intent = new Intent(context, DNDReceiver.class);
        intent.setAction(action);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) triggerAtMillis,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
    }

    /**
     * Check if app has DND policy access.
     */
    private boolean hasDndAccess() {
        return notificationManager.isNotificationPolicyAccessGranted();
    }

    /**
     * Request user to grant DND policy access.
     */
    private void requestDndAccess() {
        Toast.makeText(context, "Grant Do Not Disturb access in settings.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
