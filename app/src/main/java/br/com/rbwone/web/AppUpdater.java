package br.com.rbwone.web;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks on each foreground opening; installation always goes through Android confirmation. */
public final class AppUpdater {
    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private boolean busy, destroyed, awaitingPermission, skipNextCheck, foreground;
    private AlertDialog dialog;
    private File ready;
    public AppUpdater(Activity activity) { this.activity = activity; }
    private boolean visible() { return foreground && !destroyed && !activity.isFinishing() && !activity.isDestroyed(); }
    private static HttpURLConnection connect(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000); connection.setReadTimeout(15_000); connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
        return connection;
    }
    public void check() {
        foreground = true;
        if (skipNextCheck) { skipNextCheck = false; return; }
        if (destroyed || busy || dialog != null || ready != null) return;
        busy = true;
        worker.execute(() -> {
            UpdateRelease release = null;
            try {
                HttpURLConnection connection = connect(UpdateRelease.MANIFEST + "?t=" + System.currentTimeMillis());
                try {
                    if (connection.getResponseCode() != 200) throw new IOException();
                    try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[4096]; int count;
                        while ((count = input.read(buffer)) != -1) { bytes.write(buffer, 0, count); if (bytes.size() > 16_384) throw new IOException(); }
                        release = new UpdateRelease(new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())));
                    }
                } finally { connection.disconnect(); }
            } catch (Exception ignored) { /* Offline checking never blocks opening the app. */ }
            UpdateRelease found = release;
            activity.runOnUiThread(() -> {
                busy = false;
                if (!visible() || found == null || !found.isNewer(BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)) return;
                dialog = new AlertDialog.Builder(activity).setTitle("Atualização do RBW One")
                        .setMessage("A versão " + found.versionName + " está disponível. Deseja baixar e instalar agora?")
                        .setNegativeButton("Depois", (d, w) -> {}).setPositiveButton("Atualizar agora", (d, w) -> download(found)).create();
                AlertDialog prompt = dialog;
                prompt.setOnDismissListener(d -> { if (dialog == prompt) dialog = null; }); prompt.show();
            });
        });
    }
    private void download(UpdateRelease release) {
        busy = true; cancelled = false;
        dialog = new AlertDialog.Builder(activity).setTitle("Baixando atualização")
                .setMessage("Aguarde enquanto o RBW One baixa e verifica a versão " + release.versionName + ".")
                .setNegativeButton("Cancelar", (d, w) -> cancelled = true).create();
        AlertDialog progress = dialog;
        progress.setOnCancelListener(d -> cancelled = true);
        progress.setOnDismissListener(d -> { if (dialog == progress) dialog = null; }); progress.setCanceledOnTouchOutside(false); progress.show();
        worker.execute(() -> {
            File directory = new File(activity.getCacheDir(), "updates");
            File file = new File(directory, "rbw-" + java.util.UUID.randomUUID() + ".apk"); boolean valid = false;
            try {
                if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) throw new IOException();
                HttpURLConnection connection = connect(release.apkUrl);
                try {
                    if (connection.getResponseCode() != 200) throw new IOException();
                    try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(file)) { release.copyVerified(input, output, () -> cancelled); }
                } finally { connection.disconnect(); }
                validatePackage(file, release); valid = !cancelled;
            } catch (Exception ignored) { /* Do not expose provider internals or credentials. */ }
            if (!valid) file.delete();
            boolean success = valid;
            activity.runOnUiThread(() -> {
                busy = false; progress.dismiss();
                if (destroyed) { file.delete(); return; }
                if (success) { ready = file; if (visible()) install(); }
                else if (!visible()) return;
                else if (!cancelled) new AlertDialog.Builder(activity).setTitle("Atualização não concluída")
                        .setMessage("Não foi possível baixar ou validar o instalador. Confira a conexão e tente novamente.")
                        .setNegativeButton("Depois", (d,w) -> {}).setPositiveButton("Tentar novamente", (d,w) -> download(release)).show();
            });
        });
    }
    @SuppressWarnings("deprecation")
    private void validatePackage(File file, UpdateRelease release) throws Exception {
        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), flags);
        if (archive == null || !activity.getPackageName().equals(archive.packageName) || archive.versionCode != release.versionCode
                || !release.versionName.equals(archive.versionName) || archive.versionCode <= installed.versionCode) throw new IOException();
        Signature[] incoming = Build.VERSION.SDK_INT >= 28 && archive.signingInfo != null ? archive.signingInfo.getApkContentsSigners() : archive.signatures;
        Signature[] current = Build.VERSION.SDK_INT >= 28 && installed.signingInfo != null ? installed.signingInfo.getApkContentsSigners() : installed.signatures;
        if (incoming == null || current == null || incoming.length == 0 || !new HashSet<>(Arrays.asList(incoming)).equals(new HashSet<>(Arrays.asList(current)))) throw new IOException();
    }
    private void install() {
        if (!visible() || ready == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                dialog = new AlertDialog.Builder(activity).setTitle("Permitir atualização")
                        .setMessage("Autorize o RBW One a instalar esta atualização na próxima tela. O Android ainda pedirá sua confirmação.")
                        .setNegativeButton("Depois", (d,w) -> ready = null)
                        .setPositiveButton("Abrir configurações", (d,w) -> {
                            try { awaitingPermission = true; activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))); }
                            catch (Exception ignored) { awaitingPermission = false; ready = null; toast("Não foi possível abrir as configurações de instalação."); }
                        }).setOnCancelListener(d -> ready = null).create();
                AlertDialog permission = dialog;
                permission.setOnDismissListener(d -> { if (dialog == permission) dialog = null; }); permission.show(); return;
            }
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".updates", ready);
            skipNextCheck = true;
            activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            ready = null;
        } catch (Exception ignored) { ready = null; toast("Não foi possível abrir o instalador do Android."); }
    }
    public void resume() {
        if (!awaitingPermission) { if (ready != null && dialog == null && !busy) install(); return; }
        awaitingPermission = false;
        if (Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls()) install();
        else { ready = null; toast("Atualização adiada. A instalação não foi autorizada."); }
    }
    private void toast(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
    public void pause() { foreground = false; }
    public void close() { destroyed = true; cancelled = true; if (dialog != null) dialog.dismiss(); worker.shutdownNow(); }
}
