package com.harish.dndscheduler;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * PROPER Tutorial System with Element Highlighting
 * 
 * This creates real tutorial overlays that highlight specific UI elements
 * and show tooltips next to them with proper app theming.
 */
public class SimpleTutorialManager {
    
    private static final String PREFS_NAME = "tutorial_prefs";
    private static final String TUTORIAL_COMPLETED_KEY = "main_tutorial_completed";
    private static final String TUTORIAL_SKIPPED_KEY = "tutorial_skipped";
    private String tutorialCompletedKey = TUTORIAL_COMPLETED_KEY;
    private String tutorialSkippedKey = TUTORIAL_SKIPPED_KEY;
    
    private Activity activity;
    private SharedPreferences prefs;
    private List<TutorialStep> tutorialSteps;
    private int currentStepIndex = 0;
    private boolean isRunning = false;
    private FrameLayout tutorialOverlay;
    private TutorialListener tutorialListener;
    private HashMap<View, Drawable> originalBackgrounds; // Store original backgrounds for restoration
    private boolean useSpotlightMode = true; // Default to spotlight mode
    
    public interface TutorialListener {
        void onTutorialStarted();
        void onTutorialCompleted();
        void onTutorialSkipped();
        void onStepCompleted(int stepIndex, String stepId);
    }
    
    public static class TutorialStep {
        public String id;
        public View targetView;
        public String title;
        public String description;
        
        public TutorialStep(String id, View targetView, String title, String description) {
            this.id = id;
            this.targetView = targetView;
            this.title = title;
            this.description = description;
        }
    }
    
    public SimpleTutorialManager(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.tutorialSteps = new ArrayList<>();
    }
    
    public void setTutorialListener(TutorialListener listener) {
        this.tutorialListener = listener;
    }
    
    public SimpleTutorialManager addStep(TutorialStep step) {
        if (step.targetView != null) {
            tutorialSteps.add(step);
            Log.d("SimpleTutorialManager", "Added step: " + step.id);
        } else {
            Log.w("SimpleTutorialManager", "Skipping step with null view: " + step.id);
        }
        return this;
    }
    
    public SimpleTutorialManager addStep(String id, View targetView, String title, String description) {
        return addStep(new TutorialStep(id, targetView, title, description));
    }
    
    // Set tutorial mode - true for spotlight (main activity), false for simple slides (login)
    public SimpleTutorialManager setSpotlightMode(boolean useSpotlight) {
        this.useSpotlightMode = useSpotlight;
        return this;
    }
    
    // Set unique keys for different tutorial types (login vs main)
    public SimpleTutorialManager setTutorialKeys(String activityName) {
        Log.d("SimpleTutorialManager", "Setting tutorial keys for activity: " + activityName);
        this.tutorialCompletedKey = activityName + "_tutorial_completed";
        this.tutorialSkippedKey = activityName + "_tutorial_skipped";
        Log.d("SimpleTutorialManager", "  - completedKey: " + tutorialCompletedKey);
        Log.d("SimpleTutorialManager", "  - skippedKey: " + tutorialSkippedKey);
        return this;
    }
    
    public boolean shouldShowTutorial() {
        boolean completed = prefs.getBoolean(tutorialCompletedKey, false);
        boolean skipped = prefs.getBoolean(tutorialSkippedKey, false);
        Log.d("SimpleTutorialManager", "Should show tutorial check:");
        Log.d("SimpleTutorialManager", "  - tutorialCompletedKey: " + tutorialCompletedKey);
        Log.d("SimpleTutorialManager", "  - tutorialSkippedKey: " + tutorialSkippedKey);
        Log.d("SimpleTutorialManager", "  - Completed: " + completed);
        Log.d("SimpleTutorialManager", "  - Skipped: " + skipped);
        Log.d("SimpleTutorialManager", "  - Result (should show): " + (!completed && !skipped));
        return !completed && !skipped;
    }
    
