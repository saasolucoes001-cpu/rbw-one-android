package br.com.rbwone.web;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;
import android.print.PrintJob;
import android.print.PrintManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Collections;

/** System print UI for first-party HTML, including document.write report windows. */
final class WebPrinting {
    private final Activity activity;
    private Dialog dialog;
    private WebView popup;
    private PrintJob lastJob;
    private boolean printPending;
    private int printRequest;
    // document.write popups do not reliably emit onPageFinished.
    private static final String PRINT_READY = "Boolean(document.body && document.body.hasChildNodes() && document.readyState !== 'loading' && Array.from(document.images).every(function(image){return image.complete;}) && (!document.fonts || document.fonts.status === 'loaded'))";
    WebPrinting(Activity activity) { this.activity = activity; }

    void install(WebView web) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        WebViewCompat.addWebMessageListener(web, "RBWPrinting", Collections.singleton(RoutePolicy.ORIGIN),
            (view, message, origin, mainFrame, proxy) -> {
                if (mainFrame && RoutePolicy.officialOrigin(origin.toString()) && "print".equals(message.getData()) &&
                    (RoutePolicy.officialOrigin(view.getUrl()) || (view == popup && (view.getUrl() == null || "about:blank".equals(view.getUrl()))))) {
                    print(view);
                }
            });
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web,
                "if(window===window.top && window.RBWPrinting){window.print=function(){window.RBWPrinting.postMessage('print');};}",
                Collections.singleton(RoutePolicy.ORIGIN));
        }
    }

    void print(WebView web) {
        if (web == popup) {
            if (!printPending) {
                printPending = true;
                awaitPopup(web, ++printRequest, 0);
            }
            return;
        }
        startPrint(web);
    }

    private void awaitPopup(WebView web, int request, int attempt) {
        if (web != popup || request != printRequest || !printPending) return;
        web.evaluateJavascript(PRINT_READY, ready -> {
            if (web != popup || request != printRequest || !printPending) return;
            if ("true".equals(ready)) {
                printPending = false;
                startPrint(web);
            } else if (attempt < 100) {
                web.postDelayed(() -> awaitPopup(web, request, attempt + 1), 100);
            } else {
                printPending = false;
                Toast.makeText(activity, "O documento ainda está carregando. Tente imprimir novamente.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startPrint(WebView web) {
        if (lastJob != null && !lastJob.isCompleted() && !lastJob.isCancelled() && !lastJob.isFailed()) return;
        try {
            PrintManager manager = (PrintManager) activity.getSystemService(Activity.PRINT_SERVICE);
            if (manager == null) throw new IllegalStateException("Printing unavailable");
            String name = web.getTitle();
            if (name == null || name.isBlank()) name = "Documento RBW One";
            lastJob = manager.print(name, web.createPrintDocumentAdapter(name), null);
        } catch (Exception error) { Toast.makeText(activity, "Não foi possível abrir a impressão do Android.", Toast.LENGTH_LONG).show(); }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    boolean open(WebView opener, boolean userGesture, Message result) {
        if (!userGesture || !RoutePolicy.officialOrigin(opener.getUrl()) || popup != null) return false;
        WebView child = new WebView(activity); popup = child; printPending = false; printRequest++;
        WebSettings settings = child.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        install(child);
        Dialog window = new Dialog(activity); dialog = window;
        LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout actions = new LinearLayout(activity);
        Button print = new Button(activity); print.setText(R.string.print_document); print.setOnClickListener(v -> print(child));
        Button close = new Button(activity); close.setText(R.string.close_document); close.setOnClickListener(v -> window.dismiss());
        actions.addView(print, new LinearLayout.LayoutParams(0, -2, 1)); actions.addView(close);
        content.addView(actions); content.addView(child, new LinearLayout.LayoutParams(-1, 0, 1));
        window.setContentView(content);
        window.setOnDismissListener(v -> { if (popup == child) { popup = null; dialog = null; printPending = false; printRequest++; } child.stopLoading(); child.destroy(); });
        child.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (RoutePolicy.officialOrigin(url) || "about:blank".equals(url)) return false;
                if (request.isForMainFrame()) {
                    String scheme = request.getUrl().getScheme();
                    if ("https".equals(scheme) || "http".equals(scheme) || "mailto".equals(scheme) || "tel".equals(scheme)) {
                        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                        catch (Exception error) { Toast.makeText(activity, "Nenhum aplicativo disponível para este link.", Toast.LENGTH_LONG).show(); }
                        window.dismiss();
                    }
                }
                return true;
            }
        });
        child.setWebChromeClient(new WebChromeClient() {
            @Override public void onCloseWindow(WebView windowView) { window.dismiss(); }
        });
        window.show();
        if (window.getWindow() != null) window.getWindow().setLayout(-1, -1);
        WebView.WebViewTransport transport = (WebView.WebViewTransport) result.obj;
        transport.setWebView(child); result.sendToTarget();
        return true;
    }
    void close() { if (dialog != null) dialog.dismiss(); }
}
