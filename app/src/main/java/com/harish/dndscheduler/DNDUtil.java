package com.harish.dndscheduler;
 
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;
import java.util.List;

public class DNDUtil {
    public static void scheduleAlarms(Context context, List<ClassTimeSlot> classes, boolean isDNDEnabled) {
        if (!isDNDEnabled || classes == null || classes.isEmpty()) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        for (ClassTimeSlot slot : classes) {
            try {
                long startMillis = slot.getStartMillis();
                long endMillis = slot.getEndMillis();

                // Extract hour and minute from startMillis
                Calendar startCal = Calendar.getInstance();
                startCal.setTimeInMillis(startMillis);
                int startHour = startCal.get(Calendar.HOUR_OF_DAY);
                int startMin = startCal.get(Calendar.MINUTE);

                // Extract hour and minute from endMillis
                Calendar endCal = Calendar.getInstance();
                endCal.setTimeInMillis(endMillis);
                int endHour = endCal.get(Calendar.HOUR_OF_DAY);
                int endMin = endCal.get(Calendar.MINUTE);

                // Generate unique request codes
                int startRequestCode = (int) (slot.getStartMillis() % Integer.MAX_VALUE);
                int endRequestCode = (int) (slot.getEndMillis() % Integer.MAX_VALUE);

                scheduleDailyAlarm(context, alarmManager, startHour, startMin, "TURN_ON_DND", startRequestCode);
                scheduleDailyAlarm(context, alarmManager, endHour, endMin, "TURN_OFF_DND", endRequestCode);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void scheduleDailyAlarm(Context context, AlarmManager alarmManager, int hour, int minute, String action, int requestCode) {
        Intent intent = new Intent(context, DNDReceiver.class).setAction(action);
        
        // Add time information to the intent for better rescheduling
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);
        intent.putExtra("requestCode", requestCode);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        
        // If time has passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        
        // Primary alarm - exact timing
        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
        );
        
        // Schedule backup alarm 1 minute later (for redundancy)
        scheduleBackupAlarm(context, alarmManager, calendar, action, requestCode + 10000);
        
        android.util.Log.d("DNDUtil", "Scheduled " + action + " at " + 
                          hour + ":" + String.format("%02d", minute) + 
                          " for " + calendar.getTime() + " with backup");
    }

    /**
     * Schedule a backup alarm 1 minute after the primary alarm
     * This provides redundancy without requiring battery exemption
     */
    private static void scheduleBackupAlarm(Context context, AlarmManager alarmManager, 
                                          Calendar primaryTime, String action, int backupRequestCode) {
        try {
            Intent backupIntent = new Intent(context, DNDReceiver.class);
            backupIntent.setAction(action + "_BACKUP");
            
            // Copy time info for backup alarm
            backupIntent.putExtra("hour", primaryTime.get(Calendar.HOUR_OF_DAY));
            backupIntent.putExtra("minute", primaryTime.get(Calendar.MINUTE));
            backupIntent.putExtra("requestCode", backupRequestCode);
            backupIntent.putExtra("isBackup", true);
            
            PendingIntent backupPendingIntent = PendingIntent.getBroadcast(
                    context,
                    backupRequestCode,
                    backupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Schedule backup 1 minute after primary
            Calendar backupTime = (Calendar) primaryTime.clone();
            backupTime.add(Calendar.MINUTE, 1);
            
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    backupTime.getTimeInMillis(),
                    backupPendingIntent
            );
            
            android.util.Log.d("DNDUtil", "Scheduled backup " + action + " at " + backupTime.getTime());
            
        } catch (Exception e) {
            android.util.Log.e("DNDUtil", "Failed to schedule backup alarm", e);
        }
    }
}
