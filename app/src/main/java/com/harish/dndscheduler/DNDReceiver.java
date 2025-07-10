package com.harish.dndscheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DNDReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        DNDManager dndManager = new DNDManager(context);

        if ("TURN_ON_DND".equals(action)) {
            dndManager.setDndOn();
        } else if ("TURN_OFF_DND".equals(action)) {
            dndManager.setDndOff();
        }
    }
}
