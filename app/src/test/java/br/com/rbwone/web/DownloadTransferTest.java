package br.com.rbwone.web;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DownloadTransferTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private JSONObject message(String action) throws Exception { return new JSONObject().put("action", action).put("id", "test-1"); }
    private JSONObject begin(long size) throws Exception {
        return message("begin").put("size", size).put("name", "relatório.pdf").put("mime", "application/pdf");
    }
    private JSONObject chunk(int sequence, byte[] bytes) throws Exception {
        return message("chunk").put("sequence", sequence).put("data", Base64.getEncoder().encodeToString(bytes));
    }
    @Test public void preservesBinaryAcrossChunksAndDetachesForSave() throws Exception {
        byte[] first = new byte[DownloadTransfer.CHUNK_BYTES];
        for (int i = 0; i < first.length; i++) first[i] = (byte) i;
        byte[] last = {(byte) 255, 0, 42};
        File directory = temp.newFolder();
        try (DownloadTransfer transfer = new DownloadTransfer(directory)) {
            assertFalse(transfer.accept(begin(first.length + last.length)));
            transfer.accept(chunk(0, first)); transfer.accept(chunk(1, last));
            assertTrue(transfer.accept(message("finish")));
            assertEquals("relatório.pdf", transfer.name);
            assertEquals("application/pdf", transfer.mime);
            File file = transfer.detach(); transfer.close();
            byte[] actual = Files.readAllBytes(file.toPath());
            assertArrayEquals(first, java.util.Arrays.copyOf(actual, first.length));
            assertArrayEquals(last, java.util.Arrays.copyOfRange(actual, first.length, actual.length));
        }
    }
    @Test public void rejectsOversizedFileBeforeCreatingIt() throws Exception {
        File directory = temp.newFolder();
        try (DownloadTransfer transfer = new DownloadTransfer(directory)) {
            assertThrows(Exception.class, () -> transfer.accept(begin(DownloadTransfer.MAX_BYTES + 1)));
            assertEquals(0, directory.list().length);
        }
    }
    @Test public void rejectsMissingRepeatedAndWrongTransferChunks() throws Exception {
        try (DownloadTransfer transfer = new DownloadTransfer(temp.newFolder())) {
            transfer.accept(begin(3));
            assertThrows(Exception.class, () -> transfer.accept(chunk(1, new byte[]{1})));
            assertThrows(Exception.class, () -> transfer.accept(chunk(0, new byte[]{1}).put("id", "other")));
            transfer.accept(chunk(0, new byte[]{1}));
            assertThrows(Exception.class, () -> transfer.accept(chunk(0, new byte[]{1})));
            assertThrows(Exception.class, () -> transfer.accept(message("finish")));
            assertThrows(Exception.class, () -> transfer.accept(chunk(1, new byte[]{1, 2, 3})));
        }
    }
    @Test public void rejectsOversizedAndMalformedBase64Chunks() throws Exception {
        try (DownloadTransfer transfer = new DownloadTransfer(temp.newFolder())) {
            transfer.accept(begin(DownloadTransfer.MAX_BYTES));
            assertThrows(Exception.class, () -> transfer.accept(chunk(0, new byte[DownloadTransfer.CHUNK_BYTES + 1])));
            assertThrows(Exception.class, () -> transfer.accept(message("chunk").put("sequence", 0).put("data", "!invalid!")));
        }
    }
    @Test public void cancelRemovesPartialFileAndAllowsAnotherTransfer() throws Exception {
        File directory = temp.newFolder();
        try (DownloadTransfer transfer = new DownloadTransfer(directory)) {
            transfer.accept(begin(10)); transfer.accept(chunk(0, new byte[]{1}));
            transfer.close(); assertEquals(0, directory.list().length);
            transfer.accept(begin(0)); assertTrue(transfer.accept(message("finish")));
        }
        assertEquals(0, directory.list().length);
    }
    @Test public void sanitizesSuggestedNamesAndMimeWithoutAcceptingPaths() throws Exception {
        assertEquals(".._secret_file.pdf", DownloadTransfer.safeName("../secret\\file.pdf"));
        assertEquals("download", DownloadTransfer.safeName(".."));
        assertEquals("a_b.pdf", DownloadTransfer.safeName("a\nb.pdf"));
        String longName = "a".repeat(200) + ".xlsx";
        assertTrue(DownloadTransfer.safeName(longName).endsWith(".xlsx"));
        assertEquals(180, DownloadTransfer.safeName(longName).length());
        try (DownloadTransfer transfer = new DownloadTransfer(temp.newFolder())) {
            transfer.accept(begin(0).put("mime", "text/plain\r\nx-invalid"));
            assertEquals("application/octet-stream", transfer.mime);
        }
    }
    @Test public void supportsUnknownLengthAndFilesLargerThanPreviousLimit() throws Exception {
        File directory = temp.newFolder();
        try (DownloadTransfer transfer = new DownloadTransfer(directory)) {
            transfer.accept(begin(65L * 1024 * 1024));
            transfer.close();
            transfer.accept(begin(-1));
            transfer.accept(chunk(0, new byte[]{1, 2, 3}));
            assertTrue(transfer.accept(message("finish")));
            File file = transfer.detach();
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file.toPath()));
        }
    }
}
