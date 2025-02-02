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
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.KeyEvent;
import android.util.Log;
import android.content.ServiceConnection;

import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private AudioService audioService;
    private boolean isBound = false;
    private MediaSessionCompat mediaSession;

    // AudioServiceからの再生状態ブロードキャスト（名前空間付きアクション）を受信し、WebView内のUI更新を行う
    private final BroadcastReceiver audioStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra("state");
            Log.d(TAG, "Audio state received: " + state);
            if (state != null) {
                if ("PLAY".equals(state)) {
                    webView.evaluateJavascript("document.getElementById('waveAnimation').classList.remove('hidden');", null);
                } else if ("PAUSE".equals(state)) {
                    webView.evaluateJavascript("document.getElementById('waveAnimation').classList.add('hidden');", null);
                } else if ("STOP".equals(state)) {
                    webView.evaluateJavascript("document.getElementById('waveAnimation').classList.add('hidden');", null);
                    webView.evaluateJavascript("document.getElementById('audioPlayer').pause(); document.getElementById('audioPlayer').currentTime = 0;", null);
                }
                updateMediaSessionPlaybackState(state);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);  // activity_main.xml を用意してください

        // WebView の設定
        webView = findViewById(R.id.webView);  // layout に WebView (id:webView) を配置してください
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
        // JavaScriptインターフェースの登録
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        // assets/player.html を用意するか、適切な URL に変更してください
        webView.loadUrl("file:///android_asset/player.html");

        // ファイルピッカー結果の処理
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(new Uri[]{uri});
                    filePathCallback = null;
                }
            }
        });

        // AudioService の開始とバインド
        Intent serviceIntent = new Intent(this, AudioService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // MediaSession の初期化（旧 v4 サポートライブラリの MediaSessionCompat を使用）
        initializeMediaSession();

        // AudioService からの再生状態ブロードキャスト受信用レシーバー登録（名前空間付きアクション）
        registerReceiver(audioStateReceiver, new IntentFilter("com.coara.mp3view.ACTION_AUDIO_STATE"));
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, "MP3Player");
        // 旧サポートライブラリの MediaSessionCompat のフラグは deprecated ですが、v4 用として設定
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (audioService != null) {
                    if (audioService.isPaused()) {
                        audioService.resumeAudio();
                    } else if (audioService.getCurrentFile() != null) {
                        audioService.playAudio(audioService.getCurrentFile());
                    }
                }
            }
            @Override
            public void onPause() {
                if (audioService != null) {
                    audioService.pauseAudio();
                }
            }
            @Override
            public void onStop() {
                if (audioService != null) {
                    audioService.stopAudio();
                }
            }
        });
        mediaSession.setActive(true);
    }

    private void updateMediaSessionPlaybackState(String state) {
        if (mediaSession == null) return;
        long position = 0;
        int stateCode = PlaybackStateCompat.STATE_STOPPED;
        if ("PLAY".equals(state)) {
            stateCode = PlaybackStateCompat.STATE_PLAYING;
            position = (audioService != null) ? audioService.getCurrentPosition() : 0;
        } else if ("PAUSE".equals(state)) {
            stateCode = PlaybackStateCompat.STATE_PAUSED;
            position = (audioService != null) ? audioService.getCurrentPosition() : 0;
        } else if ("STOP".equals(state)) {
            stateCode = PlaybackStateCompat.STATE_STOPPED;
        }
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                            PlaybackStateCompat.ACTION_PAUSE |
                            PlaybackStateCompat.ACTION_STOP)
                .setState(stateCode, position, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    // JavaScript から呼ばれるインターフェース
    private class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void playAudio(String filePath) {
            if (audioService != null) {
                audioService.playAudio(filePath);
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
    }

    // AudioService との接続管理
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioService.AudioBinder binder = (AudioService.AudioBinder) service;
            audioService = binder.getService();
            isBound = true;
            Log.d(TAG, "AudioService connected");
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            Log.d(TAG, "AudioService disconnected");
        }
    };

    // 戻るキーの無効化（必要に応じて実装）
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 戻るボタン押下時の処理（ここでは無効化）
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
        if (mediaSession != null) {
            mediaSession.release();
        }
    }
}
