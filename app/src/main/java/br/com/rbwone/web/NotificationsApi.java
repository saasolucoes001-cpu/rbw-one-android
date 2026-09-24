package br.com.rbwone.web;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class NotificationsApi {
    public static final class ApiException extends IOException {
        public final int status;
        ApiException(int status) { super("Não foi possível validar as notificações (" + status + ")."); this.status = status; }
    }
    public JSONObject request(String action, JSONObject payload, String token) throws Exception {
        if (!RoutePolicy.sessionToken(token)) throw new ApiException(401);
        HttpURLConnection connection = (HttpURLConnection) new URL(BuildConfig.NOTIFICATIONS_API).openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
        connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("apikey", BuildConfig.PUBLIC_API_KEY);
        connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.PUBLIC_API_KEY);
        connection.setRequestProperty("x-rbw-session", token);
        byte[] body = payload.put("action", action).toString().getBytes(StandardCharsets.UTF_8);
        try {
            try (var output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new ApiException(status);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) { bytes.write(buffer, 0, count); if (bytes.size() > 65_536) throw new IOException("Resposta inválida."); }
            }
            JSONObject result = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            if (result.has("error")) throw new ApiException(502);
            return result;
        } finally { connection.disconnect(); }
    }
}
