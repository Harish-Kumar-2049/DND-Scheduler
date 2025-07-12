package com.harish.dndscheduler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class DNDService extends Service {

    private static final String CHANNEL_ID = "DND_SERVICE_CHANNEL";
    private static final int NOTIFICATION_ID = 1001;
    private static final int CHECK_INTERVAL = 5 * 60 * 1000; // 5 minutes

    private Handler handler;
    private Runnable checkRunnable;
    private DNDManager dndManager;

    @Override
    public void onCreate() {
        super.onCreate();
        dndManager = new DNDManager(this);
        handler = new Handler(Looper.getMainLooper());

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        // Start periodic checking
        startPeriodicCheck();

        Log.d("DNDService", "DND Service created and started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("DNDService", "DND Service started");

        // Check DND status immediately
        dndManager.checkAndSetCurrentDndStatus(null);

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

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DND Scheduler Active")
                .setContentText("Automatically managing Do Not Disturb based on your class schedule")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void startPeriodicCheck() {
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    dndManager.checkAndSetCurrentDndStatus(null);
                    Log.d("DNDService", "Periodic DND check completed");
                } catch (Exception e) {
                    Log.e("DNDService", "Error in periodic check", e);
                }

                // Schedule next check
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };

        handler.post(checkRunnable);
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