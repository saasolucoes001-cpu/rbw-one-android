package br.com.rbwone.web;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;

/** Alerts for a ringing event already received by the active WebView, not a push/SIP client. */
public final class PhoneCallAlerts {
    public static final String OPEN_CALL = "br.com.rbwone.web.OPEN_PHONE_CALL";
    private static final String CHANNEL = "rbw-live-calls-v1", TAG = "rbw-live-call";
    private final Context context;
    private final PhoneCallState state = new PhoneCallState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean foreground;
    private Runnable expiry;

    public PhoneCallAlerts(Context context) {
        this.context = context;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Chamadas no RBW One", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Chamadas recebidas enquanto o telefone do aplicativo permanece conectado.");
            // The existing WebView owns ringing audio. Avoid a second simultaneous ringtone.
            channel.setSound(null, null); channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
    private long now() { return SystemClock.elapsedRealtime(); }
    public boolean channelEnabled() {
        if (Build.VERSION.SDK_INT < 26) return true;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager == null ? null : manager.getNotificationChannel(CHANNEL);
        return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    private boolean permissionGranted() {
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                && NotificationManagerCompat.from(context).areNotificationsEnabled() && channelEnabled();
    }
    public JSONObject status(String permission) throws Exception {
        boolean granted = permissionGranted();
        String effectivePermission = !channelEnabled() || ("granted".equals(permission) && !granted) ? "denied" : permission;
        return new JSONObject().put("supported", true).put("permission", effectivePermission)
                .put("active", !foreground && state.current(now()) != null && granted);
    }
    public void sync(PhoneCallRequest request) { state.sync(request, now()); render(); }
    public void clear(String owner) { state.clear(owner); render(); }
    public void reset() { state.reset(); cancel(); }
    public void setForeground(boolean value) { foreground = value; render(); }
    public boolean isOwner(String owner) { return state.isOwner(owner); }
    private PendingIntent pending(PhoneCallState.Call call, String action) {
        Intent intent = new Intent(context, MainActivity.class).setAction(OPEN_CALL)
                .setData(Uri.parse("rbwone://live-call/" + call.nonce + "/" + action))
                .putExtra("call_nonce", call.nonce).putExtra("call_action", action)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public PhoneCallState.Action takeIntent(Intent intent) {
        if (intent == null || !OPEN_CALL.equals(intent.getAction())) return null;
        String nonce = intent.getStringExtra("call_nonce"), action = intent.getStringExtra("call_action");
        intent.setAction(Intent.ACTION_MAIN);
        if (nonce == null || action == null) return null;
        PhoneCallState.Action result = state.respond(nonce, action, now());
        render(); return result;
    }
    private void cancel() {
        if (expiry != null) { handler.removeCallbacks(expiry); expiry = null; }
        NotificationManagerCompat.from(context).cancel(TAG, 0);
    }
    private void render() {
        PhoneCallState.Call call = state.current(now());
        if (foreground || call == null || !permissionGranted()) { cancel(); return; }
        PendingIntent answer = pending(call, "answer"), reject = pending(call, "reject");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("Chamada no RBW One")
                .setContentText(call.label).setContentIntent(pending(call, "open")).setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(new NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("Chamada no RBW One").build())
                .setOnlyAlertOnce(true).setAutoCancel(false).setTimeoutAfter(Math.max(1, call.expiresAt - now()));
        if (call.acceptLabel.equals("Atender")) {
            builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(
                    new Person.Builder().setName(call.label).setImportant(true).build(), reject, answer));
        } else {
            // CallStyle action labels are selected by Android; callback origination must say "Ligar".
            builder.addAction(R.drawable.ic_notification, "Ligar", answer)
                    .addAction(R.drawable.ic_notification, "Recusar", reject);
        }
        try { NotificationManagerCompat.from(context).notify(TAG, 0, builder.build()); }
        catch (SecurityException denied) { cancel(); return; }
        if (expiry != null) handler.removeCallbacks(expiry);
        String nonce = call.nonce;
        expiry = () -> { PhoneCallState.Call current = state.current(now()); if (current == null || current.nonce.equals(nonce)) render(); };
        handler.postDelayed(expiry, Math.max(1, call.expiresAt - now()));
    }
    public void close() { reset(); }
}
