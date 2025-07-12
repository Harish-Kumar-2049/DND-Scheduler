Do Not Disturb (DND) Scheduler Android App - README

Overview

This app automatically turns on/off Do Not Disturb mode based on your university class timetable.
It scrapes your timetable from the university portal, stores it locally, and then uses scheduled alarms to control DND status.

Features

🔐 Login to university portal with CAPTCHA handling

🗓️ Timetable scraping and parsing (HTML based)

📱 Background scheduling of DND alarms for the whole week

🔔 Foreground notifications when DND is triggered

🛡️ Supports Doze mode and Android 12+ exact alarm restrictions

Technologies & APIs Used

Java (Android SDK)

AlarmManager

NotificationManager

BroadcastReceiver

OkHttp (network requests)

SharedPreferences

Vector Drawable / Notification UI

Permissions Required
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />

Architecture Overview

Key Classes

LoginActivity: Handles authentication and HTML timetable scraping

TimetableStore: Parses raw HTML into class slots with time ranges

ClassTimeSlot: Model class with startMillis, endMillis, subject

DNDManager: Core scheduler, manages all alarms and DND logic

DNDReceiver: BroadcastReceiver for turning DND on/off and rescheduling alarms

How Scheduling Works

1. Parse Timetable HTML

From the scraped HTML, class periods like:
<td class="TDtimetableHour">09:00-10:00</td>

Are extracted, and matched with days (Mon, Tue...) into ClassTimeSlot objects.

2. Schedule Alarms for Whole Week

For each ClassTimeSlot, DNDManager schedules:

A TURN_ON_DND alarm at start time

A TURN_OFF_DND alarm at end time

3. Exact Alarms
alarmManager.setExactAndAllowWhileIdle(
    AlarmManager.RTC_WAKEUP,
    triggerTime,
    pendingIntent
Ensures the alarm fires precisely even in Doze mode.

4. Rescheduling

When the alarm triggers, DNDReceiver calls rescheduleAlarmAfterTrigger()
to re-register the alarm for the next week at the same day and time.


How to Run the Project

Step-by-Step

Clone or import the project in Android Studio

Add vector icon ic_notification via Asset Studio

Set min SDK to 23+ (for exact alarm reliability)

Enable Do Not Disturb Access via prompt

Login using your university credentials

App fetches and parses timetable

DND is scheduled weekly
