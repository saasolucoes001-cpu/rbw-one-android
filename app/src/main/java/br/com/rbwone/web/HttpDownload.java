package br.com.rbwone.web;

import java.io.*;
import java.net.*;
import java.util.function.BooleanSupplier;

/** HTTPS download with bounded streaming and cookies restricted to their initial origin. */
final class HttpDownload {
    interface Connections { HttpURLConnection open(URL url) throws IOException; }
    static final class Result {
        final File file;
        final String mime, disposition, url;
        Result(File file, String mime, String disposition, String url) {
            this.file = file; this.mime = mime; this.disposition = disposition; this.url = url;
        }
    }
    static URI secureUri(String url) throws IOException {
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IOException("Only HTTPS downloads are supported");
            return uri;
        } catch (URISyntaxException error) { throw new IOException("Invalid download URL", error); }
    }
    static boolean sameOrigin(URI a, URI b) {
        return a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost().equalsIgnoreCase(b.getHost()) &&
            (a.getPort() == -1 ? 443 : a.getPort()) == (b.getPort() == -1 ? 443 : b.getPort());
    }
    static Result fetch(String url, String cookies, String agent, File directory, BooleanSupplier cancelled,
                        Connections connections) throws IOException {
        URI initial = secureUri(url), current = initial;
        for (int hop = 0; hop <= 5; hop++) {
            if (cancelled.getAsBoolean()) throw new IOException("Cancelled");
            HttpURLConnection connection = connections.open(current.toURL());
            File file = null;
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (agent != null) connection.setRequestProperty("User-Agent", agent);
                if (cookies != null && sameOrigin(initial, current)) connection.setRequestProperty("Cookie", cookies);
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || hop == 5) throw new IOException("Invalid redirect");
                    current = secureUri(current.resolve(location).toString()); continue;
                }
                if (status != 200) throw new IOException("Download unavailable");
                long expected = -1;
                String length = connection.getHeaderField("Content-Length");
                if (length != null) {
                    try { expected = Long.parseLong(length); }
                    catch (NumberFormatException error) { throw new IOException("Invalid content length", error); }
                    if (expected < 0) throw new IOException("Invalid content length");
                }
                if (expected > DownloadTransfer.MAX_BYTES) throw new IOException("File exceeds 512 MB");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cache unavailable");
                file = File.createTempFile("download-", ".tmp", directory);
                long total = 0;
                try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[64 * 1024]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (cancelled.getAsBoolean()) throw new IOException("Cancelled");
                        total += count;
                        if (total > DownloadTransfer.MAX_BYTES) throw new IOException("File exceeds 512 MB");
                        output.write(buffer, 0, count);
                    }
                }
                if (cancelled.getAsBoolean() || (expected >= 0 && total != expected)) throw new IOException("Incomplete download");
                return new Result(file, connection.getContentType(), connection.getHeaderField("Content-Disposition"), current.toString());
            } catch (IOException | RuntimeException error) {
                if (file != null) file.delete();
                throw error;
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many redirects");
    }
}
