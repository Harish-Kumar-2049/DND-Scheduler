package com.harish.dndscheduler;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

public class DNDManager {
    private final NotificationManager notificationManager;
    private final Context context;

    public DNDManager(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private boolean checkPolicyAccess() {
        if (!notificationManager.isNotificationPolicyAccessGranted()) {
            Toast.makeText(context, "Please grant DND permission in settings", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            context.startActivity(intent);
            return false;
        }
        return true;
    }

    public void enableDND() {
        if (checkPolicyAccess()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
            }
        }
    }

    public void disableDND() {
        if (checkPolicyAccess()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        }
    }
}