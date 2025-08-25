package com.harish.dndscheduler;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.getkeepsafe.taptargetview.TapTargetView;

import java.util.ArrayList;
import java.util.List;

/**
 * Premium Mobile Game-Style Interactive Tutorial System
 * 
 * Features:
 * - First-time only execution using SharedPreferences
 * - Smooth animations with blur/dim effects
 * - Interactive step-by-step guidance
 * - Skip and replay functionality
 * - Modular and non-intrusive design
 */
public class GameTutorialManager {
    
    private static final String PREFS_NAME = "tutorial_prefs";
    private static final String TUTORIAL_COMPLETED_KEY = "main_tutorial_completed";
    private static final String TUTORIAL_SKIPPED_KEY = "tutorial_skipped";
    
    private Activity activity;
    private SharedPreferences prefs;
    private List<TutorialStep> tutorialSteps;
    private TutorialListener tutorialListener;
    private boolean isRunning = false;
    private int currentStepIndex = 0;
    
    // Tutorial configuration
    private static final int PRIMARY_COLOR = Color.parseColor("#6366F1"); // Modern indigo
    private static final int BACKGROUND_DIM_COLOR = Color.parseColor("#AA000000"); // Semi-transparent black
    private static final int TEXT_COLOR = Color.WHITE;
    private static final int ANIMATION_DURATION = 300;
    
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
        public boolean dismissOnTap;
        public boolean showSkipButton;
        public int iconResourceId;
        
        public TutorialStep(String id, View targetView, String title, String description) {
            this.id = id;
            this.targetView = targetView;
            this.title = title;
            this.description = description;
            this.dismissOnTap = true;
            this.showSkipButton = true;
            this.iconResourceId = 0;
        }
        
        public TutorialStep setDismissOnTap(boolean dismissOnTap) {
            this.dismissOnTap = dismissOnTap;
            return this;
        }
        
        public TutorialStep setShowSkipButton(boolean showSkipButton) {
            this.showSkipButton = showSkipButton;
            return this;
        }
        
