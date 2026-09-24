package br.com.rbwone.web;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.WorkManager;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class NotificationCoordinator {
    private final Context context;
    private final SecureSessionStore store;
    private final NotificationsApi api = new NotificationsApi();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Consumer<JSONObject>> listeners = new CopyOnWriteArrayList<>();
    private long generation = 0;
    private String failure = "";
    private final boolean configured;

    public NotificationCoordinator(Context context) {
        this.context = context.getApplicationContext(); store = new SecureSessionStore(context);
        boolean ready = false;
        if (BuildConfig.FIREBASE_CONFIGURED) {
            try { ready = !FirebaseApp.getApps(context).isEmpty() || FirebaseApp.initializeApp(context) != null; } catch (RuntimeException ignored) { }
        }
        configured = ready;
    }
    public SecureSessionStore store() { return store; }
    public NotificationsApi api() { return api; }
    public void addListener(Consumer<JSONObject> listener) { listeners.add(listener); }
    public void removeListener(Consumer<JSONObject> listener) { listeners.remove(listener); }
    public void emit() { JSONObject status = status(); for (Consumer<JSONObject> listener : listeners) listener.accept(status); }
    public boolean permissionGranted() {
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                && NotificationManagerCompat.from(context).areNotificationsEnabled();
    }
    public void permissionPrompted() { context.getSharedPreferences("rbw_permission_ui", Context.MODE_PRIVATE).edit().putBoolean("asked", true).apply(); }
    public String permission() {
        if (permissionGranted()) return "granted";
        boolean asked = context.getSharedPreferences("rbw_permission_ui", Context.MODE_PRIVATE).getBoolean("asked", false);
        return Build.VERSION.SDK_INT >= 33 && !asked ? "prompt" : "denied";
    }
    public synchronized JSONObject status() {
        SessionRecord record = store.read();
        boolean registered = record != null && record.verified(System.currentTimeMillis()) && !record.fcmToken.isEmpty();
        boolean active = configured && registered && failure.isEmpty() && permissionGranted();
        try {
            JSONObject result = new JSONObject().put("supported", true).put("configured", configured).put("permission", permission()).put("active", active);
            if (!configured) result.put("reason", "config_missing");
            else if (record == null) result.put("reason", "session_missing");
            else if (!failure.isEmpty()) result.put("reason", "registration_failed");
            else if (!registered) result.put("reason", "token_pending");
            return result;
        } catch (Exception ignored) { return new JSONObject(); }
    }

    public AsyncResult<JSONObject> setSession(String token, boolean soundEnabled) {
        if (!RoutePolicy.sessionToken(token)) return AsyncResult.failedFuture(new IllegalArgumentException("Sessão inválida."));
        final long requestGeneration;
        synchronized (this) {
            SessionRecord previous = store.read();
            if (failure.isEmpty() && previous != null && previous.token.equals(token) && previous.soundEnabled == soundEnabled && previous.verified(System.currentTimeMillis()) && !previous.fcmToken.isEmpty()) return AsyncResult.completedFuture(status());
            generation++; requestGeneration = generation; failure = "";
            boolean sameUserSession = previous != null && previous.token.equals(token);
            if (!sameUserSession) { store.clear(); NotificationManagerCompat.from(context).cancelAll(); }
            try { store.write(new SessionRecord(token, soundEnabled,
                    sameUserSession ? previous.userId : "", sameUserSession ? previous.sessionId : "",
                    sameUserSession ? previous.expiresAt : "", sameUserSession ? previous.fcmToken : "")); }
            catch (Exception ignored) { return AsyncResult.failedFuture(new IllegalStateException("Não foi possível guardar a sessão com segurança.")); }
        }
        emit();
        if (!configured || !permissionGranted()) return AsyncResult.completedFuture(status());
        return register(token, soundEnabled, requestGeneration, null);
    }

    private AsyncResult<JSONObject> register(String token, boolean sound, long requestGeneration, String renewedToken) {
        return AsyncResult.supplyAsync(() -> {
            try {
                FirebaseMessaging messaging = FirebaseMessaging.getInstance(); messaging.setAutoInitEnabled(true);
                String fcm = renewedToken != null ? renewedToken : Tasks.await(messaging.getToken(), 5, TimeUnit.SECONDS);
                synchronized (this) { if (generation != requestGeneration) return status(); }
                JSONObject response = api.request("register_device", new JSONObject().put("installation_id", store.installationId()).put("fcm_token", fcm).put("platform", "android").put("sound_enabled", sound), token);
                SessionRecord verified = new SessionRecord(token, sound, response.optString("user_id"), response.optString("session_id"), response.optString("session_expires_at"), fcm);
                if (!response.optBoolean("success") || !verified.verified(System.currentTimeMillis())) throw new IllegalStateException("Vínculo de sessão inválido.");
                synchronized (this) {
                    if (generation != requestGeneration) return status();
                    store.write(verified); failure = "";
                }
            } catch (Exception error) {
                synchronized (this) {
                    if (generation != requestGeneration) return status();
                    failure = "registration_failed";
                    if (error instanceof NotificationsApi.ApiException && (((NotificationsApi.ApiException) error).status == 401 || ((NotificationsApi.ApiException) error).status == 403)) {
                        generation++; store.clear(); NotificationManagerCompat.from(context).cancelAll();
                    }
                }
            }
            emit(); return status();
        }, network);
    }

    public AsyncResult<JSONObject> refresh(String renewedToken) {
        SessionRecord record;
        long requestGeneration;
        synchronized (this) {
            record = store.read();
            if (record == null || !configured || !permissionGranted()) return AsyncResult.completedFuture(status());
            requestGeneration = ++generation; failure = "";
        }
        return register(record.token, record.soundEnabled, requestGeneration, renewedToken);
    }

    public AsyncResult<JSONObject> clear() {
        final SessionRecord previous;
        synchronized (this) {
            previous = store.read(); generation++; failure = ""; store.clear();
            NotificationManagerCompat.from(context).cancelAll();
            WorkManager.getInstance(context).cancelAllWorkByTag("rbw-notification");
            if (configured) FirebaseMessaging.getInstance().setAutoInitEnabled(false);
        }
        emit();
        return AsyncResult.supplyAsync(() -> {
            if (previous != null) try { api.request("revoke_device", new JSONObject().put("installation_id", store.installationId()), previous.token); } catch (Exception ignored) { /* Local removal already prevents display. */ }
            return status();
        }, network);
    }

    public synchronized boolean displayIfCurrent(SessionRecord captured, String notificationId, String route, String userId, String sessionId) {
        SessionRecord current = store.read();
        if (current == null || !current.token.equals(captured.token) || !current.matches(userId, sessionId, System.currentTimeMillis()) || !permissionGranted()) return false;
        if (store.hasSeen(notificationId)) return false;
        NotificationChannels.show(context, notificationId, route, current);
        store.remember(notificationId, System.currentTimeMillis());
        return true;
    }
}