    public void startTutorial() {
        Log.d("SimpleTutorialManager", "startTutorial called, steps: " + tutorialSteps.size());
        
        if (isRunning) {
            Log.d("SimpleTutorialManager", "Tutorial already running");
            return;
        }
        
        if (tutorialSteps.isEmpty()) {
            Log.w("SimpleTutorialManager", "No tutorial steps available");
            return;
        }
        
        if (!shouldShowTutorial()) {
            Log.d("SimpleTutorialManager", "Tutorial should not be shown");
            return;
        }
        
        Log.d("SimpleTutorialManager", "Starting tutorial with " + tutorialSteps.size() + " steps");
        
        isRunning = true;
        currentStepIndex = 0;
        
        if (tutorialListener != null) {
            tutorialListener.onTutorialStarted();
        }
        
        showCurrentStep();
    }
    
    private void showCurrentStep() {
        if (currentStepIndex >= tutorialSteps.size()) {
            // Tutorial completed
            completeTutorial();
            return;
        }
        
        TutorialStep step = tutorialSteps.get(currentStepIndex);
        Log.d("SimpleTutorialManager", "Showing step " + (currentStepIndex + 1) + ": " + step.id);
        
        createHighlightOverlay(step);
    }
    
    private void createHighlightOverlay(TutorialStep step) {
        // Remove existing overlay if any
        removeTutorialOverlay();

        if (useSpotlightMode) {
            // Use spotlight overlay for main activity (original behavior)
            createSpotlightOverlay(step);
        } else {
            // Use simple centered slides for login
            createSimpleSlideOverlay(step);
        }
    }
    
    private void createSpotlightOverlay(TutorialStep step) {
        // Create the spotlight overlay
        SpotlightOverlayView spotlightOverlay = new SpotlightOverlayView(activity);
        spotlightOverlay.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Create container for overlay and tooltip
        tutorialOverlay = new FrameLayout(activity);
        tutorialOverlay.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        tutorialOverlay.setClickable(false);
        tutorialOverlay.setFocusable(false);
        tutorialOverlay.addView(spotlightOverlay);

        // Add overlay to activity first
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        rootView.addView(tutorialOverlay);

        // Now set spotlight using the accurate positioning method
        spotlightOverlay.setSpotlightForView(step.targetView);

        // Create tooltip popup
        int[] location = new int[2];
        step.targetView.getLocationInWindow(location);
        Rect targetRect = new Rect(location[0], location[1], 
            location[0] + step.targetView.getWidth(), 
            location[1] + step.targetView.getHeight());
        createTooltipPopup(step, targetRect);

        // Set overlay elevation
        tutorialOverlay.setElevation(10f);
    }
    
    private void createSimpleSlideOverlay(TutorialStep step) {
        // Create simple dim overlay without spotlight for welcome slides
        tutorialOverlay = new FrameLayout(activity);
        tutorialOverlay.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        tutorialOverlay.setClickable(false);
        tutorialOverlay.setFocusable(false);

        // Simple dim background
        View dimBackground = new View(activity);
        dimBackground.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        dimBackground.setBackgroundColor(Color.parseColor("#BB000000")); // Semi-transparent black
        dimBackground.setClickable(false);
        tutorialOverlay.addView(dimBackground);

        // Add overlay to activity
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        rootView.addView(tutorialOverlay);

        // Create centered tooltip popup for welcome slides
        createWelcomeTooltipPopup(step);

        // Set overlay elevation
        tutorialOverlay.setElevation(10f);
    }
    
    private void bringComponentAboveOverlay(View targetView) {
        // Simply bring the component above the overlay using elevation
        // Keep original background, position, size - just change z-index
        
        // Set elevation higher than overlay (10f) and tutorial cards
        targetView.setElevation(20f); // Above everything else
        
        // Optional: Add subtle glow border while keeping original background
        Drawable originalBg = targetView.getBackground();
        if (originalBg != null) {
            // Save original for restoration
            saveOriginalBackground(targetView);
            
            // Create a subtle glow version that enhances (doesn't replace) original
            GradientDrawable glowBorder = new GradientDrawable();
            glowBorder.setCornerRadius(16f);
            
            // Keep original colors but add subtle cyan border for highlighting
            if (originalBg instanceof GradientDrawable) {
                GradientDrawable originalGrad = (GradientDrawable) originalBg;
                // Keep original color, just add glow border
                glowBorder.setColor(Color.TRANSPARENT); // Transparent so original shows through
                glowBorder.setStroke(4, Color.parseColor("#00E5FF")); // Subtle cyan glow
                
                // Layer the glow over original
                android.graphics.drawable.LayerDrawable layered = new android.graphics.drawable.LayerDrawable(
                    new Drawable[]{originalBg, glowBorder}
                );
                targetView.setBackground(layered);
            } else {
                // For other background types, just add subtle elevation
                targetView.setElevation(25f); // Extra elevation for visibility
            }
        }
        
        Log.d("SimpleTutorialManager", "Component brought above overlay with z-index");
    }
    
