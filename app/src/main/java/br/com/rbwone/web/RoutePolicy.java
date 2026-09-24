package br.com.rbwone.web;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RoutePolicy {
    public static final String ORIGIN = "https://rbwone.com.br";
    private static final Pattern PORTAL = Pattern.compile("^/(administrativo|colaborador|prestador|cliente)(/|$)");
    private static final Pattern UNSAFE = Pattern.compile("[\\\\\\s\\x00-\\x1f]|%2e|%2f|%5c|%00", Pattern.CASE_INSENSITIVE);
    private RoutePolicy() {}

    public static boolean officialOrigin(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && "rbwone.com.br".equalsIgnoreCase(uri.getHost()) && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (Exception ignored) { return false; }
    }

    public static String notificationRoute(String value) {
        if (value == null || value.length() > 2048 || !value.startsWith("/") || value.startsWith("//") || UNSAFE.matcher(value).find()) return null;
        String path = value.split("[?#]", 2)[0];
        for (String segment : path.split("/")) if (segment.equals(".") || segment.equals("..")) return null;
        String canonical = PORTAL.matcher(value).find() ? value : "/administrativo" + value;
        try {
            URI uri = new URI(ORIGIN + canonical);
            return officialOrigin(uri.toString()) && PORTAL.matcher(uri.getPath()).find() ? canonical : null;
        } catch (Exception ignored) { return null; }
    }

    public static boolean sessionToken(String value) { return value != null && value.matches("(?i)[a-f0-9]{64}"); }
    public static boolean uuid(String value) { return value != null && value.matches("(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"); }
}
