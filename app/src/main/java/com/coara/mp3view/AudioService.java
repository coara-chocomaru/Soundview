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

public class AudioService extends Service {
    private MediaPlayer mediaPlayer;
    private final IBinder binder = new AudioBinder();
    private static final String CHANNEL_ID = "AudioServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private String currentFile = null;
    private String playbackStatus = "Stopped";

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

    // オーディオの再生
    public void playAudio(String filePath) {
        if (mediaPlayer != null) {
            mediaPlayer.release();  // 再生中のメディアを解放
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(filePath);  // ファイルパスを設定
            mediaPlayer.prepare();  // メディアの準備
            mediaPlayer.start();  // 再生開始
            currentFile = filePath;  // 現在のファイルを保存
            playbackStatus = "Playing";  // 再生状態
            updateNotification();  // 通知を更新
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // オーディオの一時停止
    public void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playbackStatus = "Paused";  // 一時停止状態
            updateNotification();  // 通知を更新
        }
    }

    // オーディオの停止
    public void stopAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();  // メディアの停止
            mediaPlayer.release();  // 解放
            mediaPlayer = null;
            currentFile = null;
            playbackStatus = "Stopped";  // 停止状態
            updateNotification();  // 通知を更新
        }
    }

    // 通知を更新
    private void updateNotification() {
        int iconRes;
        switch (playbackStatus) {
            case "Playing":
                iconRes = R.drawable.ic_playing;  // 再生中のアイコン
                break;
            case "Paused":
                iconRes = R.drawable.ic_paused;  // 一時停止中のアイコン
                break;
            default:
                iconRes = R.drawable.ic_stopped;  // 停止中のアイコン
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MP3 Player")
                .setContentText(playbackStatus)
                .setSmallIcon(iconRes)
                .addAction(createAction("▶", "PLAY"))
                .addAction(createAction("⏸", "PAUSE"))
                .addAction(createAction("⏹", "STOP"))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(playbackStatus.equals("Playing"))  // 再生中はOngoing
                .setContentIntent(getPendingIntent())
                .build();

        startForeground(NOTIFICATION_ID, notification);  // 通知を前面に表示
    }

    // 通知のアクションボタンを作成
    private NotificationCompat.Action createAction(String title, String action) {
        Intent intent = new Intent("AUDIO_CONTROL");
        intent.putExtra("ACTION", action);  // アクションを指定
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action(0, title, pendingIntent);
    }

    // 通知のタップ時に開くPendingIntentを作成
    private PendingIntent getPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // 通知チャネルを作成（Android 8.0以上用）
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Audio Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    // 通知操作を受け取るブロードキャストレシーバー
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra("ACTION");
            if (action != null) {
                switch (action) {
                    case "PLAY":
                        if (currentFile != null) playAudio(currentFile);  // 再生
                        break;
                    case "PAUSE":
                        pauseAudio();  // 一時停止
                        break;
                    case "STOP":
                        stopAudio();  // 停止
                        break;
                }
            }
        }
    };

    // サービスが終了する際の処理
    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(notificationReceiver);  // レシーバーの解除
        if (mediaPlayer != null) {
            mediaPlayer.release();  // MediaPlayerを解放
            mediaPlayer = null;
        }
    }
}
