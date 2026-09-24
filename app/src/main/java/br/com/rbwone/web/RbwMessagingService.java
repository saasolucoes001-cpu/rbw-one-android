package br.com.rbwone.web;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public final class RbwMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(@NonNull String token) { ((RbwApplication) getApplication()).notifications().refresh(token); }
    @Override public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> payload = message.getData();
        String id = payload.get("notification_id");
        NotificationCoordinator coordinator = ((RbwApplication) getApplication()).notifications();
        if (!PushPolicy.eligible(coordinator.store().read(), id, payload.get("user_id"), payload.get("session_id"), payload.get("route"), message.getSentTime(), System.currentTimeMillis())) return;
        Data data = new Data.Builder().putString("id", id).putString("user", payload.get("user_id")).putString("session", payload.get("session_id"))
                .putString("route", payload.get("route")).putLong("sent", message.getSentTime()).build();
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(NotificationDeliveryWorker.class).setInputData(data)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag("rbw-notification");
        // Older Android versions run this short task normally, without a foreground-service notification.
        if (android.os.Build.VERSION.SDK_INT >= 31) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        OneTimeWorkRequest request = builder.build();
        WorkManager.getInstance(this).enqueueUniqueWork("rbw-notification-" + id, ExistingWorkPolicy.KEEP, request);
    }
}
