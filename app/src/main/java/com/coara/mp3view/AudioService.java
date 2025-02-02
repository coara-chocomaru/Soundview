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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.util.Log;

public class AudioService extends Service {
    private static final String TAG = "AudioService";
    private MediaPlayer mediaPlayer;
    private final IBinder binder = new AudioBinder();
    private static final String CHANNEL_ID = "AudioServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private String currentFile = null;
    private String playbackStatus = "STOP"; // 初期状態はSTOP

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
        registerReceiver(notificationReceiver, new IntentFilter("AUDIO_CONTROL"));
        // 初期状態でも通知を表示（STOP状態）
        updateNotification();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // 再生処理（URI文字列を受け取る）
    public void playAudio(String filePath) {
        if (filePath == null || filePath.isEmpty()) return;
        Log.d(TAG, "playAudio: " + filePath);

        // メディアプレイヤーがすでに存在する場合はリリース
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
            updateNotification(); // 状態更新後に通知を更新
            sendStateBroadcast("PLAY");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error in playAudio", e);
        }
    }

    // 一時停止処理
    public void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            Log.d(TAG, "pauseAudio");
            mediaPlayer.pause();
            playbackStatus = "PAUSE";
            updateNotification(); // 状態更新後に通知を更新
            sendStateBroadcast("PAUSE");
        }
    }

    // 停止処理
    public void stopAudio() {
        if (mediaPlayer != null) {
            Log.d(TAG, "stopAudio");
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            currentFile = null;
            playbackStatus = "STOP";
            updateNotification(); // 状態更新後に通知を更新
            sendStateBroadcast("STOP");
        }
    }

    // 通知更新処理
    private void updateNotification() {
        int iconRes;
        String notificationText;
        String currentTime = "00:00";
        String duration = "00:00";

        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            iconRes = R.drawable.ic_playing; // 再生中アイコン
            notificationText = "Playing: " + currentFile;
            currentTime = formatTime(mediaPlayer.getCurrentPosition() / 1000);
            duration = formatTime(mediaPlayer.getDuration() / 1000);
        } else if (playbackStatus.equals("PAUSE")) {
            iconRes = R.drawable.ic_paused; // 一時停止アイコン
            notificationText = "Paused: " + currentFile;
            currentTime = formatTime(mediaPlayer.getCurrentPosition() / 1000);
            duration = formatTime(mediaPlayer.getDuration() / 1000);
        } else {
            iconRes = R.drawable.ic_stopped; // 停止中アイコン
            notificationText = "No track playing";
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MP3 Player")
                .setContentText(notificationText)
                .setSmallIcon(iconRes)
                .addAction(createAction("▶ Play", "PLAY"))
                .addAction(createAction("⏸ Pause", "PAUSE"))
                .addAction(createAction("⏹ Stop", "STOP"))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(playbackStatus.equals("PLAY"))
                .setContentIntent(getPendingIntent())
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    // 時間の形式をMM:SSに変換する
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    // 通知用アクションボタンの生成
    private NotificationCompat.Action createAction(String title, String action) {
        Intent intent = new Intent("AUDIO_CONTROL");
        intent.putExtra("ACTION", action);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, action.hashCode(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action(0, title, pendingIntent);
    }

    // 通知タップ時のPendingIntent生成（MainActivityへ遷移）
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

    // 現在の再生状態をブロードキャスト送信（MainActivity側でWebView更新用）
    private void sendStateBroadcast(String state) {
        Intent intent = new Intent("ACTION_AUDIO_STATE");
        intent.putExtra("state", state);
        sendBroadcast(intent);
    }

    // 通知操作（PLAY／PAUSE／STOP）を受け取るレシーバー
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
