package com.coara.mp3view;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;

public class AudioService extends Service {
    private MediaPlayer mediaPlayer;
    private final IBinder binder = new AudioBinder();
    private static final String CHANNEL_ID = "AudioServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private String currentFile = null;
    private String playbackStatus = "STOP";

    // Binderクラス
    public class AudioBinder extends Binder {
        public AudioService getService() {
            return AudioService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registerReceiver(notificationReceiver, new IntentFilter("AUDIO_CONTROL"));
        updateNotification();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // 再生処理（URI文字列で受け取り）
    public void playAudio(String filePath) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
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
            e.printStackTrace();
        }
    }

    // 一時停止処理
    public void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playbackStatus = "PAUSE";
            updateNotification();
            sendStateBroadcast("PAUSE");
        }
    }

    // 停止処理
    public void stopAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            currentFile = null;
            playbackStatus = "STOP";
            updateNotification();
            sendStateBroadcast("STOP");
        }
    }

    // 通知の更新
    private void updateNotification() {
        int iconRes;
        switch (playbackStatus) {
            case "PLAY":
                iconRes = R.drawable.ic_playing;
                break;
            case "PAUSE":
                iconRes = R.drawable.ic_paused;
                break;
            default:
                iconRes = R.drawable.ic_stopped;
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MP3 Player")
                .setContentText(playbackStatus)
                .setSmallIcon(iconRes)
                .addAction(createAction("▶", "PLAY"))
                .addAction(createAction("⏸", "PAUSE"))
                .addAction(createAction("⏹", "STOP"))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(playbackStatus.equals("PLAY"))
                .setContentIntent(getPendingIntent())
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    // 通知用アクションボタンの生成
    private NotificationCompat.Action createAction(String title, String action) {
        Intent intent = new Intent("AUDIO_CONTROL");
        intent.putExtra("ACTION", action);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, action.hashCode(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action(0, title, pendingIntent);
    }

    // 通知タップ時のPendingIntent生成
    private PendingIntent getPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // 通知チャネルの生成（Android 8.0以上用）
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Audio Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    // 再生状態のブロードキャスト送信（MainActivity側でWebView更新用）
    private void sendStateBroadcast(String state) {
        Intent intent = new Intent("ACTION_AUDIO_STATE");
        intent.putExtra("state", state);
        sendBroadcast(intent);
    }

    // 通知操作を受け取るブロードキャストレシーバー
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra("ACTION");
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
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(notificationReceiver);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
