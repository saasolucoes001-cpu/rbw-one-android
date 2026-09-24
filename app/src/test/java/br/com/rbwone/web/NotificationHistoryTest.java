package br.com.rbwone.web;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationHistoryTest {
    @Test public void previouslyDisplayedIdSurvivesPersistenceAndBlocksRetry() throws Exception {
        String persisted = NotificationHistory.add("{}", "notification-one", 1_000_000);
        assertTrue(NotificationHistory.contains(persisted, "notification-one"));
        assertFalse(NotificationHistory.contains(persisted, "notification-two"));
    }
    @Test public void receiptOfAnotherNotificationKeepsRecentIds() throws Exception {
        String persisted = NotificationHistory.add(NotificationHistory.add("{}", "one", 1_000_000), "two", 1_001_000);
        assertTrue(NotificationHistory.contains(persisted, "one")); assertTrue(NotificationHistory.contains(persisted, "two"));
    }
    @Test public void storageRemainsBoundedAndDropsExpiredHistory() throws Exception {
        JSONObject previous = new JSONObject();
        for (int i = 0; i < 400; i++) previous.put("id-" + i, 100_000_000L);
        previous.put("expired", 1L);
        JSONObject next = new JSONObject(NotificationHistory.add(previous.toString(), "newest", 100_000_100L));
        assertEquals(256, next.length()); assertTrue(next.has("newest")); assertFalse(next.has("expired"));
    }
    @Test public void corruptPersistenceFailsClosedInsteadOfSilentlyReplaying() { assertThrows(Exception.class, () -> NotificationHistory.contains("broken", "id")); }
}
