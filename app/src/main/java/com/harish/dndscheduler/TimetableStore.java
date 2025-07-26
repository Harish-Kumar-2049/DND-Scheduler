package com.harish.dndscheduler;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimetableStore {

    public static List<ClassTimeSlot> getClassTimeSlots(Context context) {
        return getClassTimeSlotsForDay(context, -1); // -1 means ALL DAYS
    }

    public static List<ClassTimeSlot> getClassTimeSlotsForDay(Context context, int targetDay) {
        SharedPreferences prefs = context.getSharedPreferences("dnd_prefs", Context.MODE_PRIVATE);
        String html = prefs.getString("timetable_html", "");

        Log.d("TimetableStore", "Starting to parse timetable HTML, length: " + html.length());

        List<ClassTimeSlot> slots = new ArrayList<>();

        if (html.isEmpty()) return slots;

        try {
            // Step 1: Extract all time ranges (08:45-09:45, etc.)
            List<String> timeRanges = new ArrayList<>();
            Matcher timeMatcher = Pattern.compile("class=['\"]TDtimetableHour['\"]>(\\d{2}:\\d{2}-\\d{2}:\\d{2})", Pattern.CASE_INSENSITIVE).matcher(html);
            while (timeMatcher.find()) {
                timeRanges.add(timeMatcher.group(1));
            }
            Log.d("TimetableStore", "Found " + timeRanges.size() + " time slots");

            // Step 2: Parse ALL DAYS or specific day
            String[] daysShort = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
            
            if (targetDay == -1) {
                // Parse all days of the week
                for (int dayIndex = 1; dayIndex <= 6; dayIndex++) { // Mon-Sat
                    String dayName = daysShort[dayIndex];
                    slots.addAll(parseDayClasses(html, dayName, timeRanges, dayIndex));
                }
            } else {
                // Parse only specific day
                Calendar cal = Calendar.getInstance();
                String today = daysShort[cal.get(Calendar.DAY_OF_WEEK) - 1];
                slots.addAll(parseDayClasses(html, today, timeRanges, cal.get(Calendar.DAY_OF_WEEK) - 1));
            }

            Log.d("TimetableStore", "Parsed total " + slots.size() + " valid slots");
        } catch (Exception e) {
            Log.e("TimetableStore", "Error parsing timetable", e);
        }

        return slots;
    }

    private static List<ClassTimeSlot> parseDayClasses(String html, String dayName, List<String> timeRanges, int dayOfWeek) {
        List<ClassTimeSlot> daySlots = new ArrayList<>();
        
        try {
            // Step 3: Match specific day's row
            Pattern rowPattern = Pattern.compile(
                    "<tr>\\s*<td[^>]*><font[^>]*><b>" + dayName + "</b></font></td>(.*?)</tr>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
            Matcher rowMatcher = rowPattern.matcher(html);
            if (rowMatcher.find()) {
                String rowContent = rowMatcher.group(1);
                Matcher classMatcher = Pattern.compile("<td[^>]*>\\s*<font[^>]*>(.*?)</font>\\s*</td>", Pattern.CASE_INSENSITIVE).matcher(rowContent);
                int index = 0;
                while (classMatcher.find()) {
                    String code = classMatcher.group(1).trim();
                    if (!code.isEmpty() && index < timeRanges.size()) {
                        String[] parts = timeRanges.get(index).split("-");
                        
                        // Create time for the specific day of week
                        long start = timeToMillisForDay(parts[0], dayOfWeek);
                        long end = timeToMillisForDay(parts[1], dayOfWeek);
                        
                        daySlots.add(new ClassTimeSlot(start, end, code));
                        Log.d("TimetableStore", "Added " + dayName + ": " + code + " at " + timeRanges.get(index));
                    }
                    index++;
                }
            }
            
            Log.d("TimetableStore", "Parsed " + daySlots.size() + " slots for " + dayName);
        } catch (Exception e) {
            Log.e("TimetableStore", "Error parsing " + dayName + " classes", e);
        }
        
        return daySlots;
    }

    private static long timeToMillisForDay(String time, int dayOfWeek) {
        try {
            // Parse time same as before
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            // Infer PM for hours like 01:00 to 07:00
            if (hour >= 1 && hour <= 7) {
                hour += 12; // Convert to PM
            }

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek + 1); // Calendar uses 1-7
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // If the day has passed this week, schedule for next week
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.WEEK_OF_YEAR, 1);
            }

            return calendar.getTimeInMillis();
        } catch (Exception e) {
            Log.e("TimetableStore", "Error converting time to millis for day: " + time, e);
            return 0;
        }
    }
    
    private static List<ClassTimeSlot> parseTableWithSubjects(String html) {
        List<ClassTimeSlot> slots = new ArrayList<>();

        try {
            // Extract time slots from the second header row (class="TDtimetableHour")
            List<String> timeRanges = new ArrayList<>();
            Pattern timePattern = Pattern.compile("<td[^>]*class\\s*=\\s*\"TDtimetableHour\"[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE);
            Matcher timeMatcher = timePattern.matcher(html);

            Log.d("TimetableStore", "Trying to match time slot pattern");
            int matchCount = 0;
            while (timeMatcher.find()) {
                String time = timeMatcher.group(1).trim();
                timeRanges.add(time);
                Log.d("TimetableStore", "Matched time slot: " + time);
                matchCount++;
            }
            Log.d("TimetableStore", "Matched " + matchCount + " time slots");
            while (timeMatcher.find()) {
                timeRanges.add(timeMatcher.group(1).trim());
            }

            Log.d("TimetableStore", "Found " + timeRanges.size() + " time slots");

            // Get today's day abbreviation (Mon, Tue, etc.)
            Calendar cal = Calendar.getInstance();
            String todayName = getDayName(cal.get(Calendar.DAY_OF_WEEK)); // e.g. "Fri"

            // Regex to find the correct day row (e.g. <td class="tabletitle06"><font><b>Fri</b></font></td>)
            Pattern rowPattern = Pattern.compile(
                    "<tr>\\s*<td[^>]*class\\s*=\\s*\"tabletitle06\"[^>]*>\\s*<font[^>]*>\\s*<b>" + todayName + "</b>\\s*</font>\\s*</td>(.*?)</tr>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

            Matcher rowMatcher = rowPattern.matcher(html);
            if (!rowMatcher.find()) {
                Log.w("TimetableStore", "No row found for today: " + todayName);
                return slots;
            }

            String rowCells = rowMatcher.group(1); // All <td> cells for that day
            Matcher classMatcher = Pattern.compile("<td[^>]*>\\s*<font[^>]*>(.*?)</font>\\s*</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(rowCells);

            List<String> codes = new ArrayList<>();
            while (classMatcher.find()) {
                String content = classMatcher.group(1).trim();
                codes.add(content); // Might be "" or "CSE304-D"
            }

            for (int i = 0; i < codes.size() && i < timeRanges.size(); i++) {
                String code = codes.get(i);
                if (code.isEmpty()) continue;

                String subject = code; // Until we parse subject mapping table
                long[] times = parseTimeRange(timeRanges.get(i));
                if (times[0] > 0 && times[1] > 0 && times[1] > times[0]) {
                    slots.add(new ClassTimeSlot(times[0], times[1], subject));
                    Log.d("TimetableStore", timeRanges.get(i) + " => " + subject);
                }
            }

        } catch (Exception e) {
            Log.e("TimetableStore", "Error parsing timetable", e);
        }

        Log.d("TimetableStore", "Parsed total " + slots.size() + " valid slots for today");
        return slots;
    }


    private static long[] parseTimeRange(String range) {
        try {
            String[] parts = range.split("[-–~to]+");
            if (parts.length != 2) return new long[]{0, 0};
            return new long[]{timeToMillis(parts[0].trim()), timeToMillis(parts[1].trim())};
        } catch (Exception e) {
            return new long[]{0, 0};
        }
    }


    private static String getDayName(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.SUNDAY: return "Sun";
            case Calendar.MONDAY: return "Mon";
            case Calendar.TUESDAY: return "Tue";
            case Calendar.WEDNESDAY: return "Wed";
            case Calendar.THURSDAY: return "Thu";
            case Calendar.FRIDAY: return "Fri";
            case Calendar.SATURDAY: return "Sat";
            default: return "";
        }
    }

    private static long timeToMillis(String time) {
        try {
            // Try parsing with AM/PM first
            if (time.toLowerCase().contains("am") || time.toLowerCase().contains("pm")) {
                return timeToMillisWithAmPm(time);
            }

            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            // Infer PM for hours like 01:00 to 07:00 if they follow a morning slot (rough logic)
            if (hour >= 1 && hour <= 7) {
                hour += 12; // Convert to PM
            }

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            return calendar.getTimeInMillis();
        } catch (Exception e) {
            Log.e("TimetableStore", "Error converting time to millis: " + time, e);
            return 0;
        }
    }

    private static long timeToMillisWithAmPm(String time) {
        try {
            time = time.trim().toUpperCase(); // Example: "01:00 PM"
            String[] parts = time.split("\\s+"); // Split by space between time and AM/PM

            if (parts.length != 2) return 0;

            String[] hourMin = parts[0].split(":");
            int hour = Integer.parseInt(hourMin[0]);
            int minute = hourMin.length > 1 ? Integer.parseInt(hourMin[1]) : 0;
            String amPm = parts[1];

            if (amPm.equals("PM") && hour != 12) hour += 12;
            if (amPm.equals("AM") && hour == 12) hour = 0;

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            return calendar.getTimeInMillis();
        } catch (Exception e) {
            Log.e("TimetableStore", "Error in timeToMillisWithAmPm: " + time, e);
            return 0;
        }
    }



}
