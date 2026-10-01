package br.com.rbwone.web;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationClickResultTest {
    @Test public void opensOnlyAfterConfirmedServerCleanup() throws Exception {
        assertEquals("/fechadura", NotificationClickResult.route(new JSONObject().put("success", true).put("route", "/fechadura")));
        assertEquals("/administrativo/ti/chamados/iniciar?id=abc", NotificationClickResult.route(new JSONObject().put("success", true).put("route", "/ti/chamados/iniciar?id=abc")));
        assertNull(NotificationClickResult.route(null));
        assertNull(NotificationClickResult.route(new JSONObject().put("route", "/fechadura")));
        assertNull(NotificationClickResult.route(new JSONObject().put("success", false).put("route", "/fechadura")));
        assertNull(NotificationClickResult.route(new JSONObject().put("success", "true").put("route", "/fechadura")));
    }
    @Test public void rejectsMissingMalformedAndUnsafeServerRoutes() throws Exception {
        for (Object route : new Object[]{JSONObject.NULL, 123, "https://evil.test", "//evil.test", "/../secret", "/%2e%2e/secret", "/a%00b"}) {
            assertNull(NotificationClickResult.route(new JSONObject().put("success", true).put("route", route)));
        }
    }
}
