package com.coara.mp3view;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.ServiceConnection;
import android.view.KeyEvent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private AudioService audioService;
    private boolean isBound = false;

    // AudioService からの再生状態ブロードキャストを受信し、WebView 内の再生状態を更新するレシーバー
    private final BroadcastReceiver audioStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra("state");
            if (state != null) {
                switch (state) {
                    case "PLAY":
                        // WebView 内の audio 要素の再生指示（HTML 側で適宜処理するスクリプト例）
                        webView.evaluateJavascript("document.getElementById('audioPlayer').play();", null);
                        webView.evaluateJavascript("document.getElementById('waveAnimation').classList.remove('hidden');", null);
                        break;
                    case "PAUSE":
                        webView.evaluateJavascript("document.getElementById('audioPlayer').pause();", null);
                        webView.evaluateJavascript("document.getElementById('waveAnimation').classList.add('hidden');", null);
                        break;
                    case "STOP":
                        webView.evaluateJavascript("document.getElementById('audioPlayer').pause(); document.getElementById('audioPlayer').currentTime = 0;", null);
                        webView.evaluateJavascript("document.getElementById('waveAnimation').classList.add('hidden');", null);
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // WebView の初期化
        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            // <input type="file"> によるファイル選択の対応
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
        // JavaScript インターフェースの登録（HTML 側からの操作時に AudioService のメソッドを呼び出す）
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.loadUrl("file:///android_asset/player.html");

        // ファイルピッカーの結果受信
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

        // AudioService からの再生状態ブロードキャストを受信するためのレシーバー登録
        registerReceiver(audioStateReceiver, new IntentFilter("ACTION_AUDIO_STATE"));
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
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    // 戻るキーの無効化（必要に応じて）
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
    }
}
