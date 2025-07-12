package com.harish.dndscheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class DNDReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d("DNDReceiver", "Alarm received: " + action);

        DNDManager dndManager = new DNDManager(context);

        if ("TURN_ON_DND".equals(action)) {
            dndManager.setDndOn();
            Log.d("DNDReceiver", "DND turned ON via alarm");
        } else if ("TURN_OFF_DND".equals(action)) {
            dndManager.setDndOff();
            Log.d("DNDReceiver", "DND turned OFF via alarm");
        } else if ("PERIODIC_CHECK".equals(action)) {
            // Periodic check to ensure DND status is correct
            dndManager.checkAndSetCurrentDndStatus(null);
            Log.d("DNDReceiver", "Periodic DND status check completed");
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Reschedule alarms after device restart
            if (dndManager.isDndSchedulingEnabled()) {
                dndManager.scheduleDndForClasses();
                Log.d("DNDReceiver", "DND scheduling restored after boot");
            }
        } else if (Intent.ACTION_TIME_CHANGED.equals(action) ||
                Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            // Handle time/timezone changes
            if (dndManager.isDndSchedulingEnabled()) {
                dndManager.scheduleDndForClasses();
                Log.d("DNDReceiver", "DND scheduling updated after time change");
            }
        }

        // Always check and enforce current DND status
        dndManager.checkAndSetCurrentDndStatus(null);
    }
}