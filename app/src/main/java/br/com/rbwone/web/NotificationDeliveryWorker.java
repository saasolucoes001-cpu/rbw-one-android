package br.com.rbwone.web;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

public final class NotificationDeliveryWorker extends Worker {
    public NotificationDeliveryWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    @NonNull @Override public Result doWork() {
        NotificationCoordinator coordinator = ((RbwApplication) getApplicationContext()).notifications();
        SessionRecord session = coordinator.store().read();
        String id = getInputData().getString("id"), user = getInputData().getString("user"), sessionId = getInputData().getString("session"), route = getInputData().getString("route");
        if (!PushPolicy.eligible(session, id, user, sessionId, route, getInputData().getLong("sent", 0), System.currentTimeMillis()) || !coordinator.permissionGranted()) return Result.success();
        try {
            JSONObject verified = coordinator.api().request("verify_push", new JSONObject().put("installation_id", coordinator.store().installationId()).put("notification_id", id), session.token);
            if (!verified.optBoolean("allowed") || !id.equals(verified.optString("notification_id")) || !user.equals(verified.optString("user_id")) || !sessionId.equals(verified.optString("session_id"))) return Result.success();
            String verifiedRoute = RoutePolicy.notificationRoute(verified.optString("route"));
            if (verifiedRoute != null && !isStopped()) coordinator.displayIfCurrent(session, id, verifiedRoute, user, sessionId);
            return Result.success();
        } catch (NotificationsApi.ApiException error) {
            if (error.status == 401 || error.status == 403) return Result.success();
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        } catch (Exception ignored) { return getRunAttemptCount() < 3 ? Result.retry() : Result.failure(); }
    }
}
