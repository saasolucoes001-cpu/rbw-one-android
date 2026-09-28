package br.com.rbwone.web;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.Toast;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** First-party main-frame downloads. The system picker grants access to one destination only. */
final class WebDownloads {
    static final int SAVE_FILE = 105;
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final File directory;
    private DownloadTransfer transfer;
    private File pendingFile;
    private String activeId, finishRequest;
    private JavaScriptReplyProxy finishProxy;
    private volatile int generation;
    private boolean processing, closed, saving, pickerOutstanding;
    private boolean preview;
    private final java.util.List<Uri> previewUris = new java.util.ArrayList<>();
    private final java.util.List<File> previewFiles = new java.util.ArrayList<>();
    private final Runnable timeout = this::reset;

    WebDownloads(Activity activity) {
        this.activity = activity;
        directory = new File(activity.getCacheDir(), "web-downloads");
        transfer = new DownloadTransfer(directory);
        // Only remove abandoned files older than a day, never another Activity's active transfer.
        io.execute(() -> {
            File[] files = directory.listFiles();
            if (files != null) for (File file : files) {
                if (file.isFile() && file.getName().startsWith("download-") &&
                    System.currentTimeMillis() - file.lastModified() > 86400000L) file.delete();
                if (file.isDirectory() && file.getName().matches("preview-[a-f0-9-]{36}") &&
                    System.currentTimeMillis() - file.lastModified() > 86400000L) {
                    File[] documents = file.listFiles();
                    if (documents != null) for (File document : documents) if (document.isFile()) document.delete();
                    file.delete();
                }
            }
        });
    }

