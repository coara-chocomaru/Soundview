package com.coara.mp3view;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Foreground Serviceを起動
        Intent serviceIntent = new Intent(this, AudioService.class);
        startService(serviceIntent);

        // WebViewの初期化
        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false); // 自動再生を許可

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/player.html");
    }

    @Override
    public void onBackPressed() {
        // 戻るボタンを封じる
    }
}