        public TutorialStep setIcon(int iconResourceId) {
            this.iconResourceId = iconResourceId;
            return this;
        }
    }
    
    public GameTutorialManager(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.tutorialSteps = new ArrayList<>();
    }
    
    public void setTutorialListener(TutorialListener listener) {
        this.tutorialListener = listener;
    }
    
    /**
     * Add a tutorial step to the sequence
     */
    public GameTutorialManager addStep(TutorialStep step) {
        tutorialSteps.add(step);
        return this;
    }
    
    /**
     * Add a tutorial step with basic parameters
     */
    public GameTutorialManager addStep(String id, View targetView, String title, String description) {
        return addStep(new TutorialStep(id, targetView, title, description));
    }
    
    /**
     * Check if tutorial should be shown (first time only)
     */
    public boolean shouldShowTutorial() {
        return !prefs.getBoolean(TUTORIAL_COMPLETED_KEY, false) && 
               !prefs.getBoolean(TUTORIAL_SKIPPED_KEY, false);
    }
    
    /**
     * Start the tutorial sequence with premium animations
     */
    public void startTutorial() {
        Log.d("GameTutorialManager", "startTutorial() called");
        
        if (isRunning || tutorialSteps.isEmpty()) {
            Log.d("GameTutorialManager", "Cannot start tutorial - already running: " + isRunning + ", steps empty: " + tutorialSteps.isEmpty());
            return;
        }
        
        if (!shouldShowTutorial()) {
            Log.d("GameTutorialManager", "Tutorial should not be shown - already completed or skipped");
            return;
        }
        
        Log.d("GameTutorialManager", "Starting tutorial with " + tutorialSteps.size() + " steps");
        
        // Validate that all tutorial steps have valid views
        for (int i = tutorialSteps.size() - 1; i >= 0; i--) {
            TutorialStep step = tutorialSteps.get(i);
            if (step.targetView == null) {
                Log.w("GameTutorialManager", "Removing step with null view: " + step.id);
                tutorialSteps.remove(i);
            }
        }
        
        if (tutorialSteps.isEmpty()) {
            Log.w("GameTutorialManager", "No valid tutorial steps available");
            return;
        }
        
        isRunning = true;
        currentStepIndex = 0; // Reset step counter
        
        Log.d("GameTutorialManager", "Tutorial sequence starting with " + tutorialSteps.size() + " steps");
        android.widget.Toast.makeText(activity, "✨ Tutorial sequence starting!", android.widget.Toast.LENGTH_SHORT).show();
        
        if (tutorialListener != null) {
            tutorialListener.onTutorialStarted();
        }
        
        try {
            // Create the tutorial sequence with smooth animations
            TapTargetSequence.Listener sequenceListener = new TapTargetSequence.Listener() {
                @Override
                public void onSequenceFinish() {
                    Log.d("GameTutorialManager", "Tutorial sequence finished");
                    onTutorialFinished(false);
                }
                
                @Override
                public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                    Log.d("GameTutorialManager", "Tutorial step completed: " + currentStepIndex);
                    // Track step completion using a counter approach
                    if (tutorialListener != null && currentStepIndex < tutorialSteps.size()) {
                        TutorialStep step = tutorialSteps.get(currentStepIndex);
                        tutorialListener.onStepCompleted(currentStepIndex, step.id);
                        currentStepIndex++;
                    }
                }
                
                @Override
                public void onSequenceCanceled(TapTarget lastTarget) {
                    Log.d("GameTutorialManager", "Tutorial sequence canceled");
                    onTutorialFinished(true);
                }
            };
            
            // Build the sequence
            TapTargetSequence sequence = new TapTargetSequence(activity);
            
            for (int i = 0; i < tutorialSteps.size(); i++) {
                TutorialStep step = tutorialSteps.get(i);
                TapTarget target = createTapTarget(step, i);
                sequence.target(target);
            }
            
            sequence.listener(sequenceListener);
            sequence.continueOnCancel(false);
            sequence.considerOuterCircleCanceled(false);
            
            // Start with a slight delay for smooth entrance
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    sequence.start();
                } catch (Exception e) {
                    Log.e("GameTutorialManager", "Error starting tutorial sequence", e);
                    onTutorialFinished(true);
                }
            }, 500); // Increased delay to 500ms
            
        } catch (Exception e) {
            Log.e("GameTutorialManager", "Error setting up tutorial", e);
            isRunning = false;
            if (tutorialListener != null) {
                tutorialListener.onTutorialSkipped();
            }
        }
    }
    
    /**
     * Create a premium-styled TapTarget for a tutorial step
     */
    private TapTarget createTapTarget(TutorialStep step, int stepIndex) {
        // Add step counter for better UX
        String stepCounter = "Step " + (stepIndex + 1) + " of " + tutorialSteps.size();
        String fullDescription = step.description + "\n\n" + stepCounter;
        
        TapTarget target = TapTarget.forView(step.targetView, step.title, fullDescription)
                .outerCircleColor(R.color.tutorial_primary)
                .targetCircleColor(R.color.tutorial_content_bg)
                .titleTextSize(22)
                .titleTextColor(R.color.tutorial_button_primary_text)
                .descriptionTextSize(16)
                .descriptionTextColor(R.color.tutorial_description_text)
                .textColor(R.color.tutorial_button_primary_text)
                .dimColor(R.color.tutorial_overlay_background)
                .drawShadow(true)
                .cancelable(step.showSkipButton)
                .tintTarget(true)
                .transparentTarget(false)
                .targetRadius(60)
                .titleTypeface(Typeface.DEFAULT_BOLD)
                .descriptionTypeface(Typeface.DEFAULT);
        
        return target;
    }
    
    /**
     * Force start tutorial (ignores completion status)
     */
    public void forceStartTutorial() {
        Log.d("GameTutorialManager", "forceStartTutorial() called");
        android.widget.Toast.makeText(activity, "🎯 Starting interactive tutorial...", android.widget.Toast.LENGTH_SHORT).show();
        resetTutorial();
        startTutorial();
    }
    
    /**
     * Skip the tutorial
     */
    public void skipTutorial() {
        prefs.edit().putBoolean(TUTORIAL_SKIPPED_KEY, true).apply();
        isRunning = false;
        
        if (tutorialListener != null) {
            tutorialListener.onTutorialSkipped();
        }
    }
    
    /**
     * Mark tutorial as completed
     */
    private void markTutorialCompleted() {
        prefs.edit().putBoolean(TUTORIAL_COMPLETED_KEY, true).apply();
    }
    
    /**
     * Reset tutorial status (for replay functionality)
     */
    public void resetTutorial() {
        prefs.edit()
            .putBoolean(TUTORIAL_COMPLETED_KEY, false)
            .putBoolean(TUTORIAL_SKIPPED_KEY, false)
            .apply();
    }
    
    /**
     * Check if tutorial was completed
     */
    public boolean isTutorialCompleted() {
        return prefs.getBoolean(TUTORIAL_COMPLETED_KEY, false);
    }
    
    /**
     * Check if tutorial was skipped
     */
    public boolean isTutorialSkipped() {
        return prefs.getBoolean(TUTORIAL_SKIPPED_KEY, false);
    }
    
    /**
     * Handle tutorial completion
     */
    private void onTutorialFinished(boolean wasSkipped) {
        isRunning = false;
        
        if (wasSkipped) {
            skipTutorial();
        } else {
            markTutorialCompleted();
            if (tutorialListener != null) {
                tutorialListener.onTutorialCompleted();
            }
        }
    }
    
    /**
     * Show a single tutorial target (for individual highlights)
     */
    public void showSingleTarget(TutorialStep step, TapTargetView.Listener listener) {
        TapTarget target = createTapTarget(step, 0);
        
        TapTargetView.showFor(activity, target, new TapTargetView.Listener() {
            @Override
            public void onTargetClick(TapTargetView view) {
                super.onTargetClick(view);
                if (listener != null) {
                    listener.onTargetClick(view);
                }
            }
            
            @Override
            public void onTargetLongClick(TapTargetView view) {
                super.onTargetLongClick(view);
                if (listener != null) {
                    listener.onTargetLongClick(view);
                }
            }
            
            @Override
            public void onTargetCancel(TapTargetView view) {
                super.onTargetCancel(view);
                if (listener != null) {
                    listener.onTargetCancel(view);
                }
            }
            
            @Override
            public void onOuterCircleClick(TapTargetView view) {
                super.onOuterCircleClick(view);
                if (listener != null) {
                    listener.onOuterCircleClick(view);
                }
            }
        });
    }
    
    /**
     * Clear all tutorial steps
     */
    public void clearSteps() {
        tutorialSteps.clear();
    }
    
    /**
     * Get total number of steps
     */
    public int getStepCount() {
        return tutorialSteps.size();
    }
    
    /**
     * Check if tutorial is currently running
     */
    public boolean isRunning() {
        return isRunning;
    }
}
