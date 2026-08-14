package com.textbot.translator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 5001;
    private static final int REQ_PROJECTION = 5002;

    private MediaProjectionManager projectionManager;
    private WebView settingsWebView;
    private boolean launchOverlayAfterPermission = false;
    private boolean startTranslationAfterPermission = false;
    private boolean settingsMode = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        projectionManager =
                (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        String action = getIntent() != null ? getIntent().getAction() : null;

        if ("com.textbot.START_TRANSLATION".equals(action)) {
            startTranslationAfterPermission = true;
            ensureOverlayThenContinue();
        } else if ("com.textbot.OPEN_SETTINGS".equals(action)) {
            settingsMode = true;
            ensureOverlayThenContinue();
        } else {
            launchOverlayAfterPermission = true;
            ensureOverlayThenContinue();
        }
    }

    private void ensureOverlayThenContinue() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
            Toast.makeText(this,
                    "SimplyTsL이 다른 앱 위에 표시되려면 '다른 앱 위에 표시' 권한이 필요합니다.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        continueAfterOverlayPermission();
    }

    private void continueAfterOverlayPermission() {
        if (settingsMode) {
            openSettings();
            return;
        }

        if (startTranslationAfterPermission) {
            startTranslationAfterPermission = false;
            requestProjection();
        } else {
            Intent service = new Intent(this, ScreenCaptureService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            finish();
        }
    }

    private void requestProjection() {
        startActivityForResult(
                projectionManager.createScreenCaptureIntent(),
                REQ_PROJECTION);
    }

    private void openSettings() {
        settingsWebView = new WebView(this);
        WebSettings s = settingsWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        settingsWebView.setWebViewClient(new WebViewClient());
        settingsWebView.setWebChromeClient(new WebChromeClient());
        settingsWebView.loadUrl("file:///android_asset/index.html");
        setContentView(settingsWebView);
        settingsWebView.postDelayed(() ->
                settingsWebView.evaluateJavascript(
                        "if(window.openSettingsPanel) window.openSettingsPanel();", null),
                500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                continueAfterOverlayPermission();
            } else {
                Toast.makeText(this,
                        "권한이 허용되지 않아 SimplyTsL 오버레이를 표시할 수 없습니다.",
                        Toast.LENGTH_LONG).show();
                finish();
            }
            return;
        }

        if (requestCode == REQ_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, ScreenCaptureService.class);
                service.setAction("com.textbot.START_CAPTURE");
                service.putExtra("resultCode", resultCode);
                service.putExtra("resultData", data);
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(service);
                } else {
                    startService(service);
                }
            } else {
                Toast.makeText(this,
                        "화면 캡처 권한이 허용되지 않았습니다.",
                        Toast.LENGTH_LONG).show();
            }
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (settingsMode) {
            finish();
        } else {
            super.onBackPressed();
        }
    }
}
