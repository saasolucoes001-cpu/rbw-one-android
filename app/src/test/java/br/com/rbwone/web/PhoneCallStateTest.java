package br.com.rbwone.web;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PhoneCallStateTest {
    private static final String A = "owner-session-00000001", B = "owner-session-00000002";
    private PhoneCallRequest request(String owner, String id, String state) throws Exception {
        return PhoneCallRequest.parse("{\"requestId\":\"r\",\"action\":\"sync\",\"payload\":{\"ownerKey\":\""
                + owner + "\",\"callId\":\"" + id + "\",\"state\":\"" + state + "\"}}");
    }
    @Test public void notificationClickCanActOnlyOnceOnCurrentLiveCall() throws Exception {
        PhoneCallState state = new PhoneCallState(); state.sync(request(A, "call-1", "ringing"), 100);
        String nonce = state.current(100).nonce;
        assertNull(state.respond("wrong", "answer", 101));
        PhoneCallState.Action action = state.respond(nonce, "answer", 102);
        assertEquals(A, action.ownerKey); assertEquals("call-1", action.callId); assertEquals("answer", action.action);
        assertNull(state.respond(nonce, "answer", 103));
        state.sync(request(A, "call-1", "ringing"), 104);
        assertNull(state.current(104));
    }
    @Test public void bodyTapDoesNotAnswerOrConsumeTheCall() throws Exception {
        PhoneCallState state = new PhoneCallState(); state.sync(request(A, "call-1", "ringing"), 100);
        String nonce = state.current(100).nonce;
        assertNull(state.respond(nonce, "open", 101));
        assertNotNull(state.current(101));
        assertEquals("reject", state.respond(nonce, "reject", 102).action);
    }
    @Test public void oldOwnerAndOldEndCannotChangeTheNewCall() throws Exception {
        PhoneCallState state = new PhoneCallState(); state.sync(request(A, "old", "ringing"), 0);
        String oldNonce = state.current(0).nonce;
        state.sync(request(B, "new", "ringing"), 1);
        state.clear(A); state.sync(request(A, "other", "ringing"), 2);
        assertEquals("new", state.current(2).callId);
        assertNull(state.respond(oldNonce, "answer", 2));
        state.sync(request(B, "old", "ended"), 3);
        assertEquals("new", state.current(3).callId);
    }
    @Test public void repeatedStatusCannotExtendRingingOrReviveExpiredCall() throws Exception {
        PhoneCallState state = new PhoneCallState(); state.sync(request(A, "call-1", "ringing"), 0);
        String nonce = state.current(0).nonce;
        state.sync(request(A, "call-1", "ringing"), 59_000);
        assertNull(state.current(PhoneCallState.MAX_RING_MS));
        assertNull(state.respond(nonce, "answer", PhoneCallState.MAX_RING_MS));
        state.sync(request(A, "call-1", "ringing"), 61_000);
        assertNull(state.current(61_000));
        state.sync(request(A, "call-2", "ringing"), 62_000);
        assertNotNull(state.current(62_000));
    }
    @Test public void foregroundClearSuppressesOldCallButAllowsFutureCall() throws Exception {
        PhoneCallState state = new PhoneCallState(); state.sync(request(A, "call-1", "ringing"), 0);
        state.clear(A); state.sync(request(A, "call-1", "ringing"), 1);
        assertNull(state.current(1));
        state.sync(request(A, "call-2", "ringing"), 2);
        assertNotNull(state.current(2));
    }
    @Test public void connectedOrEndedStopsTheCorrespondingAlert() throws Exception {
        for (String terminal : new String[]{"talking", "ended"}) {
            PhoneCallState state = new PhoneCallState(); state.sync(request(A, "call-1", "ringing"), 0);
            state.sync(request(A, "call-1", terminal), 1); assertNull(state.current(1));
        }
    }
    @Test public void settledEarlierCallCannotReturnAfterAnotherCall() throws Exception {
        PhoneCallState state = new PhoneCallState(); state.sync(request(A, "call-1", "ringing"), 0);
        String first = state.current(0).nonce; state.respond(first, "answer", 1);
        state.sync(request(A, "call-2", "ringing"), 2);
        String second = state.current(2).nonce; state.respond(second, "reject", 3);
        state.sync(request(A, "call-1", "ringing"), 4);
        assertNull(state.current(4));
    }
    @Test public void processRecreationCannotAnswerAnOldPendingIntent() throws Exception {
        PhoneCallState before = new PhoneCallState(); before.sync(request(A, "call-1", "ringing"), 0);
        String nonce = before.current(0).nonce;
        assertNull(new PhoneCallState().respond(nonce, "answer", 1));
        before.reset(); assertNull(before.respond(nonce, "reject", 1));
    }
}
