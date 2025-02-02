package com.coara.mp3view;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class AudioService extends Service {
    private static final String TAG = "AudioService";
    private MediaPlayer mediaPlayer;
    private final IBinder binder = new AudioBinder();
    private static final String CHANNEL_ID = "AudioServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private String currentFile = null;
    private String playbackStatus = "STOP";
    private static final String ACTION_AUDIO_STATE = "ACTION_AUDIO_STATE";
    private static final String ACTION_AUDIO_CONTROL = "AUDIO_CONTROL";

    public class AudioBinder extends Binder {
        public AudioService getService() {
            return AudioService.this;
        }
    }

    private final BroadcastReceiver audioControlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra("ACTION");
            Log.d(TAG, "Received audio control: " + action);
            if (action != null) {
                switch (action) {
                    case "PLAY":
                        if (currentFile != null) {
                            playAudio(currentFile);
                        }
                        break;
                    case "PAUSE":
                        pauseAudio();
                        break;
                    case "STOP":
                        stopAudio();
                        break;
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        createNotificationChannel();
        updateNotification();

        IntentFilter filter = new IntentFilter(ACTION_AUDIO_CONTROL);
        registerReceiver(audioControlReceiver, filter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void playAudio(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "Invalid file path provided for playback");
            return;
        }
        Log.d(TAG, "playAudio: " + filePath);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaPlayer = new MediaPlayer();
        try {
            Uri uri = Uri.parse(filePath);
            mediaPlayer.setDataSource(getApplicationContext(), uri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            currentFile = filePath;
            playbackStatus = "PLAY";
            updateNotification();
            sendStateBroadcast("PLAY");
            mediaPlayer.setOnCompletionListener(mp -> {
                playbackStatus = "STOP";
                currentFile = null;
                updateNotification();
                sendStateBroadcast("STOP");
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in playAudio", e);
            playbackStatus = "STOP";
            updateNotification();
        }
    }

    public void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            Log.d(TAG, "pauseAudio");
            mediaPlayer.pause();
            playbackStatus = "PAUSE";
