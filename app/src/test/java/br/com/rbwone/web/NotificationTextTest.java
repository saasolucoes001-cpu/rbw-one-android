package br.com.rbwone.web;
import org.junit.Test;
import static org.junit.Assert.*;
public class NotificationTextTest {
    @Test public void showsAuthorizedSubjectAndSummary() {
        NotificationText text = NotificationText.from("Nova mensagem no chamado", "Chamado #42. Há uma nova mensagem para consultar.");
        assertEquals("Nova mensagem no chamado", text.title);
        assertTrue(text.body.startsWith("Chamado #42."));
    }
    @Test public void supportsOlderBackendAndCleansWhitespace() {
        assertEquals("RBW One", NotificationText.from(null, "").title);
        assertEquals("Há uma nova notificação para você.", NotificationText.from(null, "").body);
        assertEquals("Olá mundo", NotificationText.from("  Olá\n\t mundo  ", "x").title);
    }
    @Test public void boundsLongPreviewsWithoutSplittingEmoji() {
        NotificationText text = NotificationText.from("😀".repeat(200), "x".repeat(1100));
        assertEquals(160, text.title.codePointCount(0, text.title.length()));
        assertEquals(1000, text.body.length());
        assertTrue(text.body.endsWith("…"));
    }
}
