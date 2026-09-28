package br.com.rbwone.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.json.JSONObject;

/** Bounded, sequential transfer into private cache; never accepts a filesystem path. */
final class DownloadTransfer implements AutoCloseable {
    static final long MAX_BYTES = 512L * 1024 * 1024;
    static final int CHUNK_BYTES = 48 * 1024;
    private final File directory;
    private File file;
    private FileOutputStream output;
    private String id;
    private long expected, received;
    private int sequence;
    String name, mime;

    DownloadTransfer(File directory) { this.directory = directory; }

    synchronized boolean accept(JSONObject message) throws Exception {
        String action = message.getString("action");
        String incoming = message.getString("id");
        if (!incoming.matches("[a-zA-Z0-9-]{1,80}")) throw new IOException("Invalid transfer");
        if (action.equals("begin")) {
            if (file != null) throw new IOException("Transfer already active");
            long size = message.getLong("size");
            if (size < -1 || size > MAX_BYTES) throw new IOException("File exceeds limit");
            name = safeName(message.optString("name"));
            String type = message.optString("mime").split(";", 2)[0].trim();
            mime = type.matches("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+") ? type : "application/octet-stream";
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cache unavailable");
            file = File.createTempFile("download-", ".tmp", directory);
            output = new FileOutputStream(file);
            id = incoming; expected = size; received = 0; sequence = 0;
            return false;
        }
        if (file == null || !incoming.equals(id)) throw new IOException("Unknown transfer");
        if (action.equals("chunk")) {
            if (message.getInt("sequence") != sequence) throw new IOException("Invalid sequence");
            String encoded = message.getString("data");
            if (encoded.length() > CHUNK_BYTES * 4 / 3) throw new IOException("Chunk exceeds limit");
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0 || received + bytes.length > MAX_BYTES || (expected >= 0 && received + bytes.length > expected)) throw new IOException("Invalid size");
            output.write(bytes); received += bytes.length; sequence++;
            return false;
        }
        if (action.equals("finish")) {
            if (expected >= 0 && received != expected) throw new IOException("Incomplete transfer");
            output.close(); output = null;
            return true;
        }
        throw new IOException("Invalid action");
    }

    synchronized File detach() { File result = file; file = null; id = null; return result; }

    static String safeName(String input) {
        String value = input.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
        if (value.isEmpty() || value.equals(".") || value.equals("..")) return "download";
        if (value.length() > 180) {
            int dot = value.lastIndexOf('.');
            String extension = dot >= 0 && value.length() - dot <= 16 ? value.substring(dot) : "";
            value = value.substring(0, 180 - extension.length()) + extension;
        }
        return value;
    }

    @Override public synchronized void close() {
        if (output != null) try { output.close(); } catch (IOException ignored) { }
        output = null;
        if (file != null) file.delete();
        file = null; id = null;
    }
}
