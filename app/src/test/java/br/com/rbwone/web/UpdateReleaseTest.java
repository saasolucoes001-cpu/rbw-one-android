package br.com.rbwone.web;

import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.security.MessageDigest;
import static org.junit.Assert.*;

public class UpdateReleaseTest {
    private final byte[] apk = new byte[1_000_000];
    private JSONObject manifest() throws Exception {
        StringBuilder hash = new StringBuilder(); for(byte b : MessageDigest.getInstance("SHA-256").digest(apk)) hash.append(String.format("%02x", b & 255));
        return new JSONObject().put("schemaVersion",1).put("packageName","br.com.rbwone.web").put("versionCode",4).put("versionName","1.0.3")
                .put("minSdkVersion",23).put("sizeBytes",apk.length).put("sha256",hash.toString())
                .put("apkUrl","https://saasolucoes001-cpu.github.io/rbw-one-android/rbw-one-web-1.0.3.apk");
    }
    @Test public void acceptsOnlyNewCompatibleVersion() throws Exception {
        UpdateRelease release = new UpdateRelease(manifest());
        assertTrue(release.isNewer(3,23)); assertFalse(release.isNewer(4,35)); assertFalse(release.isNewer(5,35)); assertFalse(release.isNewer(3,22));
    }
    @Test public void rejectsExternalCredentialsRedirectLikeAndAmbiguousUrls() throws Exception {
        for (String url : new String[]{"http://saasolucoes001-cpu.github.io/rbw-one-android/rbw-one-web-1.0.3.apk", "https://evil.test/app.apk",
                "https://name:secret@saasolucoes001-cpu.github.io/rbw-one-android/rbw-one-web-1.0.3.apk", "https://saasolucoes001-cpu.github.io:443/rbw-one-android/rbw-one-web-1.0.3.apk",
                "https://saasolucoes001-cpu.github.io/rbw-one-android/rbw-one-web-1.0.3.apk?q=x", "https://saasolucoes001-cpu.github.io/rbw-one-android/%72bw-one-web-1.0.3.apk",
                "https://saasolucoes001-cpu.github.io/rbw-one-android/rbw-one-web-1.0.2.apk"}) assertThrows(url, Exception.class, () -> new UpdateRelease(manifest().put("apkUrl",url)));
    }
    @Test public void rejectsMalformedOrWrongPackageMetadata() throws Exception {
        for (Object[] entry : new Object[][]{{"packageName","br.com.rbwone.app"},{"schemaVersion",2},{"versionCode",4.2},{"versionCode","4"},{"versionCode",-1},{"sizeBytes",100},{"sizeBytes",100_000_000},{"sha256","bad"}}) {
            assertThrows(Exception.class, () -> new UpdateRelease(manifest().put((String)entry[0],entry[1])));
        }
    }
    @Test public void verifiesFullPayloadBeforeInstallation() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new UpdateRelease(manifest()).copyVerified(new ByteArrayInputStream(apk),out,()->false); assertArrayEquals(apk,out.toByteArray());
    }
    @Test public void rejectsTruncatedOversizedCorruptedAndCancelledDownloads() throws Exception {
        UpdateRelease release = new UpdateRelease(manifest());
        for (byte[] invalid : new byte[][]{new byte[999_999],new byte[1_000_001]}) assertThrows(Exception.class, () -> release.copyVerified(new ByteArrayInputStream(invalid),new ByteArrayOutputStream(),()->false));
        byte[] corrupt = apk.clone(); corrupt[500] = 1;
        assertThrows(Exception.class, () -> release.copyVerified(new ByteArrayInputStream(corrupt),new ByteArrayOutputStream(),()->false));
        assertThrows(Exception.class, () -> release.copyVerified(new ByteArrayInputStream(apk),new ByteArrayOutputStream(),()->true));
    }
}
