package com.harish.dndscheduler;

import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageButton;

/**
 * Tutorial configuration for Login Activity
 * Guides users through the login process and explains the Student Web Interface credentials
 */
public class LoginTutorialConfig {
    
    public static void setupLoginTutorial(LoginActivity activity, SimpleTutorialManager tutorialManager) {
        // Find views for tutorial - we'll use simple views as placeholders since no spotlight needed
        EditText etRegisterNumber = activity.findViewById(R.id.et_register_number);
        EditText etPassword = activity.findViewById(R.id.et_password);

        // Add just two simple tutorial slides
        tutorialManager
            .addStep("welcome", etRegisterNumber,
                "Welcome to DND Scheduler! 🎉",
                "Your smart DND schedule manager! This app helps you automatically enable Do Not Disturb during your classes and disable whenever there is no class, so you never miss important calls. Let's get started!")
            
            .addStep("credentials", etPassword,
                "Enter Your SWI Credentials 🔐",
                "Use your Student Web Interface login details - the same username and password you use to check results and attendance on your college website. This helps us fetch your personalized timetable.");
    }
}
