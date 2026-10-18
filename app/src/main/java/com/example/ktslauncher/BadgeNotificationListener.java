package com.example.ktslauncher;

import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class BadgeNotificationListener extends NotificationListenerService {

    private static final String TAG = "BadgeNotificationListener";
    private static BadgeNotificationListener instance = null;

    public static BadgeNotificationListener getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "BadgeNotificationListener created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
        Log.d(TAG, "BadgeNotificationListener destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        instance = this;
        return super.onBind(intent);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        Log.d(TAG, "BadgeNotificationListener connected");
        NotificationBadgeManager.getInstance().updateFromListener(this);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (instance == this) {
            instance = null;
        }
        Log.d(TAG, "BadgeNotificationListener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        NotificationBadgeManager.getInstance().updateFromListener(this);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        NotificationBadgeManager.getInstance().updateFromListener(this);
    }
}
