package com.nexus.universalbridge.finalapp;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(79, 220, 132);
    private static final int RED = Color.rgb(255, 95, 95);
    private static final int YELLOW = Color.rgb(255, 196, 77);
    private static final int BLUE = Color.rgb(78, 161, 255);
    private static final String APP_VERSION = "2.1.0";

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean actionInFlight = new AtomicBoolean(false);

    private BridgeApi api;
    private String deviceId;
    private boolean registrationRequested;
    private volatile boolean approved;
    private volatile boolean humanReady;
    private volatile String currentTaskId;
    private volatile String currentCorrelationId;
    private volatile String currentTargetUrl;
    private volatile String currentStatus;
    private volatile String allowedHost;

    private WebView webView;
    private Button openButton;
    private Button readyButton;
    private TextView apiStatus, pairingStatus, heartbeatStatus, taskStatus, correlationStatus,
            urlStatus, webStatus, humanStatus, agentStatus, resultStatus, deviceStatus, errorStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        try {
            SecretStore store = new SecretStore(this);
            deviceId = store.getOrCreateDeviceId();
            String token = store.getOrCreateToken();
            api = new BridgeApi(deviceId, token);
            setStatus(deviceStatus, "DISPOSITIVO", deviceId, BLUE);
            worker.scheduleWithFixedDelay(this::tickSafe, 0, 4, TimeUnit.SECONDS);
        } catch (Exception e) {
            setStatus(errorStatus, "ERRORE IDENTITÀ", safe(e), RED);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8, 17, 31));

        TextView title = new TextView(this);
        title.setText("NEXUS UNIVERSAL BRIDGE  •  v2.1.0");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(12), dp(12), dp(12), dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout statusBox = new LinearLayout(this);
        statusBox.setOrientation(LinearLayout.VERTICAL);
        statusBox.setPadding(dp(12), 0, dp(12), dp(6));
        apiStatus = row(statusBox); pairingStatus = row(statusBox); heartbeatStatus = row(statusBox);
        deviceStatus = row(statusBox); taskStatus = row(statusBox); correlationStatus = row(statusBox);
        urlStatus = row(statusBox); webStatus = row(statusBox); humanStatus = row(statusBox);
        agentStatus = row(statusBox); resultStatus = row(statusBox); errorStatus = row(statusBox);
        setStatus(apiStatus, "EDGE API", BridgeApi.ENDPOINT, YELLOW);
        setStatus(pairingStatus, "PAIRING", "inizializzazione", YELLOW);
        setStatus(heartbeatStatus, "HEARTBEAT", "in attesa", YELLOW);
        setStatus(taskStatus, "TASK", "nessuno", YELLOW);
        setStatus(correlationStatus, "CORRELATION ID", "—", YELLOW);
        setStatus(urlStatus, "URL", "—", YELLOW);
        setStatus(webStatus, "WEBVIEW", "chiusa", YELLOW);
        setStatus(humanStatus, "AUTORIZZAZIONE UMANA", "non concessa", YELLOW);
        setStatus(agentStatus, "AZIONE AGENTE", "in attesa", YELLOW);
        setStatus(resultStatus, "RISULTATO", "in attesa", YELLOW);
        setStatus(errorStatus, "DIAGNOSTICA", "nessun errore", GREEN);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(statusBox);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(235)));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(8), dp(4), dp(8), dp(4));
        openButton = new Button(this);
        openButton.setText("APRI SITO");
        openButton.setEnabled(false);
        openButton.setOnClickListener(v -> openCurrentTask());
        controls.addView(openButton, new LinearLayout.LayoutParams(0, -2, 1));
        readyButton = new Button(this);
        readyButton.setText("ACCESSO COMPLETATO — CONTINUA");
        readyButton.setEnabled(false);
        readyButton.setOnClickListener(v -> confirmHumanReady());
        controls.addView(readyButton, new LinearLayout.LayoutParams(0, -2, 2));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(this);
        configureWebView();
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String scheme = uri.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) return false;
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                setStatus(webStatus, "WEBVIEW", "attiva • " + shortText(url, 120), GREEN);
                if (humanReady) sendSnapshot();
            }
        });
    }

    private void tickSafe() {
        try { tick(); }
        catch (Exception e) {
            runOnUiThread(() -> {
                setStatus(apiStatus, "EDGE API", "non raggiungibile", RED);
                setStatus(errorStatus, "DIAGNOSTICA", safe(e), RED);
            });
        }
    }

    private void tick() throws Exception {
        if (!approved) {
            if (!registrationRequested) {
                JSONObject meta = new JSONObject();
                meta.put("manufacturer", Build.MANUFACTURER);
                meta.put("model", Build.MODEL);
                meta.put("sdk", Build.VERSION.SDK_INT);
                JSONObject req = new JSONObject();
                req.put("label", "Tablet NEXUS Bridge");
                req.put("appVersion", APP_VERSION);
                req.put("metadata", meta);
                JSONObject response = api.post("register_request", req);
                registrationRequested = true;
                String status = response.optString("status", "pending");
                approved = "approved".equals(status);
                updatePairing(status);
            } else {
                JSONObject response = api.post("registration_status", null);
                String status = response.optString("status", "unknown");
                approved = "approved".equals(status);
                updatePairing(status);
            }
            return;
        }

        JSONObject heartbeat = new JSONObject();
        heartbeat.put("appVersion", APP_VERSION);
        heartbeat.put("capabilities", new JSONObject().put("webview", true).put("humanGate", true).put("sanitizedDom", true));
        api.post("heartbeat", heartbeat);
        runOnUiThread(() -> {
            setStatus(apiStatus, "EDGE API", "connessa", GREEN);
            setStatus(heartbeatStatus, "HEARTBEAT", "online", GREEN);
        });

        JSONObject pulled = api.post("pull", null);
        handleTasks(pulled.optJSONArray("tasks"));
        handleActions(pulled.optJSONArray("actions"));
    }

    private void updatePairing(String status) {
        runOnUiThread(() -> {
            setStatus(apiStatus, "EDGE API", "raggiungibile", GREEN);
            if ("approved".equals(status)) setStatus(pairingStatus, "PAIRING", "APPROVATO", GREEN);
            else if ("rejected".equals(status)) setStatus(pairingStatus, "PAIRING", "RIFIUTATO", RED);
            else setStatus(pairingStatus, "PAIRING", "richiesta inviata • attendo approvazione", YELLOW);
        });
    }

    private void handleTasks(JSONArray tasks) throws Exception {
        if (tasks == null || tasks.length() == 0) {
            runOnUiThread(() -> setStatus(taskStatus, "TASK", "nessun task attivo", YELLOW));
            return;
        }
        JSONObject chosen = null;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject t = tasks.getJSONObject(i);
            if (currentTaskId != null && currentTaskId.equals(t.optString("task_id"))) { chosen = t; break; }
        }
        if (chosen == null) chosen = tasks.getJSONObject(0);
        String taskId = chosen.optString("task_id");
        boolean changed = currentTaskId == null || !currentTaskId.equals(taskId);
        currentTaskId = taskId;
        currentCorrelationId = chosen.optString("correlation_id", "");
        currentTargetUrl = chosen.optString("target_url", "");
        currentStatus = chosen.optString("status", "");
        allowedHost = Uri.parse(currentTargetUrl).getHost();
        if (changed) humanReady = "human_ready".equals(currentStatus) || "agent_running".equals(currentStatus);

        final String description = chosen.optString("description", "");
        runOnUiThread(() -> {
            setStatus(taskStatus, "TASK", currentStatus + " • " + shortText(description, 100), GREEN);
            setStatus(correlationStatus, "CORRELATION ID", currentCorrelationId, BLUE);
            setStatus(urlStatus, "URL", currentTargetUrl, BLUE);
            openButton.setEnabled(!currentTargetUrl.isEmpty());
            readyButton.setEnabled(!currentTargetUrl.isEmpty());
            setStatus(humanStatus, "AUTORIZZAZIONE UMANA", humanReady ? "CONCESSA" : "richiesta", humanReady ? GREEN : YELLOW);
        });

        if ("queued".equals(currentStatus)) {
            JSONObject extra = new JSONObject().put("taskId", currentTaskId).put("status", "delivered");
            api.post("task_status", extra);
            currentStatus = "delivered";
        }
    }

    private void openCurrentTask() {
        String url = currentTargetUrl;
        if (url == null || url.isEmpty()) return;
        humanReady = false;
        setStatus(humanStatus, "AUTORIZZAZIONE UMANA", "esegui login/2FA/CAPTCHA, poi premi CONTINUA", YELLOW);
        webView.loadUrl(url);
        worker.execute(() -> {
            try {
                api.post("task_status", new JSONObject().put("taskId", currentTaskId).put("status", "awaiting_human"));
            } catch (Exception e) { showError(e); }
        });
    }

    private void confirmHumanReady() {
        if (currentTaskId == null || webView.getUrl() == null) return;
        humanReady = true;
        setStatus(humanStatus, "AUTORIZZAZIONE UMANA", "CONCESSA — agente abilitato", GREEN);
        worker.execute(() -> {
            try {
                api.post("task_status", new JSONObject().put("taskId", currentTaskId).put("status", "human_ready"));
                runOnUiThread(this::sendSnapshot);
            } catch (Exception e) { showError(e); }
        });
    }

    private void handleActions(JSONArray actions) throws Exception {
        if (!humanReady || actions == null || actions.length() == 0 || actionInFlight.get()) return;
        JSONObject action = actions.getJSONObject(0);
        if (!currentTaskId.equals(action.optString("task_id"))) return;
        if (!actionInFlight.compareAndSet(false, true)) return;
        String actionId = action.optString("action_id");
        String type = action.optString("action_type");
        JSONObject payload = action.optJSONObject("payload");
        if (payload == null) payload = new JSONObject();
        api.post("claim_action", new JSONObject().put("actionId", actionId));
        if ("human_ready".equals(currentStatus)) {
            api.post("task_status", new JSONObject().put("taskId", currentTaskId).put("status", "agent_running"));
            currentStatus = "agent_running";
        }
        final JSONObject finalPayload = payload;
        runOnUiThread(() -> executeAction(actionId, type, finalPayload));
    }

    private void executeAction(String actionId, String type, JSONObject payload) {
        setStatus(agentStatus, "AZIONE AGENTE", type, BLUE);
        try {
            if ("navigate".equals(type)) {
                String url = payload.optString("url", "");
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || allowedHost == null || !(host.equalsIgnoreCase(allowedHost) || host.endsWith("." + allowedHost))) {
                    postActionResult(actionId, false, new JSONObject().put("error", "domain_blocked")); return;
                }
                webView.loadUrl(url);
                postActionResult(actionId, true, new JSONObject().put("navigating", true)); return;
            }
            if ("back".equals(type)) { if (webView.canGoBack()) webView.goBack(); postActionResult(actionId, true, new JSONObject()); return; }
            if ("forward".equals(type)) { if (webView.canGoForward()) webView.goForward(); postActionResult(actionId, true, new JSONObject()); return; }
            if ("refresh".equals(type)) { webView.reload(); postActionResult(actionId, true, new JSONObject()); return; }

            String script = WebAutomation.scriptFor(type, payload);
            webView.evaluateJavascript(script, raw -> {
                try {
                    JSONObject result = parseJsObject(raw);
                    boolean ok = result.optBoolean("ok", false);
                    if ("read".equals(type) && ok) postPageState(result);
                    if ("finish".equals(type) && ok && result.optBoolean("matched", false)) {
                        worker.execute(() -> {
                            try {
                                api.post("task_status", new JSONObject().put("taskId", currentTaskId).put("status", "completed").put("result", new JSONObject().put("verified", true)));
                                runOnUiThread(() -> setStatus(resultStatus, "RISULTATO", "COMPLETATO E VERIFICATO", GREEN));
                            } catch (Exception e) { showError(e); }
                        });
                    }
                    if ("finish".equals(type) && ok && !result.optBoolean("matched", false)) {
                        ok = false; result.put("error", "expected_text_not_found");
                    }
                    postActionResult(actionId, ok, result);
                } catch (Exception e) {
                    try { postActionResult(actionId, false, new JSONObject().put("error", "invalid_js_result")); }
                    catch (Exception ignored) { actionInFlight.set(false); }
                }
            });
        } catch (Exception e) {
            try { postActionResult(actionId, false, new JSONObject().put("error", "execution_error")); }
            catch (Exception ignored) { actionInFlight.set(false); }
        }
    }

    private void postActionResult(String actionId, boolean ok, JSONObject result) {
        worker.execute(() -> {
            try {
                JSONObject extra = new JSONObject().put("actionId", actionId).put("status", ok ? "done" : "failed").put("result", result);
                api.post("action_result", extra);
                runOnUiThread(() -> setStatus(agentStatus, "AZIONE AGENTE", ok ? "completata" : "fallita", ok ? GREEN : RED));
            } catch (Exception e) { showError(e); }
            finally { actionInFlight.set(false); }
        });
    }

    private void sendSnapshot() {
        if (!humanReady || currentTaskId == null) return;
        webView.evaluateJavascript(WebAutomation.snapshotScript(), raw -> {
            try { postPageState(parseJsObject(raw)); }
            catch (Exception e) { showError(e); }
        });
    }

    private void postPageState(JSONObject snapshot) {
        worker.execute(() -> {
            try {
                JSONObject extra = new JSONObject();
                extra.put("taskId", currentTaskId);
                extra.put("pageUrl", snapshot.optString("url", webView.getUrl()));
                extra.put("pageTitle", snapshot.optString("title", webView.getTitle()));
                extra.put("domSnapshot", snapshot);
                api.post("page_state", extra);
            } catch (Exception e) { showError(e); }
        });
    }

    private static JSONObject parseJsObject(String raw) throws Exception {
        if (raw == null || "null".equals(raw)) return new JSONObject().put("ok", false).put("error", "null_result");
        String decoded = raw;
        if (raw.startsWith("\"") && raw.endsWith("\"")) decoded = new JSONArray("[" + raw + "]").getString(0);
        return new JSONObject(decoded);
    }

    private TextView row(LinearLayout parent) {
        TextView t = new TextView(this);
        t.setTextSize(11);
        t.setPadding(dp(6), dp(2), dp(6), dp(2));
        parent.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    private void setStatus(TextView view, String label, String value, int color) {
        if (view == null) return;
        runOnUiThread(() -> { view.setText(label + ":  " + value); view.setTextColor(color); });
    }

    private void showError(Exception e) {
        runOnUiThread(() -> setStatus(errorStatus, "DIAGNOSTICA", safe(e), RED));
    }

    private static String safe(Throwable e) {
        String s = e == null ? "errore" : e.getMessage();
        if (s == null || s.isEmpty()) s = e.getClass().getSimpleName();
        return shortText(s, 180);
    }

    private static String shortText(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
