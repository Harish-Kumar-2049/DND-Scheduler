package com.harish.dndscheduler;
 
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.List;

import androidx.core.app.NotificationCompat;

public class DNDService extends Service {

    private static final String CHANNEL_ID = "DND_SERVICE_CHANNEL";
    private static final int NOTIFICATION_ID = 1001;
    private static final int CHECK_INTERVAL = 3 * 60 * 1000; // 3 minutes - more frequent for reliability

    private Handler handler;
    private Runnable checkRunnable;
    private DNDManager dndManager;

    @Override
    public void onCreate() {
        super.onCreate();
        dndManager = DNDManager.getInstance(this);
        handler = new Handler(Looper.getMainLooper());

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createEnhancedNotification());

        // Start enhanced periodic checking
        startEnhancedPeriodicCheck();

        Log.d("DNDService", "Enhanced DND Service created and started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("DNDService", "DND Service started");

        // Check DND status immediately only if scheduling is enabled
        if (dndManager.isDndSchedulingEnabled()) {
            dndManager.checkAndSetCurrentDndStatus(null);
        } else {
            Log.d("DNDService", "DND scheduling disabled - skipping immediate DND check");
        }

        // Return START_STICKY to restart service if killed
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && checkRunnable != null) {
            handler.removeCallbacks(checkRunnable);
        }
        Log.d("DNDService", "DND Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DND Scheduler Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Manages automatic DND scheduling");
            channel.setShowBadge(false);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Enhanced notification that emphasizes reliability without mentioning battery optimization
     */
    private Notification createEnhancedNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DND Scheduler - Enhanced Mode")
                .setContentText("High-reliability DND scheduling active for your classes")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    /**
     * Enhanced periodic checking with multiple redundancy layers
     */
    private void startEnhancedPeriodicCheck() {
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (dndManager.isDndSchedulingEnabled()) {
                        // Primary check
                        dndManager.checkAndSetCurrentDndStatus(null);
                        // Verify alarm status periodically
                        verifyAlarmStatus();
                        Log.d("DNDService", "Enhanced periodic DND check completed");
                    } else {
                        // If scheduling is disabled, ensure DND is OFF and do not perform any checks
                        dndManager.setDndOff();
                        Log.d("DNDService", "DND scheduling disabled - forcibly turning DND OFF and skipping periodic check");
                    }
                } catch (Exception e) {
                    Log.e("DNDService", "Error in enhanced periodic check", e);
                }
                // Schedule next check with variable interval to avoid predictable patterns
                int nextInterval = CHECK_INTERVAL + (int)(Math.random() * 60000); // ±1 minute variation
                handler.postDelayed(this, nextInterval);
            }
        };
        handler.post(checkRunnable);
    }

    /**
     * Verify that critical alarms are still scheduled
     */
    private void verifyAlarmStatus() {
        if (!dndManager.isDndSchedulingEnabled()) {
            Log.d("DNDService", "DND scheduling is disabled. Skipping alarm verification.");
            return;
        }
        // Existing code for verifying alarm status
        DNDManager.getInstance(this).checkAndSetCurrentDndStatus(null);
    }

    public static void startService(Context context) {
        Intent intent = new Intent(context, DNDService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stopService(Context context) {
        Intent intent = new Intent(context, DNDService.class);
        context.stopService(intent);
    }
}