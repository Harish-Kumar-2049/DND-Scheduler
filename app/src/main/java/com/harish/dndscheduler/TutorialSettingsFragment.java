package com.harish.dndscheduler;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Tutorial Settings Fragment
 * 
 * Provides UI for managing tutorial preferences:
 * - Replay tutorial
 * - Reset tutorial status
 * - Tutorial statistics
 */
public class TutorialSettingsFragment extends Fragment {
    
    private SharedPreferences prefs;
    private GameTutorialManager tutorialManager;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = requireActivity().getSharedPreferences("tutorial_prefs", Context.MODE_PRIVATE);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return createTutorialSettingsView();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupTutorialManager();
    }
    
    private void setupTutorialManager() {
        if (getActivity() instanceof MainActivity) {
            tutorialManager = new GameTutorialManager(getActivity());
            DNDTutorialConfig.setupMainActivityTutorial((MainActivity) getActivity(), tutorialManager);
        }
    }
    
    private View createTutorialSettingsView() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);
        
        // Title
        TextView title = new TextView(getContext());
        title.setText("Tutorial Settings");
        title.setTextSize(24);
        title.setTextColor(getResources().getColor(android.R.color.black));
        title.setPadding(0, 0, 0, 32);
        layout.addView(title);
        
        // Tutorial Status
        TextView statusText = new TextView(getContext());
        String status = getTutorialStatusText();
        statusText.setText(status);
        statusText.setTextSize(16);
        statusText.setPadding(0, 0, 0, 24);
        layout.addView(statusText);
        
        // Replay Tutorial Button
        View replayButton = createSettingsButton("🎯 Replay Tutorial", "Experience the guided tour again");
        replayButton.setOnClickListener(v -> showReplayConfirmation());
        layout.addView(replayButton);
        
        // Reset Tutorial Status Button
        View resetButton = createSettingsButton("🔄 Reset Tutorial Status", "Clear tutorial completion status");
        resetButton.setOnClickListener(v -> showResetConfirmation());
        layout.addView(resetButton);
        
        // Tutorial Tips Button
        View tipsButton = createSettingsButton("💡 Quick Tips", "Show contextual help for current screen");
        tipsButton.setOnClickListener(v -> showQuickTips());
        layout.addView(tipsButton);
        
        return layout;
    }
    
    private View createSettingsButton(String title, String description) {
        LinearLayout buttonLayout = new LinearLayout(getContext());
        buttonLayout.setOrientation(LinearLayout.VERTICAL);
        buttonLayout.setPadding(24, 16, 24, 16);
        buttonLayout.setBackgroundResource(android.R.drawable.btn_default);
        buttonLayout.setClickable(true);
        buttonLayout.setFocusable(true);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        buttonLayout.setLayoutParams(params);
        
        TextView titleView = new TextView(getContext());
        titleView.setText(title);
        titleView.setTextSize(18);
        titleView.setTextColor(getResources().getColor(android.R.color.black));
        buttonLayout.addView(titleView);
        
        TextView descView = new TextView(getContext());
        descView.setText(description);
        descView.setTextSize(14);
        descView.setTextColor(getResources().getColor(android.R.color.darker_gray));
        descView.setPadding(0, 4, 0, 0);
        buttonLayout.addView(descView);
        
        return buttonLayout;
    }
    
    private String getTutorialStatusText() {
        if (tutorialManager != null) {
            if (tutorialManager.isTutorialCompleted()) {
                return "✅ Tutorial completed successfully";
            } else if (tutorialManager.isTutorialSkipped()) {
                return "⏭️ Tutorial was skipped";
            } else {
                return "⏳ Tutorial not started yet";
            }
        }
        return "❓ Tutorial status unknown";
    }
    
    private void showReplayConfirmation() {
        new AlertDialog.Builder(getContext())
            .setTitle("Replay Tutorial")
            .setMessage("Would you like to replay the interactive tutorial? This will guide you through all the app features again.")
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Start Tutorial", (dialog, which) -> {
                if (tutorialManager != null) {
                    tutorialManager.forceStartTutorial();
                    Toast.makeText(getContext(), "Tutorial started! 🎯", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showResetConfirmation() {
        new AlertDialog.Builder(getContext())
            .setTitle("Reset Tutorial Status")
            .setMessage("This will reset the tutorial completion status. The tutorial will show again on next app launch.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Reset", (dialog, which) -> {
                if (tutorialManager != null) {
                    tutorialManager.resetTutorial();
                    Toast.makeText(getContext(), "Tutorial status reset! 🔄", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showQuickTips() {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            
            // Show contextual help for the current screen
            View mainContent = activity.findViewById(android.R.id.content);
            if (mainContent != null) {
                DNDTutorialConfig.showContextualHelp(
                    activity,
                    mainContent,
                    "💡 This is your main dashboard where you can monitor and control DND settings for your class schedule."
                );
            }
        }
    }
    
    /**
     * Static method to add tutorial settings to an existing settings activity
     */
    public static void addTutorialSettings(LinearLayout settingsContainer, Context context) {
        TextView tutorialHeader = new TextView(context);
        tutorialHeader.setText("Tutorial & Help");
        tutorialHeader.setTextSize(20);
        tutorialHeader.setTextColor(context.getResources().getColor(android.R.color.black));
        tutorialHeader.setPadding(0, 32, 0, 16);
        settingsContainer.addView(tutorialHeader);
        
        // Add tutorial settings options here
        // This can be integrated into existing settings screens
    }
}
