package br.com.rbwone.web;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class NotificationChannels {
    // Keep the existing channel ID and resource URI to preserve system sound preferences.
    public static final String BIRD = "rbw_updates_bem_te_vi_v1";
    public static final String SILENT = "rbw_updates_silent_v1";
    private NotificationChannels() {}
    public static Uri sound(Context context) { return Uri.parse("android.resource://" + context.getPackageName() + "/raw/bem_te_vi"); }
    public static void ensure(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel bird = new NotificationChannel(BIRD, "Atualizações · RBW One", NotificationManager.IMPORTANCE_DEFAULT);
        bird.setDescription("Novas notificações do RBW One com som do RBW One.");
        bird.setSound(sound(context), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        bird.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        NotificationChannel silent = new NotificationChannel(SILENT, "Atualizações silenciosas", NotificationManager.IMPORTANCE_DEFAULT);
        silent.setDescription("Novas notificações sem som."); silent.setSound(null, null); silent.enableVibration(false);
        silent.setLockscreenVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(bird); manager.createNotificationChannel(silent);
    }
    public static void show(Context context, String id, String route, SessionRecord session) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(context, MainActivity.class).setAction("br.com.rbwone.web.OPEN_NOTIFICATION")
                .setData(Uri.parse("rbwone://notification/" + id))
                .putExtra("notification_id", id).putExtra("route", route).putExtra("user_id", session.userId).putExtra("session_id", session.sessionId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, session.soundEnabled ? BIRD : SILENT)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("RBW One").setContentText("Há uma nova notificação para você.")
                .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(NotificationCompat.CATEGORY_STATUS).setPriority(NotificationCompat.PRIORITY_DEFAULT);
        if (session.soundEnabled) builder.setSound(sound(context)); else builder.setSilent(true);
        NotificationManagerCompat.from(context).notify(id, 0, builder.build());
    }
}
