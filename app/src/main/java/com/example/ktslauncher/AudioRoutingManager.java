package com.example.ktslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

public class AudioRoutingManager {

    private static final String TAG = "AudioRoutingManager";
    public static final String PREF_AUDIO_SEPARATION_ENABLED = "audio_separation_enabled";
    private static final int KEEP_ALIVE_INTERVAL_MS = 1000;

    private static AudioRoutingManager instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isSeparationActive = false;
    private Context appContext;

    private final Runnable separationKeepAliveRunnable = new Runnable() {
        @Override
        public void run() {
            if (isSeparationActive && appContext != null) {
                ensureAudioSeparationStable(appContext);
                handler.postDelayed(this, KEEP_ALIVE_INTERVAL_MS);
            }
        }
    };

    private AudioRoutingManager() {}

    public static synchronized AudioRoutingManager getInstance() {
        if (instance == null) {
            instance = new AudioRoutingManager();
        }
        return instance;
    }

    private AudioManager getAudioManager(Context context) {
        if (context == null) return null;
        this.appContext = context.getApplicationContext();
        return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean isAudioSeparationEnabled(Context context) {
        if (context == null) return isSeparationActive;
        try {
            SharedPreferences prefs = MainActivity.getSafeDeviceProtectedPrefs(context);
            return prefs.getBoolean(PREF_AUDIO_SEPARATION_ENABLED, false);
        } catch (Exception e) {
            return isSeparationActive;
        }
    }

    private AudioDeviceInfo getSpeakerDevice(AudioManager am) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am != null) {
            try {
                List<AudioDeviceInfo> devices = am.getAvailableCommunicationDevices();
                for (AudioDeviceInfo device : devices) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        return device;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "getSpeakerDevice error", e);
            }
        }
        return null;
    }

    public void initRouting(Context context) {
        if (context == null) return;
        this.appContext = context.getApplicationContext();
        boolean enabled = isAudioSeparationEnabled(context);
        this.isSeparationActive = enabled;
        if (enabled) {
            applyAudioSeparationRouting(context);
            handler.removeCallbacks(separationKeepAliveRunnable);
            handler.postDelayed(separationKeepAliveRunnable, KEEP_ALIVE_INTERVAL_MS);
        } else {
            AudioManager am = getAudioManager(context);
            if (am != null) {
                try {
                    if (am.getMode() != AudioManager.MODE_NORMAL) {
                        am.setMode(AudioManager.MODE_NORMAL);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public boolean toggleAudioSeparation(Context context) {
        if (isAudioSeparationEnabled(context)) {
            disableAudioSeparation(context, true);
            return false;
        } else {
            enableAudioSeparation(context, true);
            return true;
        }
    }

    public void enableAudioSeparation(Context context, boolean announce) {
        this.appContext = context != null ? context.getApplicationContext() : null;
        isSeparationActive = true;
        if (context != null) {
            try {
                SharedPreferences prefs = MainActivity.getSafeDeviceProtectedPrefs(context);
                prefs.edit().putBoolean(PREF_AUDIO_SEPARATION_ENABLED, true).apply();
            } catch (Exception ignored) {}
        }

        applyAudioSeparationRouting(context);

        handler.removeCallbacks(separationKeepAliveRunnable);
        handler.postDelayed(separationKeepAliveRunnable, KEEP_ALIVE_INTERVAL_MS);

        if (announce && context != null) {
            boolean isMl = isMalayalam(context);
            String toastText = isMl ? "ഓഡിയോ റൂട്ടിംഗ്: ഓൺ" : "Audio Routing: ON";
            String a11yText = isMl ? "ഓഡിയോ റൂട്ടിംഗ് ഓൺ ചെയ്തു" : "Audio routing enabled";
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show();
            if (context instanceof MainActivity) {
                ((MainActivity) context).announceAccessibility(a11yText);
            }
        }
    }

    public void disableAudioSeparation(Context context, boolean announce) {
        this.appContext = context != null ? context.getApplicationContext() : null;
        isSeparationActive = false;
        if (context != null) {
            try {
                SharedPreferences prefs = MainActivity.getSafeDeviceProtectedPrefs(context);
                prefs.edit().putBoolean(PREF_AUDIO_SEPARATION_ENABLED, false).apply();
            } catch (Exception ignored) {}
        }

        handler.removeCallbacks(separationKeepAliveRunnable);

        AudioManager am = getAudioManager(context);
        if (am != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        am.clearCommunicationDevice();
                    } catch (Exception ignored) {}
                }
                am.setSpeakerphoneOn(false);
                if (am.getMode() != AudioManager.MODE_NORMAL) {
                    am.setMode(AudioManager.MODE_NORMAL);
                }
            } catch (Exception ignored) {}
        }

        if (announce && context != null) {
            boolean isMl = isMalayalam(context);
            String toastText = isMl ? "ഓഡിയോ റൂട്ടിംഗ്: ഓഫ്" : "Audio Routing: OFF";
            String a11yText = isMl ? "ഓഡിയോ റൂട്ടിംഗ് ഓഫ് ചെയ്തു" : "Audio routing disabled";
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show();
            if (context instanceof MainActivity) {
                ((MainActivity) context).announceAccessibility(a11yText);
            }
        }
    }

    public void applyAudioSeparationRouting(Context context) {
        if (!isSeparationActive) return;
        AudioManager am = getAudioManager(context);
        if (am == null) return;
        try {
            if (am.getMode() != AudioManager.MODE_NORMAL) {
                am.setMode(AudioManager.MODE_NORMAL);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AudioDeviceInfo speakerDevice = getSpeakerDevice(am);
                if (speakerDevice != null) {
                    am.setCommunicationDevice(speakerDevice);
                }
            }
            am.setSpeakerphoneOn(true);
        } catch (Exception e) {
            Log.e(TAG, "applyAudioSeparationRouting error", e);
        }
    }

    public void ensureAudioSeparationStable(Context context) {
        if (!isSeparationActive) return;
        AudioManager am = getAudioManager(context);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AudioDeviceInfo currentDev = am.getCommunicationDevice();
                if (currentDev == null || currentDev.getType() != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    AudioDeviceInfo speakerDevice = getSpeakerDevice(am);
                    if (speakerDevice != null) {
                        am.setCommunicationDevice(speakerDevice);
                    }
                }
            }
            am.setSpeakerphoneOn(true);
        } catch (Exception ignored) {}
    }

    public void onResume(Context context) {
        if (context == null) return;
        this.appContext = context.getApplicationContext();
        boolean enabled = isAudioSeparationEnabled(context);
        this.isSeparationActive = enabled;
        if (enabled) {
            applyAudioSeparationRouting(context);
            handler.removeCallbacks(separationKeepAliveRunnable);
            handler.postDelayed(separationKeepAliveRunnable, KEEP_ALIVE_INTERVAL_MS);
        }
    }

    public void resetAudioMode(Context context) {
        isSeparationActive = false;
        handler.removeCallbacks(separationKeepAliveRunnable);
        try {
            AudioManager am = getAudioManager(context);
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        am.clearCommunicationDevice();
                    } catch (Exception ignored) {}
                }
                try {
                    am.setSpeakerphoneOn(false);
                } catch (Exception ignored) {}
                try {
                    if (am.getMode() != AudioManager.MODE_NORMAL) {
                        am.setMode(AudioManager.MODE_NORMAL);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resetting audio mode", e);
        }
        if (context != null) {
            try {
                SharedPreferences prefs = MainActivity.getSafeDeviceProtectedPrefs(context);
                prefs.edit().putBoolean(PREF_AUDIO_SEPARATION_ENABLED, false).apply();
            } catch (Exception ignored) {}
        }
    }

    private boolean isMalayalam(Context context) {
        if (context instanceof MainActivity) {
            return ((MainActivity) context).isCurrentLanguageMalayalam();
        }
        return false;
    }
}
