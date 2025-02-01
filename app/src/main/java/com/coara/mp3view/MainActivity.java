package com.coara.mp3view;

import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // WebViewの設定
        webView = findViewById(R.id.webView);

        // WebViewの設定
        webView.getSettings().setJavaScriptEnabled(true);  // JavaScriptを有効にする

        // WebChromeClientを設定して、Webページのロードを監視
        webView.setWebChromeClient(new WebChromeClient());

        // WebViewClientを設定して、外部URLへの遷移を防止
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 外部ブラウザで開かずにWebView内で開く
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        // WebViewにMP3プレーヤーのHTMLを読み込む
        webView.loadUrl("file:///android_asset/player.html");

        // Webページが読み込まれた後に、JavaScriptと連携する処理を書くことができます。
        webView.evaluateJavascript("console.log('WebView loaded');", null);
    }

    // バックキーが押されたとき、WebView内で前のページに戻ることができないようにする
    @Override
    public void onBackPressed() {
        // WebView内で戻ることができないように、何もしない
        // これによりWebView内で戻る操作が無効化されます
        // 画面を閉じず、戻れない状態にします
        Toast.makeText(this, "Back button is disabled while playing.", Toast.LENGTH_SHORT).show();
    }
}
