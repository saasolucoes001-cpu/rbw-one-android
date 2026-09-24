package br.com.rbwone.web;

import org.json.JSONObject;
import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class NotificationPolicyTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final String TOKEN = "a".repeat(64);
    private static final String USER = "a1c3ad09-79b6-481e-b496-e88ed28e2209";
    private static final String SESSION = "f1c3ad09-79b6-481e-b496-e88ed28e2210";
    private static final String NOTIFICATION = "d1c3ad09-79b6-481e-b496-e88ed28e2211";
    private SessionRecord current() { return new SessionRecord(TOKEN, true, USER, SESSION, Instant.ofEpochMilli(NOW + 60_000).toString(), "fcm-device-token"); }
    private boolean eligible(SessionRecord record, String user, String session, String route, long sent) { return PushPolicy.eligible(record, NOTIFICATION, user, session, route, sent, NOW); }

    @Test public void originAcceptsOnlyHttpsFirstPartyWithoutCredentials() {
        assertTrue(RoutePolicy.officialOrigin("https://rbwone.com.br/administrativo"));
        assertTrue(RoutePolicy.officialOrigin("https://rbwone.com.br:443/"));
        for (String url : new String[]{"http://rbwone.com.br", "https://rbwone.com.br.evil.test", "https://rbwone.com.br@evil.test", "https://evil.test@rbwone.com.br", "https://rbwone.com.br:8443", "javascript:alert(1)", "file:///rbwone.com.br", "https://sub.rbwone.com.br"}) assertFalse(url, RoutePolicy.officialOrigin(url));
    }
    @Test public void notificationRoutesKeepPortalAndQueries() {
        assertEquals("/administrativo/iniciar-chamados?chamado=abc", RoutePolicy.notificationRoute("/iniciar-chamados?chamado=abc"));
        assertEquals("/colaborador/vagas", RoutePolicy.notificationRoute("/colaborador/vagas"));
        assertEquals("/administrativo", RoutePolicy.notificationRoute("/administrativo"));
    }
    @Test public void notificationRoutesRejectExternalAndEncodedTraversal() {
        for (String route : new String[]{"https://evil.test", "//evil.test", "/../secret", "/administrativo/../secret", "/%2e%2e/secret", "/%2F%2Fevil.test", "/\\evil.test", "/a\n/b", "javascript:alert(1)", "", "/a%00b"}) assertNull(route, RoutePolicy.notificationRoute(route));
        assertNull(RoutePolicy.notificationRoute(null));
    }
    @Test public void routeAndRequestSizeAreBounded() {
        assertNull(RoutePolicy.notificationRoute("/" + "x".repeat(2048)));
        assertThrows(Exception.class, () -> BridgeRequest.parse(" ".repeat(4097)));
    }
    @Test public void validPushForCurrentSessionCanBeDelivered() { assertTrue(eligible(current(), USER, SESSION, "/iniciar-chamados?chamado=1", NOW - 1000)); }
    @Test public void loggedOutSessionCannotReceivePush() { assertFalse(eligible(null, USER, SESSION, "/vagas", NOW)); }
    @Test public void anotherUserCannotReceivePush() { assertFalse(eligible(current(), SESSION, SESSION, "/vagas", NOW)); }
    @Test public void previousLoginOfSameUserCannotReceivePush() { assertFalse(eligible(current(), USER, USER, "/vagas", NOW)); }
    @Test public void expiredSessionCannotReceivePush() {
        SessionRecord expired = new SessionRecord(TOKEN, true, USER, SESSION, Instant.ofEpochMilli(NOW).toString(), "fcm");
        assertFalse(eligible(expired, USER, SESSION, "/vagas", NOW));
    }
    @Test public void pushOlderThanBackendTtlCannotBeDelivered() { assertFalse(eligible(current(), USER, SESSION, "/vagas", NOW - 900_001)); }
    @Test public void timestampMissingOrFarInFutureCannotBeDelivered() {
        assertFalse(eligible(current(), USER, SESSION, "/vagas", 0));
        assertFalse(eligible(current(), USER, SESSION, "/vagas", NOW + 60_001));
    }
    @Test public void untrustedRouteOrNotificationIdCannotBeDelivered() {
        assertFalse(eligible(current(), USER, SESSION, "https://evil.test", NOW));
        assertFalse(PushPolicy.eligible(current(), "bad", USER, SESSION, "/vagas", NOW, NOW));
    }
    @Test public void pendingUnverifiedRegistrationCannotReceivePush() {
        assertFalse(eligible(new SessionRecord(TOKEN, true, "", "", "", ""), USER, SESSION, "/vagas", NOW));
    }
    @Test public void sessionRoundTripKeepsCanonicalBindingAndSoundPreference() throws Exception {
        SessionRecord original = new SessionRecord(TOKEN, false, USER, SESSION, Instant.ofEpochMilli(NOW + 60_000).toString(), "rotated-fcm");
        SessionRecord decoded = SessionRecord.parse(new JSONObject(original.json().toString()));
        assertTrue(decoded.matches(USER, SESSION, NOW)); assertFalse(decoded.soundEnabled); assertEquals("rotated-fcm", decoded.fcmToken); assertEquals(TOKEN, decoded.token);
    }
    @Test public void bridgeAcceptsValidSessionRequestAndReturnsId() throws Exception {
        BridgeRequest request = BridgeRequest.parse(new JSONObject().put("requestId", "r-1").put("action", "setSession").put("payload", new JSONObject().put("sessionToken", TOKEN).put("soundEnabled", false)).toString());
        assertEquals("r-1", request.id); assertEquals("setSession", request.action); assertFalse(request.payload.getBoolean("soundEnabled"));
    }
    @Test public void bridgeRejectsUnknownActionMissingIdAndInvalidSession() {
        for (String value : new String[]{"{}", "{\"requestId\":1,\"action\":\"clear\"}", "{\"requestId\":\"x\",\"action\":\"openExternal\"}", "{\"requestId\":\"x\",\"action\":\"setSession\",\"payload\":{\"sessionToken\":\"bad\",\"soundEnabled\":true}}"}) assertThrows(value, Exception.class, () -> BridgeRequest.parse(value));
    }
    @Test public void bridgeDoesNotCoerceStringSoundPreference() throws Exception {
        String input = new JSONObject().put("requestId", "x").put("action", "setSession").put("payload", new JSONObject().put("sessionToken", TOKEN).put("soundEnabled", "false")).toString();
        assertThrows(Exception.class, () -> BridgeRequest.parse(input));
    }
}
