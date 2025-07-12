package com.harish.dndscheduler;

public class ClassTimeSlot {
    private long startMillis;
    private long endMillis;
    private String subject;

    public ClassTimeSlot(long startMillis, long endMillis, String subject) {
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.subject = subject;
    }

    public long getStartMillis() { return startMillis; }
    public long getEndMillis() { return endMillis; }
    public String getSubject() { return subject; }
}
