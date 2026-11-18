package com.example.mainpage;

import android.os.Bundle;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class FloodNewsActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flood_news);

        Toolbar toolbar = findViewById(R.id.toolbar);
        SafeAreaHelper.apply(this, findViewById(R.id.includeToolbar), null);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String url = request.getUrl().toString();
                    if (url.startsWith("https://bagong.pagasa.dost.gov.ph") || url.contains("pagasa.dost.gov.ph")) {
                        return false; // Allow trusted PAGASA URLs in WebView
                    }
                    try {
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, request.getUrl());
                        startActivity(intent);
                    } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    progressBar.setVisibility(View.GONE);
                    android.widget.Toast.makeText(FloodNewsActivity.this, "Unable to load news. Please check your connection.", android.widget.Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel(); // Strictly reject invalid SSL certificates
                progressBar.setVisibility(View.GONE);
                android.widget.Toast.makeText(FloodNewsActivity.this, "SSL Certificate Error: Connection blocked for security.", android.widget.Toast.LENGTH_LONG).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_flood_news) {
                toolbar.setTitle("PAGASA Flood News");
                webView.loadUrl("https://bagong.pagasa.dost.gov.ph/flood");
                return true;
            } else if (id == R.id.nav_weather_forecast) {
                toolbar.setTitle("PAGASA Weather Forecast");
                webView.loadUrl("https://bagong.pagasa.dost.gov.ph/weather");
                return true;
            }
            return false;
        });

        // Loading default or intent-requested PAGASA page
        handleIntent(getIntent());
    }

    private void handleIntent(android.content.Intent intent) {
        Toolbar toolbar = findViewById(R.id.toolbar);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        String targetTab = (intent != null) ? intent.getStringExtra("target_tab") : null;

        if ("weather".equalsIgnoreCase(targetTab)) {
            if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_weather_forecast);
            if (toolbar != null) toolbar.setTitle("PAGASA Weather Forecast");
            if (webView != null) webView.loadUrl("https://bagong.pagasa.dost.gov.ph/weather");
        } else if ("flood".equalsIgnoreCase(targetTab)) {
            if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_flood_news);
            if (toolbar != null) toolbar.setTitle("PAGASA Flood News");
            if (webView != null) webView.loadUrl("https://bagong.pagasa.dost.gov.ph/flood");
        } else {
            if (toolbar != null) toolbar.setTitle("PAGASA Flood News");
            if (webView != null) webView.loadUrl("https://bagong.pagasa.dost.gov.ph/flood");
        }
    }

    @Override
    protected void onNewIntent(@androidx.annotation.NonNull android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration override = new Configuration(newBase.getResources().getConfiguration());
        override.fontScale = 1.0f;
        applyOverrideConfiguration(override);
        super.attachBaseContext(newBase);
    }
}