    private void saveOriginalBackground(View targetView) {
        // Store original background to restore later
        if (originalBackgrounds == null) {
            originalBackgrounds = new HashMap<>();
        }
        originalBackgrounds.put(targetView, targetView.getBackground());
        Log.d("SimpleTutorialManager", "Saved original background for component");
    }
    
    private void createWelcomeTooltipPopup(TutorialStep step) {
        // Create tooltip container
        LinearLayout tooltipContainer = new LinearLayout(activity);
        tooltipContainer.setOrientation(LinearLayout.VERTICAL);
        tooltipContainer.setPadding(32, 24, 32, 24);
        
        // Style tooltip with app theme
        GradientDrawable tooltipBg = new GradientDrawable();
        tooltipBg.setShape(GradientDrawable.RECTANGLE);
        tooltipBg.setColor(Color.parseColor("#5E35B1")); // App primary color
        tooltipBg.setCornerRadius(20f);
        tooltipBg.setStroke(2, Color.parseColor("#4A148C"));
        tooltipContainer.setBackground(tooltipBg);
        tooltipContainer.setElevation(12f);
        
        // Step counter
        TextView stepCounter = new TextView(activity);
        stepCounter.setText("Step " + (currentStepIndex + 1) + " of " + tutorialSteps.size());
        stepCounter.setTextSize(13);
        stepCounter.setTextColor(Color.parseColor("#E1BEE7")); // Light purple
        stepCounter.setGravity(Gravity.CENTER);
        stepCounter.setPadding(0, 0, 0, 12);
        tooltipContainer.addView(stepCounter);
        
        // Title with emoji
        TextView titleView = new TextView(activity);
        titleView.setText(step.title);
        titleView.setTextSize(22);
        titleView.setTextColor(Color.WHITE);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, 16);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        tooltipContainer.addView(titleView);
        
        // Description
        TextView descriptionView = new TextView(activity);
        descriptionView.setText(step.description);
        descriptionView.setTextSize(16);
        descriptionView.setTextColor(Color.parseColor("#F3E5F5")); // Very light purple
        descriptionView.setGravity(Gravity.CENTER);
        descriptionView.setPadding(0, 0, 0, 20);
        descriptionView.setLineSpacing(6f, 1.3f);
        tooltipContainer.addView(descriptionView);
        
