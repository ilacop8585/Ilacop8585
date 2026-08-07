package com.nexus.universalbridge.finalapp;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class BridgeApi {
    public static final String ENDPOINT = "https://xquckndlrrsskyuvbqaz.supabase.co/functions/v1/nexus-bridge-api";
    private final String deviceId;
    private final String token;

    public BridgeApi(String deviceId, String token) {
        this.deviceId = deviceId;
        this.token = token;
    }

    public JSONObject post(String action, JSONObject extras) throws Exception {
        JSONObject body = extras == null ? new JSONObject() : new JSONObject(extras.toString());
        body.put("action", action);
        body.put("deviceId", deviceId);
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Cache-Control", "no-store");
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload);
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = readLimited(stream, 512_000);
        connection.disconnect();
        if (code < 200 || code >= 300) {
            String reason = "HTTP " + code;
            try {
                reason += " " + new JSONObject(text).optString("error", "request_failed");
            } catch (Exception ignored) {}
            throw new IOException(reason);
        }
        return text.isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private static String readLimited(InputStream stream, int maxChars) throws IOException {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int read;
            while ((read = reader.read(buf)) != -1) {
                if (out.length() + read > maxChars) throw new IOException("response_too_large");
                out.append(buf, 0, read);
            }
        }
        return out.toString();
    }
}
