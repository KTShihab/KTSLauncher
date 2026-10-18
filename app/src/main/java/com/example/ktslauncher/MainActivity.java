package com.example.ktslauncher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SizeF;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.os.UserManager;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.service.notification.NotificationListenerService;

@SuppressLint({"SetTextI18n", "UnsafeIntentLaunch"})
public class MainActivity extends AppCompatActivity implements NotificationBadgeManager.BadgeUpdateListener {

    private SharedPreferences prefs;
    private LinearLayout mainGridLayout;
    private String movingCellKey = null;
    private String pendingAddPkg = null;
    private String pendingAddName = null;
    private GestureDetector gestureDetector;

    private String currentFolder = "";
    private int currentPage = 1;
    private int totalPages = 1;
    private long lastScrollTime = 0;
    private final Button[] dockButtons = new Button[5];
    private final List<AppInfo> allAppsCache = new ArrayList<>();
    private final Map<String, Drawable> iconCache = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String lastInteractedKey = null;
    private String parentFolderCellKey = null;
    private Runnable pendingRestoreRunnable = null;

    private final MultiFingerGestureHandler multiFingerHandler = new MultiFingerGestureHandler();

    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> bindWidgetLauncher;
    private ActivityResultLauncher<Intent> configureWidgetLauncher;

    private AppWidgetManager appWidgetManager;
    private LauncherAppWidgetHost appWidgetHost;
    private Context widgetHostContext;
    private final Map<Integer, AppWidgetHostView> widgetViewCache = new HashMap<>();
    private int pendingWidgetId = -1;
    private AppWidgetProviderInfo pendingWidgetInfo = null;
    private String pendingContactCellKey = null;
    private BroadcastReceiver unlockReceiver = null;

    public static SharedPreferences getSafeDeviceProtectedPrefs(Context context) {
        try {
            Context deContext = context.createDeviceProtectedStorageContext();
            UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
            if (um == null || um.isUserUnlocked()) {
                deContext.moveSharedPreferencesFrom(context, "GridAppLauncherPrefs");
            }
            return deContext.getSharedPreferences("GridAppLauncherPrefs", MODE_PRIVATE);
        } catch (Exception e) {
            Log.e("MainActivity", "Error accessing device protected prefs", e);
            return context.getSharedPreferences("GridAppLauncherPrefs", MODE_PRIVATE);
        }
    }

    static class ContactItem {
        String name;
        String number;

        ContactItem(String name, String number) {
            this.name = name;
            this.number = number;
        }

        @NonNull
        @Override
        public String toString() { return name + " (" + number + ")"; }
    }

    static class AppInfo {
        String name;
        String packageName;
        String activityName;
        int usageCount;

        AppInfo(String name, String packageName, int usageCount) {
            this(name, packageName, null, usageCount);
        }

        AppInfo(String name, String packageName, String activityName, int usageCount) {
            this.name = name;
            this.packageName = packageName;
            this.activityName = activityName;
            this.usageCount = usageCount;
        }

        @NonNull
        @Override
        public String toString() { return name; }
    }

    static class SearchItem {
        static final int TYPE_APP = 0;
        static final int TYPE_CONTACT = 1;
        static final int TYPE_WEB = 2;

        int type;
        String title;
        String subtitle;
        String data;

