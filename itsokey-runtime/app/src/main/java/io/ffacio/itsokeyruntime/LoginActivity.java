package io.ffacio.itsokeyruntime;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import android.net.http.SslError;

public final class LoginActivity extends Activity {
    private static final String LOGIN_URL = "https://v2.api.itsokey.kr/signIn";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean completing = new AtomicBoolean(false);
    private WebView webView;
    private TextView status;
    private ProgressBar progress;
    private SecureSessionStore sessionStore;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        sessionStore = new SecureSessionStore(this);
        buildUi();
        configureWebView();
        webView.loadUrl(LOGIN_URL);
        handler.postDelayed(sessionPoller, 700L);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackgroundColor(Color.rgb(0, 122, 141));

        status = new TextView(this);
        status.setText("카카오로 ITSOKEY 로그인");
        status.setTextColor(Color.WHITE);
        status.setTextSize(16f);
        status.setSingleLine(true);
        header.addView(status, new LinearLayout.LayoutParams(0, dp(48), 1f));

        progress = new ProgressBar(this);
        header.addView(progress, new LinearLayout.LayoutParams(dp(36), dp(36)));

        Button close = new Button(this);
        close.setText("닫기");
        close.setOnClickListener(v -> finishCanceled("로그인이 취소되었습니다"));
        header.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));

        webView = new WebView(this);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void configureWebView() {
        android.webkit.WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        String userAgent = settings.getUserAgentString();
        if (userAgent != null) settings.setUserAgentString(userAgent.replace("; wv", ""));
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                WebView popup = new WebView(LoginActivity.this);
                popup.getSettings().setJavaScriptEnabled(true);
                popup.getSettings().setDomStorageEnabled(true);
                popup.getSettings().setUserAgentString(webView.getSettings().getUserAgentString());
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView child, WebResourceRequest request) {
                        String target = request.getUrl().toString();
                        if (!handleExternalUrl(target)) webView.loadUrl(target);
                        child.destroy();
                        return true;
                    }
                    @Override public boolean shouldOverrideUrlLoading(WebView child, String target) {
                        if (!handleExternalUrl(target)) webView.loadUrl(target);
                        child.destroy();
                        return true;
                    }
                });
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleExternalUrl(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternalUrl(url);
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                status.setText(url.contains("itsokey.kr") ? "ITSOKEY 로그인 상태 확인 중" : "카카오 인증 중");
                inspectSession();
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                status.setText("보안 연결을 확인할 수 없습니다");
            }
        });
    }

    private boolean handleExternalUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (scheme.equals("http") || scheme.equals("https")) return false;
        try {
            Intent intent;
            if (scheme.equals("intent")) {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fallback = intent.getStringExtra("browser_fallback_url");
                // Keep Kakao OAuth inside this WebView. Leaving the WebView would
                // put the resulting ITSOKEY localStorage in another browser process.
                if (fallback != null && (fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                    webView.loadUrl(fallback);
                    return true;
                }
                if (intent.getPackage() != null && getPackageManager().getLaunchIntentForPackage(intent.getPackage()) == null) {
                    status.setText("카카오 웹 로그인으로 전환할 수 없습니다");
                    return true;
                }
            } else {
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            status.setText("카카오톡 또는 브라우저를 열 수 없습니다");
        } catch (Exception error) {
            status.setText("외부 로그인 화면을 열지 못했습니다");
        }
        return true;
    }

    private final Runnable sessionPoller = new Runnable() {
        @Override public void run() {
            inspectSession();
            if (!isFinishing() && !completing.get()) handler.postDelayed(this, 800L);
        }
    };

    private void inspectSession() {
        if (webView == null || completing.get()) return;
        String script = "(function(){try{var k=['tokenType','accessToken','accessTokenExpired','refreshToken','refreshTokenExpired','member','session'];var o={};for(var i=0;i<k.length;i++){var v=localStorage.getItem(k[i]);if(v===null)v=sessionStorage.getItem(k[i]);o[k[i]]=v;}return JSON.stringify(o);}catch(e){return null;}})()";
        webView.evaluateJavascript(script, this::handleStorageResult);
    }

    private void handleStorageResult(String encoded) {
        if (encoded == null || encoded.equals("null") || completing.get()) return;
        try {
            String decoded = new JSONArray("[" + encoded + "]").getString(0);
            JSONObject storage = new JSONObject(decoded);
            ItsokeySession session = sessionStore.fromWebStorage(storage);
            if (!session.usable()) return;
            if (!completing.compareAndSet(false, true)) return;
            status.setText("ITSOKEY 세션 검증 중");
            executor.execute(() -> {
                try {
                    ItsokeyApiClient api = new ItsokeyApiClient(sessionStore);
                    statusOnUiThread("ITSOKEY 회원정보 확인 중");
                    ItsokeySession memberSession = api.loadMemberInformation(session);
                    statusOnUiThread("ITSOKEY 위젯 세션 발급 중");
                    ItsokeySession widgetSession = api.generateWidgetSession(memberSession);
                    if (!widgetSession.usable()) throw new IllegalStateException("ITSOKEY 위젯 토큰 발급에 실패했습니다");
                    sessionStore.save(widgetSession);
                    String verification = api.verifySession();
                    boolean ok = new JSONObject(verification).optBoolean("ok", false);
                    if (!ok) throw new IllegalStateException(new JSONObject(verification).optString("message", "세션 검증 실패"));
                    runOnUiThread(() -> finishSuccess(verification));
                } catch (Exception error) {
                    sessionStore.clear();
                    completing.set(false);
                    runOnUiThread(() -> status.setText(error.getMessage() == null ? "로그인 검증 실패" : error.getMessage()));
                }
            });
        } catch (Exception ignored) {}
    }

    private void finishSuccess(String verification) {
        Intent result = new Intent();
        result.putExtra("result", verification);
        setResult(RESULT_OK, result);
        finish();
    }

    private void statusOnUiThread(String message) {
        runOnUiThread(() -> status.setText(message));
    }

    private void finishCanceled(String message) {
        Intent result = new Intent();
        result.putExtra("message", message);
        setResult(RESULT_CANCELED, result);
        finish();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finishCanceled("로그인이 취소되었습니다");
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
