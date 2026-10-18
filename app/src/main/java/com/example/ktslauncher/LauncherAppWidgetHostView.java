package com.example.ktslauncher;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/**
 * Custom AppWidgetHostView that ensures proper touch event handling inside scrollable parent containers
 * and provides clean fallback error views for accessible widgets.
 */
public class LauncherAppWidgetHostView extends AppWidgetHostView {
    private static final String TAG = "LauncherWidgetHostView";

    public LauncherAppWidgetHostView(Context context) {
        super(context);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Prevent parent ScrollView from stealing touch events from scrollable widgets (ListView, GridView)
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected View getErrorView() {
        Log.w(TAG, "Error inflating widget item in host view for widget ID: " + getAppWidgetId());
        TextView tv = new TextView(getContext());
        tv.setText("Widget item unavailable");
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(0x99222222);
        tv.setPadding(16, 16, 16, 16);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }
}
