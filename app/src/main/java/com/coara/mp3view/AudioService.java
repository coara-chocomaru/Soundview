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
import androidx.core.app.NotificationManagerCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import androidx.media.session.MediaButtonReceiver;
import androidx.media.session.MediaSessionCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

public class AudioService extends Service {
    private MediaPlayer mediaPlayer;
    private final IBinder binder = new AudioBinder();
    private static final String CHANNEL_ID = "AudioServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private String currentFile = null;
    // 再生状態を文字列で保持："PLAY"／"PAUSE"／"STOP"
    private String playbackStatus = "STOP";
    private MediaSessionCompat mediaSession;

    // Binderクラス
    public class AudioBinder extends Binder {
        public AudioService getService() {
            return AudioService.this;
        }
    }
    
    // 現在再生中のファイルのURI文字列を外部から参照できるように
    public String getCurrentFile() {
        return currentFile;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        // MediaSessionCompatの作成と有効化
        mediaSession = new MediaSessionCompat(this, "AudioService");
        mediaSession.setActive(true);
        createNotificationChannel();
        registerReceiver(notificationReceiver, new IntentFilter("AUDIO_CONTROL"));
        updateNotification();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    // 再生処理（URI文字列で渡される）
    public void playAudio(String filePath) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        currentFile = filePath;
        mediaPlayer = new MediaPlayer();
        try {
            Uri uri = Uri.parse(filePath);
            mediaPlayer.setDataSource(getApplicationContext(), uri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            playbackStatus = "PLAY";
            // 再生終了時に自動で停止状態へ
            mediaPlayer.setOnCompletionListener(mp -> {
                stopAudio();
            });
            updateNotification();
            sendStateBroadcast("PLAY");
            updateMediaSessionState();
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
            updateMediaSessionState();
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
            updateMediaSessionState();
        }
    }
    
    // MediaSessionの再生状態を更新
    private void updateMediaSessionState() {
        long position = (mediaPlayer != null) ? mediaPlayer.getCurrentPosition() : 0;
        int state;
        if ("PLAY".equals(playbackStatus)) {
            state = android.media.session.PlaybackState.STATE_PLAYING;
        } else if ("PAUSE".equals(playbackStatus)) {
            state = android.media.session.PlaybackState.STATE_PAUSED;
        } else {
            state = android.media.session.PlaybackState.STATE_STOPPED;
        }
        android.media.session.PlaybackState playbackState = new android.media.session.PlaybackState.Builder()
            .setState(state, position, 1.0f)
            .setActions(android.media.session.PlaybackState.ACTION_PLAY |
                        android.media.session.PlaybackState.ACTION_PAUSE |
                        android.media.session.PlaybackState.ACTION_STOP)
            .build();
        mediaSession.setPlaybackState(playbackState);
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
        
        // 各アクション用のPendingIntentを生成
        PendingIntent pendingPlay = PendingIntent.getBroadcast(this, "PLAY".hashCode(),
            new Intent("AUDIO_CONTROL").putExtra("ACTION", "PLAY"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pendingPause = PendingIntent.getBroadcast(this, "PAUSE".hashCode(),
            new Intent("AUDIO_CONTROL").putExtra("ACTION", "PAUSE"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pendingStop = PendingIntent.getBroadcast(this, "STOP".hashCode(),
            new Intent("AUDIO_CONTROL").putExtra("ACTION", "STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // 通知タップ時にMainActivityを起動するためのPendingIntent
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MP3 Player")
            // 状態文字列を表示（例："PLAY"／"PAUSE"／"STOP"）
            .setContentText(playbackStatus)
            .setSmallIcon(iconRes)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // 再生中なら通知をOngoingにする
            .setOngoing("PLAY".equals(playbackStatus));
        
        // 状態に応じたアクションボタンを追加
        if ("PLAY".equals(playbackStatus)) {
            builder.addAction(R.drawable.ic_paused, "Pause", pendingPause);
        } else {
            builder.addAction(R.drawable.ic_playing, "Play", pendingPlay);
        }
        builder.addAction(R.drawable.ic_stopped, "Stop", pendingStop);
        
        // MediaStyleを適用（メディアセッションのトークンを設定）
        builder.setStyle(new MediaStyle()
            .setMediaSession(mediaSession.getSessionToken())
            .setShowActionsInCompactView(0, 1));  // compact viewに最初の2つのアクションを表示
        
        Notification notification = builder.build();
        startForeground(NOTIFICATION_ID, notification);
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
    
    // 再生状態をMainActivityへブロードキャストで通知（WebView側との同期用）
    private void sendStateBroadcast(String state) {
        Intent intent = new Intent("ACTION_AUDIO_STATE");
        intent.putExtra("state", state);
        sendBroadcast(intent);
    }
    
    // 通知操作（PLAY／PAUSE／STOP）を受け取るブロードキャストレシーバー
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
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }
}
