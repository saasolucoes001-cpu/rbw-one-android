package br.com.rbwone.web;

public final class PushPolicy {
    private PushPolicy() {}
    public static boolean eligible(SessionRecord session, String id, String user, String sessionId, String route, long sentAt, long now) {
        return session != null && RoutePolicy.uuid(id) && session.matches(user, sessionId, now) && RoutePolicy.notificationRoute(route) != null
                && sentAt > 0 && now - sentAt <= 900_000L && sentAt - now <= 60_000L;
    }
}
