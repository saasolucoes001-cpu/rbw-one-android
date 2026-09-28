package br.com.rbwone.web;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
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
    private int generation;
    private boolean processing, closed, saving, pickerOutstanding;
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
            WebViewCompat.addDocumentStartJavaScript(web, script, Collections.singleton(RoutePolicy.ORIGIN));
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
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(current.mime).putExtra(Intent.EXTRA_TITLE, current.name);
                    try { pickerOutstanding = true; activity.startActivityForResult(intent, SAVE_FILE); }
                    catch (Exception error) { pickerOutstanding = false; complete(false, "Nenhum aplicativo disponível para salvar arquivos."); }
                });
            });
        } catch (Exception ignored) { /* Invalid messages cannot allocate files. */ }
    }

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
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush(); success = true;
            } catch (Exception ignored) { }
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
        reset();
    }
    private void reply(JavaScriptReplyProxy proxy, String request, boolean success, String error) {
        try {
            JSONObject response = new JSONObject().put("requestId", request).put("ok", success);
            if (error != null) response.put("error", error);
            proxy.postMessage(response.toString());
        } catch (Exception ignored) { }
    }
    void reset() {
        generation++; processing = false; saving = false; activeId = null;
        handler.removeCallbacks(timeout);
        DownloadTransfer previous = transfer;
        File abandoned = pendingFile;
        pendingFile = null; finishProxy = null; finishRequest = null;
        transfer = new DownloadTransfer(directory);
        io.execute(() -> { previous.close(); if (abandoned != null) abandoned.delete(); });
    }
    void close() { reset(); closed = true; io.shutdown(); }
}