        // Buttons layout
        LinearLayout buttonsLayout = new LinearLayout(activity);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.CENTER);
        
        // Skip button
        Button skipButton = new Button(activity);
        skipButton.setText("Skip");
        skipButton.setTextColor(Color.parseColor("#E1BEE7"));
        skipButton.setTextSize(15);
        skipButton.setBackgroundColor(Color.TRANSPARENT);
        skipButton.setPadding(20, 10, 20, 10);
        skipButton.setOnClickListener(v -> skipTutorial());
        
        // Next button
        Button nextButton = new Button(activity);
        nextButton.setText(currentStepIndex == tutorialSteps.size() - 1 ? "Get Started ✨" : "Next →");
        nextButton.setTextColor(Color.parseColor("#5E35B1"));
        nextButton.setTextSize(15);
        nextButton.setTypeface(null, android.graphics.Typeface.BOLD);
        
        GradientDrawable nextBg = new GradientDrawable();
        nextBg.setShape(GradientDrawable.RECTANGLE);
        nextBg.setColor(Color.WHITE);
        nextBg.setCornerRadius(10f);
        nextButton.setBackground(nextBg);
        nextButton.setPadding(24, 10, 24, 10);
        nextButton.setOnClickListener(v -> nextStep());
        
        buttonsLayout.addView(skipButton);
        buttonsLayout.addView(nextButton);
        tooltipContainer.addView(buttonsLayout);
        
        // Center the tooltip on screen
        FrameLayout.LayoutParams tooltipParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tooltipParams.gravity = Gravity.CENTER;
        tooltipParams.setMargins(40, 40, 40, 40);
        
        tutorialOverlay.addView(tooltipContainer, tooltipParams);
        
        // Animate tooltip appearance
        tooltipContainer.setAlpha(0f);
        tooltipContainer.setScaleX(0.8f);
        tooltipContainer.setScaleY(0.8f);
        tooltipContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .start();
    }
    
    private void createTooltipPopup(TutorialStep step, Rect targetRect) {
        // Create tooltip container
        LinearLayout tooltipContainer = new LinearLayout(activity);
        tooltipContainer.setOrientation(LinearLayout.VERTICAL);
        tooltipContainer.setPadding(24, 20, 24, 20);
        
        // Style tooltip with app theme
        GradientDrawable tooltipBg = new GradientDrawable();
        tooltipBg.setShape(GradientDrawable.RECTANGLE);
        tooltipBg.setColor(Color.parseColor("#5E35B1")); // App primary color
        tooltipBg.setCornerRadius(16f);
        tooltipBg.setStroke(2, Color.parseColor("#4A148C"));
        tooltipContainer.setBackground(tooltipBg);
        tooltipContainer.setElevation(12f);
        
        // Step counter
        TextView stepCounter = new TextView(activity);
        stepCounter.setText("Step " + (currentStepIndex + 1) + " of " + tutorialSteps.size());
        stepCounter.setTextSize(12);
        stepCounter.setTextColor(Color.parseColor("#E1BEE7")); // Light purple
        stepCounter.setGravity(Gravity.START);
        stepCounter.setPadding(0, 0, 0, 8);
        tooltipContainer.addView(stepCounter);
        
        // Title with emoji
        TextView titleView = new TextView(activity);
        titleView.setText(step.title);
        titleView.setTextSize(18);
        titleView.setTextColor(Color.WHITE);
        titleView.setGravity(Gravity.START);
        titleView.setPadding(0, 0, 0, 8);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        tooltipContainer.addView(titleView);
        
        // Description
        TextView descriptionView = new TextView(activity);
        descriptionView.setText(step.description);
        descriptionView.setTextSize(14);
        descriptionView.setTextColor(Color.parseColor("#F3E5F5")); // Very light purple
        descriptionView.setGravity(Gravity.START);
        descriptionView.setPadding(0, 0, 0, 16);
        descriptionView.setLineSpacing(4f, 1.2f);
        tooltipContainer.addView(descriptionView);
        
        // Buttons layout
        LinearLayout buttonsLayout = new LinearLayout(activity);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.END);
        
        // Skip button
        Button skipButton = new Button(activity);
        skipButton.setText("Skip");
        skipButton.setTextColor(Color.parseColor("#E1BEE7"));
        skipButton.setTextSize(14);
        skipButton.setBackgroundColor(Color.TRANSPARENT);
        skipButton.setPadding(16, 8, 16, 8);
        skipButton.setOnClickListener(v -> skipTutorial());
        
        // Next button
        Button nextButton = new Button(activity);
        nextButton.setText(currentStepIndex == tutorialSteps.size() - 1 ? "Finish ✨" : "Next →");
        nextButton.setTextColor(Color.parseColor("#5E35B1"));
        nextButton.setTextSize(14);
        nextButton.setTypeface(null, android.graphics.Typeface.BOLD);
        
        GradientDrawable nextBg = new GradientDrawable();
        nextBg.setShape(GradientDrawable.RECTANGLE);
        nextBg.setColor(Color.WHITE);
        nextBg.setCornerRadius(8f);
        nextButton.setBackground(nextBg);
        nextButton.setPadding(20, 8, 20, 8);
        nextButton.setOnClickListener(v -> nextStep());
        
        buttonsLayout.addView(skipButton);
        buttonsLayout.addView(nextButton);
        tooltipContainer.addView(buttonsLayout);
        
        // Position tooltip intelligently
        FrameLayout.LayoutParams tooltipParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        
        // Determine best position for tooltip
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        
        // Try to position tooltip below target, or above if no space
        if (targetRect.bottom + 200 < screenHeight) {
            // Position below
            tooltipParams.topMargin = targetRect.bottom + 20;
        } else {
            // Position above
            tooltipParams.topMargin = targetRect.top - 200;
        }
        
        // Center horizontally, adjust if too close to edges
        int tooltipWidth = 280; // Estimated width
        tooltipParams.leftMargin = Math.max(20, Math.min(
            targetRect.centerX() - tooltipWidth / 2,
            screenWidth - tooltipWidth - 20
        ));
        
        tooltipParams.setMargins(tooltipParams.leftMargin, tooltipParams.topMargin, 20, 20);
        
        tutorialOverlay.addView(tooltipContainer, tooltipParams);
        
        // Animate tooltip appearance
        tooltipContainer.setAlpha(0f);
        tooltipContainer.setScaleX(0.8f);
        tooltipContainer.setScaleY(0.8f);
        tooltipContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .start();
    }
    
    private void nextStep() {
        if (tutorialListener != null && currentStepIndex < tutorialSteps.size()) {
            TutorialStep step = tutorialSteps.get(currentStepIndex);
            tutorialListener.onStepCompleted(currentStepIndex, step.id);
        }
        
        currentStepIndex++;
        removeTutorialOverlay();
        
        // Small delay before next step
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            showCurrentStep();
        }, 200);
    }
    
    private void skipTutorial() {
        Log.d("SimpleTutorialManager", "Tutorial skipped by user");
        prefs.edit().putBoolean(tutorialSkippedKey, true).apply();
        removeTutorialOverlay();
        isRunning = false;
        
        if (tutorialListener != null) {
            tutorialListener.onTutorialSkipped();
        }
    }
    
    private void completeTutorial() {
        Log.d("SimpleTutorialManager", "Tutorial completed");
        prefs.edit().putBoolean(tutorialCompletedKey, true).apply();
        removeTutorialOverlay();
        isRunning = false;
        
        if (tutorialListener != null) {
            tutorialListener.onTutorialCompleted();
        }
    }
    
    private void removeTutorialOverlay() {
        if (tutorialOverlay != null) {
            ViewGroup parent = (ViewGroup) tutorialOverlay.getParent();
            if (parent != null) {
                parent.removeView(tutorialOverlay);
            }
            tutorialOverlay = null;
        }
        
        // Restore original backgrounds and reset component modifications
        restoreOriginalComponents();
        
        Log.d("SimpleTutorialManager", "Tutorial overlay removed and components restored");
    }
    
    private void restoreOriginalComponents() {
        // Restore all modified components to their original state
        if (originalBackgrounds != null) {
            for (HashMap.Entry<View, Drawable> entry : originalBackgrounds.entrySet()) {
                View view = entry.getKey();
                Drawable originalBg = entry.getValue();
                
                if (view != null) {
                    // Stop any animations
                    view.clearAnimation();
                    view.animate().cancel();
                    
                    // Restore original background
                    view.setBackground(originalBg);
                    
                    // Reset ONLY the properties we actually modified
                    view.setElevation(0f);
                    view.setAlpha(1.0f);
                    
                    Log.d("SimpleTutorialManager", "Restored component to original state");
                }
            }
            originalBackgrounds.clear();
        }
        
        // Reset tutorial step views
        for (TutorialStep step : tutorialSteps) {
            if (step.targetView != null) {
                step.targetView.clearAnimation();
                step.targetView.animate().cancel();
                step.targetView.setElevation(0f);
                step.targetView.setAlpha(1.0f);
            }
        }
        
        Log.d("SimpleTutorialManager", "All components restored safely");
    }
    
    public void forceStartTutorial() {
        Log.d("SimpleTutorialManager", "Force starting tutorial");
        resetTutorial();
        startTutorial();
    }
    
    public void resetTutorial() {
        prefs.edit()
            .putBoolean(tutorialCompletedKey, false)
            .putBoolean(tutorialSkippedKey, false)
            .apply();
        Log.d("SimpleTutorialManager", "Tutorial status reset");
    }
    
    public boolean isTutorialCompleted() {
        return prefs.getBoolean(tutorialCompletedKey, false);
    }
    
    public boolean isTutorialSkipped() {
        return prefs.getBoolean(tutorialSkippedKey, false);
    }
}
