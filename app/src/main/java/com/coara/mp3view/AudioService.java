package com.coara.mp3view;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.net.Uri;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import android.content.Context;
import android.util.Log;

public class AudioService extends Service {
    private static final String TAG = "AudioService";
    private MediaPlayer mediaPlayer;
    private final IBinder binder = new AudioBinder();
    private static final String CHANNEL_ID = "AudioServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private String currentFile = null;
    private String playbackStatus = "STOP"; 

    public class AudioBinder extends Binder {
        public AudioService getService() {
            return AudioService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        createNotificationChannel();
        updateNotification(); 
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "EXIT".equals(intent.getAction())) {
            Log.d(TAG, "EXIT action received, terminating app.");
            stopAudio();
            stopSelf();
            System.exit(0);
            return START_NOT_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
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
            updateNotification();
            sendStateBroadcast("PAUSE");
        }
    }

    public void stopAudio() {
        if (mediaPlayer != null) {
            Log.d(TAG, "stopAudio");
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            currentFile = null;
            playbackStatus = "STOP";
            updateNotification();
            sendStateBroadcast("STOP");
        }
    }

    private void updateNotification() {
    int iconRes = R.drawable.dummy;



        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sound Player")
                .setSmallIcon(iconRes)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(playbackStatus.equals("PLAY"))
                .setContentText("Sound Player")
                .setContentIntent(getPendingIntent())
                .addAction(createAction("EXIT", "EXIT"))
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private NotificationCompat.Action createAction(String title, String action) {
        Intent intent = new Intent(this, AudioService.class);
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getService(this, action.hashCode(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action(0, title, pendingIntent);
    }

    private PendingIntent getPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Audio Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void sendStateBroadcast(String state) {
        Intent intent = new Intent("ACTION_AUDIO_STATE");
        intent.putExtra("state", state);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopForeground(true);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);
    }
}
