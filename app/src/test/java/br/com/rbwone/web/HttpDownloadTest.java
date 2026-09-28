package br.com.rbwone.web;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class HttpDownloadTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static class Connection extends HttpURLConnection {
        int status = 200;
        Map<String, String> headers = new HashMap<>();
        InputStream body = new ByteArrayInputStream(new byte[]{0, (byte) 255, 42});
        boolean disconnected;
        Connection(String url) throws Exception { super(new URL(url)); }
        @Override public int getResponseCode() { return status; }
        @Override public String getHeaderField(String key) { return headers.get(key); }
        @Override public String getContentType() { return "application/pdf"; }
        @Override public InputStream getInputStream() { return body; }
        @Override public void connect() { }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
    }
    @Test public void preservesBinaryAndUsesSessionCookieForInitialOrigin() throws Exception {
        Connection connection = new Connection("https://rbwone.com.br/private.pdf");
        connection.headers.put("Content-Length", "3");
        HttpDownload.Result result = HttpDownload.fetch(connection.getURL().toString(), "session=test", "Agent", temp.newFolder(), () -> false, url -> connection);
        assertArrayEquals(new byte[]{0, (byte) 255, 42}, Files.readAllBytes(result.file.toPath()));
        assertEquals("session=test", connection.getRequestProperty("Cookie"));
        assertFalse(connection.getInstanceFollowRedirects()); assertTrue(connection.disconnected);
    }
    @Test public void redirectsNeverLeakCookieToDifferentHost() throws Exception {
        Connection first = new Connection("https://rbwone.com.br/file"); first.status = 302;
        first.headers.put("Location", "https://storage.example/file.pdf?signature=example");
        Connection second = new Connection(first.headers.get("Location"));
        HttpDownload.fetch(first.getURL().toString(), "session=test", "Agent", temp.newFolder(), () -> false,
            url -> url.getHost().equals("rbwone.com.br") ? first : second);
        assertEquals("session=test", first.getRequestProperty("Cookie"));
        assertNull(second.getRequestProperty("Cookie"));
    }
    @Test public void sameOriginRelativeRedirectRetainsCookie() throws Exception {
        Connection first = new Connection("https://rbwone.com.br/file"); first.status = 307;
        first.headers.put("Location", "/private/file.pdf");
        Connection second = new Connection("https://rbwone.com.br/private/file.pdf");
        HttpDownload.fetch(first.getURL().toString(), "session=test", null, temp.newFolder(), () -> false,
            url -> url.getPath().equals("/file") ? first : second);
        assertEquals("session=test", second.getRequestProperty("Cookie"));
    }
    @Test public void rejectsDowngradesCredentialsAndNonHttps() throws Exception {
        for (String value : new String[]{"http://rbwone.com.br/file", "file:///private", "https://user:pass@example.com/file"})
            assertThrows(IOException.class, () -> HttpDownload.secureUri(value));
        Connection first = new Connection("https://rbwone.com.br/file"); first.status = 302;
        first.headers.put("Location", "http://example.com/file");
        assertThrows(IOException.class, () -> HttpDownload.fetch(first.getURL().toString(), "session=test", null, temp.newFolder(), () -> false, url -> first));
    }
    @Test public void rejectsAuthenticationFailureTruncationAndOversizeWithoutLeavingFiles() throws Exception {
        File folder = temp.newFolder();
        Connection connection = new Connection("https://rbwone.com.br/file"); connection.status = 401;
        assertThrows(IOException.class, () -> HttpDownload.fetch(connection.getURL().toString(), null, null, folder, () -> false, url -> connection));
        connection.status = 200; connection.headers.put("Content-Length", "4");
        assertThrows(IOException.class, () -> HttpDownload.fetch(connection.getURL().toString(), null, null, folder, () -> false, url -> connection));
        connection.headers.put("Content-Length", Long.toString(DownloadTransfer.MAX_BYTES + 1));
        assertThrows(IOException.class, () -> HttpDownload.fetch(connection.getURL().toString(), null, null, folder, () -> false, url -> connection));
        assertEquals(0, folder.list().length);
    }
    @Test public void cancellationRemovesPartialDownload() throws Exception {
        File folder = temp.newFolder(); AtomicBoolean cancelled = new AtomicBoolean();
        Connection connection = new Connection("https://rbwone.com.br/file");
        connection.body = new ByteArrayInputStream(new byte[4096]) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                cancelled.set(true); return super.read(bytes, offset, length);
            }
        };
        assertThrows(IOException.class, () -> HttpDownload.fetch(connection.getURL().toString(), null, null, folder, cancelled::get, url -> connection));
        assertEquals(0, folder.list().length);
    }
    @Test public void streamsMoreThan64MiBWithoutKeepingWholeFileInMemory() throws Exception {
        long size = 65L * 1024 * 1024;
        Connection connection = new Connection("https://rbwone.com.br/large");
        connection.headers.put("Content-Length", Long.toString(size));
        connection.body = new InputStream() {
            long remaining = size;
            @Override public int read() { if (remaining == 0) return -1; remaining--; return 42; }
            @Override public int read(byte[] bytes, int offset, int length) {
                if (remaining == 0) return -1;
                int count = (int) Math.min(length, remaining);
                Arrays.fill(bytes, offset, offset + count, (byte) 42); remaining -= count; return count;
            }
        };
        HttpDownload.Result result = HttpDownload.fetch(connection.getURL().toString(), null, null, temp.newFolder(), () -> false, url -> connection);
        assertEquals(size, result.file.length());
        try (RandomAccessFile file = new RandomAccessFile(result.file, "r")) {
            file.seek(size - 1); assertEquals(42, file.read());
        }
    }
    @Test public void stopsRedirectLoops() throws Exception {
        Connection connection = new Connection("https://rbwone.com.br/file"); connection.status = 302;
        connection.headers.put("Location", "/file");
        int[] requests = {0};
        assertThrows(IOException.class, () -> HttpDownload.fetch(connection.getURL().toString(), null, null, temp.newFolder(), () -> false, url -> { requests[0]++; return connection; }));
        assertEquals(6, requests[0]);
    }
}
