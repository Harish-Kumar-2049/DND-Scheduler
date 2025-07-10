package com.harish.dndscheduler;

public class ClassTimeSlot {
    private long startMillis;
    private long endMillis;

    public ClassTimeSlot(long startMillis, long endMillis) {
        this.startMillis = startMillis;
        this.endMillis = endMillis;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }
}
