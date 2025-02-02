package com.coara.mp3view;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.view.KeyEvent;
import android.util.Log;
import android.content.ServiceConnection;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private WebView webView;
    private WebView notificationWebView; // 通知用WebView
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private AudioService audioService;
    private boolean isBound = false;

    // AudioServiceからの再生状態ブロードキャストを受信し、WebView内のUI（波形アニメーション等）を更新する
    private final BroadcastReceiver audioStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra("state");
            Log.d(TAG, "Audio state received: " + state);
            if (state != null) {
                // player.htmlを更新
                webView.evaluateJavascript("updateNotification('" + state + "');", null);
                // player0.htmlを更新
                notificationWebView.evaluateJavascript("updatePlaybackInfo(" +
                        (audioService != null && audioService.getMediaPlayer() != null ? 
                                audioService.getMediaPlayer().getCurrentPosition() / 1000.0 : 0) + "," +
                        (audioService != null && audioService.getMediaPlayer() != null ? 
                                audioService.getMediaPlayer().getDuration() / 1000.0 : 0) + ",'" + state + "');", null);
            }
        }
    };

    // AudioServiceからの時間更新を受信
    private final BroadcastReceiver timeUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals("ACTION_TIME_UPDATE")) {
                int position = intent.getIntExtra("position", 0);
                // player.htmlに時間情報を送信
                webView.evaluateJavascript("updateTimeDisplay('" + formatTime(position / 1000) + "');", null);
                // player0.htmlに時間情報を送信
                notificationWebView.evaluateJavascript("updatePlaybackInfo(" + 
                        (position / 1000.0) + "," +
                        (audioService != null && audioService.getMediaPlayer() != null ? 
                                audioService.getMediaPlayer().getDuration() / 1000.0 : 0) + 
                        ",'" + (audioService != null ? audioService.getPlaybackStatus() : "STOP") + "');", null);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("audio/*");
                filePickerLauncher.launch(intent);
                return true;
            }
        });

        // player.htmlを読み込み
        webView.loadUrl("file:///android_asset/player.html");

        // 通知用WebViewの設定
        notificationWebView = new WebView(this);
        notificationWebView.setWebViewClient(new WebViewClient());
        WebSettings notificationWebViewSettings = notificationWebView.getSettings();
        notificationWebViewSettings.setJavaScriptEnabled(true);
        notificationWebViewSettings.setAllowFileAccess(true);
        // player0.htmlを読み込み
        notificationWebView.loadUrl("file:///android_asset/player0.html");

        // JavaScriptインターフェースの登録
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        notificationWebView.addJavascriptInterface(new WebAppInterface(), "Android");

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(new Uri[]{uri});
                    filePathCallback = null;
                }
            }
        });

        Intent serviceIntent = new Intent(this, AudioService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        registerReceiver(audioStateReceiver, new IntentFilter("ACTION_AUDIO_STATE"));
        registerReceiver(timeUpdateReceiver, new IntentFilter("ACTION_TIME_UPDATE"));
    }

    private class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void playAudio(String filePath) {
            if (audioService != null) {
                audioService.playAudio(filePath);
            }
        }

        @android.webkit.JavascriptInterface
        public void playAudio() {
            if (audioService != null && audioService.getCurrentFile() != null) {
                audioService.playAudio(audioService.getCurrentFile());
            }
        }

        @android.webkit.JavascriptInterface
        public void pauseAudio() {
            if (audioService != null) {
                audioService.pauseAudio();
            }
        }

        @android.webkit.JavascriptInterface
        public void stopAudio() {
            if (audioService != null) {
                audioService.stopAudio();
            }
        }

        @android.webkit.JavascriptInterface
        public void updatePlaybackInfo(double currentTime, double duration, String state) {
            if (audioService != null) {
                audioService.updatePlaybackInfo(currentTime, duration, state);
            }
        }

        @android.webkit.JavascriptInterface
        public void seekTo(double time) {
            if (audioService != null) {
                audioService.seekTo(time);
            }
        }

        @android.webkit.JavascriptInterface
        public void setDuration(double duration) {
            if (audioService != null) {
                audioService.setDuration(duration);
            }
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioService.AudioBinder binder = (AudioService.AudioBinder) service;
            audioService = binder.getService();
            isBound = true;
            Log.d(TAG, "Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            Log.d(TAG, "Service disconnected");
        }
    };

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    // 現在のファイルパスを取得するためのヘルパーメソッド
    private String currentFile() {
        return audioService != null ? audioService.getCurrentFile() : "";
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        unregisterReceiver(audioStateReceiver);
        unregisterReceiver(timeUpdateReceiver);
    }
}
