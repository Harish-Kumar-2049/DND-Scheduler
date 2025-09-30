package com.harish.dndscheduler;
 
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.util.Calendar;

public class DNDReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("dnd_prefs", Context.MODE_PRIVATE);
        boolean isSchedulingEnabled = prefs.getBoolean("dnd_scheduling_enabled", true);

        if (!isSchedulingEnabled) {
            Log.d("DNDReceiver", "DND scheduling is disabled. Ignoring alarm.");
            return; // Do nothing if scheduling is disabled
        }

        // Existing code for handling DND ON/OFF actions
        String action = intent.getAction();
        Log.d("DNDReceiver", "=== ALARM RECEIVED ===");
        Log.d("DNDReceiver", "Action: " + action);
        Log.d("DNDReceiver", "Time: " + System.currentTimeMillis());
        Log.d("DNDReceiver", "==================");

        try {
            DNDManager dndManager = DNDManager.getInstance(context);

            if ("TURN_ON_DND".equals(action) || "TURN_ON_DND_BACKUP".equals(action)) {
                // Check if DND scheduling is still enabled
                if (!dndManager.isDndSchedulingEnabled()) {
                    Log.d("DNDReceiver", "DND scheduling is disabled - ignoring TURN_ON_DND alarm");
                    return;
                }
                
                Log.d("DNDReceiver", "Current silent mode type: " + dndManager.getSilentModeType());
                boolean isBackup = intent.getBooleanExtra("isBackup", false);
                if (dndManager.setSilentModeOn()) {
                    String modeType = dndManager.getSilentModeType();
                    String modeText = modeType.equals("dnd") ? "DND" : 
                                     modeType.equals("vibrate") ? "Vibrate mode" : "Silent mode";
                    Log.d("DNDReceiver", modeText + " turned ON via " + (isBackup ? "backup " : "") + "alarm");
                    if (!isBackup) { // Only reschedule from primary alarm
                        rescheduleAlarmForNextWeek(context, intent);
                    }
                } else {
                    String modeType = dndManager.getSilentModeType();
                    String modeText = modeType.equals("dnd") ? "DND" : 
                                     modeType.equals("vibrate") ? "Vibrate mode" : "Silent mode";
                    Log.e("DNDReceiver", "Failed to turn ON " + modeText + " via " + (isBackup ? "backup " : "") + "alarm");
                }
                
            } else if ("TURN_OFF_DND".equals(action) || "TURN_OFF_DND_BACKUP".equals(action)) {
                // Check if DND scheduling is still enabled
                if (!dndManager.isDndSchedulingEnabled()) {
                    Log.d("DNDReceiver", "DND scheduling is disabled - ignoring TURN_OFF_DND alarm");
                    return;
                }
                
                Log.d("DNDReceiver", "=== TURNING OFF SILENT MODE ===");
                Log.d("DNDReceiver", "Current silent mode type: " + dndManager.getSilentModeType());
                boolean isBackup = intent.getBooleanExtra("isBackup", false);
                if (dndManager.setSilentModeOff()) {
                    String modeType = dndManager.getSilentModeType();
                    String modeText = modeType.equals("dnd") ? "DND" : 
                                     modeType.equals("vibrate") ? "Vibrate mode" : "Silent mode";
                    Log.d("DNDReceiver", modeText + " turned OFF via " + (isBackup ? "backup " : "") + "alarm");
                    if (!isBackup) { // Only reschedule from primary alarm
                        rescheduleAlarmForNextWeek(context, intent);
                    }
                } else {
                    String modeType = dndManager.getSilentModeType();
                    String modeText = modeType.equals("dnd") ? "DND" : 
                                     modeType.equals("vibrate") ? "Vibrate mode" : "Silent mode";
                    Log.e("DNDReceiver", "Failed to turn OFF " + modeText + " via " + (isBackup ? "backup " : "") + "alarm");
                }
                
            } else if ("PERIODIC_CHECK".equals(action)) {
                // Check if DND scheduling is still enabled before periodic check
                if (!dndManager.isDndSchedulingEnabled()) {
                    Log.d("DNDReceiver", "DND scheduling is disabled - ignoring periodic check");
                    return;
                }
                dndManager.checkAndSetCurrentDndStatus(null);
                Log.d("DNDReceiver", "Periodic DND status check completed");
                
            } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                if (dndManager.isDndSchedulingEnabled()) {
                    dndManager.scheduleDndForClasses();
                    Log.d("DNDReceiver", "DND scheduling restored after boot");
                }
                
            } else if (Intent.ACTION_TIME_CHANGED.equals(action) ||
                    Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                if (dndManager.isDndSchedulingEnabled()) {
                    dndManager.scheduleDndForClasses();
                    Log.d("DNDReceiver", "DND scheduling updated after time change");
                }
            }

            // Always check and enforce current DND status
            dndManager.checkAndSetCurrentDndStatus(null);
            
        } catch (Exception e) {
            Log.e("DNDReceiver", "Error handling alarm: " + action, e);
        }
    }

    /**
     * Reschedules the same alarm for next week (7 days later)
     * This ensures alarms continue working indefinitely
     */
    private void rescheduleAlarmForNextWeek(Context context, Intent originalIntent) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.e("DNDReceiver", "AlarmManager is null, cannot reschedule");
                return;
            }
            
            // Create new intent with same action and data
            Intent newIntent = new Intent(context, DNDReceiver.class);
            newIntent.setAction(originalIntent.getAction());
            
            // Preserve the time information
            if (originalIntent.hasExtra("hour") && originalIntent.hasExtra("minute")) {
                newIntent.putExtra("hour", originalIntent.getIntExtra("hour", 0));
                newIntent.putExtra("minute", originalIntent.getIntExtra("minute", 0));
                newIntent.putExtra("requestCode", originalIntent.getIntExtra("requestCode", 0));
            }
            
            // Use the same request code to maintain uniqueness
            int requestCode = originalIntent.getIntExtra("requestCode", originalIntent.hashCode());
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Schedule for exactly 7 days from now (same time next week)
            Calendar nextWeek = Calendar.getInstance();
            nextWeek.add(Calendar.DAY_OF_YEAR, 7);
            
            // If we have the original time info, use it for more precise scheduling
            if (originalIntent.hasExtra("hour") && originalIntent.hasExtra("minute")) {
                nextWeek.set(Calendar.HOUR_OF_DAY, originalIntent.getIntExtra("hour", 0));
                nextWeek.set(Calendar.MINUTE, originalIntent.getIntExtra("minute", 0));
                nextWeek.set(Calendar.SECOND, 0);
                nextWeek.set(Calendar.MILLISECOND, 0);
            }
            
            // Validate future time
            if (nextWeek.getTimeInMillis() <= System.currentTimeMillis()) {
                Log.w("DNDReceiver", "Calculated next week time is in the past, adding another week");
                nextWeek.add(Calendar.DAY_OF_YEAR, 7);
            }
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextWeek.getTimeInMillis(),
                pendingIntent
            );
            
            Log.d("DNDReceiver", "Rescheduled " + originalIntent.getAction() + 
                  " for next week: " + nextWeek.getTime() + " (RequestCode: " + requestCode + ")");
                  
        } catch (SecurityException e) {
            Log.e("DNDReceiver", "Permission denied for alarm scheduling", e);
        } catch (Exception e) {
            Log.e("DNDReceiver", "Failed to reschedule alarm", e);
        }
    }
}