package br.com.rbwone.web;

import org.json.JSONObject;

/** Bounded, persistent deduplication of IDs already displayed for the current login. */
public final class NotificationHistory {
    private NotificationHistory() { }
    public static boolean contains(String encoded, String id) throws Exception { return new JSONObject(encoded).has(id); }
    public static String add(String encoded, String id, long now) throws Exception {
        JSONObject seen = new JSONObject(encoded), fresh = new JSONObject();
        var keys = seen.keys(); int retained = 0;
        while (keys.hasNext()) {
            String key = keys.next(); long timestamp = seen.optLong(key);
            if (!key.equals(id) && now - timestamp < 86_400_000L && retained++ < 255) fresh.put(key, timestamp);
        }
        return fresh.put(id, now).toString();
    }
}
