package br.com.rbwone.web;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION = 101, WEB_PERMISSION = 102, LOCATION_PERMISSION = 103, FILE_PICKER = 104;
    private WebView web;
    private LinearLayout errorPanel;
    private NotificationCoordinator coordinator;
    private JavaScriptReplyProxy replyProxy;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final List<AsyncResult<JSONObject>> permissionWaiters = new ArrayList<>();
    private boolean askingNotifications;
    private PermissionRequest pendingWebPermission;
    private GeolocationPermissions.Callback pendingLocation;
    private String pendingLocationOrigin;
    private ValueCallback<Uri[]> fileCallback;
    private MediaPlayer preview;
    private boolean destroyed;
    private final Consumer<JSONObject> statusListener = status -> runOnUiThread(() -> emit("status", "status", status));

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        coordinator = ((RbwApplication) getApplication()).notifications();
        coordinator.addListener(statusListener);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        if (!BuildConfig.FIREBASE_CONFIGURED) addNotice(root, "Este aplicativo ainda precisa da configuração Firebase para receber notificações em segundo plano.");
        errorPanel = new LinearLayout(this); errorPanel.setOrientation(LinearLayout.VERTICAL); errorPanel.setPadding(24, 24, 24, 24); errorPanel.setVisibility(View.GONE);
        TextView failure = new TextView(this); failure.setText(R.string.load_failed); errorPanel.addView(failure);
        Button retry = new Button(this); retry.setText(R.string.retry); retry.setOnClickListener(view -> { errorPanel.setVisibility(View.GONE); web.reload(); }); errorPanel.addView(retry); root.addView(errorPanel);
        web = new WebView(this); web.setId(View.generateViewId()); root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true); settings.setSupportMultipleWindows(false);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (RoutePolicy.officialOrigin(url)) return false;
                if (request.isForMainFrame() && request.hasGesture()) openExternal(request.getUrl());
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) { replyProxy = null; errorPanel.setVisibility(View.GONE); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) errorPanel.setVisibility(View.VISIBLE);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) { runOnUiThread(() -> handleWebPermission(request)); }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) { if (pendingWebPermission == request) pendingWebPermission = null; }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (!RoutePolicy.officialOrigin(origin) || !officialPage() || pendingLocation != null) { callback.invoke(origin, false, false); return; }
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) { callback.invoke(origin, true, false); return; }
                pendingLocation = callback; pendingLocationOrigin = origin;
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
            }
            @Override public void onGeolocationPermissionsHidePrompt() { pendingLocation = null; pendingLocationOrigin = null; }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (!officialPage()) { callback.onReceiveValue(null); return true; }
                if (fileCallback != null) fileCallback.onReceiveValue(null); fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                String[] types = params.getAcceptTypes();
                if (types != null && types.length > 0 && !types[0].isEmpty()) intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(intent, FILE_PICKER); } catch (ActivityNotFoundException ignored) { fileCallback.onReceiveValue(null); fileCallback = null; }
                return true;
            }
        });
        web.setDownloadListener((url, userAgent, disposition, mime, length) -> openExternal(Uri.parse(url)));
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "RBWNotifications", Collections.singleton(RoutePolicy.ORIGIN), (view, message, sourceOrigin, isMainFrame, proxy) -> {
                if (!isMainFrame || !RoutePolicy.officialOrigin(sourceOrigin.toString()) || !officialPage()) return;
                replyProxy = proxy;
                try { handleBridge(BridgeRequest.parse(message.getData()), proxy); }
                catch (Exception ignored) { /* Invalid or unbounded requests cannot reach privileged actions. */ }
            });
        } else addNotice(root, "Atualize o Android System WebView para ativar as notificações deste aplicativo.");
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(RoutePolicy.ORIGIN);
        handleNotificationIntent(getIntent());
    }

    private void addNotice(LinearLayout parent, String text) { TextView notice = new TextView(this); notice.setPadding(16, 12, 16, 12); notice.setText(text); notice.setBackgroundColor(0xfffff2c6); parent.addView(notice); }
    private boolean officialPage() { return web != null && RoutePolicy.officialOrigin(web.getUrl()); }
    private boolean hasPermission(String permission) { return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED; }

    private void handleBridge(BridgeRequest request, JavaScriptReplyProxy proxy) {
        AsyncResult<JSONObject> result;
        switch (request.action) {
            case "setSession":
                SessionRecord previous = coordinator.store().read();
                if (!request.payload.optBoolean("soundEnabled") || previous == null || !previous.token.equals(request.payload.optString("sessionToken"))) stopPreview();
                result = coordinator.setSession(request.payload.optString("sessionToken"), request.payload.optBoolean("soundEnabled")); break;
            case "clear": stopPreview(); result = coordinator.clear(); break;
            case "getStatus": result = AsyncResult.completedFuture(coordinator.status()); break;
            case "requestPermission": result = requestNotifications(); break;
            case "previewSound":
                try { playPreview(); result = AsyncResult.completedFuture(coordinator.status()); }
                catch (Exception ignored) { result = new AsyncResult<>(); result.completeExceptionally(new IllegalStateException("Não foi possível reproduzir o som.")); }
                break;
            default: return;
        }
        result.whenComplete((status, error) -> runOnUiThread(() -> {
            if (destroyed || proxy != replyProxy || !officialPage() || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return;
            try {
                JSONObject reply = new JSONObject().put("requestId", request.id).put("ok", error == null);
                if (error == null) reply.put("result", status); else reply.put("error", "Não foi possível concluir o pedido de notificações.");
                proxy.postMessage(reply.toString());
            } catch (Exception ignored) { }
        }));
    }

    private void emit(String event, String key, Object value) {
        if (destroyed || replyProxy == null || !officialPage() || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return;
        try { replyProxy.postMessage(new JSONObject().put("event", event).put(key, value).toString()); } catch (Exception ignored) { }
    }

    private AsyncResult<JSONObject> requestNotifications() {
        if (coordinator.permissionGranted()) return coordinator.refresh(null);
        AsyncResult<JSONObject> result = new AsyncResult<>(); permissionWaiters.add(result);
        if (askingNotifications) return result;
        askingNotifications = true;
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                && (!coordinator.permission().equals("denied") || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS))) {
            coordinator.permissionPrompted(); ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        } else {
            try {
                Intent settings = Build.VERSION.SDK_INT >= 26
                        ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(settings);
            }
            catch (ActivityNotFoundException ignored) { finishNotificationPermission(); }
        }
        return result;
    }
    private void finishNotificationPermission() {
        if (!askingNotifications) return;
        askingNotifications = false;
        List<AsyncResult<JSONObject>> pending = new ArrayList<>(permissionWaiters); permissionWaiters.clear();
        coordinator.refresh(null).whenComplete((status, error) -> { for (AsyncResult<JSONObject> request : pending) { if (error == null) request.complete(status); else request.completeExceptionally(error); } });
    }
    private void playPreview() throws Exception {
        SessionRecord session = coordinator.store().read();
        if (session == null || !RoutePolicy.sessionToken(session.token)) throw new IllegalStateException("Faça login para testar o som.");
        stopPreview();
        MediaPlayer player = new MediaPlayer(); preview = player;
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        try (android.content.res.AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.bem_te_vi)) { player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength()); }
        player.setOnCompletionListener(completed -> { completed.release(); if (preview == completed) preview = null; });
        player.setOnErrorListener((failed, what, extra) -> { failed.release(); if (preview == failed) preview = null; return true; });
        player.prepare(); player.start();
    }
    private void stopPreview() { if (preview != null) { preview.release(); preview = null; } }

    private void handleWebPermission(PermissionRequest request) {
        if (!RoutePolicy.officialOrigin(request.getOrigin().toString()) || !officialPage() || pendingWebPermission != null) { request.deny(); return; }
        List<String> permissions = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) permissions.add(Manifest.permission.CAMERA);
            else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) permissions.add(Manifest.permission.RECORD_AUDIO);
            else { request.deny(); return; }
        }
        if (permissions.isEmpty()) { request.deny(); return; }
        pendingWebPermission = request;
        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), WEB_PERMISSION);
    }
    @Override public void onRequestPermissionsResult(int code, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == NOTIFICATION_PERMISSION) finishNotificationPermission();
        if (code == WEB_PERMISSION && pendingWebPermission != null) {
            PermissionRequest request = pendingWebPermission; pendingWebPermission = null;
            boolean allow = officialPage() && RoutePolicy.officialOrigin(request.getOrigin().toString()) && results.length > 0;
            for (int result : results) allow &= result == PackageManager.PERMISSION_GRANTED;
            if (allow) request.grant(request.getResources()); else request.deny();
        }
        if (code == LOCATION_PERMISSION && pendingLocation != null) {
            pendingLocation.invoke(pendingLocationOrigin, officialPage() && (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)), false);
            pendingLocation = null; pendingLocationOrigin = null;
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && officialPage()) {
                if (data.getClipData() != null) { result = new Uri[data.getClipData().getItemCount()]; for (int i = 0; i < result.length; i++) result[i] = data.getClipData().getItemAt(i).getUri(); }
                else if (data.getData() != null) result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result); fileCallback = null;
        }
    }

    private void openExternal(Uri uri) {
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme) || "mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme))) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (ActivityNotFoundException ignored) { Toast.makeText(this, "Nenhum aplicativo disponível para abrir este link.", Toast.LENGTH_SHORT).show(); }
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleNotificationIntent(intent); }
    private void handleNotificationIntent(Intent intent) {
        if (intent == null || !"br.com.rbwone.web.OPEN_NOTIFICATION".equals(intent.getAction())) return;
        String id = intent.getStringExtra("notification_id"), user = intent.getStringExtra("user_id"), sessionId = intent.getStringExtra("session_id");
        intent.setAction(Intent.ACTION_MAIN); // Do not replay a click after recreation.
        SessionRecord captured = coordinator.store().read();
        if (!RoutePolicy.uuid(id) || captured == null || !captured.matches(user, sessionId, System.currentTimeMillis())) return;
        AsyncResult.supplyAsync(() -> {
            try {
                JSONObject response = coordinator.api().request("verify_push", new JSONObject().put("installation_id", coordinator.store().installationId()).put("notification_id", id), captured.token);
                if (!response.optBoolean("allowed") || !id.equals(response.optString("notification_id")) || !user.equals(response.optString("user_id")) || !sessionId.equals(response.optString("session_id"))) return null;
                return RoutePolicy.notificationRoute(response.optString("route"));
            } catch (Exception ignored) { return null; }
        }, network).thenAccept(route -> runOnUiThread(() -> {
            SessionRecord current = coordinator.store().read();
            if (destroyed || route == null || current == null || !current.token.equals(captured.token) || !current.matches(user, sessionId, System.currentTimeMillis())) return;
            if (replyProxy != null && officialPage()) emit("open", "route", route);
            else web.loadUrl(RoutePolicy.ORIGIN + route);
        }));
    }
    @Override protected void onResume() { super.onResume(); if (coordinator != null) { coordinator.emit(); if (askingNotifications) finishNotificationPermission(); } }
    @Override protected void onSaveInstanceState(Bundle state) { web.saveState(state); super.onSaveInstanceState(state); }
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() {
        destroyed = true; coordinator.removeListener(statusListener); replyProxy = null;
        for (AsyncResult<JSONObject> pending : permissionWaiters) pending.completeExceptionally(new IllegalStateException("Tela encerrada.")); permissionWaiters.clear();
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        if (pendingWebPermission != null) pendingWebPermission.deny();
        if (pendingLocation != null) pendingLocation.invoke(pendingLocationOrigin, false, false);
        stopPreview();
        if (web != null) { web.stopLoading(); web.destroy(); }
        network.shutdownNow(); super.onDestroy();
    }
}
