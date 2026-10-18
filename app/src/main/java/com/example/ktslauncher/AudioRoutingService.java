package com.example.ktslauncher;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class AudioRoutingService extends Service {

    public static final String ACTION_START = "com.example.ktslauncher.action.START_AUDIO_ROUTING";
    public static final String ACTION_STOP = "com.example.ktslauncher.action.STOP_AUDIO_ROUTING";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AudioRoutingManager.getInstance().resetAudioMode(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
