package com.harish.dndscheduler;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class SpotlightOverlayView extends View {
    private Paint dimPaint;
    private Paint clearPaint;
    private Paint borderPaint;
    private Rect spotlightRect;
    private float cornerRadius = 24f;
    private float borderWidth = 8f;

    public SpotlightOverlayView(Context context) {
        super(context);
        init();
    }
    public SpotlightOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        dimPaint = new Paint();
        dimPaint.setColor(Color.parseColor("#B3000000")); // Semi-transparent black
        dimPaint.setAntiAlias(true);

        clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setAntiAlias(true);

        borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#00E5FF")); // Cyan border
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
        borderPaint.setAntiAlias(true);
    }

    // Call this to set the spotlight area (in screen coordinates)
    public void setSpotlightRect(Rect rect) {
        this.spotlightRect = rect;
        invalidate();
    }

    // Set spotlight using target view directly for accurate positioning
    public void setSpotlightForView(View targetView) {
        if (targetView == null) return;
        
        // Wait for this overlay to be laid out before calculating positions
        this.post(() -> {
            int[] targetLocation = new int[2];
            int[] overlayLocation = new int[2];
            
            targetView.getLocationOnScreen(targetLocation);
            this.getLocationOnScreen(overlayLocation);
            
            // Add some padding around the component
            int padding = 16;
            int left = targetLocation[0] - overlayLocation[0] - padding;
            int top = targetLocation[1] - overlayLocation[1] - padding;
            int right = left + targetView.getWidth() + (padding * 2);
            int bottom = top + targetView.getHeight() + (padding * 2);
            
            this.spotlightRect = new Rect(left, top, right, bottom);
            invalidate();
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draw dim overlay
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        if (spotlightRect != null) {
            Path path = new Path();
            RectF rectF = new RectF(spotlightRect);
            path.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW);
            // Cut out the spotlight area
            canvas.drawPath(path, clearPaint);
            // Draw border
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint);
        }
    }
}
