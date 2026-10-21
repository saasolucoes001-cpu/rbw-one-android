package br.com.rbwone.web;

import java.util.LinkedHashSet;
import java.util.UUID;

/** Ephemeral state for a live WebView; restarting the process never restores an answer command. */
public final class PhoneCallState {
    public static final long MAX_RING_MS = 60_000;
    public static final class Call {
        public final String ownerKey, callId, nonce, label, acceptLabel;
        public final long expiresAt;
        private Call(String owner, String id, String nonce, String label, String accept, long expiresAt) {
            ownerKey = owner; callId = id; this.nonce = nonce; this.label = label; acceptLabel = accept; this.expiresAt = expiresAt;
        }
    }
    public static final class Action {
        public final String ownerKey, callId, action;
        private Action(String owner, String id, String action) { ownerKey = owner; callId = id; this.action = action; }
    }
    private String ownerKey = "";
    private final LinkedHashSet<String> settledCalls = new LinkedHashSet<>();
    private Call call;
    private final LinkedHashSet<String> retiredOwners = new LinkedHashSet<>();

    public void sync(PhoneCallRequest request, long now) {
        if (retiredOwners.contains(request.ownerKey)) return;
        if (!ownerKey.equals(request.ownerKey)) {
            if (!ownerKey.isEmpty()) {
                retiredOwners.add(ownerKey);
                if (retiredOwners.size() > 16) retiredOwners.remove(retiredOwners.iterator().next());
            }
            ownerKey = request.ownerKey; call = null; settledCalls.clear();
        }
        current(now);
        if (!request.state.equals("ringing")) {
            settle(request.callId);
            if (call != null && call.callId.equals(request.callId)) call = null;
            return;
        }
        if (settledCalls.contains(request.callId)) return;
        if (call == null || !call.callId.equals(request.callId)) {
            if (call != null) settle(call.callId);
            call = new Call(ownerKey, request.callId, UUID.randomUUID().toString(), request.label, request.acceptLabel, now + MAX_RING_MS);
        } else {
            // Text updates do not extend a stale ringing notification indefinitely.
            call = new Call(ownerKey, call.callId, call.nonce, request.label, request.acceptLabel, call.expiresAt);
        }
    }
    public Call current(long now) {
        if (call != null && now >= call.expiresAt) { settle(call.callId); call = null; }
        return call;
    }
    public boolean isOwner(String owner) { return !ownerKey.isEmpty() && ownerKey.equals(owner); }
    public void clear(String owner) {
        if (isOwner(owner) && call != null) { settle(call.callId); call = null; }
    }
    public Action respond(String nonce, String action, long now) {
        Call current = current(now);
        if (current == null || !current.nonce.equals(nonce) || !("answer".equals(action) || "reject".equals(action))) return null;
        Action result = new Action(current.ownerKey, current.callId, action);
        settle(current.callId); call = null;
        return result;
    }
    private void settle(String id) {
        settledCalls.add(id);
        if (settledCalls.size() > 64) settledCalls.remove(settledCalls.iterator().next());
    }
    public void reset() { ownerKey = ""; settledCalls.clear(); call = null; retiredOwners.clear(); }
}
