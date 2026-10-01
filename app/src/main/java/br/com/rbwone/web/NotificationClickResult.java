package br.com.rbwone.web;

import org.json.JSONObject;

/** Navigation is allowed only after the server confirms ownership and cleanup. */
public final class NotificationClickResult {
    private NotificationClickResult() {}
    public static String route(JSONObject response) {
        if (response == null || !Boolean.TRUE.equals(response.opt("success"))) return null;
        Object route = response.opt("route");
        return route instanceof String ? RoutePolicy.notificationRoute((String) route) : null;
    }
}