    boolean install(WebView web) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false;
        try {
            String script;
            try (java.io.InputStream input = activity.getAssets().open("downloads.js")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                script = bytes.toString(StandardCharsets.UTF_8.name());
            }
            WebViewCompat.addWebMessageListener(web, "RBWDownloads", Collections.singleton(RoutePolicy.ORIGIN),
                (view, message, origin, mainFrame, proxy) -> {
                    if (!closed && mainFrame && RoutePolicy.officialOrigin(origin.toString()) && RoutePolicy.officialOrigin(web.getUrl())) {
                        receive(message.getData(), proxy);
                    }
                });
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(web, script, Collections.singleton(RoutePolicy.ORIGIN));
            }
            return true;
        } catch (Exception ignored) { return false; }
    }

    private void receive(String text, JavaScriptReplyProxy proxy) {
        if (text == null || text.length() > 68000) return;
        try {
            JSONObject message = new JSONObject(text);
            String id = message.getString("id");
            if ("cancel".equals(message.optString("action"))) {
                if (id.equals(activeId)) reset();
                return;
            }
            String request = message.getString("requestId");
            if (!request.matches("[a-zA-Z0-9-]{1,80}")) return;
            if (processing || pendingFile != null || saving || pickerOutstanding) {
                reply(proxy, request, false, "Conclua o download atual antes de iniciar outro."); return;
            }
            if (activeId != null && !activeId.equals(id)) {
                reply(proxy, request, false, "Já existe um download em andamento."); return;
            }
            activeId = id;
            if ("begin".equals(message.optString("action"))) preview = "preview".equals(message.optString("mode"));
            processing = true;
            int captured = generation;
            DownloadTransfer current = transfer;
            handler.removeCallbacks(timeout);
            handler.postDelayed(timeout, 60000);
            io.execute(() -> {
                boolean finished;
                try { finished = current.accept(message); }
                catch (Exception error) {
                    current.close();
                    handler.post(() -> {
                        if (closed || captured != generation) return;
                        reset(); reply(proxy, request, false, "Não foi possível receber o arquivo. Tente novamente.");
                    });
                    return;
                }
                handler.post(() -> {
                    if (closed || captured != generation) return;
                    processing = false;
                    if (!finished) { reply(proxy, request, true, null); return; }
                    pendingFile = current.detach(); finishProxy = proxy; finishRequest = request;
                    handler.removeCallbacks(timeout); handler.postDelayed(timeout, 10 * 60 * 1000);
                    present(current.name, current.mime);
                });
            });
        } catch (Exception ignored) { /* Invalid messages cannot allocate files. */ }
    }

    void downloadHttp(String url, String userAgent, String disposition, String mime) {
        if (closed) return;
        if (processing || pendingFile != null || saving || pickerOutstanding || activeId != null) {
            Toast.makeText(activity, "Conclua o download atual antes de iniciar outro.", Toast.LENGTH_LONG).show(); return;
        }
        try { HttpDownload.secureUri(url); }
        catch (Exception error) { Toast.makeText(activity, "O download precisa usar uma conexão HTTPS.", Toast.LENGTH_LONG).show(); return; }
        // Only the official site's cookies are copied; never share them with external redirects.
        String cookie = RoutePolicy.officialOrigin(url) ? CookieManager.getInstance().getCookie(url) : null;
        int captured = generation; processing = true; preview = false;
        Toast.makeText(activity, "Preparando arquivo…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                HttpDownload.Result result = HttpDownload.fetch(url, cookie, userAgent, directory,
                    () -> captured != generation, value -> (java.net.HttpURLConnection) value.openConnection());
                handler.post(() -> {
                    if (closed || captured != generation) { ioDelete(result.file); return; }
                    processing = false; pendingFile = result.file;
                    String type = result.mime == null ? mime : result.mime;
                    if (type == null) type = "application/octet-stream";
                    type = type.split(";", 2)[0].trim();
                    String name = DownloadTransfer.safeName(URLUtil.guessFileName(result.url,
                        result.disposition == null ? disposition : result.disposition, type));
                    present(name, type);
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (closed || captured != generation) return;
                    cancelTransfer();
                    Toast.makeText(activity, "Não foi possível baixar o arquivo. Verifique a conexão, o acesso e o limite de 512 MB.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void present(String name, String mime) {
        if (preview) {
            try {
                File folder = new File(directory, "preview-" + java.util.UUID.randomUUID());
                if (!folder.mkdirs()) throw new java.io.IOException("Cache unavailable");
                File target = new File(folder, name);
                if (!pendingFile.renameTo(target)) { folder.delete(); throw new java.io.IOException("Move failed"); }
                pendingFile = target;
                Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".documents", target);
                Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(android.content.ClipData.newRawUri(name, uri));
                activity.startActivity(intent);
                previewUris.add(uri); previewFiles.add(target); pendingFile = null;
                complete(true, null); return;
            } catch (android.content.ActivityNotFoundException error) {
                Toast.makeText(activity, "Escolha onde salvar para abrir o documento depois.", Toast.LENGTH_LONG).show();
                // Fall back to the save picker if no viewer is installed.
            } catch (Exception error) { complete(false, "Não foi possível abrir o documento."); return; }
        }
        handler.removeCallbacks(timeout); handler.postDelayed(timeout, 10 * 60 * 1000);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mime).putExtra(Intent.EXTRA_TITLE, name);
        try { pickerOutstanding = true; activity.startActivityForResult(intent, SAVE_FILE); }
        catch (Exception error) { pickerOutstanding = false; complete(false, "Nenhum aplicativo disponível para salvar arquivos."); }
    }

    private void ioDelete(File file) { if (file != null) file.delete(); }

    void onActivityResult(int result, Intent data) {
        pickerOutstanding = false;
        if (closed || pendingFile == null) return;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) {
            complete(true, null); return; // Cancellation is not a download failure.
        }
        Uri destination = data.getData();
        if (!"content".equals(destination.getScheme())) { complete(false, "Destino inválido."); return; }
        File source = pendingFile; pendingFile = null; saving = true;
        handler.removeCallbacks(timeout);
        int captured = generation;
        io.execute(() -> {
            boolean success = false;
            try (FileInputStream input = new FileInputStream(source);
                 OutputStream output = activity.getContentResolver().openOutputStream(destination, "wt")) {
                if (output == null) throw new java.io.IOException("Destination unavailable");
                byte[] buffer = new byte[64 * 1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (captured != generation) throw new java.io.IOException("Cancelled");
                    output.write(buffer, 0, count);
                }
                if (captured != generation) throw new java.io.IOException("Cancelled");
                output.flush(); success = true;
            } catch (Exception ignored) {
                // ACTION_CREATE_DOCUMENT creates a new destination; remove a partial document on failure.
                try { android.provider.DocumentsContract.deleteDocument(activity.getContentResolver(), destination); }
                catch (Exception unavailable) { /* Some providers do not support deletion. */ }
            }
            finally { source.delete(); }
            boolean saved = success;
            handler.post(() -> {
                if (closed || captured != generation) return;
                if (saved) Toast.makeText(activity, "Arquivo salvo.", Toast.LENGTH_SHORT).show();
                complete(saved, saved ? null : "Não foi possível salvar o arquivo no destino escolhido.");
            });
        });
    }

    private void complete(boolean success, String error) {
        if (finishProxy != null) reply(finishProxy, finishRequest, success, error);
        else if (!success && error != null) Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
        cancelTransfer();
    }
    private void reply(JavaScriptReplyProxy proxy, String request, boolean success, String error) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return;
        try {
            JSONObject response = new JSONObject().put("requestId", request).put("ok", success);
            if (error != null) response.put("error", error);
            proxy.postMessage(response.toString());
        } catch (Exception ignored) { }
    }
    void reset() {
        for (Uri uri : previewUris) activity.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        previewUris.clear();
        for (File file : previewFiles) { file.delete(); file.getParentFile().delete(); }
        previewFiles.clear();
        cancelTransfer();
    }
    private void cancelTransfer() {
        generation++; processing = false; saving = false; activeId = null;
        handler.removeCallbacks(timeout);
        DownloadTransfer previous = transfer;
        File abandoned = pendingFile;
        pendingFile = null; finishProxy = null; finishRequest = null;
        transfer = new DownloadTransfer(directory);
        io.execute(() -> {
            previous.close();
            if (abandoned != null) {
                abandoned.delete();
                File parent = abandoned.getParentFile();
                if (parent.getName().matches("preview-[a-f0-9-]{36}")) parent.delete();
            }
        });
    }
    void close() { reset(); closed = true; io.shutdown(); }
}
