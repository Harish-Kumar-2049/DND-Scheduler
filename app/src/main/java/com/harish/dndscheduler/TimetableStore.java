package com.harish.dndscheduler;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class TimetableStore {

    public static List<ClassTimeSlot> getClassTimeSlots(Context context) {
        // 🔑 Replace with actual DB or SharedPreferences fetch logic
        List<ClassTimeSlot> slots = new ArrayList<>();
        // Example dummy data (replace with parsed real data)
        // slots.add(new ClassTimeSlot(System.currentTimeMillis() + 60000, System.currentTimeMillis() + 3600000));
        return slots;
    }
}
