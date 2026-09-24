package br.com.rbwone.web;

import org.json.JSONObject;
import java.time.Instant;

public final class SessionRecord {
    public final String token, userId, sessionId, expiresAt, fcmToken;
    public final boolean soundEnabled;
    public SessionRecord(String token, boolean soundEnabled, String userId, String sessionId, String expiresAt, String fcmToken) {
        this.token = token; this.soundEnabled = soundEnabled; this.userId = userId; this.sessionId = sessionId; this.expiresAt = expiresAt; this.fcmToken = fcmToken;
    }
    public boolean verified(long now) {
        try { return RoutePolicy.sessionToken(token) && RoutePolicy.uuid(userId) && RoutePolicy.uuid(sessionId) && Instant.parse(expiresAt).toEpochMilli() > now; }
        catch (Exception ignored) { return false; }
    }
    public boolean matches(String user, String session, long now) { return verified(now) && userId.equals(user) && sessionId.equals(session); }
    public JSONObject json() throws Exception {
        return new JSONObject().put("token", token).put("sound", soundEnabled).put("user", userId).put("session", sessionId).put("expires", expiresAt).put("fcm", fcmToken);
    }
    public static SessionRecord parse(JSONObject json) {
        return new SessionRecord(json.optString("token"), json.optBoolean("sound", true), json.optString("user"), json.optString("session"), json.optString("expires"), json.optString("fcm"));
    }
}
