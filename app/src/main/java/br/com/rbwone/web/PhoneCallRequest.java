package br.com.rbwone.web;

import org.json.JSONObject;
import java.util.Set;

/** UI-only bridge. It never accepts a session token, destination, or a PBX command. */
public final class PhoneCallRequest {
    private static final Set<String> ACTIONS = Set.of("sync", "clear", "getStatus", "requestPermission");
    public final String id, action, ownerKey, callId, state, label, acceptLabel;
    private PhoneCallRequest(String id, String action, String owner, String call, String state, String label, String accept) {
        this.id = id; this.action = action; ownerKey = owner; callId = call; this.state = state; this.label = label; acceptLabel = accept;
    }
    public static PhoneCallRequest parse(String text) throws Exception {
        if (text == null || text.length() > 4096) throw new IllegalArgumentException("Pedido inválido.");
        JSONObject input = new JSONObject(text);
        Object id = input.opt("requestId"), action = input.opt("action");
        if (!(id instanceof String) || !((String) id).matches("[A-Za-z0-9_-]{1,128}")
                || !(action instanceof String) || !ACTIONS.contains(action)) throw new IllegalArgumentException("Pedido inválido.");
        JSONObject payload = input.optJSONObject("payload");
        if (payload == null) payload = new JSONObject();
        String owner = "", call = "", state = "", label = "Chamada no RBW One", accept = "Atender";
        if (action.equals("sync") || action.equals("clear")) {
            if (!(payload.opt("ownerKey") instanceof String)) throw new IllegalArgumentException("Vínculo inválido.");
            owner = payload.getString("ownerKey");
            if (!owner.matches("[A-Za-z0-9_:.-]{16,160}")) throw new IllegalArgumentException("Vínculo inválido.");
        }
        if (action.equals("sync")) {
            if (!(payload.opt("callId") instanceof String) || !(payload.opt("state") instanceof String)) throw new IllegalArgumentException("Chamada inválida.");
            call = payload.getString("callId"); state = payload.getString("state");
            if (!call.matches("[A-Za-z0-9_:.-]{1,160}") || !Set.of("ringing", "talking", "ended").contains(state)) throw new IllegalArgumentException("Chamada inválida.");
            if (payload.has("label")) {
                if (!(payload.opt("label") instanceof String) || payload.getString("label").length() > 160) throw new IllegalArgumentException("Nome inválido.");
                label = payload.getString("label").replaceAll("[\\p{Cntrl}]", " ").trim();
                if (label.isEmpty()) label = "Chamada no RBW One";
                if (label.length() > 80) label = label.substring(0, 80);
            }
            if (payload.has("acceptLabel")) {
                Object value = payload.opt("acceptLabel");
                if (!(value instanceof String) || !Set.of("Atender", "Ligar").contains(value)) throw new IllegalArgumentException("Ação inválida.");
                accept = (String) value;
            }
        }
        return new PhoneCallRequest((String) id, (String) action, owner, call, state, label, accept);
    }
}
