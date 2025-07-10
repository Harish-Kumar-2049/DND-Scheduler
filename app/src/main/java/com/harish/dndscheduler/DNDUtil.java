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
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
        );
    }
}
