package br.com.rbwone.web;

/** Presentation of server-authorized previews; never uses unverified FCM text. */
public final class NotificationText {
    public final String title, body;
    private NotificationText(String title, String body) { this.title = title; this.body = body; }
    public static NotificationText from(String title, String body) {
        return new NotificationText(clean(title, 160, "RBW One"), clean(body, 1000, "Há uma nova notificação para você."));
    }
    private static String clean(String value, int limit, String fallback) {
        if (value == null) return fallback;
        String text = value.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (text.isEmpty()) return fallback;
        int count = text.codePointCount(0, text.length());
        return count > limit ? text.substring(0, text.offsetByCodePoints(0, limit - 1)) + "…" : text;
    }
}
