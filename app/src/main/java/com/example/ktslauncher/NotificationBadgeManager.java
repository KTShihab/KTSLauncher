package com.example.ktslauncher;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationBadgeManager {

    private static final String TAG = "NotificationBadgeMgr";
    private static final String PREF_NAME = "KTS_Badge_Counts";
    private static NotificationBadgeManager instance;

    private final Map<String, Integer> packageCounts = new ConcurrentHashMap<>();
    private final List<BadgeUpdateListener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Context appContext;

    public interface BadgeUpdateListener {
        void onBadgesUpdated();
    }

    public static synchronized NotificationBadgeManager getInstance() {
        if (instance == null) {
            instance = new NotificationBadgeManager();
        }
        return instance;
    }

    private NotificationBadgeManager() {}

    public void init(Context context) {
        this.appContext = context.getApplicationContext();
        loadSavedCounts();
    }

    public void registerListener(BadgeUpdateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregisterListener(BadgeUpdateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            for (BadgeUpdateListener l : listeners) {
                try {
                    l.onBadgesUpdated();
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying listener", e);
                }
            }
        });
    }

    public static boolean isNotificationAccessGranted(Context context) {
        if (context == null) return false;
        try {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    public static void openNotificationAccessSettings(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    public void updateFromListener(NotificationListenerService listener) {
        if (listener == null) return;
        try {
            StatusBarNotification[] active = listener.getActiveNotifications();
            updateCounts(active);
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving active notifications", e);
        }
    }

    public void updateCounts(StatusBarNotification[] activeNotifications) {
        Map<String, List<StatusBarNotification>> groupedByPkg = new HashMap<>();

        if (activeNotifications != null) {
            for (StatusBarNotification sbn : activeNotifications) {
                if (sbn == null || sbn.getNotification() == null) continue;

                // Filter out non-clearable / ongoing notifications (music player, background service, etc.)
                if (sbn.isOngoing()) continue;
                if ((sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;

                String pkg = sbn.getPackageName();
                if (pkg == null || pkg.isEmpty()) continue;

                groupedByPkg.computeIfAbsent(pkg, k -> new ArrayList<>()).add(sbn);
            }
        }

        Map<String, Integer> newCounts = new HashMap<>();

        for (Map.Entry<String, List<StatusBarNotification>> entry : groupedByPkg.entrySet()) {
            String pkg = entry.getKey();
            List<StatusBarNotification> list = entry.getValue();

            // Check if there are individual non-summary child notifications
            boolean hasChildren = false;
            for (StatusBarNotification sbn : list) {
                if ((sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0) {
                    hasChildren = true;
                    break;
                }
            }

            int total = 0;
            for (StatusBarNotification sbn : list) {
                boolean isSummary = (sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0;
                // If group has child notifications, don't double count the group summary
                if (hasChildren && isSummary) {
                    continue;
                }

                int num = sbn.getNotification().number;
                if (num > 1) {
                    total += num;
                } else {
                    total += 1;
                }
            }

            if (total > 0) {
                newCounts.put(pkg, total);
            }
        }

        packageCounts.clear();
        packageCounts.putAll(newCounts);
        saveCounts();
        notifyListeners();
    }

    public int getBadgeCount(String packageName) {
        if (packageName == null || packageName.isEmpty()) return 0;
        Integer count = packageCounts.get(packageName);
        return count != null ? count : 0;
    }

    public int getFolderBadgeCount(SharedPreferences prefs, String folderName, int totalPages, int maxRows, int maxCols) {
        if (folderName == null || folderName.isEmpty() || prefs == null) return 0;
        int folderTotal = 0;
        for (int p = 1; p <= totalPages; p++) {
            for (int r = 1; r <= maxRows; r++) {
                for (int c = 1; c <= maxCols; c++) {
                    String pagePrefix = (p == 1) ? (r + "_" + c) : ("p" + p + "_" + r + "_" + c);
                    String key = "folder_" + folderName + "_" + pagePrefix + "_pkg";
                    String pkg = prefs.getString(key, "");
                    if (!pkg.isEmpty() && !pkg.startsWith("folder:") && !pkg.contains(":")) {
                        folderTotal += getBadgeCount(pkg);
                    }
                }
            }
        }
        return folderTotal;
    }

    public boolean isPhoneApp(Context context, String pkgName, String appName) {
        if (pkgName == null) return false;
        try {
            TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null && pkgName.equals(tm.getDefaultDialerPackage())) {
                return true;
            }
        } catch (Exception ignored) {}

        String lowerName = appName != null ? appName.toLowerCase() : "";
        String lowerPkg = pkgName.toLowerCase();

        return lowerName.contains("phone") || lowerName.contains("dial") || lowerName.contains("call") ||
                lowerPkg.contains("dialer") || lowerPkg.contains("telecom") || lowerPkg.contains("com.truecaller") ||
                lowerPkg.equals("com.google.android.dialer") || lowerPkg.equals("com.samsung.android.dialer");
    }

    public boolean isMessagingApp(Context context, String pkgName, String appName) {
        if (pkgName == null) return false;
        try {
            String defaultSms = Telephony.Sms.getDefaultSmsPackage(context);
            if (pkgName.equals(defaultSms)) {
                return true;
            }
        } catch (Exception ignored) {}

        String lowerName = appName != null ? appName.toLowerCase() : "";
        String lowerPkg = pkgName.toLowerCase();

        return lowerName.contains("message") || lowerName.contains("messaging") ||
                lowerName.contains("sms") || lowerName.contains("mms") ||
                lowerName.contains("chat") ||
                lowerPkg.contains("messaging") || lowerPkg.contains("sms") ||
                lowerPkg.contains("mms") || lowerPkg.contains("conversations") ||
                lowerPkg.equals("com.google.android.apps.messaging") ||
                lowerPkg.equals("com.whatsapp") || lowerPkg.equals("com.whatsapp.w4b") ||
                lowerPkg.equals("org.telegram.messenger") || lowerPkg.equals("org.thunderdog.challegram") ||
                lowerPkg.equals("com.facebook.orca") || lowerPkg.equals("org.thoughtcrime.securesms") ||
                lowerPkg.equals("com.viber.voip") || lowerPkg.equals("com.tencent.mm");
    }

    public String getBadgeAccessibilityText(Context context, String pkgName, String appName, int count, String lang) {
        if (count <= 0) return "";
        boolean isMl = "ml".equalsIgnoreCase(lang);

        if (isPhoneApp(context, pkgName, appName)) {
            if (isMl) {
                return count == 1 ? "1 മിസ്ഡ് കോൾ" : count + " മിസ്ഡ് കോളുകൾ";
            } else {
                return count == 1 ? "1 missed call" : count + " missed calls";
            }
        } else if (isMessagingApp(context, pkgName, appName)) {
            if (isMl) {
                return count == 1 ? "1 വായിക്കാത്ത സന്ദേശം" : count + " വായിക്കാത്ത സന്ദേശങ്ങൾ";
            } else {
                return count == 1 ? "1 unread message" : count + " unread messages";
            }
        } else {
            if (isMl) {
                return count == 1 ? "1 വായിക്കാത്ത അറിയിപ്പ്" : count + " വായിക്കാത്ത അറിയിപ്പുകൾ";
            } else {
                return count == 1 ? "1 unread notification" : count + " unread notifications";
            }
        }
    }

    private SharedPreferences getBadgePrefs() {
        if (appContext == null) return null;
        try {
            Context deContext = appContext.createDeviceProtectedStorageContext();
            android.os.UserManager um = (android.os.UserManager) appContext.getSystemService(Context.USER_SERVICE);
            if (um == null || um.isUserUnlocked()) {
                deContext.moveSharedPreferencesFrom(appContext, PREF_NAME);
            }
            return deContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        } catch (Exception e) {
            Log.e(TAG, "Error obtaining device protected prefs", e);
            return appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    private void saveCounts() {
        if (appContext == null) return;
        try {
            SharedPreferences sp = getBadgePrefs();
            if (sp == null) return;
            SharedPreferences.Editor editor = sp.edit();
            editor.clear();
            for (Map.Entry<String, Integer> entry : packageCounts.entrySet()) {
                editor.putInt(entry.getKey(), entry.getValue());
            }
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving badge counts", e);
        }
    }

    private void loadSavedCounts() {
        if (appContext == null) return;
        try {
            SharedPreferences sp = getBadgePrefs();
            if (sp == null) return;
            Map<String, ?> all = sp.getAll();
            packageCounts.clear();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                if (entry.getValue() instanceof Integer) {
                    packageCounts.put(entry.getKey(), (Integer) entry.getValue());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading badge counts", e);
        }
    }
}
