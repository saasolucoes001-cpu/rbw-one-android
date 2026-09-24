package br.com.rbwone.web;

import org.json.JSONObject;
import java.net.URI;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.function.BooleanSupplier;

/** The public channel is untrusted input, never a source of credentials or arbitrary URLs. */
public final class UpdateRelease {
    public static final String MANIFEST = "https://saasolucoes001-cpu.github.io/rbw-one-android/web-latest.json";
    public final int versionCode, minSdk;
    public final String versionName, apkUrl, sha256;
    public final long size;
    public UpdateRelease(JSONObject json) throws Exception {
        versionCode = integer(json, "versionCode"); minSdk = integer(json, "minSdkVersion");
        versionName = json.getString("versionName"); apkUrl = json.getString("apkUrl"); sha256 = json.getString("sha256");
        size = integer(json, "sizeBytes");
        URI uri = new URI(apkUrl);
        if (integer(json, "schemaVersion") != 1 || !"br.com.rbwone.web".equals(json.getString("packageName"))
                || !versionName.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}") || versionCode < 1 || minSdk < 23
                || size < 1_000_000 || size > 50 * 1024 * 1024 || !sha256.matches("[a-f0-9]{64}")
                || !"https".equals(uri.getScheme()) || !"saasolucoes001-cpu.github.io".equals(uri.getHost())
                || uri.getPort() != -1 || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("/rbw-one-android/rbw-one-web-" + versionName + ".apk").equals(uri.getRawPath())) {
            throw new IOException("Canal de atualização inválido.");
        }
    }
    private static int integer(JSONObject json, String key) throws Exception {
        Object value = json.get(key);
        if (!(value instanceof Number) || ((Number)value).doubleValue() != ((Number)value).intValue()) throw new IOException("Versão inválida.");
        return ((Number)value).intValue();
    }
    public boolean isNewer(int installed, int sdk) { return versionCode > installed && minSdk <= sdk; }
    public void copyVerified(InputStream input, OutputStream output, BooleanSupplier cancelled) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[16_384]; long total = 0; int count;
        while ((count = input.read(buffer)) != -1) {
            if (cancelled.getAsBoolean()) throw new IOException("Download cancelado.");
            total += count; if (total > size) throw new IOException("Tamanho inválido.");
            digest.update(buffer, 0, count); output.write(buffer, 0, count);
        }
        StringBuilder actual = new StringBuilder(); for (byte b : digest.digest()) actual.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        if (total != size || !sha256.contentEquals(actual)) throw new IOException("Arquivo de atualização incompleto ou inválido.");
    }
}
