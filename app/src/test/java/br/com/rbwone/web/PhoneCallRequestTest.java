package br.com.rbwone.web;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PhoneCallRequestTest {
    private static final String OWNER = "owner-session-00000001";
    private String sync(String extra) {
        return "{\"requestId\":\"r-1\",\"action\":\"sync\",\"payload\":{\"ownerKey\":\"" + OWNER
                + "\",\"callId\":\"call-1\",\"state\":\"ringing\"" + extra + "}}";
    }
    @Test public void defaultRecipientActionIsAnswer() throws Exception {
        PhoneCallRequest request = PhoneCallRequest.parse(sync(""));
        assertEquals("Atender", request.acceptLabel);
        assertEquals("Chamada no RBW One", request.label);
    }
    @Test public void callbackOriginLabelIsExplicitlyLimited() throws Exception {
        assertEquals("Ligar", PhoneCallRequest.parse(sync(",\"acceptLabel\":\"Ligar\"")).acceptLabel);
        assertThrows(IllegalArgumentException.class, () -> PhoneCallRequest.parse(sync(",\"acceptLabel\":\"Discar destino\"")));
    }
    @Test public void labelsCannotCarryControlsOrUnboundedText() throws Exception {
        assertEquals("Lucas 5100", PhoneCallRequest.parse(sync(",\"label\":\"Lucas\\n5100\"")).label);
        assertThrows(IllegalArgumentException.class, () -> PhoneCallRequest.parse(sync(",\"label\":\"" + "x".repeat(161) + "\"")));
    }
    @Test public void syncRequiresOwnerAndCallAndKnownState() {
        assertThrows(IllegalArgumentException.class, () -> PhoneCallRequest.parse(sync("").replace(OWNER, "short")));
        assertThrows(IllegalArgumentException.class, () -> PhoneCallRequest.parse(sync("").replace("call-1", "https://example.org")));
        assertThrows(IllegalArgumentException.class, () -> PhoneCallRequest.parse(sync("").replace("ringing", "dial")));
    }
    @Test public void onlyUIActionsAreAccepted() {
        for (String action : new String[]{"dial", "login", "setSession", "evaluateJavaScript", "answer"}) {
            assertThrows(IllegalArgumentException.class, () -> PhoneCallRequest.parse("{\"requestId\":\"r\",\"action\":\"" + action + "\"}"));
        }
    }
    @Test public void requestEnvelopeIsBoundedAndStatusDoesNotNeedIdentity() throws Exception {
        assertEquals("getStatus", PhoneCallRequest.parse("{\"requestId\":\"r\",\"action\":\"getStatus\"}").action);
        assertThrows(IllegalArgumentException.class, () -> PhoneCallRequest.parse("x".repeat(4097)));
        assertThrows(IllegalArgumentException.class, () -> PhoneCallRequest.parse("{\"requestId\":\"bad id\",\"action\":\"getStatus\"}"));
    }
}
