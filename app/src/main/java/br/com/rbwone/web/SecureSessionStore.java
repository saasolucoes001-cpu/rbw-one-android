package br.com.rbwone.web;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** No session bearer is stored in plaintext or included in Android backup. */
public final class SecureSessionStore {
    private static final String ALIAS = "rbw.notifications.session.v1";
    private final SharedPreferences preferences;
    public SecureSessionStore(Context context) { preferences = context.getSharedPreferences("rbw_notifications", Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    public synchronized String installationId() {
        String id = preferences.getString("installation", "");
        if (!RoutePolicy.uuid(id)) { id = UUID.randomUUID().toString(); preferences.edit().putString("installation", id).commit(); }
        return id;
    }
    public synchronized SessionRecord read() {
        String raw = preferences.getString("encrypted_session", null);
        if (raw == null) return null;
        try {
            String[] parts = raw.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            byte[] plaintext = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP));
            return SessionRecord.parse(new JSONObject(new String(plaintext, StandardCharsets.UTF_8)));
        } catch (Exception ignored) { clear(); return null; }
    }
    public synchronized void write(SessionRecord record) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(record.json().toString().getBytes(StandardCharsets.UTF_8));
        String payload = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        if (!preferences.edit().putString("encrypted_session", payload).commit()) throw new IllegalStateException("Não foi possível guardar a sessão com segurança.");
    }
    public synchronized void clear() { preferences.edit().remove("encrypted_session").remove("seen_notifications").commit(); }
    public synchronized boolean hasSeen(String id) {
        try { return NotificationHistory.contains(preferences.getString("seen_notifications", "{}"), id); }
        catch (Exception ignored) { return true; }
    }
    public synchronized boolean remember(String id, long now) {
        try {
            String seen = preferences.getString("seen_notifications", "{}");
            if (NotificationHistory.contains(seen, id)) return false;
            return preferences.edit().putString("seen_notifications", NotificationHistory.add(seen, id, now)).commit();
        } catch (Exception ignored) { return false; }
    }
}
