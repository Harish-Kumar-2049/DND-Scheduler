package com.harish.dndscheduler;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Settings Activity with Tutorial Controls
 * 
 * This is a demonstration of how to integrate tutorial controls
 * into your existing settings screen.
 */
public class SettingsActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create a simple settings layout
        LinearLayout layout = createSettingsLayout();
        setContentView(layout);
        
        // Setup action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
    }
    
    private LinearLayout createSettingsLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        // Settings Title
        TextView title = new TextView(this);
        title.setText("App Settings");
        title.setTextSize(28);
        title.setTextColor(getResources().getColor(R.color.text_color));
        title.setPadding(0, 0, 0, 32);
        layout.addView(title);
        
        // Tutorial Section
        TextView tutorialSection = new TextView(this);
        tutorialSection.setText("Tutorial & Help");
        tutorialSection.setTextSize(20);
        tutorialSection.setTextColor(getResources().getColor(R.color.primary));
        tutorialSection.setPadding(0, 16, 0, 16);
        layout.addView(tutorialSection);
        
        // Replay Tutorial Button
        View replayButton = createSettingsButton(
            "🎯 Replay Interactive Tutorial",
            "Experience the guided tour again with all app features"
        );
        replayButton.setOnClickListener(v -> replayTutorial());
        layout.addView(replayButton);
        
        // Reset Tutorial Button
        View resetButton = createSettingsButton(
            "🔄 Reset Tutorial Status", 
            "Clear tutorial completion status"
        );
        resetButton.setOnClickListener(v -> resetTutorialStatus());
        layout.addView(resetButton);
        
        // Quick Help Button
        View helpButton = createSettingsButton(
            "💡 Quick Help", 
            "Show contextual help for main screen"
        );
        helpButton.setOnClickListener(v -> showQuickHelp());
        layout.addView(helpButton);
        
        // App Information Section
        TextView appSection = new TextView(this);
        appSection.setText("App Information");
        appSection.setTextSize(20);
        appSection.setTextColor(getResources().getColor(R.color.primary));
        appSection.setPadding(0, 32, 0, 16);
        layout.addView(appSection);
        
        // Version Info
        TextView versionInfo = new TextView(this);
        versionInfo.setText("Version 1.0\nDND Scheduler with Premium Tutorial System");
        versionInfo.setTextSize(14);
        versionInfo.setTextColor(getResources().getColor(R.color.hint_color));
        versionInfo.setPadding(0, 8, 0, 16);
        layout.addView(versionInfo);
        
        return layout;
    }
    
    private View createSettingsButton(String title, String description) {
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.VERTICAL);
        buttonLayout.setPadding(24, 20, 24, 20);
        buttonLayout.setBackgroundResource(R.drawable.card_background);
        buttonLayout.setClickable(true);
        buttonLayout.setFocusable(true);
        buttonLayout.setElevation(4f);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        buttonLayout.setLayoutParams(params);
        
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(18);
        titleView.setTextColor(getResources().getColor(R.color.text_color));
        buttonLayout.addView(titleView);
        
        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextSize(14);
        descView.setTextColor(getResources().getColor(R.color.hint_color));
        descView.setPadding(0, 4, 0, 0);
        buttonLayout.addView(descView);
        
        return buttonLayout;
    }
    
    private void replayTutorial() {
        // Go back to MainActivity and replay tutorial
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("replay_tutorial", true);
        startActivity(intent);
        finish();
    }
    
    private void resetTutorialStatus() {
        // Reset tutorial status using GameTutorialManager
        GameTutorialManager tempManager = new GameTutorialManager(this);
        tempManager.resetTutorial();
    }
    
    private void showQuickHelp() {
        // Show a quick help overlay
        View rootView = findViewById(android.R.id.content);
        DNDTutorialConfig.showContextualHelp(
            this,
            rootView,
            "💡 This settings screen allows you to control the tutorial system and app preferences. Use the replay button to experience the interactive tutorial again!"
        );
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
