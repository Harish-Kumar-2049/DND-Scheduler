package com.harish.dndscheduler;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

        for (ClassTimeSlot slot : slots) {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(slot.getStartMillis());

            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(slot.getEndMillis());

            int dayOfWeek = start.get(Calendar.DAY_OF_WEEK);

            scheduleWeeklyAlarm(dayOfWeek, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE),
                    "TURN_ON_DND", (int) slot.getStartMillis());

            scheduleWeeklyAlarm(dayOfWeek, end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE),
                    "TURN_OFF_DND", (int) slot.getEndMillis());

            Log.d("DNDManager", "Scheduled weekly DND for class on day " + dayOfWeek +
                    " at " + start.get(Calendar.HOUR_OF_DAY) + ":" + start.get(Calendar.MINUTE));
        }

        prefs.edit().putBoolean("dnd_scheduling_enabled", true).apply();
    }

    private void scheduleWeeklyAlarm(int dayOfWeek, int hour, int minute, String action, int requestCode) {
        Intent intent = new Intent(context, DNDReceiver.class);
        intent.setAction(action);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        }

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY * 7,
                pi
        );
    }

    public void checkAndSetCurrentDndStatus(List<ClassTimeSlot> classSlots) {
        if (classSlots == null) {
            classSlots = TimetableStore.getClassTimeSlots(context);
        }

        Calendar now = Calendar.getInstance();
        int nowDay = now.get(Calendar.DAY_OF_WEEK);
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        boolean inClass = false;
        String currentClass = "";

        for (ClassTimeSlot slot : classSlots) {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(slot.getStartMillis());

            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(slot.getEndMillis());

            if (start.get(Calendar.DAY_OF_WEEK) != nowDay) continue;

            int startMin = start.get(Calendar.HOUR_OF_DAY) * 60 + start.get(Calendar.MINUTE);
            int endMin = end.get(Calendar.HOUR_OF_DAY) * 60 + end.get(Calendar.MINUTE);

            if (nowMinutes >= startMin && nowMinutes < endMin) {
                inClass = true;
                currentClass = String.format("%02d:%02d - %02d:%02d",
                        start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE),
                        end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE));
                break;
            }
        }

        if (inClass) {
            setDndOn();
            Log.d("DNDManager", "Inside class: DND ON – " + currentClass);
        } else {
            setDndOff();
            Log.d("DNDManager", "Not in class: DND OFF");
        }
    }

    public void cancelDndSchedules() {
        List<ClassTimeSlot> slots = TimetableStore.getClassTimeSlots(context);
        for (ClassTimeSlot slot : slots) {
            cancelAlarm((int) slot.getStartMillis(), "TURN_ON_DND");
            cancelAlarm((int) slot.getEndMillis(), "TURN_OFF_DND");
        }

        setDndOff();
        prefs.edit().putBoolean("dnd_scheduling_enabled", false).apply();
        Log.d("DNDManager", "Cancelled all DND schedules");
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
    }

    public void setDndOn() {
        if (hasDndAccess()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
            prefs.edit().putBoolean("dnd_currently_on", true).apply();
        } else {
            requestDndAccess();
        }
    }

    public void setDndOff() {
        if (hasDndAccess()) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            prefs.edit().putBoolean("dnd_currently_on", false).apply();
        } else {
            requestDndAccess();
        }
    }

    public boolean isDndCurrentlyOn() {
        if (!hasDndAccess()) return false;
        int filter = notificationManager.getCurrentInterruptionFilter();
        return filter == NotificationManager.INTERRUPTION_FILTER_NONE;
    }

    public boolean isDndSchedulingEnabled() {
        return prefs.getBoolean("dnd_scheduling_enabled", false);
    }

    private boolean hasDndAccess() {
        return notificationManager.isNotificationPolicyAccessGranted();
    }

    private void requestDndAccess() {
        Toast.makeText(context, "Grant Do Not Disturb access in settings.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
