package com.harish.dndscheduler;

import android.view.View;

/**
 * Simple Tutorial Configuration for DND Scheduler
 * 
 * This uses the SimpleTutorialManager which creates custom overlays
 * instead of relying on external libraries.
 */
public class SimpleTutorialConfig {
    
    public static void setupMainActivityTutorial(MainActivity activity, SimpleTutorialManager tutorialManager) {
        
        // Step 1: Welcome and Service Status - Target the actual status text
        View serviceStatusView = activity.findViewById(R.id.tv_service_status);
        if (serviceStatusView != null) {
            tutorialManager.addStep(
                "welcome",
                serviceStatusView,
                "Welcome to DND Scheduler! 🎉",
                "This shows your current Do Not Disturb service status. Let's explore all the features!"
            );
        }
        
        // Step 2: DND Toggle Button - Target the actual clickable button container
        View toggleButton = activity.findViewById(R.id.btn_toggle_dnd);
        if (toggleButton != null) {
            tutorialManager.addStep(
                "toggle_dnd",
                toggleButton,
                "DND Control Center 🔕",
                "Tap this button to manually enable or disable automatic DND scheduling anytime!"
            );
        }
        
        // Step 3: Saturday Schedule - Target the clickable layout container
        View saturdayLayout = activity.findViewById(R.id.layout_saturday_dropdown);
        if (saturdayLayout != null) {
            tutorialManager.addStep(
                "saturday_schedule",
                saturdayLayout,
                "Saturday Schedule 📅",
                "Configure your Saturday classes here. The app will auto-manage DND during selected times."
            );
        }
        
        // Step 4: Today's Classes - Target the RecyclerView
        View recyclerView = activity.findViewById(R.id.rv_today_classes);
        if (recyclerView != null) {
            tutorialManager.addStep(
                "todays_classes",
                recyclerView,
                "Today's Schedule 📚",
                "Your daily classes appear here. DND activates automatically during these periods!"
            );
        }
    }
    
    /**
     * Show a quick help overlay for any feature
     */
    public static void showQuickHelp(MainActivity activity, String title, String description) {
        SimpleTutorialManager helpManager = new SimpleTutorialManager(activity);
        View contentView = activity.findViewById(android.R.id.content);
        if (contentView != null) {
            helpManager.addStep("help", contentView, title, description);
            helpManager.forceStartTutorial();
        }
    }
}
