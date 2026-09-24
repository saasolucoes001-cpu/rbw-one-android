package br.com.rbwone.web;

import org.json.JSONObject;
import java.util.Set;

/** Only the first-party top frame can reach this parser; nevertheless bound every request. */
public final class BridgeRequest {
    private static final Set<String> ACTIONS = Set.of("setSession", "clear", "previewSound", "getStatus", "requestPermission");
    public final String id, action;
    public final JSONObject payload;
    private BridgeRequest(String id, String action, JSONObject payload) { this.id = id; this.action = action; this.payload = payload; }
    public static BridgeRequest parse(String text) throws Exception {
        if (text == null || text.length() > 4096) throw new IllegalArgumentException("Pedido inválido.");
        JSONObject input = new JSONObject(text);
        Object id = input.opt("requestId"), action = input.opt("action");
        if (!(id instanceof String) || ((String) id).isEmpty() || ((String) id).length() > 128 || !(action instanceof String) || !ACTIONS.contains(action)) throw new IllegalArgumentException("Pedido inválido.");
        JSONObject payload = input.optJSONObject("payload");
        if (payload == null) payload = new JSONObject();
        if (action.equals("setSession") && (!(payload.opt("sessionToken") instanceof String) || !RoutePolicy.sessionToken(payload.optString("sessionToken")) || !(payload.opt("soundEnabled") instanceof Boolean))) throw new IllegalArgumentException("Sessão inválida.");
        return new BridgeRequest((String) id, (String) action, payload);
    }
}
