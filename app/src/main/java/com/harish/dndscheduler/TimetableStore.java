package com.harish.dndscheduler;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimetableStore {

    private static final String TAG = "TimetableStore";

    public static List<ClassTimeSlot> getClassTimeSlots(Context context) {
        List<ClassTimeSlot> slots = new ArrayList<>();

        try {
            if (context == null) {
                Log.e(TAG, "Context is null");
                return slots;
            }

            SharedPreferences prefs = context.getSharedPreferences("dnd_prefs", Context.MODE_PRIVATE);
            String timetableHtml = prefs.getString("timetable_html", "");

            if (timetableHtml.isEmpty()) {
                Log.d(TAG, "No timetable data found in SharedPreferences");
                return slots;
            }

            Log.d(TAG, "Parsing timetable HTML of length: " + timetableHtml.length());
            slots = parseTimetableHtml(timetableHtml);

        } catch (Exception e) {
            Log.e(TAG, "Error getting class time slots", e);
        }

        return slots;
    }

    private static List<ClassTimeSlot> parseTimetableHtml(String html) {
        List<ClassTimeSlot> slots = new ArrayList<>();

        try {
            if (html == null || html.trim().isEmpty()) {
                Log.w(TAG, "HTML is null or empty");
                return slots;
            }

            // Multiple patterns to catch different time formats
            List<Pattern> timePatterns = new ArrayList<>();

            // Pattern 1: HH:MM-HH:MM (24-hour format)
            timePatterns.add(Pattern.compile("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})"));

            // Pattern 2: HH:MM AM/PM - HH:MM AM/PM
            timePatterns.add(Pattern.compile("(\\d{1,2}:\\d{2})\\s*(AM|PM)\\s*-\\s*(\\d{1,2}:\\d{2})\\s*(AM|PM)", Pattern.CASE_INSENSITIVE));

            // Pattern 3: Look for table data with time patterns
            timePatterns.add(Pattern.compile("<td[^>]*>\\s*(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})\\s*</td>", Pattern.CASE_INSENSITIVE));

            for (Pattern pattern : timePatterns) {
                Matcher matcher = pattern.matcher(html);

                while (matcher.find()) {
                    try {
                        String startTime, endTime;

                        if (matcher.groupCount() >= 4) {
                            // AM/PM format
                            startTime = matcher.group(1) + " " + matcher.group(2);
                            endTime = matcher.group(3) + " " + matcher.group(4);
                        } else {
                            // 24-hour format
                            startTime = matcher.group(1);
                            endTime = matcher.group(2);
                        }

                        // Convert to milliseconds
                        long startMillis = timeToMillis(startTime);
                        long endMillis = timeToMillis(endTime);

                        if (startMillis > 0 && endMillis > 0 && endMillis > startMillis) {
                            slots.add(new ClassTimeSlot(startMillis, endMillis));
                            Log.d(TAG, "Added slot: " + startTime + " - " + endTime);
                        } else {
                            Log.w(TAG, "Invalid time slot: " + startTime + " - " + endTime);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing individual time slot", e);
                    }
                }
            }

            // Remove duplicates
            slots = removeDuplicateSlots(slots);

        } catch (Exception e) {
            Log.e(TAG, "Error parsing timetable HTML", e);
        }

        Log.d(TAG, "Successfully parsed " + slots.size() + " time slots");
        return slots;
    }

    private static List<ClassTimeSlot> removeDuplicateSlots(List<ClassTimeSlot> slots) {
        List<ClassTimeSlot> uniqueSlots = new ArrayList<>();

        for (ClassTimeSlot slot : slots) {
            boolean isDuplicate = false;
            for (ClassTimeSlot existing : uniqueSlots) {
                if (existing.getStartMillis() == slot.getStartMillis() &&
                        existing.getEndMillis() == slot.getEndMillis()) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                uniqueSlots.add(slot);
            }
        }

        return uniqueSlots;
    }

    private static long timeToMillis(String time) {
        try {
            if (time == null || time.trim().isEmpty()) {
                return 0;
            }

            time = time.trim();
            boolean isPM = time.toUpperCase().contains("PM");
            boolean isAM = time.toUpperCase().contains("AM");

            // Remove AM/PM from time string
            time = time.replaceAll("(?i)\\s*(AM|PM)\\s*", "");

            String[] parts = time.split(":");
            if (parts.length != 2) {
                Log.w(TAG, "Invalid time format: " + time);
                return 0;
            }

            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());

            // Convert 12-hour to 24-hour format
            if (isPM && hour != 12) {
                hour += 12;
            } else if (isAM && hour == 12) {
                hour = 0;
            }

            // Validate hour and minute
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                Log.w(TAG, "Invalid time values: " + hour + ":" + minute);
                return 0;
            }

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            return calendar.getTimeInMillis();

        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing time numbers: " + time, e);
            return 0;
        } catch (Exception e) {
            Log.e(TAG, "Error converting time to millis: " + time, e);
            return 0;
        }
    }

    /**
     * Debug method to help troubleshoot timetable parsing
     */
    public static void debugTimetableHtml(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("dnd_prefs", Context.MODE_PRIVATE);
            String timetableHtml = prefs.getString("timetable_html", "");

            Log.d(TAG, "=== TIMETABLE DEBUG ===");
            Log.d(TAG, "HTML length: " + timetableHtml.length());
            Log.d(TAG, "HTML empty: " + timetableHtml.isEmpty());

            if (!timetableHtml.isEmpty()) {
                // Show first 500 characters
                String preview = timetableHtml.length() > 500 ?
                        timetableHtml.substring(0, 500) + "..." : timetableHtml;
                Log.d(TAG, "HTML preview: " + preview);

                // Check for common patterns
                Log.d(TAG, "Contains 'table': " + timetableHtml.toLowerCase().contains("table"));
                Log.d(TAG, "Contains time pattern: " + timetableHtml.matches(".*\\d{1,2}:\\d{2}.*"));
                Log.d(TAG, "Contains 'AM' or 'PM': " +
                        (timetableHtml.toUpperCase().contains("AM") || timetableHtml.toUpperCase().contains("PM")));
            }

            List<ClassTimeSlot> slots = getClassTimeSlots(context);
            Log.d(TAG, "Parsed slots count: " + slots.size());

            for (int i = 0; i < slots.size(); i++) {
                ClassTimeSlot slot = slots.get(i);
                Calendar startCal = Calendar.getInstance();
                startCal.setTimeInMillis(slot.getStartMillis());
                Calendar endCal = Calendar.getInstance();
                endCal.setTimeInMillis(slot.getEndMillis());

                Log.d(TAG, "Slot " + i + ": " +
                        String.format("%02d:%02d - %02d:%02d",
                                startCal.get(Calendar.HOUR_OF_DAY), startCal.get(Calendar.MINUTE),
                                endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE)));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in debug method", e);
        }
    }
}