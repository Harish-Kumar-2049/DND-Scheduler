package com.harish.dndscheduler;

import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

/**
 * DND Scheduler Tutorial Configuration
 * 
 * This class defines the specific tutorial steps for the DND Scheduler app.
 * It creates a guided tour of all the main features and UI components.
 */
public class DNDTutorialConfig {
    
    public static void setupMainActivityTutorial(MainActivity activity, GameTutorialManager tutorialManager) {
        
        // Step 1: Welcome and Service Status
        View serviceStatusView = activity.findViewById(R.id.tv_service_status);
        if (serviceStatusView != null) {
            tutorialManager.addStep(
                new GameTutorialManager.TutorialStep(
                    "welcome",
                    serviceStatusView,
                    "Welcome to DND Scheduler! 🎉",
                    "This shows your current DND service status. Let's take a quick tour of the app!"
                ).setIcon(android.R.drawable.ic_dialog_info)
            );
        }
        
        // Step 2: DND Toggle Button
        View toggleButton = activity.findViewById(R.id.btn_dnd_menu);
        if (toggleButton != null) {
            tutorialManager.addStep(
                new GameTutorialManager.TutorialStep(
                    "toggle_dnd",
                    toggleButton,
                    "DND Control Center 🔕",
                    "Tap here to manually enable or disable Do Not Disturb mode anytime you need it."
                ).setIcon(android.R.drawable.ic_lock_silent_mode)
            );
        }
        
        // Step 3: Saturday Schedule Dropdown (same structure as Step 2)
        View saturdayButton = activity.findViewById(R.id.layout_saturday_dropdown);
        if (saturdayButton != null) {
            tutorialManager.addStep(
                new GameTutorialManager.TutorialStep(
                    "saturday_schedule",
                    saturdayButton,
                    "Saturday Schedule 📅",
                    "Configure your Saturday classes here. Choose which weekday Saturday should follow."
                ).setIcon(android.R.drawable.ic_menu_day)
            );
        }
        
        // Step 4: Refresh Functionality
        View swipeRefresh = activity.findViewById(R.id.swipe_refresh_layout);
        if (swipeRefresh != null) {
            tutorialManager.addStep(
                new GameTutorialManager.TutorialStep(
                    "refresh",
                    swipeRefresh,
                    "Pull to Refresh 🔄",
                    "Pull down to refresh your schedule and sync the latest class timings from the server."
                ).setIcon(android.R.drawable.ic_popup_sync)
                .setDismissOnTap(true)
            );
        }
        
        // Step 5: Final Tips - Use the main content view as fallback
        View finalView = activity.findViewById(android.R.id.content);
        if (finalView != null) {
            tutorialManager.addStep(
                new GameTutorialManager.TutorialStep(
                    "final_tips",
                    finalView,
                    "You're All Set! ✨",
                    "The app will now automatically manage your Do Not Disturb settings during class hours. You can replay this tutorial anytime from settings."
                ).setIcon(android.R.drawable.ic_dialog_info)
            );
        }
    }
    
    /**
     * Setup tutorial for login screen
     */
    public static void setupLoginTutorial(AppCompatActivity activity, GameTutorialManager tutorialManager) {
        
        // Add login-specific tutorial steps if needed
        tutorialManager.addStep(
            new GameTutorialManager.TutorialStep(
                "login_welcome",
                activity.findViewById(android.R.id.content),
                "Welcome! 👋",
                "Let's get you logged in to access your class schedule and enable automatic DND management."
            ).setIcon(android.R.drawable.ic_dialog_info)
        );
        
        // Add more login steps as needed...
    }
    
    /**
     * Create a quick highlight for a specific feature
     */
    public static void highlightFeature(AppCompatActivity activity, View targetView, String title, String description) {
        GameTutorialManager tutorialManager = new GameTutorialManager(activity);
        
        GameTutorialManager.TutorialStep step = new GameTutorialManager.TutorialStep(
            "feature_highlight",
            targetView,
            title,
            description
        ).setShowSkipButton(false);
        
        tutorialManager.showSingleTarget(step, null);
    }
    
    /**
     * Show a contextual help tooltip
     */
    public static void showContextualHelp(AppCompatActivity activity, View targetView, String helpText) {
        highlightFeature(activity, targetView, "Quick Tip 💡", helpText);
    }
}
