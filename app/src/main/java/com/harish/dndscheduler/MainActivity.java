package com.harish.dndscheduler;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvDndStatus;
    private Button btnToggleDnd;
    private boolean isDndOn = false;

    private SharedPreferences prefs;
    private DNDManager dndManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDndStatus = findViewById(R.id.tv_dnd_status);
        btnToggleDnd = findViewById(R.id.btn_toggle_dnd);

        prefs = getSharedPreferences("dnd_prefs", MODE_PRIVATE);
        isDndOn = prefs.getBoolean("is_dnd_on", false);

        dndManager = new DNDManager(this);

        // Update UI based on current state
        updateUI();

        btnToggleDnd.setOnClickListener(v -> toggleDnd());
    }

    private void toggleDnd() {
        if (!hasDndAccess()) {
            Toast.makeText(this, "Grant Do Not Disturb access in settings.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            return;
        }

        isDndOn = !isDndOn;

        if (isDndOn) {
            // Schedule DND ON/OFF based on timetable
            dndManager.scheduleDndForClasses();
            Toast.makeText(this, "DND auto scheduling enabled.", Toast.LENGTH_SHORT).show();
        } else {
            // Cancel scheduled DND and turn off if currently on
            dndManager.cancelDndSchedules();
            dndManager.setDndOff();
            Toast.makeText(this, "DND auto scheduling disabled.", Toast.LENGTH_SHORT).show();
        }

        // Save state
        prefs.edit().putBoolean("is_dnd_on", isDndOn).apply();
        updateUI();
    }

    private void updateUI() {
        if (isDndOn) {
            tvDndStatus.setText("DND is ON");
            btnToggleDnd.setText("Turn OFF DND");
        } else {
            tvDndStatus.setText("DND is OFF");
            btnToggleDnd.setText("Turn ON DND");
        }
    }

    private boolean hasDndAccess() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return nm.isNotificationPolicyAccessGranted();
    }
}
