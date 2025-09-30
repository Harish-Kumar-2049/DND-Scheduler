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
                "Shows the current status of auto-dnd enabling functionality status. Let's explore all the features!"
            );
        }
        
        // Step 2: DND Toggle Button - Target the actual clickable button container
        View toggleButton = activity.findViewById(R.id.btn_dnd_menu);
        if (toggleButton != null) {
            tutorialManager.addStep(
                "toggle_dnd",
                toggleButton,
                "DND Control Center 🔕",
                "This is the main toggle button for enabling or disabling the automatic DND scheduling feature."
            );
        }
        
        // Step 3: Saturday Schedule - Target the clickable layout container
        View saturdayLayout = activity.findViewById(R.id.layout_saturday_dropdown);
        if (saturdayLayout != null) {
            tutorialManager.addStep(
                "saturday_schedule",
                saturdayLayout,
                "Saturday Schedule 📅",
                "Configure your Saturday classes here. Choose which weekday Saturday should follow."
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
