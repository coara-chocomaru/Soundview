package com.coara.mp3view;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import java.io.IOException;

public class AudioService extends Service {
    private static final String TAG = "AudioService";
    private MediaPlayer mediaPlayer;
    private String currentFile;
    private final IBinder binder = new AudioBinder();
    private static final String CHANNEL_ID = "MP3PlayerServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    public class AudioBinder extends Binder {
        AudioService getService() {
            return AudioService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void playAudio(String filePath) {
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.setDataSource(this, Uri.parse(filePath));
            mediaPlayer.prepare();
            mediaPlayer.start();

            currentFile = filePath;
            startForeground(NOTIFICATION_ID, buildNotification());
            sendBroadcast("PLAY");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error playing audio: " + e.getMessage());
        }
    }

    public void pauseAudio() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            sendBroadcast("PAUSE");
        }
    }

    public void stopAudio() {
        mediaPlayer.stop();
        mediaPlayer.reset();
        sendBroadcast("STOP");
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
    }

    public long getCurrentPosition() {
        return mediaPlayer.getCurrentPosition();
    }

    public String getCurrentFile() {
        return currentFile;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "MP3 Player Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MP3 Player")
                .setContentText("Playing Audio")
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(pendingIntent)
                .setOngoing(true)  // 通知を停止しない限り動作し続けるように設定
                .build();
    }

    private void sendBroadcast(String state) {
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
    }
}