        SearchItem(int type, String title, String subtitle, String data) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.data = data;
        }
    }

    @Override
    protected void attachBaseContext(@NonNull Context newBase) {
        SharedPreferences preferences = getSafeDeviceProtectedPrefs(newBase);
        String lang = preferences.getString("app_lang", null);
        if (lang != null) {
            Locale locale = new Locale.Builder().setLanguage(lang).build();
            Locale.setDefault(locale);
            Configuration config = newBase.getResources().getConfiguration();
            config.setLocale(locale);
            Context context = newBase.createConfigurationContext(config);
            super.attachBaseContext(context);
        } else {
            super.attachBaseContext(newBase);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View topBar = findViewById(R.id.topBarLayout);
        if (topBar != null) {
            topBar.setPadding(dpToPx(16), getStatusBarHeight() + dpToPx(8), dpToPx(16), dpToPx(6));
        }
        View dock = findViewById(R.id.dockContainer);
        if (dock != null) {
            dock.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), getNavigationBarHeight() + dpToPx(12));
        }

        prefs = getSafeDeviceProtectedPrefs(this);
        totalPages = prefs.getInt("total_pages", 1);
        if (savedInstanceState != null) {
            lastInteractedKey = savedInstanceState.getString("last_interacted_key");
            parentFolderCellKey = savedInstanceState.getString("parent_folder_cell_key");
        }
        if (lastInteractedKey == null) {
            lastInteractedKey = prefs.getString("last_interacted_key", null);
        }
        NotificationBadgeManager.getInstance().init(this);
        AudioRoutingManager.getInstance().resetAudioMode(this);

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("MainActivity", "Uncaught exception in KTSLauncher", throwable);
            try {
                Intent restartIntent = new Intent(getApplicationContext(), MainActivity.class);
                restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(restartIntent);
            } catch (Exception ignored) {}
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(1);
            }
        });

        setupDirectBootReceiver();
        setupBackPressedHandling();

        widgetHostContext = new ContextThemeWrapper(getApplicationContext(), android.R.style.Theme_DeviceDefault_DayNight);
        appWidgetManager = AppWidgetManager.getInstance(widgetHostContext);
        appWidgetHost = new LauncherAppWidgetHost(widgetHostContext, 1024);
        appWidgetHost.startListening();

        mainGridLayout = findViewById(R.id.mainGridLayout);
        ScrollView mainScrollView = findViewById(R.id.mainScrollView);
        setupHomeScreenAccessibility(mainScrollView, mainGridLayout);
        Button settingsButton = findViewById(R.id.settingsButton);
        Button allAppsButton = findViewById(R.id.allAppsButton);
        Button backButton = findViewById(R.id.backButton);

        dockButtons[0] = findViewById(R.id.dock0);
        dockButtons[1] = findViewById(R.id.dock1);
        dockButtons[2] = findViewById(R.id.dock2);
        dockButtons[3] = findViewById(R.id.dock3);
        dockButtons[4] = findViewById(R.id.dock4);

        settingsButton.setOnClickListener(v -> {
            setLastInteractedKey("settings");
            provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showSettingsMenu();
        });

        allAppsButton.setOnClickListener(v -> {
            setLastInteractedKey("all_apps");
            provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showAllAppsDrawer();
        });
        Drawable allAppsIcon = ContextCompat.getDrawable(this, R.drawable.ic_all_apps);
        if (allAppsIcon != null) {
            allAppsIcon = allAppsIcon.mutate();
            allAppsIcon.setTint(Color.WHITE);
            int sz = dpToPx(20);
            allAppsIcon.setBounds(0, 0, sz, sz);
            allAppsButton.setCompoundDrawables(allAppsIcon, null, null, null);
            allAppsButton.setCompoundDrawablePadding(dpToPx(8));
        }

        backButton.setOnClickListener(v -> {
            provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (parentFolderCellKey != null) {
                setLastInteractedKey(parentFolderCellKey);
                parentFolderCellKey = null;
            }
            currentFolder = "";
            buildGrid();
            announceAccessibility("Returned to Page " + currentPage);
        });

        applyCurrentTheme();

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null) {
                    float diffY = e2.getY() - e1.getY();
                    float diffX = e2.getX() - e1.getX();

                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (diffX > 50 && Math.abs(velocityX) > 100) {
                            if (currentPage > 1) {
                                currentPage--;
                                provideHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                buildGrid();
                                announceAccessibility("Page " + currentPage);
                            }
                            else if (prefs.getBoolean("google_feed_enabled", true)) {
                                provideHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                openGoogleFeed();
                            }
                            return true;
                        } else if (diffX < -50 && Math.abs(velocityX) > 100) {
                            if (currentPage < totalPages) {
                                currentPage++;
                                provideHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                buildGrid();
                                announceAccessibility("Page " + currentPage);
                            }
                            else askCreateNewPage();
                            return true;
                        }
                    } else {
                        if (diffY < -50 && Math.abs(velocityY) > 100) {
                            executeGestureAction("swipe_up_1");
                            return true;
                        } else if (diffY > 50 && Math.abs(velocityY) > 100) {
                            executeGestureAction("swipe_down_1");
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        backupLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri != null) writeBackupToFile(uri);
        });

        restoreLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) readBackupFromFile(uri);
        });

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                if (pendingContactCellKey != null) {
                    showContactSelectionDialog(pendingContactCellKey);
                    pendingContactCellKey = null;
                }
            } else {
                Toast.makeText(this, "Contacts permission denied", Toast.LENGTH_SHORT).show();
                pendingContactCellKey = null;
            }
        });

        bindWidgetLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                checkWidgetConfiguration();
            } else {
                cancelWidgetAdd();
            }
        });

        configureWidgetLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                finalizeWidgetAdd();
            } else {
                cancelWidgetAdd();
            }
        });

        AudioRoutingManager.getInstance().initRouting(this);
        buildGrid();
        handleIntent(getIntent());

        if (AppUpdateManager.isAutoCheckEnabled(this)) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    try {
                        AppUpdateManager.getInstance().checkForUpdates(this, false);
                    } catch (Exception ignored) {}
                }
            }, 5000);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        appWidgetHost.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        appWidgetHost.stopListening();
    }

    @Override
    protected void onDestroy() {
        if (unlockReceiver != null) {
            try {
                unregisterReceiver(unlockReceiver);
                unlockReceiver = null;
            } catch (Exception ignored) {}
        }
        super.onDestroy();
        widgetViewCache.clear();
    }

    private void setupDirectBootReceiver() {
        UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
        if (um != null && !um.isUserUnlocked()) {
            unlockReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                        try {
                            unregisterReceiver(this);
                            unlockReceiver = null;
                        } catch (Exception ignored) {}
                        try {
                            Context deContext = createDeviceProtectedStorageContext();
                            deContext.moveSharedPreferencesFrom(MainActivity.this, "GridAppLauncherPrefs");
                        } catch (Exception e) {
                            Log.e("MainActivity", "Failed to migrate CE prefs on unlock", e);
                        }
                        loadAppsInBackground();
                        buildGrid();
                    }
                }
            };
            registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        }
    }

    private void setupHomeScreenAccessibility(View scrollView, View gridLayout) {
        AccessibilityDelegateCompat homeDelegate = new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                boolean isMl = isCurrentLanguageMalayalam();

                info.setScrollable(true);
                info.setClickable(true);
                info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_LEFT);
                info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_RIGHT);

                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.action_gesture_search, isMl ? "സാർവത്രിക തിരച്ചിൽ" : "Universal Search"));
                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.action_gesture_app_drawer, isMl ? "ആപ്പ് ഡ്രോയർ" : "App Drawer"));
                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.action_gesture_next_page, isMl ? "അടുത്ത പേജ്" : "Next Page"));
                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.action_gesture_prev_page, isMl ? "മുൻപത്തെ പേജ്" : "Previous Page"));
                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.action_gesture_settings, isMl ? "ലോഞ്ചർ ക്രമീകരണങ്ങൾ" : "Launcher Settings"));
                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.action_gesture_notifications, isMl ? "അറിയിപ്പ് പാനൽ" : "Notification Panel"));
                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.action_gesture_quick_settings, isMl ? "ദ്രുത ക്രമീകരണങ്ങൾ" : "Quick Settings"));
                if (prefs != null && prefs.getBoolean("google_feed_enabled", true)) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_gesture_feed, isMl ? "ഗൂഗിൾ ഫീഡ്" : "Google Feed"));
                }
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, @Nullable Bundle args) {
                if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                    executeGestureAction("double_tap_2");
                    return true;
                } else if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) {
                    executeGestureAction("swipe_up_2");
                    return true;
                } else if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) {
                    executeGestureAction("swipe_down_2");
                    return true;
                } else if (action == AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_LEFT.getId()) {
                    executeGestureAction("swipe_left_2");
                    return true;
                } else if (action == AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_RIGHT.getId()) {
                    executeGestureAction("swipe_right_2");
                    return true;
                } else if (action == R.id.action_gesture_search) {
                    showUniversalSearchDialog();
                    return true;
                } else if (action == R.id.action_gesture_app_drawer) {
                    showAllAppsDrawer();
                    return true;
                } else if (action == R.id.action_gesture_next_page) {
                    executeGestureAction("swipe_left_2");
                    return true;
                } else if (action == R.id.action_gesture_prev_page) {
                    executeGestureAction("swipe_right_2");
                    return true;
                } else if (action == R.id.action_gesture_settings) {
                    showSettingsMenu();
                    return true;
                } else if (action == R.id.action_gesture_notifications) {
                    expandStatusBar(false);
                    return true;
                } else if (action == R.id.action_gesture_quick_settings) {
                    expandStatusBar(true);
                    return true;
                } else if (action == R.id.action_gesture_feed) {
                    openGoogleFeed();
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        };

        if (scrollView != null) {
            ViewCompat.setAccessibilityDelegate(scrollView, homeDelegate);
            scrollView.setOnClickListener(v -> executeGestureAction("double_tap_2"));
        }
        if (gridLayout != null) {
            ViewCompat.setAccessibilityDelegate(gridLayout, homeDelegate);
            gridLayout.setOnClickListener(v -> executeGestureAction("double_tap_2"));
        }
    }

    private void setupBackPressedHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleLauncherBackPressed();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (lastInteractedKey != null) {
            outState.putString("last_interacted_key", lastInteractedKey);
        }
        if (parentFolderCellKey != null) {
            outState.putString("parent_folder_cell_key", parentFolderCellKey);
        }
    }

    private void handleLauncherBackPressed() {
        if (pendingAddPkg != null || movingCellKey != null) {
            pendingAddPkg = null;
            pendingAddName = null;
            movingCellKey = null;
            buildGrid();
            announceAccessibility(isCurrentLanguageMalayalam() ? "റദ്ദാക്കി" : "Cancelled");
            return;
        }
        if (!currentFolder.isEmpty()) {
            if (parentFolderCellKey != null) {
                setLastInteractedKey(parentFolderCellKey);
                parentFolderCellKey = null;
            }
            currentFolder = "";
            buildGrid();
            announceAccessibility(isCurrentLanguageMalayalam() ? "പേജ് " + currentPage + "-ലേക്ക് തിരികെ വന്നു" : "Returned to Page " + currentPage);
            return;
        }
        if (currentPage != 1) {
            currentPage = 1;
            buildGrid();
            announceAccessibility(isCurrentLanguageMalayalam() ? "പേജ് 1" : "Page 1");
            return;
        }
        // Root page 1: consume back key. Never exit or finish MainActivity.
    }

    public boolean isCurrentLanguageMalayalam() {
        if (prefs == null) {
            return "ml".equalsIgnoreCase(Locale.getDefault().getLanguage());
        }
        String lang = prefs.getString("app_lang", null);
        if (lang != null) {
            return "ml".equalsIgnoreCase(lang);
        }
        return "ml".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }

    @Override
    public void onBackPressed() {
        handleLauncherBackPressed();
    }

    private void provideHapticFeedback(int feedbackConstant) {
        View view = getWindow().getDecorView();
        view.performHapticFeedback(feedbackConstant);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int dpToPx(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        if (result <= 0) {
            result = dpToPx(36);
        }
        return result;
    }

    private int getNavigationBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @Override
    protected void onResume() {
        super.onResume();
        AudioRoutingManager.getInstance().onResume(this);
        applyCurrentTheme();
        NotificationBadgeManager.getInstance().registerListener(this);

        boolean pendingAppInfo = prefs.getBoolean("pending_restricted_settings_flow", false);
        if (pendingAppInfo) {
            prefs.edit().putBoolean("pending_restricted_settings_flow", false).apply();
            if (!NotificationBadgeManager.isNotificationAccessGranted(this)) {
                promptStep2NotificationAccess();
            } else {
                prefs.edit().putBoolean("notification_badges_enabled", true).apply();
                buildGrid();
                Toast.makeText(this, "Notification badges enabled", Toast.LENGTH_SHORT).show();
                announceAccessibility("Notification badges enabled");
            }
        }

        if (prefs.getBoolean("notification_badges_enabled", true) && NotificationBadgeManager.isNotificationAccessGranted(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    NotificationListenerService.requestRebind(new ComponentName(this, BadgeNotificationListener.class));
                } catch (Exception ignored) {}
            }
            if (BadgeNotificationListener.getInstance() != null) {
                NotificationBadgeManager.getInstance().updateFromListener(BadgeNotificationListener.getInstance());
            }
        }
        loadAppsInBackground();
        restoreLastFocusedView();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        restoreLastFocusedView();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            restoreLastFocusedView();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        NotificationBadgeManager.getInstance().unregisterListener(this);
    }

    @Override
    public void onBadgesUpdated() {
        buildGrid();
    }

    private Drawable createBadgedIcon(Drawable baseIcon, int count) {
        return createBadgedIcon(baseIcon, count, dpToPx(44));
    }

    private Drawable createBadgedIcon(Drawable baseIcon, int count, int customSizePx) {
        if (baseIcon == null) return null;
        if (count <= 0) return baseIcon;

        int size = customSizePx > 0 ? customSizePx : dpToPx(44);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        baseIcon.setBounds(0, 0, size, size);
        baseIcon.draw(canvas);

        Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setColor(0xFFE53935);
        badgePaint.setStyle(Paint.Style.FILL);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(1.5f));

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        String countText = count > 99 ? "99+" : String.valueOf(count);

        float radius = dpToPx(8.5f);
        float cx = size - radius - dpToPx(1);
        float cy = radius + dpToPx(1);

        if (countText.length() > 1) {
            textPaint.setTextSize(dpToPx(8.5f));
            float textWidth = textPaint.measureText(countText);
            float pillWidth = Math.max(textWidth + dpToPx(6), radius * 2);
            RectF pillRect = new RectF(size - pillWidth - dpToPx(1), dpToPx(1), size - dpToPx(1), dpToPx(1) + radius * 2);
            canvas.drawRoundRect(pillRect, radius, radius, badgePaint);
            canvas.drawRoundRect(pillRect, radius, radius, borderPaint);

            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = pillRect.centerY() - (fm.ascent + fm.descent) / 2;
            canvas.drawText(countText, pillRect.centerX(), textY, textPaint);
        } else {
            textPaint.setTextSize(dpToPx(9.5f));
            canvas.drawCircle(cx, cy, radius, badgePaint);
            canvas.drawCircle(cx, cy, radius, borderPaint);

            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = cy - (fm.ascent + fm.descent) / 2;
            canvas.drawText(countText, cx, textY, textPaint);
        }

        return new BitmapDrawable(getResources(), bitmap);
    }

    private Drawable getCachedAppIcon(String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        Drawable d = iconCache.get(packageName);
        if (d != null) return d;
        try {
            d = getPackageManager().getApplicationIcon(packageName);
            if (d != null) {
                iconCache.put(packageName, d);
            }
        } catch (Exception ignored) {}
        return d;
    }

    private AppInfo findAppByNameOrPackage(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String q = query.trim().toLowerCase();
        for (AppInfo info : allAppsCache) {
            if (info.name.equalsIgnoreCase(q) || info.packageName.equalsIgnoreCase(q)) {
                return info;
            }
        }
        for (AppInfo info : allAppsCache) {
            if (info.name.toLowerCase().contains(q) || q.contains(info.name.toLowerCase())) {
                return info;
            }
        }
        return null;
    }

    private List<String> getFolderAppPackages(String folderName) {
        List<String> pkgs = new ArrayList<>();
        if (folderName == null || folderName.isEmpty()) return pkgs;
        int maxRows = prefs.getInt("grid_rows", 10);
        int maxCols = prefs.getInt("grid_cols", 4);
        for (int p = 1; p <= totalPages; p++) {
            for (int r = 1; r <= maxRows; r++) {
                for (int c = 1; c <= maxCols; c++) {
                    String key = "folder_" + folderName + "_page_" + p + "_" + r + "_" + c + "_pkg";
                    String itemPkg = prefs.getString(key, "");
                    if (!itemPkg.isEmpty() && isAppPackage(itemPkg)) {
                        pkgs.add(itemPkg);
                        if (pkgs.size() >= 4) return pkgs;
                    }
                }
            }
        }
        return pkgs;
    }

    private Drawable createFolderCompositeIcon(String folderName, List<String> pkgs) {
        int size = dpToPx(44);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        ThemeManager.ActiveTheme activeTheme = ThemeManager.getActiveTheme(prefs);
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(activeTheme.isLight ? 0xFFE2E8F0 : 0xFF23232C);
        float radius = dpToPx(12);
        canvas.drawRoundRect(new RectF(0, 0, size, size), radius, radius, bgPaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(activeTheme.isLight ? 0xFFCBD5E1 : 0xFF3E3E4C);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(1));
        canvas.drawRoundRect(new RectF(0, 0, size, size), radius, radius, borderPaint);

        int count = Math.min(pkgs.size(), 4);
        if (count == 1) {
            Drawable icon = getCachedAppIcon(pkgs.get(0));
            if (icon != null) {
                int pad = dpToPx(6);
                icon.setBounds(pad, pad, size - pad, size - pad);
                icon.draw(canvas);
            }
        } else if (count > 1) {
            int miniSize = dpToPx(16);
            int margin = dpToPx(4);
            int gap = dpToPx(2);
            for (int i = 0; i < count; i++) {
                Drawable icon = getCachedAppIcon(pkgs.get(i));
                if (icon != null) {
                    int col = i % 2;
                    int row = i / 2;
                    int left = margin + col * (miniSize + gap);
                    int top = margin + row * (miniSize + gap);
                    icon.setBounds(left, top, left + miniSize, top + miniSize);
                    icon.draw(canvas);
                }
            }
        }
        return new BitmapDrawable(getResources(), bitmap);
    }

    private Drawable createFolderBadgedIcon(Drawable baseIcon) {
        if (baseIcon == null) return null;
        int size = dpToPx(44);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        baseIcon.setBounds(0, 0, size, size);
        baseIcon.draw(canvas);

        Drawable folderGlyph = ContextCompat.getDrawable(this, R.drawable.ic_folder);
        if (folderGlyph != null) {
            int badgeSize = dpToPx(16);
            int left = size - badgeSize - dpToPx(1);
            int top = size - badgeSize - dpToPx(1);

            ThemeManager.ActiveTheme activeTheme = ThemeManager.getActiveTheme(prefs);
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(activeTheme.isLight ? 0xFFFFFFFF : 0xFF1E1E24);
            canvas.drawCircle(left + badgeSize / 2f, top + badgeSize / 2f, badgeSize / 2f + dpToPx(1), bgPaint);

            folderGlyph = folderGlyph.mutate();
            folderGlyph.setTint(0xFFFFA726); // Warm Amber
            folderGlyph.setBounds(left, top, left + badgeSize, top + badgeSize);
            folderGlyph.draw(canvas);
        }
        return new BitmapDrawable(getResources(), bitmap);
    }

    private Drawable getFolderIcon(String folderName) {
        if (folderName == null || folderName.isEmpty()) {
            Drawable def = ContextCompat.getDrawable(this, R.drawable.ic_folder);
            if (def != null) {
                def = def.mutate();
                def.setTint(0xFFFFA726);
            }
            return def;
        }

        List<String> innerPkgs = getFolderAppPackages(folderName);
        if (!innerPkgs.isEmpty()) {
            return createFolderCompositeIcon(folderName, innerPkgs);
        }

        AppInfo matchingApp = findAppByNameOrPackage(folderName);
        if (matchingApp != null) {
            Drawable appIcon = getCachedAppIcon(matchingApp.packageName);
            if (appIcon != null) {
                return createFolderBadgedIcon(appIcon);
            }
        }

        String lower = folderName.toLowerCase();
        if (lower.contains("web") || lower.contains("link") || lower.contains("site") || lower.contains("url")) {
            Drawable webIcon = ContextCompat.getDrawable(this, R.drawable.ic_web_link);
            if (webIcon != null) {
                webIcon = webIcon.mutate();
                webIcon.setTint(0xFF29B6F6);
                return createFolderBadgedIcon(webIcon);
            }
        }
        if (lower.contains("media") || lower.contains("music") || lower.contains("audio") || lower.contains("video")) {
            Drawable mediaIcon = ContextCompat.getDrawable(this, R.drawable.ic_media_play_pause);
            if (mediaIcon != null) {
                mediaIcon = mediaIcon.mutate();
                mediaIcon.setTint(0xFFFF7043);
                return createFolderBadgedIcon(mediaIcon);
            }
        }

        Drawable folderDrawable = ContextCompat.getDrawable(this, R.drawable.ic_folder);
        if (folderDrawable != null) {
            folderDrawable = folderDrawable.mutate();
            folderDrawable.setTint(0xFFFFA726);
            return folderDrawable;
        }
        return ContextCompat.getDrawable(this, android.R.drawable.ic_menu_agenda);
    }

    private void loadAppsInBackground() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);

            List<AppInfo> tempAppList = new ArrayList<>();
            String myPkg = getPackageName();
            for (ResolveInfo ri : activities) {
                if (ri == null || ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (myPkg.equals(pkg)) continue;
                String actName = ri.activityInfo.name;
                CharSequence label = ri.loadLabel(pm);
                String appName = label != null ? label.toString() : pkg;
                tempAppList.add(new AppInfo(appName, pkg, actName, prefs.getInt("usage_" + pkg, 0)));
            }

            tempAppList.sort((a, b) -> {
                if (b.usageCount != a.usageCount) return Integer.compare(b.usageCount, a.usageCount);
                return a.name.compareToIgnoreCase(b.name);
            });

            runOnUiThread(() -> {
                allAppsCache.clear();
                allAppsCache.addAll(tempAppList);
            });
        }).start();
    }

    private void loadAppsSynchronously() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);

        allAppsCache.clear();
        String myPkg = getPackageName();
        for (ResolveInfo ri : activities) {
            if (ri == null || ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (myPkg.equals(pkg)) continue;
            String actName = ri.activityInfo.name;
            CharSequence label = ri.loadLabel(pm);
            String appName = label != null ? label.toString() : pkg;
            allAppsCache.add(new AppInfo(appName, pkg, actName, prefs.getInt("usage_" + pkg, 0)));
        }

        allAppsCache.sort((a, b) -> {
            if (b.usageCount != a.usageCount) return Integer.compare(b.usageCount, a.usageCount);
            return a.name.compareToIgnoreCase(b.name);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        if (intent.getBooleanExtra("open_audio_routing_dialog", false) || intent.getBooleanExtra("toggle_audio_routing", false)) {
            AudioRoutingManager.getInstance().toggleAudioSeparation(this);
            buildGrid();
            return;
        }

        if (intent.getAction() == null) return;

        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            boolean shouldRebuild = false;
            if (pendingAddPkg != null || movingCellKey != null) {
                pendingAddPkg = null;
                pendingAddName = null;
                movingCellKey = null;
                shouldRebuild = true;
            }
            if (!currentFolder.isEmpty()) {
                currentFolder = "";
                shouldRebuild = true;
            } else if (currentPage != 1) {
                currentPage = 1;
                shouldRebuild = true;
            }
            if (shouldRebuild) {
                buildGrid();
            }
            return;
        }

        if ("android.content.pm.action.CONFIRM_PIN_SHORTCUT".equals(intent.getAction())) {
            LauncherApps.PinItemRequest request = intent.getParcelableExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST);
            if (request != null && request.getRequestType() == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                ShortcutInfo info = request.getShortcutInfo();
                if (info != null) {
                    String name = info.getShortLabel() != null ? info.getShortLabel().toString() : "Shortcut";
                    String pkg = info.getPackage();
                    String id = info.getId();
                    request.accept();
                    promptAddMethod("shortcut:" + pkg + ":" + id, name);
                }
            }
        } else if ("com.android.launcher.action.INSTALL_SHORTCUT".equals(intent.getAction())) {
            String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
            Intent shortcutIntent = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
            if (name != null && shortcutIntent != null) {
                String uri = shortcutIntent.toUri(0);
                promptAddMethod("legacy_shortcut:" + uri, name);
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            multiFingerHandler.resetTouch();
        }

        boolean multiHandled = multiFingerHandler.onTouchEvent(event);

        if (multiHandled || multiFingerHandler.isMultiFinger()) {
            return true;
        }

        gestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastScrollTime < 500) return true;

            float hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
            if (hScroll != 0) {
                lastScrollTime = currentTime;

                if (hScroll < 0) {
                    if (currentPage > 1) {
                        currentPage--;
                        provideHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        buildGrid();
                        announceAccessibility("Page " + currentPage);
                    } else {
                        if (prefs.getBoolean("google_feed_enabled", true)) openGoogleFeed();
                    }
                } else if (hScroll > 0) {
                    if (currentPage < totalPages) {
                        currentPage++;
                        provideHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        buildGrid();
                        announceAccessibility("Page " + currentPage);
                    } else {
                        askCreateNewPage();
                    }
                }
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    public void announceAccessibility(String text) {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am != null && am.isEnabled()) {
            AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
            event.setClassName(getClass().getName());
            event.setPackageName(getPackageName());
            event.getText().add(text);
            am.sendAccessibilityEvent(event);
        }
    }

    private void showUniversalSearchDialog() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Universal Search");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);

        EditText searchBox = new EditText(this);
        searchBox.setHint("Search apps, contacts, web...");
        layout.addView(searchBox);

        ListView listView = new ListView(this);
        layout.addView(listView);
        builder.setView(layout);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        List<SearchItem> currentResults = new ArrayList<>();
        ArrayAdapter<SearchItem> adapter = new ArrayAdapter<SearchItem>(this, android.R.layout.simple_list_item_2, android.R.id.text1, currentResults) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);
                SearchItem item = getItem(position);
                if (item != null) {
                    text1.setText(item.title);
                    text2.setText(item.subtitle);
                }
                return view;
            }
        };
        listView.setAdapter(adapter);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentResults.clear();
                String query = s.toString().trim();
                if (!query.isEmpty()) {
                    for (AppInfo app : allAppsCache) {
                        if (app.name.toLowerCase().contains(query.toLowerCase())) {
                            currentResults.add(new SearchItem(SearchItem.TYPE_APP, app.name, "App", app.packageName));
                        }
                    }
                    if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        searchContacts(query, currentResults);
                    }
                    currentResults.add(new SearchItem(SearchItem.TYPE_WEB, "Search Web", "Google Search: " + query, query));
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            SearchItem item = adapter.getItem(position);
            if (item != null) {
                provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (item.type == SearchItem.TYPE_APP) {
                    launchApp(item.data, item.title);
                } else if (item.type == SearchItem.TYPE_CONTACT) {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                    dialIntent.setData(Uri.parse("tel:" + item.data));
                    dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(dialIntent);
                } else if (item.type == SearchItem.TYPE_WEB) {
                    openWebSearch(item.data);
                }
                dialog.dismiss();
            }
        });

        dialog.show();
        searchBox.requestFocus();
    }

    @SuppressLint("Range")
    private void searchContacts(String query, List<SearchItem> results) {
        try {
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
            String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
            String[] selectionArgs = {"%" + query + "%"};
            Cursor cursor = getContentResolver().query(uri, projection, selection, selectionArgs, null);

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < 5) {
                    String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    String number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    results.add(new SearchItem(SearchItem.TYPE_CONTACT, name, "Contact: " + number, number));
                    count++;
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error searching contacts", e);
        }
    }

    private void openWebSearch(String query) {
        try {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(android.app.SearchManager.QUERY, query);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            openGoogleFeed();
        }
    }

    private void openGoogleFeed() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.android.googlequicksearchbox");
            if (intent != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
    }

    private String getBaseKeyForPage(int pageNum, String cellKey, String folderName) {
        String pagePrefix = (pageNum == 1) ? cellKey : ("p" + pageNum + "_" + cellKey);
        if (folderName.isEmpty()) return pagePrefix;
        else return "folder_" + folderName + "_" + pagePrefix;
    }

    private String getBaseKey(String cellKey) {
        return getBaseKeyForPage(currentPage, cellKey, currentFolder);
    }

    private void updateTopBar() {
        TextView titleText = findViewById(R.id.gridTitleText);
        Button settingsBtn = findViewById(R.id.settingsButton);
        Button backBtn = findViewById(R.id.backButton);
        Button allAppsBtn = findViewById(R.id.allAppsButton);

        boolean isMl = isCurrentLanguageMalayalam();

        if (currentFolder.isEmpty()) {
            if (titleText != null) {
                titleText.setText("");
                titleText.setVisibility(View.GONE);
                titleText.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            if (allAppsBtn != null) {
                allAppsBtn.setVisibility(View.VISIBLE);
                allAppsBtn.setText(isMl ? "എല്ലാ ആപ്പുകളും" : "All Apps");
                allAppsBtn.setTag("all_apps");
            }
            if (settingsBtn != null) {
                settingsBtn.setVisibility(View.VISIBLE);
                settingsBtn.setText(isMl ? "സെറ്റിങ്‌സ്" : "Settings");
                settingsBtn.setTag("settings");
            }
            if (backBtn != null) {
                backBtn.setVisibility(View.GONE);
                backBtn.setTag("back_button");
            }
        } else {
            if (titleText != null) {
                titleText.setVisibility(View.VISIBLE);
                titleText.setText(currentFolder);
                titleText.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                titleText.setContentDescription(currentFolder + (isMl ? " ഫോൾഡർ" : " Folder"));
            }
            if (allAppsBtn != null) {
                allAppsBtn.setVisibility(View.GONE);
            }
            if (settingsBtn != null) {
                settingsBtn.setVisibility(View.GONE);
            }
            if (backBtn != null) {
                backBtn.setVisibility(View.VISIBLE);
                backBtn.setText(isMl ? "തിരികെ" : "Back");
                backBtn.setTag("back_button");
            }
        }
    }

    private void applyCurrentTheme() {
        if (prefs == null) return;
        ThemeManager.ActiveTheme theme = ThemeManager.getActiveTheme(prefs);
        View root = findViewById(R.id.mainRootLayout);
        if (root != null) {
            root.setBackgroundColor(theme.bgColor);
        }

        ThemeManager.applySystemBarColors(this, theme.bgColor, theme.isLight);

        Button allAppsBtn = findViewById(R.id.allAppsButton);
        if (allAppsBtn != null) {
            allAppsBtn.setBackgroundTintList(ColorStateList.valueOf(theme.dockColor));
            allAppsBtn.setTextColor(theme.dockTextColor);
            Drawable allAppsIcon = ContextCompat.getDrawable(this, R.drawable.ic_all_apps);
            if (allAppsIcon != null) {
                allAppsIcon = allAppsIcon.mutate();
                allAppsIcon.setTint(theme.dockTextColor);
                int sz = dpToPx(20);
                allAppsIcon.setBounds(0, 0, sz, sz);
                allAppsBtn.setCompoundDrawables(allAppsIcon, null, null, null);
                allAppsBtn.setCompoundDrawablePadding(dpToPx(8));
            }
        }

        Button settingsBtn = findViewById(R.id.settingsButton);
        if (settingsBtn != null) {
            settingsBtn.setBackgroundTintList(ColorStateList.valueOf(theme.topBarBtnColor));
            settingsBtn.setTextColor(theme.topBarBtnTextColor);
        }

        Button backBtn = findViewById(R.id.backButton);
        if (backBtn != null) {
            backBtn.setBackgroundTintList(ColorStateList.valueOf(theme.topBarBtnColor));
            backBtn.setTextColor(theme.topBarBtnTextColor);
        }

        TextView titleText = findViewById(R.id.gridTitleText);
        if (titleText != null) {
            titleText.setTextColor(theme.textColor);
        }
    }

    private void announceTimeAndBattery() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
        String timeStr = timeFormat.format(new Date());
        String dateStr = dateFormat.format(new Date());

        int batteryLevel = -1;
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, intentFilter);
        if (batteryStatus != null) {
            batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        }

        String announcement = "Time is " + timeStr + ", " + dateStr + ". Battery level " + batteryLevel + " percent.";
        Toast.makeText(this, timeStr + " | Battery: " + batteryLevel + "%", Toast.LENGTH_SHORT).show();
        announceAccessibility(announcement);
    }

    private void buildGrid() {
        updateTopBar();
        applyCurrentTheme();
        mainGridLayout.removeAllViews();

        int align = prefs.getInt("grid_alignment", 0);
        if (align == 0) {
            mainGridLayout.setGravity(android.view.Gravity.TOP);
        } else if (align == 1) {
            mainGridLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        } else {
            mainGridLayout.setGravity(android.view.Gravity.BOTTOM);
        }

        String pageType = prefs.getString("page_" + currentPage + "_type", "grid");
        if (pageType.equals("widget")) {
            buildWidgetPage();
        } else {
            buildGridPage();
        }
        buildDock();
        restoreLastFocusedView();
    }

    private void buildWidgetPage() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollParams);

        LinearLayout widgetContainer = new LinearLayout(this);
        widgetContainer.setOrientation(LinearLayout.VERTICAL);
        widgetContainer.setPadding(16, 16, 16, 16);
        scrollView.addView(widgetContainer);

        // Header action buttons for Flat Screen
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.setMargins(0, 0, 0, dpToPx(16));
        headerBar.setLayoutParams(headerParams);

        Button addWidgetBtn = new Button(this);
        addWidgetBtn.setText("+ Add Widget");
        addWidgetBtn.setContentDescription("Add Widget to this flat screen");
        GradientDrawable addBg = new GradientDrawable();
        addBg.setShape(GradientDrawable.RECTANGLE);
        addBg.setCornerRadius(dpToPx(12));
        addBg.setColor(0xFF2E7D32);
        addWidgetBtn.setBackground(addBg);
        addWidgetBtn.setTextColor(Color.WHITE);
        addWidgetBtn.setAllCaps(false);
        addWidgetBtn.setTextSize(14f);
        addWidgetBtn.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        addParams.setMargins(0, 0, totalPages > 1 ? dpToPx(8) : 0, 0);
        addWidgetBtn.setLayoutParams(addParams);
        addWidgetBtn.setOnClickListener(v -> {
            provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showAllWidgetsPicker();
        });
        headerBar.addView(addWidgetBtn);

        if (totalPages > 1) {
            Button deletePageBtn = new Button(this);
            deletePageBtn.setText("Delete Flat Screen");
            deletePageBtn.setContentDescription("Delete this flat screen");
            GradientDrawable delBg = new GradientDrawable();
            delBg.setShape(GradientDrawable.RECTANGLE);
            delBg.setCornerRadius(dpToPx(12));
            delBg.setColor(0xFFC62828);
            deletePageBtn.setBackground(delBg);
            deletePageBtn.setTextColor(Color.WHITE);
            deletePageBtn.setAllCaps(false);
            deletePageBtn.setTextSize(14f);
            deletePageBtn.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            delParams.setMargins(dpToPx(8), 0, 0, 0);
            deletePageBtn.setLayoutParams(delParams);
            deletePageBtn.setOnClickListener(v -> {
                provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                deleteCurrentPage();
            });
            headerBar.addView(deletePageBtn);
        }
        widgetContainer.addView(headerBar);

        String savedWidgets = prefs.getString("page_" + currentPage + "_widgets", "");
        if (!savedWidgets.isEmpty()) {
            String[] ids = savedWidgets.split(",");
            StringBuilder validIds = new StringBuilder();
            boolean deadIdsFound = false;

            for (String idStr : ids) {
                if (idStr.isEmpty()) continue;
                int id;
                try {
                    id = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    deadIdsFound = true;
                    continue;
                }

                AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(id);
                if (info != null) {
                    if (validIds.length() > 0) validIds.append(",");
                    validIds.append(id);

                    LinearLayout wrapper = new LinearLayout(this);
                    wrapper.setOrientation(LinearLayout.VERTICAL);
                    wrapper.setPadding(0, 0, 0, 64);

                    Button removeBtn = new Button(this);
                    removeBtn.setText("Remove Widget: " + info.loadLabel(getPackageManager()));
                    removeBtn.setContentDescription("Remove Widget: " + info.loadLabel(getPackageManager()));

                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(dpToPx(12));
                    bg.setColor(0xFF333333);
                    removeBtn.setBackground(bg);
                    removeBtn.setTextColor(Color.WHITE);
                    removeBtn.setAllCaps(false);

                    removeBtn.setOnClickListener(v -> removeWidgetFromPage(id));
                    wrapper.addView(removeBtn);

                    try {
                        DisplayMetrics dm = getResources().getDisplayMetrics();
                        int screenWidthDp = (int) (dm.widthPixels / dm.density);
                        int defaultWidthDp = Math.max(screenWidthDp - 32, 200);
                        int defaultHeightDp = Math.max(info.minHeight > 0 ? info.minHeight : 240, 320);

                        Bundle options = createWidgetOptions(info, defaultWidthDp, defaultHeightDp);
                        try {
                            appWidgetManager.updateAppWidgetOptions(id, options);
                            appWidgetManager.notifyAppWidgetViewDataChanged(id, android.R.id.list);
                        } catch (Exception ignored) {}

                        AppWidgetHostView hostView = widgetViewCache.get(id);
                        if (hostView == null) {
                            hostView = appWidgetHost.createView(widgetHostContext, id, info);
                            widgetViewCache.put(id, hostView);
                        } else {
                            ViewGroup parent = (ViewGroup) hostView.getParent();
                            if (parent != null) {
                                parent.removeView(hostView);
                            }
                        }

                        try {
                            hostView.updateAppWidgetSize(options, defaultWidthDp, defaultHeightDp, defaultWidthDp, defaultHeightDp);
                        } catch (Exception ignored) {}

                        int minHeightPx = dpToPx(defaultHeightDp);

                        LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                minHeightPx
                        );
                        viewParams.setMargins(0, dpToPx(8), 0, dpToPx(8));
                        hostView.setLayoutParams(viewParams);

                        wrapper.addView(hostView);
                    } catch (Exception e) {
                        TextView errorText = new TextView(this);
                        errorText.setText("Widget rendering error.");
                        errorText.setTextColor(Color.WHITE);
                        wrapper.addView(errorText);
                    }

                    widgetContainer.addView(wrapper);
                } else {
                    deadIdsFound = true;
                    AppWidgetHostView deadView = widgetViewCache.remove(id);
                    if (deadView != null && deadView.getParent() != null) {
                        ((ViewGroup) deadView.getParent()).removeView(deadView);
                    }
                    try {
                        appWidgetHost.deleteAppWidgetId(id);
                    } catch (Exception ignored) {}
                }
            }

            if (deadIdsFound) {
                prefs.edit().putString("page_" + currentPage + "_widgets", validIds.toString()).apply();
            }
        } else {
            TextView emptyText = new TextView(this);
            emptyText.setText("This flat screen is empty.\nTap '+ Add Widget' above to add widgets.");
            emptyText.setPadding(32, 48, 32, 32);
            emptyText.setTextSize(16f);
            emptyText.setTextColor(0xFFAAAAAA);
            emptyText.setGravity(android.view.Gravity.CENTER);
            widgetContainer.addView(emptyText);
        }

        mainGridLayout.addView(scrollView);
    }

    private void buildGridPage() {
        int maxRows = prefs.getInt("grid_rows", 10);
        int maxCols = prefs.getInt("grid_cols", 4);
        int align = prefs.getInt("grid_alignment", 0);

        if (align == 1 || align == 2) {
            View topSpacer = new View(this);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
            topSpacer.setLayoutParams(spacerParams);
            mainGridLayout.addView(topSpacer);
        }

        for (int row = 1; row <= maxRows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowLayout.setLayoutParams(rowParams);

            for (int col = 1; col <= maxCols; col++) {
                String cellKey = row + "_" + col;

                Button cellButton = new Button(this);
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                btnParams.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                cellButton.setLayoutParams(btnParams);
                cellButton.setMinimumHeight(dpToPx(80));

                String fullBaseKey = getBaseKey(cellKey);
                cellButton.setTag(fullBaseKey);
                updateCellButton(cellButton, fullBaseKey, false, row, col);

                cellButton.setOnClickListener(v -> {
                    setLastInteractedKey(fullBaseKey);
                    provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    handleCellClick(fullBaseKey, false);
                });

                cellButton.setOnLongClickListener(v -> {
                    setLastInteractedKey(fullBaseKey);
                    provideHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    handleCellLongClick(fullBaseKey);
                    return true;
                });

                rowLayout.addView(cellButton);
            }
            mainGridLayout.addView(rowLayout);
        }

        if (align == 1) {
            View bottomSpacer = new View(this);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
            bottomSpacer.setLayoutParams(spacerParams);
            mainGridLayout.addView(bottomSpacer);
        }
    }

    private void buildDock() {
        for (int i = 0; i < 5; i++) {
            String dockKey = "dock_" + i;
            Button btn = dockButtons[i];

            LinearLayout.LayoutParams btnParams = (LinearLayout.LayoutParams) btn.getLayoutParams();
            btnParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            btnParams.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
            btn.setLayoutParams(btnParams);
            btn.setMinimumHeight(dpToPx(80));

            btn.setTag(dockKey);

            btn.setOnClickListener(v -> {
                setLastInteractedKey(dockKey);
                provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                handleCellClick(dockKey, true);
            });

            btn.setOnLongClickListener(v -> {
                setLastInteractedKey(dockKey);
                provideHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                handleCellLongClick(dockKey);
                return true;
            });

            updateCellButton(btn, dockKey, true, -1, i + 1);
        }
    }

    private Bundle createWidgetOptions(AppWidgetProviderInfo info, int widthDp, int heightDp) {
        Bundle options = new Bundle();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidthDp = (int) (dm.widthPixels / dm.density);
        int screenHeightDp = (int) (dm.heightPixels / dm.density);

        int allocatedWidthDp = Math.max(widthDp, 200);
        int allocatedHeightDp = Math.max(heightDp, 200);

        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, allocatedWidthDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, allocatedHeightDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, Math.max(allocatedWidthDp, screenWidthDp));
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, Math.max(allocatedHeightDp, screenHeightDp));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ArrayList<SizeF> sizes = new ArrayList<>();
            sizes.add(new SizeF(allocatedWidthDp, allocatedHeightDp));
            options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes);
        }
        return options;
    }

    private void initiateWidgetAdd(AppWidgetProviderInfo info) {
        pendingWidgetId = appWidgetHost.allocateAppWidgetId();
        pendingWidgetInfo = info;
        prefs.edit().putInt("pending_widget_id", pendingWidgetId).apply();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidthDp = (int) (dm.widthPixels / dm.density);
        int defaultWidthDp = Math.max(screenWidthDp - 32, 200);
        int defaultHeightDp = Math.max(info != null && info.minHeight > 0 ? info.minHeight : 240, 320);

        Bundle options = createWidgetOptions(info, defaultWidthDp, defaultHeightDp);

        boolean allowed;
        try {
            allowed = appWidgetManager.bindAppWidgetIdIfAllowed(pendingWidgetId, info.getProfile(), info.provider, options);
        } catch (Exception e) {
            allowed = false;
        }

        if (allowed) {
            checkWidgetConfiguration();
        } else {
            try {
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile());
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
                bindWidgetLauncher.launch(intent);
            } catch (Exception e) {
                cancelWidgetAdd();
                Toast.makeText(this, "Binding not supported on this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkWidgetConfiguration() {
        if (pendingWidgetId == -1) {
            pendingWidgetId = prefs.getInt("pending_widget_id", -1);
        }
        if (pendingWidgetInfo == null && pendingWidgetId != -1) {
            pendingWidgetInfo = appWidgetManager.getAppWidgetInfo(pendingWidgetId);
        }

        if (pendingWidgetInfo != null && pendingWidgetInfo.configure != null) {
            try {
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                intent.setComponent(pendingWidgetInfo.configure);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
                configureWidgetLauncher.launch(intent);
            } catch (Exception e) {
                finalizeWidgetAdd();
            }
        } else {
            finalizeWidgetAdd();
        }
    }

    private void finalizeWidgetAdd() {
        if (pendingWidgetId == -1) {
            pendingWidgetId = prefs.getInt("pending_widget_id", -1);
        }

        if (pendingWidgetId != -1) {
            if (pendingWidgetInfo == null) {
                pendingWidgetInfo = appWidgetManager.getAppWidgetInfo(pendingWidgetId);
            }

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenWidthDp = (int) (dm.widthPixels / dm.density);
            int defaultWidthDp = Math.max(screenWidthDp - 32, 200);
            int defaultHeightDp = Math.max(pendingWidgetInfo != null && pendingWidgetInfo.minHeight > 0 ? pendingWidgetInfo.minHeight : 240, 320);
            Bundle options = createWidgetOptions(pendingWidgetInfo, defaultWidthDp, defaultHeightDp);
            try {
                appWidgetManager.updateAppWidgetOptions(pendingWidgetId, options);
                appWidgetManager.notifyAppWidgetViewDataChanged(pendingWidgetId, android.R.id.list);
            } catch (Exception ignored) {}

            try {
                if (pendingWidgetInfo != null && pendingWidgetInfo.provider != null) {
                    String pkg = pendingWidgetInfo.provider.getPackageName();
                    Intent updateBroadcast = new Intent(pkg + ".BOOKMARK_APPWIDGET_UPDATE");
                    updateBroadcast.setPackage(pkg);
                    updateBroadcast.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
                    sendBroadcast(updateBroadcast);
                }
            } catch (Exception ignored) {}

            int targetPage = findOrCreateWidgetPage();
            String key = "page_" + targetPage + "_widgets";
            String existing = prefs.getString(key, "");
            if (existing.isEmpty()) {
                existing = String.valueOf(pendingWidgetId);
            } else {
                existing += "," + pendingWidgetId;
            }

            prefs.edit()
                    .putString(key, existing)
                    .putInt("pending_widget_id", -1)
                    .apply();

            pendingWidgetId = -1;
            pendingWidgetInfo = null;

            currentPage = targetPage;
            buildGrid();
            announceAccessibility("Widget added successfully");
            Toast.makeText(this, "Widget Added", Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelWidgetAdd() {
        if (pendingWidgetId == -1) {
            pendingWidgetId = prefs.getInt("pending_widget_id", -1);
        }

        if (pendingWidgetId != -1) {
            AppWidgetHostView removedView = widgetViewCache.remove(pendingWidgetId);
            if (removedView != null && removedView.getParent() != null) {
                ((ViewGroup) removedView.getParent()).removeView(removedView);
            }
            appWidgetHost.deleteAppWidgetId(pendingWidgetId);
            prefs.edit().putInt("pending_widget_id", -1).apply();
            pendingWidgetId = -1;
            pendingWidgetInfo = null;
        }
    }

    private int findOrCreateWidgetPage() {
        String currType = prefs.getString("page_" + currentPage + "_type", "grid");
        if ("widget".equals(currType)) {
            return currentPage;
        }
        for (int i = 1; i <= totalPages; i++) {
            if ("widget".equals(prefs.getString("page_" + i + "_type", "grid"))) {
                return i;
            }
        }
        totalPages++;
        prefs.edit()
                .putInt("total_pages", totalPages)
                .putString("page_" + totalPages + "_type", "widget")
                .apply();
        return totalPages;
    }

    private void removeWidgetFromPage(int widgetId) {
        String key = "page_" + currentPage + "_widgets";
        String existing = prefs.getString(key, "");
        if (!existing.isEmpty()) {
            String[] ids = existing.split(",");
            StringBuilder newIds = new StringBuilder();
            for (String idStr : ids) {
                if (!idStr.isEmpty() && Integer.parseInt(idStr) != widgetId) {
                    if (newIds.length() > 0) newIds.append(",");
                    newIds.append(idStr);
                }
            }
            prefs.edit().putString(key, newIds.toString()).apply();
            AppWidgetHostView removedView = widgetViewCache.remove(widgetId);
            if (removedView != null && removedView.getParent() != null) {
                ((ViewGroup) removedView.getParent()).removeView(removedView);
            }
            appWidgetHost.deleteAppWidgetId(widgetId);
            buildGrid();
            announceAccessibility("Widget removed");
        }
    }

    private List<AppWidgetProviderInfo> getWidgetsForPackage(String pkgName) {
        List<AppWidgetProviderInfo> result = new ArrayList<>();
        if (appWidgetManager == null || pkgName == null || pkgName.isEmpty()) return result;
        try {
            List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProvidersForPackage(pkgName, android.os.Process.myUserHandle());
            if (providers != null && !providers.isEmpty()) {
                return providers;
            }
        } catch (Exception ignored) {}

        try {
            List<AppWidgetProviderInfo> allProviders = appWidgetManager.getInstalledProviders();
            if (allProviders != null) {
                for (AppWidgetProviderInfo info : allProviders) {
                    if (info != null && info.provider != null && pkgName.equals(info.provider.getPackageName())) {
                        result.add(info);
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void showAllWidgetsPicker() {
        if (appWidgetManager == null) return;
        List<AppWidgetProviderInfo> allProviders;
        try {
            allProviders = appWidgetManager.getInstalledProviders();
        } catch (Exception e) {
            allProviders = Collections.emptyList();
        }

        if (allProviders == null || allProviders.isEmpty()) {
            Toast.makeText(this, "No widgets available on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        PackageManager pm = getPackageManager();
        Map<String, List<AppWidgetProviderInfo>> widgetMap = new HashMap<>();
        Map<String, String> appNameMap = new HashMap<>();

        for (AppWidgetProviderInfo info : allProviders) {
            if (info != null && info.provider != null) {
                String pkg = info.provider.getPackageName();
                List<AppWidgetProviderInfo> list = widgetMap.computeIfAbsent(pkg, k -> new ArrayList<>());
                list.add(info);
                if (!appNameMap.containsKey(pkg)) {
                    try {
                        appNameMap.put(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString());
                    } catch (Exception e) {
                        appNameMap.put(pkg, pkg);
                    }
                }
            }
        }

        List<String> pkgsWithWidgets = new ArrayList<>(widgetMap.keySet());
        pkgsWithWidgets.sort((p1, p2) -> appNameMap.get(p1).compareToIgnoreCase(appNameMap.get(p2)));

        String[] displayNames = new String[pkgsWithWidgets.size()];
        for (int i = 0; i < pkgsWithWidgets.size(); i++) {
            String p = pkgsWithWidgets.get(i);
            displayNames[i] = appNameMap.get(p) + " (" + widgetMap.get(p).size() + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Select App for Widget")
                .setItems(displayNames, (dialog, which) -> {
                    String selectedPkg = pkgsWithWidgets.get(which);
                    showWidgetsDialog(selectedPkg);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showWidgetsDialog(String pkgName) {
        List<AppWidgetProviderInfo> widgets = getWidgetsForPackage(pkgName);
        if (widgets == null || widgets.isEmpty()) {
            Toast.makeText(this, "No widgets available", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[widgets.size()];
        for (int i = 0; i < widgets.size(); i++) {
            names[i] = widgets.get(i).loadLabel(getPackageManager());
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Widget")
                .setItems(names, (dialog, which) -> initiateWidgetAdd(widgets.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addToFirstEmptyCell(String pkg, String name) {
        int maxRows = prefs.getInt("grid_rows", 10);
        int maxCols = prefs.getInt("grid_cols", 4);

        boolean targetPageFound = false;
        if (!prefs.getString("page_" + currentPage + "_type", "grid").equals("grid")) {
            for (int p = 1; p <= totalPages; p++) {
                if (prefs.getString("page_" + p + "_type", "grid").equals("grid")) {
                    currentPage = p;
                    targetPageFound = true;
                    break;
                }
            }
            if (!targetPageFound) {
                totalPages++;
                prefs.edit().putInt("total_pages", totalPages).putString("page_" + totalPages + "_type", "grid").apply();
                currentPage = totalPages;
            }
        }

        for (int r = 1; r <= maxRows; r++) {
            for (int c = 1; c <= maxCols; c++) {
                String fullBaseKey = getBaseKey(r + "_" + c);
                if (prefs.getString(fullBaseKey + "_pkg", "").isEmpty()) {
                    prefs.edit().putString(fullBaseKey + "_name", name).putString(fullBaseKey + "_pkg", pkg).apply();
                    buildGrid();
                    Toast.makeText(this, "Added to Page " + currentPage, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        Toast.makeText(this, "No space on this page", Toast.LENGTH_LONG).show();
    }

    private void promptAddMethod(String pkg, String name) {
        movingCellKey = null;
        String[] options = {"Add Automatically", "Place Manually"};
        new AlertDialog.Builder(this)
                .setTitle("Options for " + name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        addToFirstEmptyCell(pkg, name);
                    } else {
                        pendingAddPkg = pkg;
                        pendingAddName = name;
                        Toast.makeText(this, "Double tap an empty cell", Toast.LENGTH_LONG).show();
                        announceAccessibility("Navigate and double tap an empty cell to place");
                        buildGrid();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleCellClick(String fullBaseKey, boolean isDock) {
        setLastInteractedKey(fullBaseKey);
        if (pendingAddPkg != null) {
            String savedPkg = prefs.getString(fullBaseKey + "_pkg", "");
            if (savedPkg.isEmpty()) {
                prefs.edit().putString(fullBaseKey + "_name", pendingAddName).putString(fullBaseKey + "_pkg", pendingAddPkg).apply();
                pendingAddPkg = null;
                pendingAddName = null;
                Toast.makeText(this, "Placed", Toast.LENGTH_SHORT).show();
                announceAccessibility("Placed successfully");
            } else if (savedPkg.startsWith("folder:")) {
                parentFolderCellKey = fullBaseKey;
                currentFolder = savedPkg.substring(7);
                announceAccessibility("Folder opened. Select an empty cell to place.");
            } else {
                Toast.makeText(this, "Cell is occupied", Toast.LENGTH_SHORT).show();
                announceAccessibility("Cell is occupied");
            }
            buildGrid();
            return;
        }

        if (movingCellKey != null) {
            if (movingCellKey.equals(fullBaseKey)) {
                movingCellKey = null;
                Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
            } else {
                String targetPkg = prefs.getString(fullBaseKey + "_pkg", "");
                String movingPkg = prefs.getString(movingCellKey + "_pkg", "");

                if (targetPkg.startsWith("folder:") && !movingPkg.startsWith("folder:")) {
                    parentFolderCellKey = fullBaseKey;
                    currentFolder = targetPkg.substring(7);
                    announceAccessibility("Folder opened. Select an empty cell to move here.");
                } else {
                    swapCells(movingCellKey, fullBaseKey);
                    movingCellKey = null;
                    Toast.makeText(this, "Moved", Toast.LENGTH_SHORT).show();
                    announceAccessibility("Position Changed");
                }
            }
            buildGrid();
            return;
        }

        String savedPkg = prefs.getString(fullBaseKey + "_pkg", "");
        String savedName = prefs.getString(fullBaseKey + "_name", "");

        if (savedPkg.isEmpty()) {
            String dtType = prefs.getString("gesture_double_tap_2_type", null);
            if (dtType == null || !"none".equals(dtType)) {
                executeGestureAction("double_tap_2");
            } else {
                showAddTypeMenu(fullBaseKey, isDock);
            }
        } else {
            if (savedPkg.startsWith("folder:")) {
                parentFolderCellKey = fullBaseKey;
            }
            launchSavedItem(savedPkg, savedName);
        }
    }

    private void handleCellLongClick(String fullBaseKey) {
        if (pendingAddPkg != null) {
            pendingAddPkg = null;
            pendingAddName = null;
            Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
            announceAccessibility("Placement cancelled");
            buildGrid();
            return;
        }

        if (movingCellKey == null) {
            String savedPkg = prefs.getString(fullBaseKey + "_pkg", "");
            if (!savedPkg.isEmpty()) {
                showAppOptionsMenu(fullBaseKey, savedPkg, prefs.getString(fullBaseKey + "_name", ""));
            } else {
                showAddTypeMenu(fullBaseKey, fullBaseKey.startsWith("dock_"));
            }
        }
    }

    private boolean isInstalledFromPlayStore(String pkgName) {
        try {
            PackageManager pm = getPackageManager();
            String installer = pm.getInstallSourceInfo(pkgName).getInstallingPackageName();
            return "com.android.vending".equals(installer);
        } catch (Exception e) {
            return false;
        }
    }

    private void askCreateNewPage() {
        String[] types = {"Grid Page (Apps)", "Widget Page (Flat View)"};
        new AlertDialog.Builder(this)
                .setTitle("New Page Type?")
                .setItems(types, (dialog, which) -> {
                    totalPages++;
                    prefs.edit()
                            .putInt("total_pages", totalPages)
                            .putString("page_" + totalPages + "_type", which == 0 ? "grid" : "widget")
                            .apply();
                    currentPage = totalPages;
                    buildGrid();
                    announceAccessibility(which == 0 ? "New Grid Page created" : "New Widget Page created");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCurrentPage() {
        deleteCurrentPage(null);
    }

    private void deleteCurrentPage(Runnable onCancel) {
        if (totalPages <= 1) {
            Toast.makeText(this, "Cannot delete", Toast.LENGTH_SHORT).show();
            if (onCancel != null) onCancel.run();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete Page?")
                .setPositiveButton("Yes", (d, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    int maxRows = prefs.getInt("grid_rows", 10);
                    int maxCols = prefs.getInt("grid_cols", 4);

                    String currType = prefs.getString("page_" + currentPage + "_type", "grid");
                    if (currType.equals("widget")) {
                        String savedWidgets = prefs.getString("page_" + currentPage + "_widgets", "");
                        if (!savedWidgets.isEmpty()) {
                            String[] ids = savedWidgets.split(",");
                            for (String idStr : ids) {
                                if (!idStr.isEmpty()) {
                                    try {
                                        int wId = Integer.parseInt(idStr);
                                        AppWidgetHostView removedView = widgetViewCache.remove(wId);
                                        if (removedView != null && removedView.getParent() != null) {
                                            ((ViewGroup) removedView.getParent()).removeView(removedView);
                                        }
                                        appWidgetHost.deleteAppWidgetId(wId);
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }

                    for (int p = currentPage; p <= totalPages; p++) {
                        String nextType = prefs.getString("page_" + (p + 1) + "_type", "grid");
                        editor.putString("page_" + p + "_type", p == totalPages ? "grid" : nextType);

                        if (p == totalPages) {
                            editor.remove("page_" + p + "_widgets");
                        } else {
                            String nextWidgets = prefs.getString("page_" + (p + 1) + "_widgets", "");
                            editor.putString("page_" + p + "_widgets", nextWidgets);
                        }

                        for (int r = 1; r <= maxRows; r++) {
                            for (int c = 1; c <= maxCols; c++) {
                                String currBase = getBaseKeyForPage(p, r + "_" + c, "");
                                String nextBase = getBaseKeyForPage(p + 1, r + "_" + c, "");
                                if (p == totalPages) editor.remove(currBase + "_pkg").remove(currBase + "_name");
                                else {
                                    String nextPkg = prefs.getString(nextBase + "_pkg", "");
                                    String nextName = prefs.getString(nextBase + "_name", "");
                                    if (nextPkg.isEmpty()) editor.remove(currBase + "_pkg").remove(currBase + "_name");
                                    else editor.putString(currBase + "_pkg", nextPkg).putString(currBase + "_name", nextName);
                                }
                            }
                        }
                    }
                    totalPages--;
                    editor.putInt("total_pages", totalPages).apply();
                    if (currentPage > totalPages) currentPage = totalPages;
                    buildGrid();
                    announceAccessibility("Page deleted");
                })
                .setNegativeButton("No", (d, which) -> d.cancel())
                .create();
        if (onCancel != null) {
            attachBackHandler(dialog, onCancel);
        }
        dialog.show();
    }

    @SuppressLint("UnsafeIntentLaunch")
    private void launchSavedItem(String savedPkg, String savedName) {
        try {
            if (savedPkg.startsWith("folder:")) {
                currentFolder = savedPkg.substring(7);
                buildGrid();
                announceAccessibility("Folder opened");
            } else if (savedPkg.startsWith("tel:")) {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(savedPkg)));
            } else if (savedPkg.startsWith("url:")) {
                String url = savedPkg.substring(4);
                if (!url.startsWith("http")) url = "https://" + url;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } else if (savedPkg.startsWith("intent:tether")) {
                try {
                    Intent tetherIntent = new Intent();
                    tetherIntent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
                    startActivity(tetherIntent);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                }
            } else if (savedPkg.startsWith("sys:")) {
                startActivity(new Intent(savedPkg.substring(4)));
            } else if (savedPkg.startsWith("shortcut:")) {
                String[] parts = savedPkg.split(":", 3);
                if (parts.length == 3) {
                    try {
                        LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
                        launcherApps.startShortcut(parts[1], parts[2], null, null, android.os.Process.myUserHandle());
                    } catch (Exception e) {
                        Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (savedPkg.startsWith("media:")) {
                String action = savedPkg.substring(6);
                executeMediaAction(action);
            } else if (savedPkg.startsWith("legacy_shortcut:")) {
                try {
                    String uri = savedPkg.substring(16);
                    Intent intent = Intent.parseUri(uri, 0);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
                }
            } else {
                launchApp(savedPkg, savedName);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
    }

    private void executeMediaAction(String action) {
        if ("audio_volume".equals(action) || "audio_routing".equals(action) ||
                "audio_routing_toggle".equals(action) || "media_mute_toggle".equals(action) ||
                "acc_vol_up".equals(action) || "acc_vol_down".equals(action)) {
            AudioRoutingManager.getInstance().toggleAudioSeparation(this);
            buildGrid();
            return;
        }
        int keyCode;
        String announcement;
        switch (action) {
            case "play_pause":
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
                announcement = "Play Pause";
                break;
            case "next":
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT;
                announcement = "Next Track";
                break;
            case "previous":
                keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                announcement = "Previous Track";
                break;
            case "fast_forward":
                keyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
                announcement = "Fast Forward";
                break;
            case "rewind":
                keyCode = KeyEvent.KEYCODE_MEDIA_REWIND;
                announcement = "Rewind";
                break;
            default:
                return;
        }
        sendMediaKeyEvent(keyCode);
        Toast.makeText(this, announcement, Toast.LENGTH_SHORT).show();
        announceAccessibility(announcement);
    }

    private void sendMediaKeyEvent(int keyCode) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long eventTime = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0));
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error dispatching media key", e);
        }
    }

    private boolean isPhoneAppConfig(String pkgName, String appName) {
        String lowerName = appName != null ? appName.toLowerCase() : "";
        boolean isPhone = lowerName.contains("phone") || lowerName.contains("dial") || lowerName.contains("call") ||
                pkgName.toLowerCase().contains("dialer") || pkgName.contains("com.truecaller") || pkgName.contains("telecom");

        if (pkgName.equals("com.android.contacts") || pkgName.equals("com.vivo.contacts")) {
            if (appName != null && !lowerName.contains("contact") && !lowerName.contains("people")) {
                isPhone = true;
            }
        }
        return isPhone;
    }

    @SuppressLint("UnsafeIntentLaunch")
    private void launchApp(String pkgName, String appName) {
        launchApp(pkgName, appName, null);
    }

    @SuppressLint("UnsafeIntentLaunch")
    private void launchApp(String pkgName, String appName, @Nullable String activityName) {
        if (pkgName == null || pkgName.isEmpty()) return;
        int count = prefs.getInt("usage_" + pkgName, 0);
        prefs.edit().putInt("usage_" + pkgName, count + 1).apply();

        if (isPhoneAppConfig(pkgName, appName)) {
            try {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                dialIntent.setData(Uri.parse("tel:"));
                dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(dialIntent);
                return;
            } catch (Exception ignored) {
            }
        }

        Intent launchIntent = null;
        if (activityName != null && !activityName.isEmpty()) {
            try {
                Intent explicitIntent = new Intent(Intent.ACTION_MAIN);
                explicitIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                explicitIntent.setClassName(pkgName, activityName);
                explicitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (getPackageManager().resolveActivity(explicitIntent, 0) != null) {
                    launchIntent = explicitIntent;
                }
            } catch (Exception ignored) {}
        }

        if (launchIntent == null) {
            try {
                launchIntent = getPackageManager().getLaunchIntentForPackage(pkgName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                }
            } catch (Exception ignored) {}
        }

        if (launchIntent != null) {
            try {
                startActivity(launchIntent);
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to start activity for " + pkgName, e);
                Toast.makeText(this, (isCurrentLanguageMalayalam() ? "തുറക്കാൻ സാധിച്ചില്ല: " : "Could not open ") + (appName != null ? appName : "app"), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, (isCurrentLanguageMalayalam() ? "ആപ്പ് കണ്ടെത്തിയില്ല: " : "App not found: ") + (appName != null ? appName : pkgName), Toast.LENGTH_SHORT).show();
        }
    }

    private void setLastInteractedKey(String key) {
        if (key == null || key.isEmpty()) return;
        this.lastInteractedKey = key;
        prefs.edit().putString("last_interacted_key", key).apply();
    }

    private View findViewForKey(String key) {
        if (key == null || key.isEmpty()) return null;
        if (key.startsWith("dock_")) {
            try {
                int idx = Integer.parseInt(key.substring(5));
                if (idx >= 0 && idx < dockButtons.length) {
                    return dockButtons[idx];
                }
            } catch (Exception ignored) {}
        }
        if ("all_apps".equals(key)) {
            return findViewById(R.id.allAppsButton);
        }
        if ("settings".equals(key)) {
            return findViewById(R.id.settingsButton);
        }
        if ("back_button".equals(key)) {
            return findViewById(R.id.backButton);
        }
        if (mainGridLayout != null) {
            View v = mainGridLayout.findViewWithTag(key);
            if (v != null) return v;
        }
        View decor = getWindow() != null ? getWindow().getDecorView() : null;
        if (decor != null) {
            return decor.findViewWithTag(key);
        }
        return null;
    }

    private void restoreLastFocusedView() {
        if (lastInteractedKey == null) {
            lastInteractedKey = prefs.getString("last_interacted_key", null);
        }
        if (lastInteractedKey == null) return;

        attemptRestoreAccessibilityFocus(lastInteractedKey, 0);
    }

    private void attemptRestoreAccessibilityFocus(final String targetKey, final int attempt) {
        if (targetKey == null || isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }

        if (pendingRestoreRunnable != null) {
            mainHandler.removeCallbacks(pendingRestoreRunnable);
            pendingRestoreRunnable = null;
        }

        long delay = (attempt == 0) ? 60 : (attempt == 1 ? 180 : 350);
        pendingRestoreRunnable = () -> {
            pendingRestoreRunnable = null;
            if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                return;
            }

            View targetView = findViewForKey(targetKey);
            if (targetView != null && targetView.isAttachedToWindow() && targetView.getVisibility() == View.VISIBLE) {
                targetView.setFocusable(true);
                targetView.setFocusableInTouchMode(true);
                targetView.requestFocus();
                targetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                targetView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                targetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                targetView.requestRectangleOnScreen(new android.graphics.Rect(0, 0, targetView.getWidth(), targetView.getHeight()), false);
            }

            if (attempt < 2) {
                View v = findViewForKey(targetKey);
                if (v == null || !v.isAccessibilityFocused()) {
                    attemptRestoreAccessibilityFocus(targetKey, attempt + 1);
                }
            }
        };
        mainHandler.postDelayed(pendingRestoreRunnable, delay);
    }

    private void showAddTypeMenu(String fullBaseKey, boolean isDock) {
        ArrayList<String> optionsList = new ArrayList<>();
        optionsList.add("App");
        optionsList.add("Web Link");
        optionsList.add("Speed Dial");
        optionsList.add("Shortcut");
        optionsList.add("Media Shortcut");
        optionsList.add("Widget");
        if (currentFolder.isEmpty()) {
            optionsList.add("Folder");
            if (!isDock && totalPages > 1) optionsList.add("Delete Page");
        }
        String[] options = optionsList.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Options")
                .setItems(options, (dialog, which) -> {
                    String selected = options[which];
                    if (selected.equals("App")) showAppSelectionDialog(fullBaseKey);
                    else if (selected.equals("Web Link")) showWebLinkDialog(fullBaseKey);
                    else if (selected.equals("Speed Dial")) showContactSelectionDialog(fullBaseKey);
                    else if (selected.equals("Shortcut")) showShortcutMenu(fullBaseKey);
                    else if (selected.equals("Media Shortcut")) showMediaShortcutMenu(fullBaseKey);
                    else if (selected.equals("Widget")) showAllWidgetsPicker();
                    else if (selected.equals("Folder")) showFolderDialog(fullBaseKey);
                    else if (selected.equals("Delete Page")) deleteCurrentPage();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showFolderDialog(String fullBaseKey) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Folder Name");
        EditText input = new EditText(this);
        builder.setView(input);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (!folderName.isEmpty()) {
                prefs.edit().putString(fullBaseKey + "_name", folderName).putString(fullBaseKey + "_pkg", "folder:" + folderName).apply();
                buildGrid();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void showWebLinkDialog(String fullBaseKey) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);
        EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        layout.addView(nameInput);
        EditText urlInput = new EditText(this);
        urlInput.setHint("URL");
        layout.addView(urlInput);
        new AlertDialog.Builder(this).setTitle("Web Link").setView(layout)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String url = urlInput.getText().toString().trim();
                    if (name.isEmpty()) name = "Link";
                    if (!url.isEmpty()) {
                        prefs.edit().putString(fullBaseKey + "_name", name).putString(fullBaseKey + "_pkg", "url:" + url).apply();
                        buildGrid();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void showContactSelectionDialog(String fullBaseKey) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingContactCellKey = fullBaseKey;
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Speed Dial - Select Contact");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);

        EditText searchBox = new EditText(this);
        searchBox.setHint("Search contact name or number...");
        layout.addView(searchBox);

        ListView listView = new ListView(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listView.setLayoutParams(listParams);
        layout.addView(listView);

        builder.setView(layout);
        builder.setNegativeButton("Cancel", null);
        builder.setNeutralButton("Enter Manually", (d, w) -> showManualContactDialog(fullBaseKey));

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        List<ContactItem> allContacts = new ArrayList<>();
        List<ContactItem> displayedContacts = new ArrayList<>();

        ArrayAdapter<ContactItem> adapter = new ArrayAdapter<ContactItem>(this, android.R.layout.simple_list_item_2, android.R.id.text1, displayedContacts) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);
                ContactItem item = getItem(position);
                if (item != null) {
                    text1.setText(item.name);
                    text2.setText(item.number);
                }
                return view;
            }
        };
        listView.setAdapter(adapter);

        new Thread(() -> {
            List<ContactItem> fetched = fetchAllContacts();
            runOnUiThread(() -> {
                allContacts.clear();
                allContacts.addAll(fetched);
                displayedContacts.clear();
                displayedContacts.addAll(fetched);
                adapter.notifyDataSetChanged();
            });
        }).start();

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase();
                displayedContacts.clear();
                if (query.isEmpty()) {
                    displayedContacts.addAll(allContacts);
                } else {
                    for (ContactItem item : allContacts) {
                        if (item.name.toLowerCase().contains(query) || item.number.replace(" ", "").contains(query)) {
                            displayedContacts.add(item);
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ContactItem item = adapter.getItem(position);
            if (item != null) {
                provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                prefs.edit().putString(fullBaseKey + "_name", item.name).putString(fullBaseKey + "_pkg", "tel:" + item.number).apply();
                buildGrid();
                announceAccessibility("Speed dial added: " + item.name);
                Toast.makeText(this, "Speed Dial: " + item.name, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        dialog.show();
        searchBox.requestFocus();
    }

    @SuppressLint("Range")
    private List<ContactItem> fetchAllContacts() {
        List<ContactItem> list = new ArrayList<>();
        try {
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = {
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
            };
            Cursor cursor = getContentResolver().query(uri, projection, null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC");
            if (cursor != null) {
                int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIdx);
                    String number = cursor.getString(numIdx);
                    if (name != null && number != null && !number.trim().isEmpty()) {
                        list.add(new ContactItem(name, number.trim()));
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error fetching contacts", e);
        }
        return list;
    }

    private void showManualContactDialog(String fullBaseKey) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);
        EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        layout.addView(nameInput);
        EditText numInput = new EditText(this);
        numInput.setHint("Number");
        layout.addView(numInput);
        new AlertDialog.Builder(this).setTitle("Speed Dial (Manual)").setView(layout)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String num = numInput.getText().toString().trim();
                    if (name.isEmpty()) name = num;
                    if (!num.isEmpty()) {
                        prefs.edit().putString(fullBaseKey + "_name", name).putString(fullBaseKey + "_pkg", "tel:" + num).apply();
                        buildGrid();
                        announceAccessibility("Speed dial added: " + name);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void showMediaShortcutMenu(String fullBaseKey) {
        boolean isMl = isCurrentLanguageMalayalam();
        String[] names = isMl ? new String[]{
                "പ്ലേ / പോസ്",
                "അടുത്ത ട്രാക്ക്",
                "മുമ്പത്തെ ട്രാക്ക്",
                "ഫാസ്റ്റ് ഫോർവേഡ്",
                "റിവൈൻഡ്",
                "ഓഡിയോ റൂട്ടിംഗ് (Audio Routing)"
        } : new String[]{
                "Play / Pause",
                "Next Track",
                "Previous Track",
                "Fast Forward",
                "Rewind",
                "Audio Routing"
        };
        String[] actions = {
                "media:play_pause",
                "media:next",
                "media:previous",
                "media:fast_forward",
                "media:rewind",
                "media:audio_routing"
        };
        new AlertDialog.Builder(this).setTitle(isMl ? "മീഡിയ ഷോർട്ട്‌കട്ട്" : "Media Shortcut")
                .setItems(names, (dialog, which) -> {
                    prefs.edit().putString(fullBaseKey + "_name", names[which]).putString(fullBaseKey + "_pkg", actions[which]).apply();
                    buildGrid();
                    announceAccessibility((isMl ? "മീഡിയ ഷോർട്ട്‌കട്ട് ചേർത്തു: " : "Media shortcut added: ") + names[which]);
                }).setNegativeButton(isMl ? "റദ്ദാക്കുക" : "Cancel", null).show();
    }

    private void showShortcutMenu(String fullBaseKey) {
        String[] names = {"Wi-Fi", "Mobile Data", "Bluetooth", "Hotspot", "Display", "Sound", "Accessibility"};
        String[] actions = {
                "sys:" + Settings.ACTION_WIFI_SETTINGS,
                "sys:" + Settings.ACTION_DATA_USAGE_SETTINGS,
                "sys:" + Settings.ACTION_BLUETOOTH_SETTINGS,
                "intent:tether",
                "sys:" + Settings.ACTION_DISPLAY_SETTINGS,
                "sys:" + Settings.ACTION_SOUND_SETTINGS,
                "sys:" + Settings.ACTION_ACCESSIBILITY_SETTINGS
        };
        new AlertDialog.Builder(this).setTitle("Shortcut")
                .setItems(names, (dialog, which) -> {
                    prefs.edit().putString(fullBaseKey + "_name", names[which]).putString(fullBaseKey + "_pkg", actions[which]).apply();
                    buildGrid();
                }).setNegativeButton("Cancel", null).show();
    }

    private void updateCellButton(Button cellButton, String fullBaseKey, boolean isDock, int row, int col) {
        String appName = prefs.getString(fullBaseKey + "_name", "");
        String savedPkg = prefs.getString(fullBaseKey + "_pkg", "");
        int acc_level = prefs.getInt("acc_level", 0);
        boolean badgesEnabled = prefs.getBoolean("notification_badges_enabled", true);
        boolean isMl = isCurrentLanguageMalayalam();
        String lang = isMl ? "ml" : "en";

        int badgeCount = 0;
        if (badgesEnabled) {
            if (savedPkg.startsWith("folder:")) {
                String folderName = savedPkg.substring(7);
                int maxRows = prefs.getInt("grid_rows", 10);
                int maxCols = prefs.getInt("grid_cols", 4);
                badgeCount = NotificationBadgeManager.getInstance().getFolderBadgeCount(prefs, folderName, totalPages, maxRows, maxCols);
            } else if (isAppPackage(savedPkg)) {
                badgeCount = NotificationBadgeManager.getInstance().getBadgeCount(savedPkg);
            }
        }

        cellButton.setBackgroundTintList(null);
        cellButton.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(16));

        if (movingCellKey != null && movingCellKey.equals(fullBaseKey)) {
            bg.setStroke(dpToPx(2), 0xFFFFA500);
            bg.setColor(0x44FFA500);
            cellButton.setText(appName.isEmpty() ? "+" : appName);
            cellButton.setTextColor(0xFFFFFFFF);
        } else if (pendingAddPkg != null && savedPkg.isEmpty()) {
            bg.setStroke(dpToPx(2), 0xFF00FF00);
            bg.setColor(0x2200FF00);
            cellButton.setText("Place");
            cellButton.setTextColor(0xFF00FF00);
        } else if (savedPkg.isEmpty()) {
            bg.setStroke(0, 0x00000000);
            bg.setColor(0x00000000);
            cellButton.setText("");
        } else {
            ThemeManager.ActiveTheme activeTheme = ThemeManager.getActiveTheme(prefs);
            if (isDock) {
                bg.setColor(activeTheme.dockColor);
                cellButton.setTextColor(activeTheme.dockTextColor);
            } else {
                bg.setColor(activeTheme.cellColor);
                if (activeTheme.cellStrokeColor != 0) {
                    bg.setStroke(dpToPx(1), activeTheme.cellStrokeColor);
                }
                cellButton.setTextColor(activeTheme.textColor);
            }

            if (savedPkg.startsWith("media:audio_routing") || savedPkg.startsWith("media:audio_volume") ||
                    savedPkg.startsWith("media:audio_routing_toggle") || savedPkg.startsWith("media:media_mute_toggle") ||
                    savedPkg.startsWith("media:acc_vol")) {
                boolean isSeparationOn = AudioRoutingManager.getInstance().isAudioSeparationEnabled(this);
                cellButton.setText(isMl ?
                        (isSeparationOn ? "ഓഡിയോ റൂട്ടിംഗ്: ഓൺ" : "ഓഡിയോ റൂട്ടിംഗ്: ഓഫ്") :
                        (isSeparationOn ? "Audio Routing: ON" : "Audio Routing: OFF"));
            } else {
                cellButton.setText(appName);
            }
            cellButton.setTypeface(null, Typeface.BOLD);
            if (activeTheme.isLight) {
                cellButton.setShadowLayer(0, 0, 0, 0);
            } else {
                cellButton.setShadowLayer(3f, 0f, 2f, 0xFF000000);
            }

            try {
                Drawable icon = null;
                boolean needsTint = false;

                if (savedPkg.startsWith("folder:")) {
                    String fName = savedPkg.substring(7);
                    icon = getFolderIcon(fName);
                    needsTint = false;
                } else if (savedPkg.startsWith("url:")) {
                    icon = ContextCompat.getDrawable(this, R.drawable.ic_web_link);
                    if (icon != null) {
                        icon = icon.mutate();
                        icon.setTint(0xFF29B6F6);
                    }
                    needsTint = false;
                } else if (savedPkg.startsWith("tel:")) {
                    icon = ContextCompat.getDrawable(this, R.drawable.ic_phone_call);
                    if (icon != null) {
                        icon = icon.mutate();
                        icon.setTint(0xFF66BB6A);
                    }
                    needsTint = false;
                } else if (savedPkg.startsWith("media:")) {
                    String mediaAction = savedPkg.substring(6);
                    switch (mediaAction) {
                        case "play_pause":
                            icon = ContextCompat.getDrawable(this, R.drawable.ic_media_play_pause);
                            break;
                        case "next":
                            icon = ContextCompat.getDrawable(this, R.drawable.ic_media_next);
                            break;
                        case "previous":
                            icon = ContextCompat.getDrawable(this, R.drawable.ic_media_previous);
                            break;
                        case "fast_forward":
                            icon = ContextCompat.getDrawable(this, R.drawable.ic_media_fast_forward);
                            break;
                        case "rewind":
                            icon = ContextCompat.getDrawable(this, R.drawable.ic_media_rewind);
                            break;
                        case "audio_volume":
                        case "audio_routing":
                        case "audio_routing_toggle":
                        case "media_mute_toggle":
                        case "acc_vol_up":
                        case "acc_vol_down":
                            icon = ContextCompat.getDrawable(this, R.drawable.ic_audio_routing);
                            break;
                    }
                    if (icon != null) {
                        icon = icon.mutate();
                        if ("audio_volume".equals(mediaAction) || "audio_routing".equals(mediaAction) ||
                            "media_mute_toggle".equals(mediaAction) || "audio_routing_toggle".equals(mediaAction) ||
                            "acc_vol_up".equals(mediaAction) || "acc_vol_down".equals(mediaAction)) {
                            boolean isSepOn = AudioRoutingManager.getInstance().isAudioSeparationEnabled(this);
                            icon.setTint(isSepOn ? 0xFF00E676 : 0xFF29B6F6);
                        } else {
                            icon.setTint(0xFFFF7043);
                        }
                    }
                    needsTint = false;
                } else if (savedPkg.startsWith("shortcut:")) {
                    String[] parts = savedPkg.split(":", 3);
                    if (parts.length == 3) {
                        try {
                            LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
                            if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
                                LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
                                query.setPackage(parts[1]);
                                query.setShortcutIds(Collections.singletonList(parts[2]));
                                query.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC |
                                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST |
                                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
                                List<ShortcutInfo> list = launcherApps.getShortcuts(query, android.os.Process.myUserHandle());
                                if (list != null && !list.isEmpty()) {
                                    icon = launcherApps.getShortcutIconDrawable(list.get(0), getResources().getDisplayMetrics().densityDpi);
                                }
                            }
                        } catch (Exception ignored) {}
                        if (icon == null) {
                            icon = getCachedAppIcon(parts[1]);
                        }
                    }
                    if (icon == null) {
                        icon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_preferences);
                        needsTint = true;
                    }
                } else if (savedPkg.startsWith("sys:") || savedPkg.startsWith("intent:") || savedPkg.startsWith("legacy_shortcut:")) {
                    icon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_preferences);
                    if (icon != null) {
                        icon = icon.mutate();
                        icon.setTint(0xFFBA68C8);
                    }
                    needsTint = false;
                } else if (!savedPkg.contains(":")) {
                    icon = getCachedAppIcon(savedPkg);
                    needsTint = false;
                }

                if (icon != null) {
                    icon = icon.mutate();
                    if (needsTint) {
                        icon.setTint(activeTheme.isLight ? (isDock ? activeTheme.dockTextColor : activeTheme.textColor) : Color.WHITE);
                    }
                    if (badgesEnabled && badgeCount > 0) {
                        icon = createBadgedIcon(icon, badgeCount, dpToPx(44));
                    }
                    int size = dpToPx(44);
                    icon.setBounds(0, 0, size, size);
                    cellButton.setCompoundDrawables(null, icon, null, null);
                    cellButton.setCompoundDrawablePadding(dpToPx(4));
                }
            } catch (Exception ignored) {
            }
        }

        cellButton.setBackground(bg);
        cellButton.setGravity(android.view.Gravity.CENTER);
        cellButton.setSingleLine(true);
        cellButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cellButton.setAllCaps(false);
        cellButton.setTextSize(11f);
        cellButton.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(4));

        cellButton.setTag(fullBaseKey);
        cellButton.setFocusable(true);
        cellButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        cellButton.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                setLastInteractedKey(fullBaseKey);
            }
        });

        int speakingOrder = prefs.getInt("speaking_order", 0);
        // 0: Name, Row, Col
        // 1: Row, Col, Name
        // 2: Name, Col, Row
        // 3: Col, Row, Name

        String positionDesc;
        if (isDock) {
            positionDesc = isMl ? ("ഡോക്ക് " + col) : ("Dock " + col);
        } else {
            if (speakingOrder == 2 || speakingOrder == 3) {
                positionDesc = isMl ? ("കോളം " + col + ", വരി " + row) : ("Column " + col + ", Row " + row);
            } else {
                positionDesc = isMl ? ("വരി " + row + ", കോളം " + col) : ("Row " + row + ", Column " + col);
            }
        }

        boolean nameFirst = (speakingOrder == 0 || speakingOrder == 2);

        String badgeDesc = "";
        if (badgesEnabled && badgeCount > 0) {
            if (savedPkg.startsWith("folder:")) {
                badgeDesc = ", " + (isMl ? (badgeCount + " പുതിയ അറിയിപ്പുകൾ") : (badgeCount + (badgeCount == 1 ? " unread notification" : " unread notifications")));
            } else {
                badgeDesc = ", " + NotificationBadgeManager.getInstance().getBadgeAccessibilityText(this, savedPkg, appName, badgeCount, lang);
            }
        }

        String itemLabel;
        if (savedPkg.isEmpty()) {
            itemLabel = isMl ? "കാലി" : "Empty";
        } else if (savedPkg.startsWith("folder:")) {
            itemLabel = (isMl ? "ഫോൾഡർ " : "Folder ") + appName + badgeDesc;
        } else {
            itemLabel = appName + badgeDesc;
        }

        String desc = "";

        if (pendingAddPkg != null) {
            String actionHint = savedPkg.isEmpty()
                    ? (isMl ? ". സ്ഥാപിക്കാൻ ഇരട്ടത്തവണ അമർത്തുക." : ". Double tap to place.")
                    : (savedPkg.startsWith("folder:")
                        ? (isMl ? ". തുറക്കാനും ഉള്ളിൽ സ്ഥാപിക്കാനും ഇരട്ടത്തവണ അമർത്തുക." : ". Double tap to open and place inside.")
                        : (isMl ? ". തിരക്കിലാണ്." : ". Occupied."));
            if (nameFirst) {
                desc = itemLabel + ", " + positionDesc + actionHint;
            } else {
                desc = positionDesc + ", " + itemLabel + actionHint;
            }
        } else if (acc_level == 0) {
            String actionHint;
            if (movingCellKey != null && movingCellKey.equals(fullBaseKey)) {
                actionHint = isMl ? ". തിരഞ്ഞെടുത്തു. മാറ്റങ്ങൾ ലഭ്യമാണ്." : ". Selected. Actions available.";
            } else if (movingCellKey != null) {
                actionHint = savedPkg.startsWith("folder:")
                        ? (isMl ? ". തുറക്കാനും ഇവിടെ മാറ്റാനും ഇരട്ടത്തവണ അമർത്തുക." : ". Double tap to open and move inside.")
                        : (isMl ? ". സ്ഥാപിക്കാൻ മാറ്റങ്ങൾ ലഭ്യമാണ്." : ". Actions available to place.");
            } else {
                actionHint = isMl ? ". മാറ്റങ്ങൾ ലഭ്യമാണ്." : ". Actions available.";
            }
            if (nameFirst) {
                desc = itemLabel + ", " + positionDesc + actionHint;
            } else {
                desc = positionDesc + ", " + itemLabel + actionHint;
            }
        } else if (acc_level == 1) {
            if (nameFirst) {
                desc = itemLabel + ", " + positionDesc + ".";
            } else {
                desc = positionDesc + ", " + itemLabel + ".";
            }
        } else if (acc_level == 2) {
            String actionHint;
            if (movingCellKey != null && movingCellKey.equals(fullBaseKey)) {
                actionHint = isMl ? ". മാറ്റങ്ങൾ ലഭ്യമാണ്." : ". Actions available.";
            } else if (movingCellKey != null) {
                actionHint = savedPkg.startsWith("folder:")
                        ? (isMl ? ". തുറക്കാനും ഇവിടെ മാറ്റാനും ഇരട്ടത്തവണ അമർത്തുക." : ". Double tap to open and move inside.")
                        : (isMl ? ". സ്ഥാപിക്കാൻ മാറ്റങ്ങൾ ലഭ്യമാണ്." : ". Actions available to place.");
            } else {
                actionHint = isMl ? ". മാറ്റങ്ങൾ ലഭ്യമാണ്." : ". Actions available.";
            }
            desc = itemLabel + actionHint;
        } else if (acc_level == 3) {
            desc = itemLabel;
        }
        cellButton.setContentDescription(desc);

        ViewCompat.setAccessibilityDelegate(cellButton, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClickable(true);
                info.setLongClickable(true);
                info.setEnabled(true);

                if (pendingAddPkg != null) {
                    if (savedPkg.isEmpty()) {
                        info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.action_cell_place_here, isMl ? "ഇവിടെ സ്ഥാപിക്കുക" : "Place Here"));
                    }
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_cell_cancel, isMl ? "റദ്ദാക്കുക" : "Cancel"));
                } else if (movingCellKey != null) {
                    if (movingCellKey.equals(fullBaseKey)) {
                        info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.action_cell_cancel, isMl ? "റദ്ദാക്കുക" : "Cancel"));
                    } else {
                        info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.action_cell_place_here, isMl ? "ഇവിടെ സ്ഥാപിക്കുക" : "Place Here"));
                    }
                } else if (!savedPkg.isEmpty()) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_cell_move, isMl ? "മാറ്റുക" : "Move"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_cell_remove, isMl ? "ഒഴിവാക്കുക" : "Remove"));

                    boolean isApp = isAppPackage(savedPkg);
                    if (isApp) {
                        info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.action_cell_info, isMl ? "വിവരങ്ങൾ" : "Info"));

                        List<ShortcutInfo> shortcuts = getAppShortcuts(savedPkg);
                        if (!shortcuts.isEmpty()) {
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.action_cell_shortcuts, isMl ? "ഷോർട്ട്കട്ടുകൾ" : "Shortcuts"));
                        }

                        List<AppWidgetProviderInfo> wList = getWidgetsForPackage(savedPkg);
                        if (wList != null && !wList.isEmpty()) {
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.action_cell_widgets, isMl ? "വിഡ്ജറ്റുകൾ" : "Widgets"));
                        }

                        if (isInstalledFromPlayStore(savedPkg)) {
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.action_cell_share, isMl ? "പങ്കിടുക" : "Share"));
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.action_cell_update, isMl ? "അപ്ഡേറ്റ്" : "Update"));
                        }
                    }
                } else {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_cell_add, isMl ? "ചേർക്കുക" : "Add"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_gesture_search, isMl ? "സാർവത്രിക തിരച്ചിൽ" : "Universal Search"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_gesture_app_drawer, isMl ? "ആപ്പ് ഡ്രോയർ" : "App Drawer"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_gesture_settings, isMl ? "ലോഞ്ചർ ക്രമീകരണങ്ങൾ" : "Launcher Settings"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_gesture_next_page, isMl ? "അടുത്ത പേജ്" : "Next Page"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_gesture_prev_page, isMl ? "മുൻപത്തെ പേജ്" : "Previous Page"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_gesture_notifications, isMl ? "അറിയിപ്പ് പാനൽ" : "Notification Panel"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.action_gesture_quick_settings, isMl ? "ദ്രുത ക്രമീകരണങ്ങൾ" : "Quick Settings"));
                    if (prefs.getBoolean("google_feed_enabled", true)) {
                        info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.action_gesture_feed, isMl ? "ഗൂഗിൾ ഫീഡ്" : "Google Feed"));
                    }
                }
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, @Nullable Bundle args) {
                if (action == AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS || action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                    setLastInteractedKey(fullBaseKey);
                }
                if (pendingAddPkg != null) {
                    if (action == R.id.action_cell_place_here) {
                        provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        handleCellClick(fullBaseKey, isDock);
                        return true;
                    } else if (action == R.id.action_cell_cancel) {
                        provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        handleCellLongClick(fullBaseKey);
                        return true;
                    }
                    return super.performAccessibilityAction(host, action, args);
                }

                if (action == R.id.action_cell_widgets) {
                    showWidgetsDialog(savedPkg);
                    return true;
                }

                if (action == R.id.action_cell_shortcuts) {
                    showShortcutsDialog(savedPkg, appName, getAppShortcuts(savedPkg));
                    return true;
                }

                if (action == R.id.action_cell_cancel || action == R.id.action_cell_place_here) {
                    provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    handleCellClick(fullBaseKey, isDock);
                    return true;
                } else if (action == R.id.action_cell_add) {
                    provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    showAddTypeMenu(fullBaseKey, isDock);
                    return true;
                } else if (action == R.id.action_gesture_search) {
                    showUniversalSearchDialog();
                    return true;
                } else if (action == R.id.action_gesture_app_drawer) {
                    showAllAppsDrawer();
                    return true;
                } else if (action == R.id.action_gesture_settings) {
                    showSettingsMenu();
                    return true;
                } else if (action == R.id.action_gesture_next_page) {
                    executeGestureAction("swipe_left_2");
                    return true;
                } else if (action == R.id.action_gesture_prev_page) {
                    executeGestureAction("swipe_right_2");
                    return true;
                } else if (action == R.id.action_gesture_notifications) {
                    expandStatusBar(false);
                    return true;
                } else if (action == R.id.action_gesture_quick_settings) {
                    expandStatusBar(true);
                    return true;
                } else if (action == R.id.action_gesture_feed) {
                    openGoogleFeed();
                    return true;
                } else if (!savedPkg.isEmpty() && movingCellKey == null) {
                    if (action == R.id.action_cell_move) {
                        movingCellKey = fullBaseKey;
                        String msg = isMl ? "ഒരു പുതിയ സെൽ തിരഞ്ഞെടുക്കുക" : "Select a new cell";
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        announceAccessibility(msg);
                        buildGrid();
                        return true;
                    } else if (action == R.id.action_cell_remove) {
                        prefs.edit().remove(fullBaseKey + "_name").remove(fullBaseKey + "_pkg").apply();
                        announceAccessibility(isMl ? "ഒഴിവാക്കി" : "Removed");
                        buildGrid();
                        return true;
                    } else if (action == R.id.action_cell_share) {
                        openShareMenu(savedPkg);
                        return true;
                    } else if (action == R.id.action_cell_update) {
                        openPlayStore(savedPkg);
                        return true;
                    } else if (action == R.id.action_cell_info) {
                        openAppInfo(savedPkg);
                        return true;
                    }
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    private void swapCells(String key1, String key2) {
        String pkg1 = prefs.getString(key1 + "_pkg", "");
        String name1 = prefs.getString(key1 + "_name", "");
        String pkg2 = prefs.getString(key2 + "_pkg", "");
        String name2 = prefs.getString(key2 + "_name", "");
        SharedPreferences.Editor editor = prefs.edit();
        if (pkg2.isEmpty()) editor.remove(key1 + "_pkg").remove(key1 + "_name");
        else editor.putString(key1 + "_pkg", pkg2).putString(key1 + "_name", name2);
        if (pkg1.isEmpty()) editor.remove(key2 + "_pkg").remove(key2 + "_name");
        else editor.putString(key2 + "_pkg", pkg1).putString(key2 + "_name", name1);
        editor.apply();
    }

    private boolean isAppPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return !pkg.startsWith("tel:") &&
                !pkg.startsWith("url:") &&
                !pkg.startsWith("sys:") &&
                !pkg.startsWith("folder:") &&
                !pkg.startsWith("shortcut:") &&
                !pkg.startsWith("legacy_shortcut:") &&
                !pkg.startsWith("media:") &&
                !pkg.equals("intent:tether");
    }

    private List<ShortcutInfo> getAppShortcuts(String pkgName) {
        try {
            LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
                LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
                query.setPackage(pkgName);
                query.setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC |
                                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST |
                                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                );
                List<ShortcutInfo> list = launcherApps.getShortcuts(query, android.os.Process.myUserHandle());
                if (list != null) return list;
            }
        } catch (SecurityException e) {
            Log.w("MainActivity", "Shortcut host permission missing", e);
        } catch (Exception e) {
            Log.e("MainActivity", "Error getting shortcuts", e);
        }
        return Collections.emptyList();
    }

    private void showShortcutsDialog(String pkgName, String appName, List<ShortcutInfo> shortcuts) {
        if (shortcuts == null || shortcuts.isEmpty()) {
            Toast.makeText(this, "No shortcuts available", Toast.LENGTH_SHORT).show();
            return;
        }

        LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        int density = getResources().getDisplayMetrics().densityDpi;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(appName + " Shortcuts");

        ArrayAdapter<ShortcutInfo> adapter = new ArrayAdapter<ShortcutInfo>(this, android.R.layout.simple_list_item_1, shortcuts) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                ShortcutInfo info = getItem(position);
                if (info != null) {
                    CharSequence label = info.getShortLabel();
                    if (label == null || label.length() == 0) label = info.getLongLabel();
                    if (label == null || label.length() == 0) label = info.getId();
                    view.setText(label);

                    Drawable icon = null;
                    try {
                        if (launcherApps != null) {
                            icon = launcherApps.getShortcutIconDrawable(info, density);
                        }
                    } catch (Exception ignored) {}

                    if (icon == null) {
                        try {
                            icon = getPackageManager().getApplicationIcon(pkgName);
                        } catch (Exception ignored) {}
                    }

                    if (icon != null) {
                        int size = dpToPx(32);
                        icon.setBounds(0, 0, size, size);
                        view.setCompoundDrawables(icon, null, null, null);
                        view.setCompoundDrawablePadding(dpToPx(12));
                    }
                    view.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
                }
                return view;
            }
        };

        builder.setAdapter(adapter, (dialog, which) -> {
            ShortcutInfo selected = shortcuts.get(which);
            CharSequence label = selected.getShortLabel();
            if (label == null || label.length() == 0) label = selected.getLongLabel();
            if (label == null || label.length() == 0) label = appName;
            String shortcutName = label.toString();

            String[] actions = {"Open Shortcut", "Add to Home Screen"};
            new AlertDialog.Builder(this)
                    .setTitle(shortcutName)
                    .setItems(actions, (actionDialog, actionWhich) -> {
                        if (actionWhich == 0) {
                            try {
                                if (launcherApps != null) {
                                    launcherApps.startShortcut(pkgName, selected.getId(), null, null, android.os.Process.myUserHandle());
                                }
                            } catch (Exception e) {
                                Toast.makeText(this, "Could not launch shortcut", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            promptAddMethod("shortcut:" + pkgName + ":" + selected.getId(), shortcutName);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void promptSetDefaultHomeForShortcuts() {
        new AlertDialog.Builder(this)
                .setTitle("Default Launcher Required")
                .setMessage("Android requires KTS Launcher to be set as your default home app to display app shortcuts.")
                .setPositiveButton("Settings", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAppOptionsMenu(String fullBaseKey, String pkgName, String appName) {
        boolean isApp = isAppPackage(pkgName);
        ArrayList<String> optionsList = new ArrayList<>();
        if (fullBaseKey != null) {
            optionsList.add("Move");
            optionsList.add("Remove");
        } else {
            optionsList.add("Add to Home");
        }

        List<ShortcutInfo> shortcuts = isApp ? getAppShortcuts(pkgName) : Collections.emptyList();
        LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        boolean hasShortcutPerm = launcherApps != null && launcherApps.hasShortcutHostPermission();

        if (isApp) {
            if (!shortcuts.isEmpty()) {
                optionsList.add("Shortcuts");
            } else if (!hasShortcutPerm) {
                optionsList.add("Shortcuts");
            }

            optionsList.add("Info");

            List<AppWidgetProviderInfo> wList = getWidgetsForPackage(pkgName);
            if (wList != null && !wList.isEmpty()) {
                optionsList.add("Widgets");
            }

            if (isInstalledFromPlayStore(pkgName)) {
                optionsList.add("Share");
                optionsList.add("Update");
            }
        }

        if (fullBaseKey != null && pkgName.startsWith("folder:")) {
            String fName = pkgName.substring(7);
            AppInfo matchingApp = findAppByNameOrPackage(fName);
            boolean isMl = isCurrentLanguageMalayalam();
            if (matchingApp != null) {
                optionsList.add(isMl ? ("ആപ്പാക്കി മാറ്റുക (" + matchingApp.name + ")") : ("Convert to App (" + matchingApp.name + ")"));
            }
        }

        String[] options = optionsList.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(appName)
                .setItems(options, (dialog, which) -> {
                    String selected = options[which];
                    if (selected.equals("Move")) {
                        movingCellKey = fullBaseKey;
                        Toast.makeText(this, "Select a new cell", Toast.LENGTH_SHORT).show();
                        announceAccessibility("Select a new cell");
                        buildGrid();
                    } else if (selected.equals("Remove")) {
                        prefs.edit().remove(fullBaseKey + "_name").remove(fullBaseKey + "_pkg").apply();
                        announceAccessibility("Removed");
                        buildGrid();
                    } else if (selected.startsWith("Convert to App") || selected.startsWith("ആപ്പാക്കി മാറ്റുക")) {
                        String fName = pkgName.substring(7);
                        AppInfo matchingApp = findAppByNameOrPackage(fName);
                        boolean isMl = isCurrentLanguageMalayalam();
                        if (matchingApp != null) {
                            prefs.edit()
                                    .putString(fullBaseKey + "_name", matchingApp.name)
                                    .putString(fullBaseKey + "_pkg", matchingApp.packageName)
                                    .apply();
                            buildGrid();
                            Toast.makeText(this, isMl ? (matchingApp.name + " ആപ്പാക്കി മാറ്റി") : ("Converted to " + matchingApp.name), Toast.LENGTH_SHORT).show();
                            announceAccessibility(matchingApp.name + (isMl ? " ആപ്പാക്കി മാറ്റി" : " placed as direct app"));
                        }
                    } else if (selected.equals("Add to Home")) {
                        promptAddMethod(pkgName, appName);
                    } else if (selected.equals("Shortcuts")) {
                        if (hasShortcutPerm) {
                            showShortcutsDialog(pkgName, appName, shortcuts);
                        } else {
                            promptSetDefaultHomeForShortcuts();
                        }
                    } else if (selected.equals("Widgets")) {
                        showWidgetsDialog(pkgName);
                    } else if (selected.equals("Share")) {
                        openShareMenu(pkgName);
                    } else if (selected.equals("Update")) {
                        openPlayStore(pkgName);
                    } else if (selected.equals("Info")) {
                        openAppInfo(pkgName);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void changeGridAlignment() {
        changeGridAlignment(this::showGridLayoutSettingsDialog);
    }

    private void changeGridAlignment(Runnable onBack) {
        boolean isMl = isCurrentLanguageMalayalam();
        String[] alignments = {
                isMl ? "മുകളിൽ (Top)" : "Top",
                isMl ? "നടുവിൽ (Center)" : "Center",
                isMl ? "താഴെ (Bottom)" : "Bottom"
        };
        int currentAlign = prefs.getInt("grid_alignment", 0);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "ഗ്രിഡ് അലൈൻമെന്റ് (Grid Alignment)" : "Grid Alignment")
                .setSingleChoiceItems(alignments, currentAlign, (dialogInterface, which) -> {
                    prefs.edit().putInt("grid_alignment", which).apply();
                    buildGrid();
                    dialogInterface.dismiss();
                    if (onBack != null) onBack.run();
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (dialogInterface, which) -> dialogInterface.cancel())
                .create();
        attachBackHandler(dialog, onBack != null ? onBack : this::showGridLayoutSettingsDialog);
        dialog.show();
    }

    private void showSettingsMenu() {
        boolean isMl = isCurrentLanguageMalayalam();

        ArrayList<String> categories = new ArrayList<>();
        categories.add(isMl ? "📐 ഗ്രിഡ് ക്രമീകരണങ്ങൾ (Grid & Layout)" : "📐 Grid & Layout");
        categories.add(isMl ? "🔊 ശബ്ദവും അക്സസിബിലിറ്റിയും (Speech & Audio)" : "🔊 Speech & Audio");
        categories.add(isMl ? "🎨 തീമുകളും നിറങ്ങളും (Themes & Appearance)" : "🎨 Themes & Appearance");
        categories.add(isMl ? "👆 ജെസ്റ്റർ ക്രമീകരണങ്ങൾ (Gestures)" : "👆 Gesture Settings");
        categories.add(isMl ? "💾 ബാക്കപ്പും റീസ്റ്റോറും (Backup & Restore)" : "💾 Backup & Restore");
        categories.add(isMl ? "🚀 ആപ്പ് അപ്‌ഡേറ്റ് (Check for Updates)" : "🚀 App Updates");
        categories.add(isMl ? "⚙️ പൊതുവായ ക്രമീകരണങ്ങൾ (General Settings)" : "⚙️ General Settings");

        String[] options = categories.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(isMl ? "ക്രമീകരണങ്ങൾ (Settings)" : "Settings")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            showGridLayoutSettingsDialog();
                            break;
                        case 1:
                            showSpeechAudioSettingsDialog();
                            break;
                        case 2:
                            showThemesAndAppearanceDialog();
                            break;
                        case 3:
                            showGestureSettingsDialog();
                            break;
                        case 4:
                            showBackupRestoreSettingsDialog();
                            break;
                        case 5:
                            showAppUpdateSettingsDialog();
                            break;
                        case 6:
                            showGeneralSettingsDialog();
                            break;
                    }
                })
                .setNegativeButton(isMl ? "റദ്ദാക്കുക" : "Cancel", null)
                .show();
    }

    private void showGridLayoutSettingsDialog() {
        boolean isMl = isCurrentLanguageMalayalam();
        int align = prefs.getInt("grid_alignment", 0);
        String[] alignNames = {"Top", "Center", "Bottom"};
        String[] alignNamesMl = {"മുകളിൽ (Top)", "നടുവിൽ (Center)", "താഴെ (Bottom)"};

        ArrayList<String> items = new ArrayList<>();
        items.add(isMl ? "വരി ചേർക്കുക (Add Row)" : "Add Row");
        items.add(isMl ? "വരി നീക്കം ചെയ്യുക (Remove Row)" : "Remove Row");
        items.add(isMl ? "കോളം ചേർക്കുക (Add Column)" : "Add Column");
        items.add(isMl ? "കോളം നീക്കം ചെയ്യുക (Remove Column)" : "Remove Column");
        items.add((isMl ? "ഗ്രിഡ് അലൈൻമെന്റ്: " : "Grid Alignment: ") + (isMl ? alignNamesMl[align] : alignNames[align]));
        if (totalPages > 1) {
            String currPageType = prefs.getString("page_" + currentPage + "_type", "grid");
            String typeLabel = currPageType.equals("widget") ? (isMl ? "ഫ്ലാറ്റ് സ്ക്രീൻ" : "Flat Screen") : (isMl ? "ഗ്രിഡ് സ്ക്രീൻ" : "Grid Screen");
            items.add((isMl ? "നിലവിലെ പേജ് ഒഴിവാക്കുക (" : "Delete Current Page (") + typeLabel + " " + currentPage + ")");
        }

        String[] options = items.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "📐 ഗ്രിഡ് ക്രമീകരണങ്ങൾ" : "📐 Grid & Layout Settings")
                .setItems(options, (d, which) -> {
                    String selected = options[which];
                    if (selected.contains("Add Row") || selected.contains("വരി ചേർക്കുക")) askRowPosition(true, this::showGridLayoutSettingsDialog);
                    else if (selected.contains("Remove Row") || selected.contains("വരി നീക്കം ചെയ്യുക")) askRowPosition(false, this::showGridLayoutSettingsDialog);
                    else if (selected.contains("Add Column") || selected.contains("കോളം ചേർക്കുക")) askColPosition(true, this::showGridLayoutSettingsDialog);
                    else if (selected.contains("Remove Column") || selected.contains("കോളം നീക്കം ചെയ്യുക")) askColPosition(false, this::showGridLayoutSettingsDialog);
                    else if (selected.contains("Grid Alignment") || selected.contains("ഗ്രിഡ് അലൈൻമെന്റ്")) changeGridAlignment(this::showGridLayoutSettingsDialog);
                    else if (selected.contains("Delete Current Page") || selected.contains("നിലവിലെ പേജ് ഒഴിവാക്കുക")) deleteCurrentPage(this::showGridLayoutSettingsDialog);
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, this::showSettingsMenu);
        dialog.show();
    }

    private void showSpeechAudioSettingsDialog() {
        boolean isMl = isCurrentLanguageMalayalam();
        int accLevel = prefs.getInt("acc_level", 0);
        String[] levelNamesEn = {"All", "Most", "Some", "None"};
        String[] levelNamesMl = {"All (പൂർണ്ണം)", "Most (കൂടുതൽ)", "Some (ചിലത്)", "None (പേര് മാത്രം)"};

        int order = prefs.getInt("speaking_order", 0);
        String[] orderNamesEn = {
            "Name, Row, Column",
            "Row, Column, Name",
            "Name, Column, Row",
            "Column, Row, Name"
        };
        String[] orderNamesMl = {
            "പേര്, വരി, കോളം",
            "വരി, കോളം, പേര്",
            "പേര്, കോളം, വരി",
            "കോളം, വരി, പേര്"
        };

        boolean isAudioSepOn = AudioRoutingManager.getInstance().isAudioSeparationEnabled(this);

        ArrayList<String> items = new ArrayList<>();
        items.add((isMl ? "സംസാര ലെവൽ: " : "Speaking Level: ") + (isMl ? levelNamesMl[accLevel] : levelNamesEn[accLevel]));
        items.add((isMl ? "അറിയിപ്പ് ക്രമം: " : "Speaking Order: ") + (isMl ? orderNamesMl[order] : orderNamesEn[order]));
        items.add(isMl ?
                ("ഓഡിയോ റൂട്ടിംഗ്: " + (isAudioSepOn ? "ഓൺ" : "ഓഫ്")) :
                ("Audio Routing: " + (isAudioSepOn ? "ON" : "OFF")));

        String[] options = items.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "🔊 ശബ്ദവും അക്സസിബിലിറ്റിയും" : "🔊 Speech & Audio Settings")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            changeSpeechLevel(this::showSpeechAudioSettingsDialog);
                            break;
                        case 1:
                            showSpeakingOrderDialog(this::showSpeechAudioSettingsDialog);
                            break;
                        case 2:
                            AudioRoutingManager.getInstance().toggleAudioSeparation(this);
                            buildGrid();
                            showSpeechAudioSettingsDialog();
                            break;
                    }
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, this::showSettingsMenu);
        dialog.show();
    }

    private void showThemesAndAppearanceDialog() {
        boolean isMl = isCurrentLanguageMalayalam();
        boolean badgesEnabled = prefs.getBoolean("notification_badges_enabled", true);

        ThemeManager.ActiveTheme currentTheme = ThemeManager.getActiveTheme(prefs);
        ThemeManager.ThemePreset preset = ThemeManager.getPresetById(currentTheme.presetId);
        String presetName = isMl ? preset.nameMl : preset.nameEn;
        String status = currentTheme.isCustom ? (isMl ? "കസ്റ്റം (" + presetName + ")" : "Custom (" + presetName + ")") : presetName;

        ArrayList<String> items = new ArrayList<>();
        items.add((isMl ? "തീമുകളും നിറങ്ങളും (" : "Themes & Colors (") + status + ")");
        items.add((isMl ? "നോട്ടിഫിക്കേഷൻ ബാഡ്ജുകൾ: " : "Notification Badges: ") + (badgesEnabled ? (isMl ? "ഓൺ" : "On") : (isMl ? "ഓഫ്" : "Off")));

        String[] options = items.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "🎨 തീമുകളും രൂപകൽപ്പനയും" : "🎨 Themes & Appearance")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            showThemesAndColorsDialog(this::showThemesAndAppearanceDialog);
                            break;
                        case 1:
                            toggleNotificationBadges();
                            showThemesAndAppearanceDialog();
                            break;
                    }
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, this::showSettingsMenu);
        dialog.show();
    }

    private void showBackupRestoreSettingsDialog() {
        boolean isMl = isCurrentLanguageMalayalam();
        String[] options = {
                isMl ? "ബാക്കപ്പ് എടുക്കുക (Backup Settings)" : "Backup Settings",
                isMl ? "ബാക്കപ്പ് പുനഃസ്ഥാപിക്കുക (Restore Settings)" : "Restore Settings"
        };
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "💾 ബാക്കപ്പും റീസ്റ്റോറും" : "💾 Backup & Restore")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        backupLauncher.launch("KTS_Backup.json");
                    } else if (which == 1) {
                        restoreLauncher.launch(new String[]{"application/json", "*/*"});
                    }
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, this::showSettingsMenu);
        dialog.show();
    }

    private void showAppUpdateSettingsDialog() {
        boolean isMl = isCurrentLanguageMalayalam();
        boolean autoCheck = AppUpdateManager.isAutoCheckEnabled(this);
        String versionStr = "v" + AppUpdateManager.getCurrentVersionName(this) + " (Build " + AppUpdateManager.getCurrentVersionCode(this) + ")";

        String[] items = {
                isMl ? "അപ്‌ഡേറ്റുകൾ പരിശോധിക്കുക (Check for Updates)" : "Check for Updates",
                (isMl ? "ഓട്ടോമാറ്റിക് അപ്‌ഡേറ്റ് ചെക്ക്: " : "Auto Check for Updates: ") + (autoCheck ? (isMl ? "ഓൺ" : "On") : (isMl ? "ഓഫ്" : "Off")),
                (isMl ? "നിലവിലെ വേർഷൻ: " : "Installed Version: ") + versionStr
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "🚀 ആപ്പ് അപ്‌ഡേറ്റ് (GitHub Updates)" : "🚀 App Updates")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        AppUpdateManager.getInstance().checkForUpdates(this, true);
                    } else if (which == 1) {
                        boolean newState = !autoCheck;
                        AppUpdateManager.setAutoCheckEnabled(this, newState);
                        String msg = newState ? (isMl ? "ഓട്ടോമാറ്റിക് അപ്‌ഡേറ്റ് ചെക്ക് ഓൺ ചെയ്തു" : "Auto update check enabled") : (isMl ? "ഓട്ടോമാറ്റിക് അപ്‌ഡേറ്റ് ചെക്ക് ഓഫ് ചെയ്തു" : "Auto update check disabled");
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        announceAccessibility(msg);
                        showAppUpdateSettingsDialog();
                    } else if (which == 2) {
                        Toast.makeText(this, versionStr, Toast.LENGTH_SHORT).show();
                        announceAccessibility(versionStr);
                        showAppUpdateSettingsDialog();
                    }
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, this::showSettingsMenu);
        dialog.show();
    }

    private void showGeneralSettingsDialog() {
        boolean isMl = isCurrentLanguageMalayalam();
        boolean isDefault = isDefaultLauncher();
        String defaultStatus = isDefault ? (isMl ? "ഡിഫോൾട്ടാണ്" : "Active") : (isMl ? "മാറ്റുക" : "Set as Default");

        ArrayList<String> items = new ArrayList<>();
        items.add((isMl ? "ഡിഫോൾട്ട് ഹോം ആപ്പ് (" : "Default Home App (") + defaultStatus + ")");
        items.add(isMl ? "ഭാഷ (Language)" : "Language");
        items.add(isMl ? "ലോഞ്ചർ റീസെറ്റ് ചെയ്യുക (Reset Launcher)" : "Reset Launcher");

        String[] options = items.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "⚙️ പൊതുവായ ക്രമീകരണങ്ങൾ" : "⚙️ General Settings")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            requestDefaultLauncherRole();
                            break;
                        case 1:
                            showLanguageSelectionDialog(this::showGeneralSettingsDialog);
                            break;
                        case 2:
                            confirmReset(this::showGeneralSettingsDialog);
                            break;
                    }
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, this::showSettingsMenu);
        dialog.show();
    }

    private void attachBackHandler(Dialog dialog, Runnable onBack) {
        if (dialog == null || onBack == null) return;
        final boolean[] handled = new boolean[]{false};
        Runnable safeBack = () -> {
            if (!handled[0]) {
                handled[0] = true;
                onBack.run();
            }
        };
        dialog.setOnCancelListener(d -> safeBack.run());
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    d.dismiss();
                    safeBack.run();
                }
                return true;
            }
            return false;
        });
    }

    private void showThemesAndColorsDialog() {
        showThemesAndColorsDialog(this::showThemesAndAppearanceDialog);
    }

    private void showThemesAndColorsDialog(Runnable onBack) {
        boolean isMl = isCurrentLanguageMalayalam();
        ThemeManager.ActiveTheme currentTheme = ThemeManager.getActiveTheme(prefs);
        ThemeManager.ThemePreset preset = ThemeManager.getPresetById(currentTheme.presetId);
        String presetName = isMl ? preset.nameMl : preset.nameEn;
        String status = currentTheme.isCustom ? (isMl ? "കസ്റ്റം (" + presetName + ")" : "Custom (" + presetName + ")") : presetName;

        String[] items = {
                (isMl ? "തീം തിരഞ്ഞെടുക്കുക (" : "Select Theme Preset (") + status + ")",
                isMl ? "പശ്ചാത്തല നിറം (Background Color)" : "Background Color",
                isMl ? "ഐക്കൺ / സെൽ കാർഡ് നിറം (Cell Card Color)" : "Icon / Cell Card Color",
                isMl ? "ഡോക്ക് നിറം (Dock Color)" : "Dock Color",
                isMl ? "ആക്സന്റ് നിറം (Accent Color)" : "Accent Color",
                isMl ? "തീം റീസെറ്റ് ചെയ്യുക (Reset Theme)" : "Reset Theme to Default"
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "തീമുകളും നിറങ്ങളും" : "Themes & Colors")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            showThemePresetSelectionDialog(() -> showThemesAndColorsDialog(onBack));
                            break;
                        case 1:
                            showColorPickerDialog(
                                    isMl ? "പശ്ചാത്തല നിറം" : "Background Color",
                                    currentTheme.bgColor,
                                    color -> {
                                        ThemeManager.setCustomBgColor(prefs, color);
                                        applyCurrentTheme();
                                        buildGrid();
                                        String msg = isMl ? "പശ്ചാത്തല നിറം മാറ്റി" : "Background color updated";
                                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                                        announceAccessibility(msg);
                                        showThemesAndColorsDialog(onBack);
                                    }
                            );
                            break;
                        case 2:
                            showColorPickerDialog(
                                    isMl ? "സെൽ കാർഡ് നിറം" : "Cell Card Color",
                                    currentTheme.cellColor,
                                    color -> {
                                        ThemeManager.setCustomCellColor(prefs, color);
                                        applyCurrentTheme();
                                        buildGrid();
                                        String msg = isMl ? "സെൽ നിറം മാറ്റി" : "Cell color updated";
                                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                                        announceAccessibility(msg);
                                        showThemesAndColorsDialog(onBack);
                                    }
                            );
                            break;
                        case 3:
                            showColorPickerDialog(
                                    isMl ? "ഡോക്ക് നിറം" : "Dock Color",
                                    currentTheme.dockColor,
                                    color -> {
                                        ThemeManager.setCustomDockColor(prefs, color);
                                        applyCurrentTheme();
                                        buildGrid();
                                        String msg = isMl ? "ഡോക്ക് നിറം മാറ്റി" : "Dock color updated";
                                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                                        announceAccessibility(msg);
                                        showThemesAndColorsDialog(onBack);
                                    }
                            );
                            break;
                        case 4:
                            showColorPickerDialog(
                                    isMl ? "ആക്സന്റ് നിറം" : "Accent Color",
                                    currentTheme.accentColor,
                                    color -> {
                                        prefs.edit().putInt("theme_custom_accent", color).putBoolean("theme_is_custom", true).apply();
                                        applyCurrentTheme();
                                        buildGrid();
                                        String msg = isMl ? "ആക്സന്റ് നിറം മാറ്റി" : "Accent color updated";
                                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                                        announceAccessibility(msg);
                                        showThemesAndColorsDialog(onBack);
                                    }
                            );
                            break;
                        case 5:
                            ThemeManager.resetToPreset(prefs);
                            applyCurrentTheme();
                            buildGrid();
                            String msg = isMl ? "തീം റീസെറ്റ് ചെയ്തു" : "Theme reset to default";
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                            announceAccessibility(msg);
                            showThemesAndColorsDialog(onBack);
                            break;
                    }
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, onBack != null ? onBack : this::showThemesAndAppearanceDialog);
        dialog.show();
    }

    private void showThemePresetSelectionDialog() {
        showThemePresetSelectionDialog(this::showThemesAndColorsDialog);
    }

    private void showThemePresetSelectionDialog(Runnable onBack) {
        boolean isMl = isCurrentLanguageMalayalam();
        List<ThemeManager.ThemePreset> presets = ThemeManager.getPresets();
        String[] names = new String[presets.size()];
        int selectedIndex = 0;
        String currentPresetId = prefs.getString("theme_preset_id", "classic_dark");

        for (int i = 0; i < presets.size(); i++) {
            ThemeManager.ThemePreset p = presets.get(i);
            names[i] = (isMl ? p.nameMl : p.nameEn) + (p.isLight ? (isMl ? " (ബ്രൈറ്റ്)" : " (Bright)") : (isMl ? " (ഡാർക്ക്)" : " (Dark)"));
            if (p.id.equalsIgnoreCase(currentPresetId)) {
                selectedIndex = i;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "തീം തിരഞ്ഞെടുക്കുക" : "Select Theme")
                .setSingleChoiceItems(names, selectedIndex, (d, which) -> {
                    ThemeManager.ThemePreset chosen = presets.get(which);
                    ThemeManager.setPreset(prefs, chosen.id);
                    applyCurrentTheme();
                    buildGrid();
                    String msg = isMl ? ("തീം മാറ്റി: " + chosen.nameMl) : ("Theme set to " + chosen.nameEn);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    announceAccessibility(msg);
                    d.dismiss();
                    if (onBack != null) onBack.run(); else showThemesAndColorsDialog();
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, onBack != null ? onBack : this::showThemesAndColorsDialog);
        dialog.show();
    }

    interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    private void showColorPickerDialog(String title, int initialColor, OnColorSelectedListener listener) {
        boolean isMl = isCurrentLanguageMalayalam();
        final int[] selectedColor = new int[]{initialColor};

        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16));
        scrollView.addView(container);

        // Preview box
        TextView previewBox = new TextView(this);
        previewBox.setText(String.format(Locale.US, "#%06X", (0xFFFFFF & initialColor)));
        previewBox.setTextColor(ThemeManager.getContrastingTextColor(initialColor));
        previewBox.setTextSize(16f);
        previewBox.setTypeface(null, Typeface.BOLD);
        previewBox.setGravity(android.view.Gravity.CENTER);
        previewBox.setPadding(dpToPx(12), dpToPx(16), dpToPx(12), dpToPx(16));

        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.RECTANGLE);
        previewBg.setCornerRadius(dpToPx(12));
        previewBg.setColor(initialColor);
        previewBg.setStroke(dpToPx(2), 0xFF888888);
        previewBox.setBackground(previewBg);
        container.addView(previewBox);

        // Hex input label
        TextView hexLabel = new TextView(this);
        hexLabel.setText(isMl ? "ഹെക്സ് കളർ കോഡ് (HEX):" : "Color HEX Code:");
        hexLabel.setTextSize(13f);
        hexLabel.setPadding(0, dpToPx(12), 0, dpToPx(4));
        container.addView(hexLabel);

        // Hex input edit text
        EditText hexInput = new EditText(this);
        hexInput.setText(String.format(Locale.US, "#%06X", (0xFFFFFF & initialColor)));
        hexInput.setTextSize(15f);
        hexInput.setSingleLine(true);
        container.addView(hexInput);

        // Curated swatches label
        TextView swatchesLabel = new TextView(this);
        swatchesLabel.setText(isMl ? "പ്രധാന നിറങ്ങൾ (Palette):" : "Curated Palette:");
        swatchesLabel.setTextSize(14f);
        swatchesLabel.setTypeface(null, Typeface.BOLD);
        swatchesLabel.setPadding(0, dpToPx(16), 0, dpToPx(8));
        container.addView(swatchesLabel);

        class ColorSwatch {
            final int color;
            final String nameEn;
            final String nameMl;
            ColorSwatch(int color, String nameEn, String nameMl) {
                this.color = color;
                this.nameEn = nameEn;
                this.nameMl = nameMl;
            }
        }

        ColorSwatch[] swatches = new ColorSwatch[]{
                new ColorSwatch(0xFFFFFFFF, "Pure White", "വെള്ള"),
                new ColorSwatch(0xFFF8FAFC, "Snow White", "സ്നോ വൈറ്റ്"),
                new ColorSwatch(0xFFFFFBEB, "Warm Cream", "ക്രീം"),
                new ColorSwatch(0xFFE2E8F0, "Light Slate", "ലൈറ്റ് സ്ലേറ്റ്"),
                new ColorSwatch(0xFF94A3B8, "Cool Gray", "കൂൾ ഗ്രേ"),
                new ColorSwatch(0xFF475569, "Medium Slate", "മീഡിയം സ്ലേറ്റ്"),
                new ColorSwatch(0xFF1E293B, "Dark Slate", "ഡാർക്ക് സ്ലേറ്റ്"),
                new ColorSwatch(0xFF0F172A, "Deep Navy Slate", "ഡീപ് നേവി സ്ലേറ്റ്"),
                new ColorSwatch(0xFF121212, "Classic Charcoal", "ചാർക്കോൾ"),
                new ColorSwatch(0xFF000000, "Pitch Black", "കറുപ്പ്"),
                new ColorSwatch(0xFF38BDF8, "Sky Blue", "ആകാശനീല"),
                new ColorSwatch(0xFF0284C7, "Ocean Blue", "സമുദ്രനീല"),
                new ColorSwatch(0xFF2563EB, "Royal Blue", "റോയൽ ബ്ലൂ"),
                new ColorSwatch(0xFF0D9488, "Teal Cyan", "ടീൽ സിയാൻ"),
                new ColorSwatch(0xFF34D399, "Mint Green", "മിന്റ് പച്ച"),
                new ColorSwatch(0xFF10B981, "Emerald Green", "മരതകപ്പച്ച"),
                new ColorSwatch(0xFF166534, "Forest Green", "ഫോറസ്റ്റ് ഗ്രീൻ"),
                new ColorSwatch(0xFFFACC15, "Bright Yellow", "തെളിഞ്ഞ മഞ്ഞ"),
                new ColorSwatch(0xFFF59E0B, "Amber Gold", "ഗോൾഡൻ ആംബർ"),
                new ColorSwatch(0xFFF97316, "Warm Orange", "ഓറഞ്ച്"),
                new ColorSwatch(0xFFEF4444, "Bright Red", "ചുവപ്പ്"),
                new ColorSwatch(0xFFDC2626, "Crimson Red", "ക്രിംസൺ റെഡ്"),
                new ColorSwatch(0xFFEC4899, "Pink", "പിങ്ക്"),
                new ColorSwatch(0xFF8B5CF6, "Soft Purple", "പർപ്പിൾ"),
                new ColorSwatch(0xFF5E35B1, "Deep Violet", "ഡീപ് വയലറ്റ്")
        };

        int cols = 5;
        LinearLayout rowLayout = null;
        for (int i = 0; i < swatches.length; i++) {
            if (i % cols == 0) {
                rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, dpToPx(4), 0, dpToPx(4));
                rowLayout.setLayoutParams(lp);
                container.addView(rowLayout);
            }

            ColorSwatch swatch = swatches[i];
            Button swatchBtn = new Button(this);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, dpToPx(48), 1.0f);
            btnLp.setMargins(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3));
            swatchBtn.setLayoutParams(btnLp);

            GradientDrawable sBg = new GradientDrawable();
            sBg.setShape(GradientDrawable.RECTANGLE);
            sBg.setCornerRadius(dpToPx(10));
            sBg.setColor(swatch.color);
            if (ThemeManager.isColorLight(swatch.color)) {
                sBg.setStroke(dpToPx(1), 0xFFCBD5E1);
            } else {
                sBg.setStroke(dpToPx(1), 0xFF333333);
            }
            swatchBtn.setBackground(sBg);

            String a11yName = isMl ? swatch.nameMl : swatch.nameEn;
            swatchBtn.setContentDescription(a11yName);

            swatchBtn.setOnClickListener(v -> {
                provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                selectedColor[0] = swatch.color;
                previewBg.setColor(swatch.color);
                previewBox.setText(String.format(Locale.US, "#%06X", (0xFFFFFF & swatch.color)));
                previewBox.setTextColor(ThemeManager.getContrastingTextColor(swatch.color));
                hexInput.setText(String.format(Locale.US, "#%06X", (0xFFFFFF & swatch.color)));
                announceAccessibility(a11yName);
            });

            if (rowLayout != null) {
                rowLayout.addView(swatchBtn);
            }
        }

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String hex = s.toString().trim();
                if (!hex.startsWith("#")) hex = "#" + hex;
                try {
                    int c = Color.parseColor(hex);
                    selectedColor[0] = c;
                    previewBg.setColor(c);
                    previewBox.setText(hex.toUpperCase(Locale.US));
                    previewBox.setTextColor(ThemeManager.getContrastingTextColor(c));
                } catch (Exception ignored) {}
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton(isMl ? "ശരി" : "Apply", (d, w) -> {
                    provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (listener != null) {
                        listener.onColorSelected(selectedColor[0]);
                    }
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, w) -> d.cancel())
                .create();
        attachBackHandler(dialog, this::showThemesAndColorsDialog);
        dialog.show();
    }

    private boolean isDefaultLauncher() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                return getPackageName().equals(resolveInfo.activityInfo.packageName);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void requestDefaultLauncherRole() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager roleManager = getSystemService(RoleManager.class);
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                        Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
                        startActivity(intent);
                        return;
                    } else {
                        Toast.makeText(this, isCurrentLanguageMalayalam() ? "KTS Launcher ഇതിനകം ഡിഫോൾട്ട് ആപ്പാണ്" : "KTS Launcher is already default home app", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
            Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {}
        }
    }

    private void toggleNotificationBadges() {
        boolean currentlyEnabled = prefs.getBoolean("notification_badges_enabled", true);
        if (currentlyEnabled) {
            prefs.edit().putBoolean("notification_badges_enabled", false).apply();
            buildGrid();
            Toast.makeText(this, "Notification badges disabled", Toast.LENGTH_SHORT).show();
            announceAccessibility("Notification badges disabled");
        } else {
            if (!NotificationBadgeManager.isNotificationAccessGranted(this)) {
                promptEnableNotificationAccess();
            } else {
                prefs.edit().putBoolean("notification_badges_enabled", true).apply();
                if (BadgeNotificationListener.getInstance() != null) {
                    NotificationBadgeManager.getInstance().updateFromListener(BadgeNotificationListener.getInstance());
                }
                buildGrid();
                Toast.makeText(this, "Notification badges enabled", Toast.LENGTH_SHORT).show();
                announceAccessibility("Notification badges enabled");
            }
        }
    }

    private void promptEnableNotificationAccess() {
        boolean isMl = isCurrentLanguageMalayalam();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String title = isMl ? "നോട്ടിഫിക്കേഷൻ ബാഡ്ജുകൾ ഓൺ ചെയ്യുക" : "Enable Notification Badges";
            String message = isMl ?
                    "ആൻഡ്രോയിഡ് 13-ലും അതിനു മുകളിലും നോട്ടിഫിക്കേഷൻ ആക്‌സസ് നൽകാൻ 2 ലളിതമായ സ്റ്റെപ്പുകൾ:\n\n" +
                    "സ്റ്റെപ്പ് 1: താഴെയുള്ള '1. App Info തുറക്കുക' ക്ലിക്ക് ചെയ്ത്, മുകളിൽ വലതുവശത്തെ 3 ഡോട്ടുകളിൽ (⋮) തൊട്ട് 'Allow restricted settings' നൽകുക.\n\n" +
                    "സ്റ്റെപ്പ് 2: ശേഷം '2. Notification Access' ക്ലിക്ക് ചെയ്ത് ലിസ്റ്റിൽ നിന്ന് KTS Launcher ഓൺ ചെയ്യുക." :
                    "On Android 13 and newer, 2 quick steps are needed:\n\n" +
                    "Step 1: Tap '1. Open App Info', tap the 3 dots (⋮) in top-right corner, and select 'Allow restricted settings'.\n\n" +
                    "Step 2: Then tap '2. Notification Access' and turn ON KTS Launcher.";

            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setNeutralButton(isMl ? "1. App Info തുറക്കുക" : "1. Open App Info", (dialog, which) -> {
                        prefs.edit().putBoolean("pending_restricted_settings_flow", true)
                                    .putBoolean("notification_badges_enabled", true)
                                    .apply();
                        openAppInfo(getPackageName());
                    })
                    .setPositiveButton(isMl ? "2. Notification Access" : "2. Notification Access", (dialog, which) -> {
                        prefs.edit().putBoolean("notification_badges_enabled", true).apply();
                        NotificationBadgeManager.openNotificationAccessSettings(this);
                    })
                    .setNegativeButton(isMl ? "റദ്ദാക്കുക" : "Cancel", null)
                    .show();
        } else {
            String title = isMl ? "നോട്ടിഫിക്കേഷൻ ആക്‌സസ് ആവശ്യമാണ്" : "Notification Access Required";
            String message = isMl ?
                    "നോട്ടിഫിക്കേഷൻ ബാഡ്ജുകൾ കാണിക്കുന്നതിനും മിസ്ഡ് കോളുകളും മെസ്സേജുകളും അറിയുന്നതിനും KTS Launcher-ന് നോട്ടിഫിക്കേഷൻ ആക്‌സസ് നൽകുക." :
                    "To display unread badges and announce missed calls or messages, Android requires Notification Access permission for KTS Launcher.";

            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(isMl ? "അനുവദിക്കുക" : "Grant Access", (dialog, which) -> {
                        prefs.edit().putBoolean("notification_badges_enabled", true).apply();
                        NotificationBadgeManager.openNotificationAccessSettings(this);
                    })
                    .setNegativeButton(isMl ? "റദ്ദാക്കുക" : "Cancel", null)
                    .show();
        }
    }

    private void promptStep2NotificationAccess() {
        boolean isMl = isCurrentLanguageMalayalam();

        String title = isMl ? "സ്റ്റെപ്പ് 2: നോട്ടിഫിക്കേഷൻ ആക്‌സസ്" : "Step 2: Notification Access";
        String message = isMl ?
                "ആദ്യ ഘട്ടം പൂർത്തിയായി! ഇനി താഴെയുള്ള 'തുറക്കുക' അമർത്തി ലിസ്റ്റിൽ നിന്ന് KTS Launcher ഓൺ ചെയ്യുക." :
                "Step 1 complete! Now tap 'Open' and switch ON KTS Launcher in the list.";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(isMl ? "തുറക്കുക" : "Open", (dialog, which) -> {
                    NotificationBadgeManager.openNotificationAccessSettings(this);
                })
                .setNegativeButton(isMl ? "റദ്ദാക്കുക" : "Cancel", null)
                .show();
    }

    private void writeBackupToFile(Uri uri) {
        try {
            JSONObject json = new JSONObject();
            Map<String, ?> allEntries = prefs.getAll();
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.close();
                Toast.makeText(this, "Backup Saved", Toast.LENGTH_SHORT).show();
                announceAccessibility("Backup saved successfully");
            }
        } catch (Exception e) {
            Toast.makeText(this, "Backup Failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void readBackupFromFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is != null) {
                int size = is.available();
                byte[] buffer = new byte[size];
                int bytesRead = is.read(buffer);
                is.close();
                if (bytesRead > 0) {
                    String jsonString = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(jsonString);

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.clear();

                    Iterator<String> keys = json.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object value = json.get(key);
                        if (value instanceof String) editor.putString(key, (String) value);
                        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                        else if (value instanceof Float) editor.putFloat(key, (Float) value);
                        else if (value instanceof Long) editor.putLong(key, (Long) value);
                    }
                    editor.apply();

                    totalPages = prefs.getInt("total_pages", 1);
                    currentPage = 1;
                    buildGrid();

                    Toast.makeText(this, "Layout Restored", Toast.LENGTH_SHORT).show();
                    announceAccessibility("Layout restored. Restarting.");

                    Intent intent = getIntent();
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        finish();
                        startActivity(intent);
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Restore Failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmReset() {
        confirmReset(this::showGeneralSettingsDialog);
    }

    private void confirmReset(Runnable onBack) {
        boolean isMl = isCurrentLanguageMalayalam();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "ലോഞ്ചർ റീസെറ്റ് ചെയ്യണോ?" : "Reset Launcher?")
                .setMessage(isMl ? "എല്ലാ ആപ്പ് ക്രമീകരണങ്ങളും പേജുകളും ഒഴിവാക്കപ്പെടും. ഇത് പഴയപടിയാക്കാൻ സാധിക്കില്ല." : "All app placements and pages will be deleted. This cannot be undone.")
                .setPositiveButton(isMl ? "റീസെറ്റ്" : "Reset", (d, which) -> {
                    prefs.edit().clear().apply();
                    totalPages = 1;
                    currentPage = 1;
                    buildGrid();
                    Toast.makeText(this, isMl ? "റീസെറ്റ് പൂർത്തിയായി" : "Reset Complete", Toast.LENGTH_SHORT).show();
                    announceAccessibility(isMl ? "ലോഞ്ചർ വിജയകരമായി റീസെറ്റ് ചെയ്തു" : "Launcher reset successfully");
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, onBack != null ? onBack : this::showGeneralSettingsDialog);
        dialog.show();
    }

    private void changeSpeechLevel() {
        changeSpeechLevel(this::showSpeechAudioSettingsDialog);
    }

    private void changeSpeechLevel(Runnable onBack) {
        boolean isMl = isCurrentLanguageMalayalam();
        String[] levelsListEn = {
            "All (Detailed: Name, Position, Actions)",
            "Most (Name, Position)",
            "Some (Name, Actions)",
            "None (Minimal: Name only)"
        };
        String[] levelsListMl = {
            "All (പൂർണ്ണ വിവരങ്ങൾ: പേര്, സ്ഥാനം, മാറ്റങ്ങൾ)",
            "Most (പേരും സ്ഥാനവും)",
            "Some (പേരും മാറ്റങ്ങളും)",
            "None (കുറഞ്ഞ വിവരങ്ങൾ: പേര് മാത്രം)"
        };
        int currentLevel = prefs.getInt("acc_level", 0);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "സംസാര ലെവൽ (Speaking Level)" : "Speaking Level")
                .setSingleChoiceItems(isMl ? levelsListMl : levelsListEn, currentLevel, (d, which) -> {
                    prefs.edit().putInt("acc_level", which).apply();
                    buildGrid();
                    String msg = isMl ? ("സംസാര ലെവൽ മാറ്റി") : ("Speaking level changed");
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    announceAccessibility(msg);
                    d.dismiss();
                    if (onBack != null) onBack.run(); else showSpeechAudioSettingsDialog();
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, onBack != null ? onBack : this::showSpeechAudioSettingsDialog);
        dialog.show();
    }

    private void showSpeakingOrderDialog() {
        showSpeakingOrderDialog(this::showSpeechAudioSettingsDialog);
    }

    private void showSpeakingOrderDialog(Runnable onBack) {
        boolean isMl = isCurrentLanguageMalayalam();
        String[] orderNamesEn = {
            "Name, Row, Column (e.g. WhatsApp, Row 1, Column 1)",
            "Row, Column, Name (e.g. Row 1, Column 1, WhatsApp)",
            "Name, Column, Row (e.g. WhatsApp, Column 1, Row 1)",
            "Column, Row, Name (e.g. Column 1, Row 1, WhatsApp)"
        };
        String[] orderNamesMl = {
            "ആദ്യം പേര്, തുടർന്ന് വരി, കോളം (ഉദാ: വാട്ട്‌സ്ആപ്പ്, വരി 1, കോളം 1)",
            "ആദ്യം വരി, കോളം, തുടർന്ന് പേര് (ഉദാ: വരി 1, കോളം 1, വാട്ട്‌സ്ആപ്പ്)",
            "ആദ്യം പേര്, തുടർന്ന് കോളം, വരി (ഉദാ: വാട്ട്‌സ്ആപ്പ്, കോളം 1, വരി 1)",
            "ആദ്യം കോളം, വരി, തുടർന്ന് പേര് (ഉദാ: കോളം 1, വരി 1, വാട്ട്‌സ്ആപ്പ്)"
        };
        int currentOrder = prefs.getInt("speaking_order", 0);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "അറിയിപ്പ് ക്രമം (Announcement Order)" : "Announcement Order")
                .setSingleChoiceItems(isMl ? orderNamesMl : orderNamesEn, currentOrder, (d, which) -> {
                    prefs.edit().putInt("speaking_order", which).apply();
                    buildGrid();
                    String msg = isMl ? ("അറിയിപ്പ് ക്രമം മാറ്റി: " + orderNamesMl[which]) : ("Speaking order set to: " + orderNamesEn[which]);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    announceAccessibility(msg);
                    d.dismiss();
                    if (onBack != null) onBack.run(); else showSpeechAudioSettingsDialog();
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, onBack != null ? onBack : this::showSpeechAudioSettingsDialog);
        dialog.show();
    }

    private void showLanguageSelectionDialog() {
        showLanguageSelectionDialog(this::showGeneralSettingsDialog);
    }

    private void showLanguageSelectionDialog(Runnable onBack) {
        boolean isMl = isCurrentLanguageMalayalam();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isMl ? "ഭാഷ (Language)" : "Language");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        EditText searchBox = new EditText(this);
        searchBox.setHint(isMl ? "തിരയുക..." : "Search...");
        layout.addView(searchBox);
        ListView listView = new ListView(this);
        layout.addView(listView);
        builder.setView(layout);
        builder.setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel());
        AlertDialog dialog = builder.create();
        attachBackHandler(dialog, onBack != null ? onBack : this::showGeneralSettingsDialog);

        Locale[] locales = Locale.getAvailableLocales();
        ArrayList<String> langNames = new ArrayList<>();
        final HashMap<String, String> langMap = new HashMap<>();
        for (Locale loc : locales) {
            String name = loc.getDisplayLanguage();
            String code = loc.getLanguage();
            if (!name.isEmpty() && !langNames.contains(name)) {
                langNames.add(name);
                langMap.put(name, code);
            }
        }
        langNames.sort(String::compareToIgnoreCase);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, langNames);
        listView.setAdapter(adapter);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.getFilter().filter(s); }
            @Override public void afterTextChanged(Editable s) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedLangName = adapter.getItem(position);
            String selectedLangCode = langMap.get(selectedLangName);
            if (selectedLangCode != null) {
                prefs.edit().putString("app_lang", selectedLangCode).apply();
                dialog.dismiss();
                Intent intent = getIntent();
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    finish();
                    startActivity(intent);
                }
            }
        });
        dialog.show();
    }

    private void askRowPosition(boolean isAdding) {
        askRowPosition(isAdding, this::showGridLayoutSettingsDialog);
    }

    private void askRowPosition(boolean isAdding, Runnable onBack) {
        boolean isMl = isCurrentLanguageMalayalam();
        String[] options = {isMl ? "മുകളിൽ (Top)" : "Top", isMl ? "താഴെ (Bottom)" : "Bottom"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isAdding ? (isMl ? "വരി ചേർക്കുക?" : "Add row?") : (isMl ? "വരി നീക്കം ചെയ്യുക?" : "Remove row?"))
                .setItems(options, (d, which) -> {
                    int currentRows = prefs.getInt("grid_rows", 10);
                    if (!isAdding && currentRows <= 1) return;
                    if (which == 0) shiftGridItems(isAdding ? 1 : -1, 0);
                    prefs.edit().putInt("grid_rows", currentRows + (isAdding ? 1 : -1)).apply();
                    buildGrid();
                    if (onBack != null) onBack.run();
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, onBack != null ? onBack : this::showGridLayoutSettingsDialog);
        dialog.show();
    }

    private void askColPosition(boolean isAdding) {
        askColPosition(isAdding, this::showGridLayoutSettingsDialog);
    }

    private void askColPosition(boolean isAdding, Runnable onBack) {
        boolean isMl = isCurrentLanguageMalayalam();
        String[] options = {isMl ? "ഇടത്ത് (Left)" : "Left", isMl ? "വലത്ത് (Right)" : "Right"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isAdding ? (isMl ? "കോളം ചേർക്കുക?" : "Add column?") : (isMl ? "കോളം നീക്കം ചെയ്യുക?" : "Remove column?"))
                .setItems(options, (d, which) -> {
                    int currentCols = prefs.getInt("grid_cols", 4);
                    if (!isAdding && currentCols <= 1) return;
                    if (which == 0) shiftGridItems(0, isAdding ? 1 : -1);
                    prefs.edit().putInt("grid_cols", currentCols + (isAdding ? 1 : -1)).apply();
                    buildGrid();
                    if (onBack != null) onBack.run();
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, onBack != null ? onBack : this::showGridLayoutSettingsDialog);
        dialog.show();
    }

    private void shiftGridItems(int rowShift, int colShift) {
        int maxRows = prefs.getInt("grid_rows", 10);
        int maxCols = prefs.getInt("grid_cols", 4);
        HashMap<String, AppInfo> tempApps = new HashMap<>();

        for (int p = 1; p <= totalPages; p++) {
            for (int r = 1; r <= maxRows; r++) {
                for (int c = 1; c <= maxCols; c++) {
                    String baseKey = getBaseKeyForPage(p, r + "_" + c, currentFolder);
                    String pkg = prefs.getString(baseKey + "_pkg", "");
                    if (!pkg.isEmpty()) tempApps.put(baseKey, new AppInfo(prefs.getString(baseKey + "_name", ""), pkg, 0));
                }
            }
        }
        SharedPreferences.Editor editor = prefs.edit();
        for (int p = 1; p <= totalPages; p++) {
            for (int r = 1; r <= maxRows; r++) {
                for (int c = 1; c <= maxCols; c++) {
                    String baseKey = getBaseKeyForPage(p, r + "_" + c, currentFolder);
                    editor.remove(baseKey + "_pkg").remove(baseKey + "_name");
                }
            }
        }

        for (String k : tempApps.keySet()) {
            String[] parts = k.split("_");
            int rIndex = parts.length - 2;
            int cIndex = parts.length - 1;
            int newR = Integer.parseInt(parts[rIndex]) + rowShift;
            int newC = Integer.parseInt(parts[cIndex]) + colShift;
            AppInfo appInfo = tempApps.get(k);
            if (appInfo != null && newR > 0 && newC > 0) {
                parts[rIndex] = String.valueOf(newR);
                parts[cIndex] = String.valueOf(newC);
                String newKey = String.join("_", parts);
                editor.putString(newKey + "_name", appInfo.name);
                editor.putString(newKey + "_pkg", appInfo.packageName);
            }
        }
        editor.apply();
    }

    private ArrayAdapter<AppInfo> createAppListAdapter(List<AppInfo> apps) {
        return new ArrayAdapter<AppInfo>(this, R.layout.item_app_list, apps) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = getLayoutInflater().inflate(R.layout.item_app_list, parent, false);
                }
                ImageView iconView = row.findViewById(R.id.appListItemIcon);
                TextView nameView = row.findViewById(R.id.appListItemName);
                AppInfo item = getItem(position);
                if (item != null) {
                    nameView.setText(item.name);
                    Drawable appIcon = getCachedAppIcon(item.packageName);
                    if (appIcon != null) {
                        iconView.setImageDrawable(appIcon);
                    } else {
                        iconView.setImageResource(android.R.drawable.sym_def_app_icon);
                    }
                    row.setContentDescription(item.name);
                }
                return row;
            }
        };
    }

    private void showAppSelectionDialog(String fullBaseKey) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select App");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(0xFF1E1E24);

        EditText searchBox = new EditText(this);
        searchBox.setHint("Search apps...");
        searchBox.setHintTextColor(0xFF888899);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_search_bar));
        searchBox.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        layout.addView(searchBox);

        ListView listView = new ListView(this);
        listView.setDivider(new ColorDrawable(0xFF2C2C36));
        listView.setDividerHeight(dpToPx(1));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listParams.setMargins(0, dpToPx(8), 0, 0);
        listView.setLayoutParams(listParams);
        layout.addView(listView);

        builder.setView(layout);
        AlertDialog dialog = builder.create();

        if (allAppsCache.isEmpty()) {
            Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show();
            loadAppsSynchronously();
        }

        ArrayAdapter<AppInfo> adapter = createAppListAdapter(new ArrayList<>(allAppsCache));
        listView.setAdapter(adapter);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.getFilter().filter(s); }
            @Override public void afterTextChanged(Editable s) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo selectedApp = adapter.getItem(position);
            if (selectedApp != null) {
                prefs.edit().putString(fullBaseKey + "_name", selectedApp.name).putString(fullBaseKey + "_pkg", selectedApp.packageName).apply();
                buildGrid();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void showAllAppsDrawer() {
        if (allAppsCache.isEmpty()) {
            Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show();
            loadAppsSynchronously();
        }

        ThemeManager.ActiveTheme activeTheme = ThemeManager.getActiveTheme(prefs);
        final Dialog dialog = new Dialog(this, R.style.Theme_KTSLauncher_AppDrawer);
        dialog.setContentView(R.layout.dialog_app_drawer);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(activeTheme.drawerBgColor));
            ThemeManager.applySystemBarColors(dialog.getWindow(), activeTheme.drawerBgColor, activeTheme.isLight);
        }

        View drawerRoot = dialog.findViewById(R.id.drawerRootLayout);
        if (drawerRoot != null) {
            drawerRoot.setBackgroundColor(activeTheme.drawerBgColor);
        }

        View searchBarContainer = dialog.findViewById(R.id.drawerSearchBarContainer);
        if (searchBarContainer != null) {
            GradientDrawable searchBg = new GradientDrawable();
            searchBg.setShape(GradientDrawable.RECTANGLE);
            searchBg.setCornerRadius(dpToPx(14));
            searchBg.setColor(activeTheme.drawerSearchBgColor);
            if (activeTheme.isLight) {
                searchBg.setStroke(dpToPx(1), 0xFFCBD5E1);
            } else {
                searchBg.setStroke(dpToPx(1), 0xFF383848);
            }
            searchBarContainer.setBackground(searchBg);
        }

        ImageView searchIcon = dialog.findViewById(R.id.drawerSearchIcon);
        if (searchIcon != null) {
            searchIcon.setImageTintList(ColorStateList.valueOf(activeTheme.isLight ? 0xFF64748B : 0xFF9E9EA8));
        }

        View drawerSearch = dialog.findViewById(R.id.drawerSearchInput);
        if (drawerSearch != null && drawerSearch.getParent() instanceof View) {
            View searchBar = (View) drawerSearch.getParent();
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) searchBar.getLayoutParams();
            if (mlp != null) {
                mlp.topMargin = getStatusBarHeight() + dpToPx(8);
                searchBar.setLayoutParams(mlp);
            }
        }

        GridView gridView = dialog.findViewById(R.id.drawerGridView);
        if (gridView != null) {
            gridView.setClipToPadding(false);
            gridView.setPadding(0, 0, 0, getNavigationBarHeight() + dpToPx(36));
        }

        boolean isMl = isCurrentLanguageMalayalam();
        final String lang = isMl ? "ml" : "en";

        EditText searchInput = dialog.findViewById(R.id.drawerSearchInput);
        ImageButton clearBtn = dialog.findViewById(R.id.drawerSearchClear);

        searchInput.setHint(isMl ? "ആപ്പുകൾ തിരയുക..." : "Search apps...");
        searchInput.setTextColor(activeTheme.drawerSearchTextColor);
        searchInput.setHintTextColor(activeTheme.isLight ? 0x88000000 : 0x88FFFFFF);
        if (clearBtn != null) {
            clearBtn.setImageTintList(ColorStateList.valueOf(activeTheme.isLight ? 0xFF64748B : 0xFF9E9EA8));
        }

        List<AppInfo> currentList = new ArrayList<>(allAppsCache);

        class DrawerAdapter extends BaseAdapter implements Filterable {
            private List<AppInfo> displayList = new ArrayList<>(currentList);

            @Override
            public int getCount() {
                return displayList.size();
            }

            @Override
            public AppInfo getItem(int position) {
                return displayList.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = getLayoutInflater().inflate(R.layout.item_app_drawer, parent, false);
                }

                ImageView iconView = view.findViewById(R.id.drawerItemIcon);
                TextView nameView = view.findViewById(R.id.drawerItemName);

                AppInfo appInfo = getItem(position);
                if (appInfo != null) {
                    boolean badgesEnabled = prefs.getBoolean("notification_badges_enabled", true);
                    int bCount = badgesEnabled ? NotificationBadgeManager.getInstance().getBadgeCount(appInfo.packageName) : 0;

                    // Clean app name only - NO "(311)" or "(5)" in visible text!
                    nameView.setText(appInfo.name);
                    nameView.setTextColor(activeTheme.drawerItemTextColor);

                    Drawable baseIcon = getCachedAppIcon(appInfo.packageName);
                    if (baseIcon != null) {
                        Drawable displayIcon = baseIcon;
                        if (badgesEnabled && bCount > 0) {
                            displayIcon = createBadgedIcon(baseIcon, bCount, dpToPx(52));
                        }
                        iconView.setImageDrawable(displayIcon);
                    } else {
                        iconView.setImageResource(android.R.drawable.sym_def_app_icon);
                    }

                    // Content description for screen readers (TalkBack / Jieshuo)
                    String a11yDesc = appInfo.name;
                    if (bCount > 0) {
                        a11yDesc += ", " + NotificationBadgeManager.getInstance().getBadgeAccessibilityText(MainActivity.this, appInfo.packageName, appInfo.name, bCount, lang);
                    }
                    view.setContentDescription(a11yDesc);

                    view.setFocusable(true);
                    view.setClickable(true);
                    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

                    view.setOnClickListener(v -> {
                        provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        setLastInteractedKey("all_apps");
                        launchApp(appInfo.packageName, appInfo.name, appInfo.activityName);
                        dialog.dismiss();
                    });

                    view.setOnLongClickListener(v -> {
                        provideHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        showAppOptionsMenu(null, appInfo.packageName, appInfo.name);
                        dialog.dismiss();
                        return true;
                    });

                    ViewCompat.setAccessibilityDelegate(view, new AccessibilityDelegateCompat() {
                        @Override
                        public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                            super.onInitializeAccessibilityNodeInfo(host, info);
                            info.setClickable(true);
                            info.setLongClickable(true);
                            info.setEnabled(true);
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK);
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK);

                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.action_drawer_add, isMl ? "ഹോം സ്ക്രീനിലേക്ക് ചേർക്കുക" : "Add to Home"));
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    R.id.action_drawer_info, isMl ? "വിവരങ്ങൾ" : "Info"));

                            List<ShortcutInfo> sList = getAppShortcuts(appInfo.packageName);
                            if (!sList.isEmpty()) {
                                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        R.id.action_drawer_shortcuts, isMl ? "ഷോർട്ട്കട്ടുകൾ" : "Shortcuts"));
                            }

                            List<AppWidgetProviderInfo> wList = getWidgetsForPackage(appInfo.packageName);
                            if (wList != null && !wList.isEmpty()) {
                                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        R.id.action_drawer_widgets, isMl ? "വിഡ്ജറ്റുകൾ" : "Widgets"));
                            }

                            if (isInstalledFromPlayStore(appInfo.packageName)) {
                                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        R.id.action_drawer_share, isMl ? "പങ്കിടുക" : "Share"));
                                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        R.id.action_drawer_update, isMl ? "അപ്ഡേറ്റ്" : "Update"));
                            }
                        }

                        @Override
                        public boolean performAccessibilityAction(@NonNull View host, int action, @Nullable Bundle args) {
                            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
                                provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                                setLastInteractedKey("all_apps");
                                launchApp(appInfo.packageName, appInfo.name, appInfo.activityName);
                                dialog.dismiss();
                                return true;
                            } else if (action == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK) {
                                provideHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                showAppOptionsMenu(null, appInfo.packageName, appInfo.name);
                                dialog.dismiss();
                                return true;
                            } else if (action == R.id.action_drawer_add) {
                                promptAddMethod(appInfo.packageName, appInfo.name);
                                dialog.dismiss();
                                return true;
                            } else if (action == R.id.action_drawer_shortcuts) {
                                showShortcutsDialog(appInfo.packageName, appInfo.name, getAppShortcuts(appInfo.packageName));
                                dialog.dismiss();
                                return true;
                            } else if (action == R.id.action_drawer_widgets) {
                                showWidgetsDialog(appInfo.packageName);
                                dialog.dismiss();
                                return true;
                            } else if (action == R.id.action_drawer_share) {
                                openShareMenu(appInfo.packageName);
                                return true;
                            } else if (action == R.id.action_drawer_update) {
                                openPlayStore(appInfo.packageName);
                                return true;
                            } else if (action == R.id.action_drawer_info) {
                                openAppInfo(appInfo.packageName);
                                return true;
                            }
                            return super.performAccessibilityAction(host, action, args);
                        }
                    });
                }
                return view;
            }

            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        if (constraint == null || constraint.length() == 0) {
                            results.values = new ArrayList<>(allAppsCache);
                            results.count = allAppsCache.size();
                        } else {
                            String filterString = constraint.toString().toLowerCase().trim();
                            List<AppInfo> filtered = new ArrayList<>();
                            for (AppInfo info : allAppsCache) {
                                if (info.name.toLowerCase().contains(filterString) ||
                                    info.packageName.toLowerCase().contains(filterString)) {
                                    filtered.add(info);
                                }
                            }
                            results.values = filtered;
                            results.count = filtered.size();
                        }
                        return results;
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        displayList = (List<AppInfo>) results.values;
                        notifyDataSetChanged();
                    }
                };
            }
        }

        DrawerAdapter adapter = new DrawerAdapter();
        gridView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.length() > 0) {
                    clearBtn.setVisibility(View.VISIBLE);
                } else {
                    clearBtn.setVisibility(View.GONE);
                }
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        clearBtn.setOnClickListener(v -> searchInput.setText(""));

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo selectedApp = adapter.getItem(position);
            if (selectedApp != null) {
                provideHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                setLastInteractedKey("all_apps");
                launchApp(selectedApp.packageName, selectedApp.name, selectedApp.activityName);
                dialog.dismiss();
            }
        });

        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            AppInfo selectedApp = adapter.getItem(position);
            if (selectedApp != null) {
                provideHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showAppOptionsMenu(null, selectedApp.packageName, selectedApp.name);
                dialog.dismiss();
            }
            return true;
        });

        dialog.show();
    }

    @SuppressLint("UnsafeIntentLaunch")
    private void openShareMenu(String pkgName) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=" + pkgName);
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    @SuppressLint("UnsafeIntentLaunch")
    private void openPlayStore(String pkgName) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkgName))); }
        catch (Exception e) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkgName))); }
    }

    @SuppressLint("UnsafeIntentLaunch")
    private void openAppInfo(String pkgName) {
        Intent infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        infoIntent.setData(Uri.parse("package:" + pkgName));
        startActivity(infoIntent);
    }

    private void showGestureSettingsDialog() {
        boolean isMl = isCurrentLanguageMalayalam();
        String[] categories = {
                isMl ? "ഒരു വിരൽ ജെസ്റ്ററുകൾ (1 Finger - 2 Gestures)" : "1 Finger Gestures (2)",
                isMl ? "രണ്ട് വിരൽ ജെസ്റ്ററുകൾ (2 Fingers - 7 Gestures)" : "2 Finger Gestures (7)",
                isMl ? "മൂന്ന് വിരൽ ജെസ്റ്ററുകൾ (3 Fingers - 7 Gestures)" : "3 Finger Gestures (7)",
                isMl ? "നാല് വിരൽ ജെസ്റ്ററുകൾ (4 Fingers - 7 Gestures)" : "4 Finger Gestures (7)",
                isMl ? "എല്ലാ ജെസ്റ്ററുകളും റീസെറ്റ് ചെയ്യുക" : "Reset All Gestures to Default"
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "ജെസ്റ്റർ ക്രമീകരണങ്ങൾ" : "Gesture Settings")
                .setItems(categories, (d, which) -> {
                    if (which == 0) showGestureCategoryDialog(1);
                    else if (which == 1) showGestureCategoryDialog(2);
                    else if (which == 2) showGestureCategoryDialog(3);
                    else if (which == 3) showGestureCategoryDialog(4);
                    else if (which == 4) confirmResetGestures();
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, this::showSettingsMenu);
        dialog.show();
    }

    private void showGestureCategoryDialog(int fingers) {
        boolean isMl = isCurrentLanguageMalayalam();
        String[] keys;
        String title;
        if (fingers == 1) {
            keys = new String[]{"swipe_up_1", "swipe_down_1"};
            title = isMl ? "ഒരു വിരൽ ജെസ്റ്ററുകൾ" : "1 Finger Gestures";
        } else if (fingers == 2) {
            keys = new String[]{
                    "swipe_up_2", "swipe_down_2", "swipe_left_2", "swipe_right_2",
                    "single_tap_2", "double_tap_2", "triple_tap_2"
            };
            title = isMl ? "രണ്ട് വിരൽ ജെസ്റ്ററുകൾ" : "2 Finger Gestures";
        } else if (fingers == 3) {
            keys = new String[]{
                    "swipe_up_3", "swipe_down_3", "swipe_left_3", "swipe_right_3",
                    "single_tap_3", "double_tap_3", "triple_tap_3"
            };
            title = isMl ? "മൂന്ന് വിരൽ ജെസ്റ്ററുകൾ" : "3 Finger Gestures";
        } else {
            keys = new String[]{
                    "swipe_up_4", "swipe_down_4", "swipe_left_4", "swipe_right_4",
                    "single_tap_4", "double_tap_4", "triple_tap_4"
            };
            title = isMl ? "നാല് വിരൽ ജെസ്റ്ററുകൾ" : "4 Finger Gestures";
        }

        String[] displayItems = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            displayItems[i] = getGestureDisplayName(k, isMl) + "\n   ↳ " + getGestureActionSummary(k, isMl);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(displayItems, (d, which) -> {
                    String selectedKey = keys[which];
                    showGestureActionTypeDialog(selectedKey, getGestureDisplayName(selectedKey, isMl), fingers);
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, this::showGestureSettingsDialog);
        dialog.show();
    }

    private void showGestureActionTypeDialog(String key, String gestureDisplayName, int returnFingers) {
        boolean isMl = isCurrentLanguageMalayalam();
        String currentSummary = getGestureActionSummary(key, isMl);
        String[] options = {
                isMl ? "ഒന്നുമില്ല (റദ്ദാക്കുക)" : "None (Disabled)",
                isMl ? "ലോഞ്ചർ ഷോർട്ട്കട്ടുകൾ" : "Launcher Shortcuts",
                isMl ? "ആപ്ലിക്കേഷനുകൾ (Apps)" : "Apps"
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(gestureDisplayName + "\n(" + (isMl ? "നിലവിൽ: " : "Current: ") + currentSummary + ")")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        prefs.edit()
                                .putString("gesture_" + key + "_type", "none")
                                .remove("gesture_" + key + "_action")
                                .remove("gesture_" + key + "_name")
                                .apply();
                        Toast.makeText(this, (isMl ? "റദ്ദാക്കി: " : "Disabled: ") + gestureDisplayName, Toast.LENGTH_SHORT).show();
                        announceAccessibility((isMl ? "റദ്ദാക്കി: " : "Disabled: ") + gestureDisplayName);
                        showGestureCategoryDialog(returnFingers);
                    } else if (which == 1) {
                        showLauncherShortcutPicker(key, gestureDisplayName, returnFingers);
                    } else if (which == 2) {
                        showAppSelectionForGesture(key, gestureDisplayName, returnFingers);
                    }
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, () -> showGestureCategoryDialog(returnFingers));
        dialog.show();
    }

    private void showLauncherShortcutPicker(String key, String gestureDisplayName, int returnFingers) {
        boolean isMl = isCurrentLanguageMalayalam();
        final String[] shortcutKeys = {
                "shortcut_universal_search",
                "shortcut_app_drawer",
                "shortcut_next_screen",
                "shortcut_prev_screen",
                "shortcut_google_feed",
                "shortcut_settings",
                "shortcut_notifications",
                "shortcut_quick_settings",
                "shortcut_audio_routing",
                "shortcut_check_update"
        };
        final String[] shortcutLabels = {
                isMl ? "സാർവത്രിക തിരച്ചിൽ (Universal Search)" : "Universal Search",
                isMl ? "ആപ്പ് ഡ്രോയർ (App Drawer)" : "App Drawer",
                isMl ? "അടുത്ത സ്ക്രീൻ (Next Screen)" : "Next Screen",
                isMl ? "മുൻപത്തെ സ്ക്രീൻ (Previous Screen)" : "Previous Screen",
                isMl ? "ഗൂഗിൾ ഫീഡ് (Google Feed)" : "Google Feed",
                isMl ? "ലോഞ്ചർ ക്രമീകരണങ്ങൾ (Settings)" : "Launcher Settings",
                isMl ? "അറിയിപ്പ് പാനൽ (Notifications)" : "Notifications Panel",
                isMl ? "ദ്രുത ക്രമീകരണങ്ങൾ (Quick Settings)" : "Quick Settings",
                isMl ? "ഓഡിയോ റൂട്ടിംഗ് (Audio Routing)" : "Audio Routing",
                isMl ? "അപ്‌ഡേറ്റ് പരിശോധിക്കുക (Check for Updates)" : "Check for Updates"
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(gestureDisplayName + ": " + (isMl ? "ഷോർട്ട്കട്ട് തിരഞ്ഞെടുക്കുക" : "Select Shortcut"))
                .setItems(shortcutLabels, (d, which) -> {
                    String selectedAction = shortcutKeys[which];
                    String selectedName = shortcutLabels[which];
                    prefs.edit()
                            .putString("gesture_" + key + "_type", "shortcut")
                            .putString("gesture_" + key + "_action", selectedAction)
                            .putString("gesture_" + key + "_name", selectedName)
                            .apply();
                    Toast.makeText(this, gestureDisplayName + " -> " + selectedName, Toast.LENGTH_SHORT).show();
                    announceAccessibility(gestureDisplayName + " " + selectedName);
                    showGestureCategoryDialog(returnFingers);
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, () -> showGestureActionTypeDialog(key, gestureDisplayName, returnFingers));
        dialog.show();
    }

    private void showAppSelectionForGesture(String key, String gestureDisplayName, int returnFingers) {
        boolean isMl = isCurrentLanguageMalayalam();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(gestureDisplayName + ": " + (isMl ? "ആപ്പ് തിരഞ്ഞെടുക്കുക" : "Select App"));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(0xFF1E1E24);
        EditText searchBox = new EditText(this);
        searchBox.setHint(isMl ? "തിരയുക..." : "Search apps...");
        searchBox.setHintTextColor(0xFF888899);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_search_bar));
        searchBox.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        layout.addView(searchBox);
        ListView listView = new ListView(this);
        listView.setDivider(new ColorDrawable(0xFF2C2C36));
        listView.setDividerHeight(dpToPx(1));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listParams.setMargins(0, dpToPx(8), 0, 0);
        listView.setLayoutParams(listParams);
        layout.addView(listView);
        builder.setView(layout);
        builder.setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel());
        AlertDialog dialog = builder.create();
        attachBackHandler(dialog, () -> showGestureActionTypeDialog(key, gestureDisplayName, returnFingers));

        if (allAppsCache.isEmpty()) {
            Toast.makeText(this, isMl ? "ലോഡ് ചെയ്യുന്നു..." : "Loading...", Toast.LENGTH_SHORT).show();
            loadAppsSynchronously();
        }

        ArrayAdapter<AppInfo> adapter = createAppListAdapter(new ArrayList<>(allAppsCache));
        listView.setAdapter(adapter);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.getFilter().filter(s); }
            @Override public void afterTextChanged(Editable s) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo selectedApp = adapter.getItem(position);
            if (selectedApp != null) {
                String act = selectedApp.activityName != null ? selectedApp.activityName : "";
                prefs.edit()
                        .putString("gesture_" + key + "_type", "app")
                        .putString("gesture_" + key + "_action", selectedApp.packageName + ":" + act)
                        .putString("gesture_" + key + "_name", selectedApp.name)
                        .apply();
                Toast.makeText(this, gestureDisplayName + " -> " + selectedApp.name, Toast.LENGTH_SHORT).show();
                announceAccessibility(gestureDisplayName + " " + selectedApp.name);
                dialog.dismiss();
                showGestureCategoryDialog(returnFingers);
            }
        });
        dialog.show();
    }

    private void confirmResetGestures() {
        boolean isMl = isCurrentLanguageMalayalam();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isMl ? "ജെസ്റ്ററുകൾ റീസെറ്റ് ചെയ്യണോ?" : "Reset Gestures?")
                .setMessage(isMl ? "എല്ലാ ജെസ്റ്ററുകളും ഡിഫോൾട്ട് രീതിയിലേക്ക് പുനഃക്രമീകരിക്കപ്പെടും." : "All gestures will be reset to launcher defaults.")
                .setPositiveButton(isMl ? "റീസെറ്റ്" : "Reset", (d, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    String[] allKeys = {
                            "swipe_up_1", "swipe_down_1",
                            "swipe_up_2", "swipe_down_2", "swipe_left_2", "swipe_right_2", "single_tap_2", "double_tap_2", "triple_tap_2",
                            "swipe_up_3", "swipe_down_3", "swipe_left_3", "swipe_right_3", "single_tap_3", "double_tap_3", "triple_tap_3",
                            "swipe_up_4", "swipe_down_4", "swipe_left_4", "swipe_right_4", "single_tap_4", "double_tap_4", "triple_tap_4"
                    };
                    for (String k : allKeys) {
                        editor.remove("gesture_" + k + "_type");
                        editor.remove("gesture_" + k + "_action");
                        editor.remove("gesture_" + k + "_name");
                    }
                    editor.apply();
                    Toast.makeText(this, isMl ? "ജെസ്റ്ററുകൾ ഡിഫോൾട്ടിലേക്ക് മാറ്റി" : "Gestures reset to default", Toast.LENGTH_SHORT).show();
                    announceAccessibility(isMl ? "ജെസ്റ്ററുകൾ റീസെറ്റ് ചെയ്തു" : "Gestures reset");
                    showGestureSettingsDialog();
                })
                .setNegativeButton(isMl ? "തിരികെ" : "Back", (d, which) -> d.cancel())
                .create();
        attachBackHandler(dialog, this::showGestureSettingsDialog);
        dialog.show();
    }

    private String getGestureDisplayName(String key, boolean isMl) {
        switch (key) {
            case "swipe_up_1": return isMl ? "ഒരു വിരൽ മുകളിലേക്ക് സ്വൈപ്പ്" : "Single finger swipe up";
            case "swipe_down_1": return isMl ? "ഒരു വിരൽ താഴേക്ക് സ്വൈപ്പ്" : "Single finger swipe down";
            case "swipe_up_2": return isMl ? "രണ്ട് വിരൽ മുകളിലേക്ക് സ്വൈപ്പ്" : "Two finger swipe up";
            case "swipe_down_2": return isMl ? "രണ്ട് വിരൽ താഴേക്ക് സ്വൈപ്പ്" : "Two finger swipe down";
            case "swipe_left_2": return isMl ? "രണ്ട് വിരൽ ഇടത്തോട്ട് സ്വൈപ്പ്" : "Two finger swipe left";
            case "swipe_right_2": return isMl ? "രണ്ട് വിരൽ വലത്തോട്ട് സ്വൈപ്പ്" : "Two finger swipe right";
            case "single_tap_2": return isMl ? "രണ്ട് വിരൽ ഒറ്റ ടാപ്പ്" : "Two finger single tap";
            case "double_tap_2": return isMl ? "രണ്ട് വിരൽ ഇരട്ട ടാപ്പ്" : "Two finger double tap";
            case "triple_tap_2": return isMl ? "രണ്ട് വിരൽ മൂന്ന് ടാപ്പ്" : "Two finger triple tap";
            case "swipe_up_3": return isMl ? "മൂന്ന് വിരൽ മുകളിലേക്ക് സ്വൈപ്പ്" : "Three finger swipe up";
            case "swipe_down_3": return isMl ? "മൂന്ന് വിരൽ താഴേക്ക് സ്വൈപ്പ്" : "Three finger swipe down";
            case "swipe_left_3": return isMl ? "മൂന്ന് വിരൽ ഇടത്തോട്ട് സ്വൈപ്പ്" : "Three finger swipe left";
            case "swipe_right_3": return isMl ? "മൂന്ന് വിരൽ വലത്തോട്ട് സ്വൈപ്പ്" : "Three finger swipe right";
            case "single_tap_3": return isMl ? "മൂന്ന് വിരൽ ഒറ്റ ടാപ്പ്" : "Three finger single tap";
            case "double_tap_3": return isMl ? "മൂന്ന് വിരൽ ഇരട്ട ടാപ്പ്" : "Three finger double tap";
            case "triple_tap_3": return isMl ? "മൂന്ന് വിരൽ മൂന്ന് ടാപ്പ്" : "Three finger triple tap";
            case "swipe_up_4": return isMl ? "നാല് വിരൽ മുകളിലേക്ക് സ്വൈപ്പ്" : "Four finger swipe up";
            case "swipe_down_4": return isMl ? "നാല് വിരൽ താഴേക്ക് സ്വൈപ്പ്" : "Four finger swipe down";
            case "swipe_left_4": return isMl ? "നാല് വിരൽ ഇടത്തോട്ട് സ്വൈപ്പ്" : "Four finger swipe left";
            case "swipe_right_4": return isMl ? "നാല് വിരൽ വലത്തോട്ട് സ്വൈപ്പ്" : "Four finger swipe right";
            case "single_tap_4": return isMl ? "നാല് വിരൽ ഒറ്റ ടാപ്പ്" : "Four finger single tap";
            case "double_tap_4": return isMl ? "നാല് വിരൽ ഇരട്ട ടാപ്പ്" : "Four finger double tap";
            case "triple_tap_4": return isMl ? "നാല് വിരൽ മൂന്ന് ടാപ്പ്" : "Four finger triple tap";
            default: return key;
        }
    }

    private String getGestureActionSummary(String key, boolean isMl) {
        String type = prefs.getString("gesture_" + key + "_type", null);
        String action = prefs.getString("gesture_" + key + "_action", null);
        String name = prefs.getString("gesture_" + key + "_name", null);

        if (type == null) {
            if ("swipe_up_1".equals(key) || "swipe_up_2".equals(key)) {
                return isMl ? "ആപ്പ് ഡ്രോയർ (ഡിഫോൾട്ട്)" : "App Drawer (Default)";
            } else if ("swipe_down_1".equals(key) || "swipe_down_2".equals(key)) {
                return isMl ? "സാർവത്രിക തിരച്ചിൽ (ഡിഫോൾട്ട്)" : "Universal Search (Default)";
            } else if ("swipe_left_2".equals(key)) {
                return isMl ? "അടുത്ത സ്ക്രീൻ (ഡിഫോൾട്ട്)" : "Next Screen (Default)";
            } else if ("swipe_right_2".equals(key)) {
                return isMl ? "മുൻപത്തെ സ്ക്രീൻ (ഡിഫോൾട്ട്)" : "Previous Screen (Default)";
            } else if ("double_tap_2".equals(key)) {
                return isMl ? "ലോഞ്ചർ ക്രമീകരണങ്ങൾ (ഡിഫോൾട്ട്)" : "Launcher Settings (Default)";
            }
            return isMl ? "ഒന്നുമില്ല (Disabled)" : "None";
        }

        if ("none".equals(type)) {
            return isMl ? "ഒന്നുമില്ല (Disabled)" : "None";
        }

        if ("shortcut".equals(type)) {
            if (action == null) return isMl ? "ഒന്നുമില്ല" : "None";
            switch (action) {
                case "shortcut_universal_search": return isMl ? "സാർവത്രിക തിരച്ചിൽ" : "Universal Search";
                case "shortcut_app_drawer": return isMl ? "ആപ്പ് ഡ്രോയർ" : "App Drawer";
                case "shortcut_next_screen": return isMl ? "അടുത്ത സ്ക്രീൻ" : "Next Screen";
                case "shortcut_prev_screen": return isMl ? "മുൻപത്തെ സ്ക്രീൻ" : "Previous Screen";
                case "shortcut_google_feed": return isMl ? "ഗൂഗിൾ ഫീഡ്" : "Google Feed";
                case "shortcut_settings": return isMl ? "ലോഞ്ചർ ക്രമീകരണങ്ങൾ" : "Launcher Settings";
                case "shortcut_notifications": return isMl ? "അറിയിപ്പ് പാനൽ" : "Notifications Panel";
                case "shortcut_quick_settings": return isMl ? "ദ്രുത ക്രമീകരണങ്ങൾ" : "Quick Settings";
                case "shortcut_audio_routing":
                case "shortcut_audio_routing_toggle":
                    return isMl ? "ഓഡിയോ റൂട്ടിംഗ്" : "Audio Routing";
                case "shortcut_check_update":
                    return isMl ? "അപ്‌ഡേറ്റ് പരിശോധിക്കുക" : "Check for Updates";
                default: return name != null ? name : action;
            }
        }

        if ("app".equals(type)) {
            return (isMl ? "ആപ്പ്: " : "App: ") + (name != null ? name : action);
        }

        return isMl ? "ഒന്നുമില്ല" : "None";
    }

    private void executeGestureAction(String gestureKey) {
        String type = prefs.getString("gesture_" + gestureKey + "_type", null);
        String action = prefs.getString("gesture_" + gestureKey + "_action", null);

        if (type == null) {
            if ("swipe_up_1".equals(gestureKey) || "swipe_up_2".equals(gestureKey)) {
                type = "shortcut";
                action = "shortcut_app_drawer";
            } else if ("swipe_down_1".equals(gestureKey) || "swipe_down_2".equals(gestureKey)) {
                type = "shortcut";
                action = "shortcut_universal_search";
            } else if ("swipe_left_2".equals(gestureKey)) {
                type = "shortcut";
                action = "shortcut_next_screen";
            } else if ("swipe_right_2".equals(gestureKey)) {
                type = "shortcut";
                action = "shortcut_prev_screen";
            } else if ("double_tap_2".equals(gestureKey)) {
                type = "shortcut";
                action = "shortcut_settings";
            } else {
                type = "none";
            }
        }

        if ("none".equals(type) || action == null || action.isEmpty()) {
            return;
        }

        boolean isMl = isCurrentLanguageMalayalam();
        provideHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);

        if ("shortcut".equals(type)) {
            switch (action) {
                case "shortcut_universal_search":
                    announceAccessibility(isMl ? "സാർവത്രിക തിരച്ചിൽ" : "Universal Search");
                    showUniversalSearchDialog();
                    break;
                case "shortcut_app_drawer":
                    announceAccessibility(isMl ? "ആപ്പ് ഡ്രോയർ" : "App Drawer");
                    showAllAppsDrawer();
                    break;
                case "shortcut_next_screen":
                    if (currentPage < totalPages) {
                        currentPage++;
                        provideHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        buildGrid();
                        announceAccessibility(isMl ? "പേജ് " + currentPage : "Page " + currentPage);
                    } else {
                        askCreateNewPage();
                    }
                    break;
                case "shortcut_prev_screen":
                    if (currentPage > 1) {
                        currentPage--;
                        provideHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        buildGrid();
                        announceAccessibility(isMl ? "പേജ് " + currentPage : "Page " + currentPage);
                    } else if (prefs.getBoolean("google_feed_enabled", true)) {
                        openGoogleFeed();
                    }
                    break;
                case "shortcut_google_feed":
                    announceAccessibility(isMl ? "ഗൂഗിൾ ഫീഡ്" : "Google Feed");
                    openGoogleFeed();
                    break;
                case "shortcut_settings":
                    announceAccessibility(isMl ? "ക്രമീകരണങ്ങൾ" : "Settings");
                    showSettingsMenu();
                    break;
                case "shortcut_notifications":
                    announceAccessibility(isMl ? "അറിയിപ്പ് പാനൽ" : "Notifications");
                    expandStatusBar(false);
                    break;
                case "shortcut_quick_settings":
                    announceAccessibility(isMl ? "ദ്രുത ക്രമീകരണങ്ങൾ" : "Quick Settings");
                    expandStatusBar(true);
                    break;
                case "shortcut_audio_routing":
                case "shortcut_audio_routing_toggle":
                    AudioRoutingManager.getInstance().toggleAudioSeparation(this);
                    buildGrid();
                    break;
                case "shortcut_check_update":
                    announceAccessibility(isMl ? "അപ്‌ഡേറ്റ് പരിശോധിക്കുന്നു" : "Checking for updates");
                    AppUpdateManager.getInstance().checkForUpdates(this, true);
                    break;
            }
        } else if ("app".equals(type)) {
            String appName = prefs.getString("gesture_" + gestureKey + "_name", "App");
            announceAccessibility(appName);
            String[] parts = action.split(":", 2);
            String pkg = parts[0];
            String act = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
            launchApp(pkg, appName, act);
        }
    }

    @SuppressLint("WrongConstant")
    private void expandStatusBar(boolean quickSettings) {
        try {
            Object sbservice = getSystemService("statusbar");
            if (sbservice != null) {
                Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
                String methodName = quickSettings ? "expandSettingsPanel" : "expandNotificationsPanel";
                Method method = statusbarManager.getMethod(methodName);
                method.invoke(sbservice);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to expand status bar", e);
        }
    }

    class MultiFingerGestureHandler {
        private int maxPointers = 1;
        private boolean multiFinger = false;
        private boolean gestureFired = false;
        private boolean pointerMoved = false;
        private long startTime = 0L;

        private final Map<Integer, PointF> pointerDownMap = new HashMap<>();

        private int pendingTapFingers = 0;
        private int tapCount = 0;
        private final Handler tapHandler = new Handler(Looper.getMainLooper());
        private final Runnable tapRunnable = () -> {
            if (tapCount > 0 && pendingTapFingers >= 2) {
                String actionPrefix = tapCount == 1 ? "single_tap_" : (tapCount == 2 ? "double_tap_" : "triple_tap_");
                executeGestureAction(actionPrefix + pendingTapFingers);
            }
            tapCount = 0;
            pendingTapFingers = 0;
        };

        public void resetTouch() {
            maxPointers = 1;
            multiFinger = false;
            gestureFired = false;
            pointerMoved = false;
            pointerDownMap.clear();
        }

        public boolean isMultiFinger() {
            return multiFinger || maxPointers >= 2;
        }

        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int count = event.getPointerCount();

            if (count > maxPointers) {
                maxPointers = count;
            }
            if (maxPointers >= 2) {
                multiFinger = true;
            }

            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    resetTouch();
                    startTime = System.currentTimeMillis();
                    int id = event.getPointerId(0);
                    pointerDownMap.put(id, new PointF(event.getX(0), event.getY(0)));
                    break;
                }

                case MotionEvent.ACTION_POINTER_DOWN: {
                    int actionIdx = event.getActionIndex();
                    int id = event.getPointerId(actionIdx);
                    pointerDownMap.put(id, new PointF(event.getX(actionIdx), event.getY(actionIdx)));
                    break;
                }

                case MotionEvent.ACTION_MOVE: {
                    if (maxPointers >= 2) {
                        float totalDx = 0f;
                        float totalDy = 0f;
                        int validCount = 0;

                        for (int i = 0; i < count; i++) {
                            int id = event.getPointerId(i);
                            PointF downPt = pointerDownMap.get(id);
                            if (downPt != null) {
                                float dx = event.getX(i) - downPt.x;
                                float dy = event.getY(i) - downPt.y;
                                totalDx += dx;
                                totalDy += dy;
                                validCount++;

                                if (Math.hypot(dx, dy) > dpToPx(35)) {
                                    pointerMoved = true;
                                }
                            }
                        }

                        if (!gestureFired && validCount > 0) {
                            float avgDx = totalDx / validCount;
                            float avgDy = totalDy / validCount;
                            float swipeThreshold = dpToPx(50);

                            if (Math.hypot(avgDx, avgDy) > swipeThreshold) {
                                gestureFired = true;
                                pointerMoved = true;
                                int fingers = Math.min(maxPointers, 4);
                                String gestureKey;
                                if (Math.abs(avgDy) > Math.abs(avgDx)) {
                                    gestureKey = (avgDy < 0 ? "swipe_up_" : "swipe_down_") + fingers;
                                } else {
                                    gestureKey = (avgDx < 0 ? "swipe_left_" : "swipe_right_") + fingers;
                                }
                                executeGestureAction(gestureKey);
                                return true;
                            }
                        }
                    }
                    if (isMultiFinger()) {
                        return true;
                    }
                    break;
                }

                case MotionEvent.ACTION_POINTER_UP: {
                    if (isMultiFinger()) {
                        return true;
                    }
                    break;
                }

                case MotionEvent.ACTION_UP: {
                    if (isMultiFinger()) {
                        long duration = System.currentTimeMillis() - startTime;
                        if (!gestureFired && !pointerMoved && duration < 500) {
                            int fingers = Math.min(maxPointers, 4);
                            handleTap(fingers);
                        }
                        return true;
                    }
                    break;
                }

                case MotionEvent.ACTION_CANCEL: {
                    resetTouch();
                    break;
                }
            }
            return false;
        }

        private void handleTap(int fingers) {
            tapHandler.removeCallbacks(tapRunnable);
            if (pendingTapFingers == fingers) {
                tapCount++;
            } else {
                if (tapCount > 0 && pendingTapFingers >= 2) {
                    tapRunnable.run();
                }
                pendingTapFingers = fingers;
                tapCount = 1;
            }

            if (tapCount >= 3) {
                executeGestureAction("triple_tap_" + pendingTapFingers);
                tapCount = 0;
                pendingTapFingers = 0;
            } else {
                tapHandler.postDelayed(tapRunnable, 320);
            }
        }
    }
}