package com.proseller.notiflistener;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class WebhookSender {

    public interface Callback {
        void onResult(String result);
    }

    public static void send(String urlStr, String jsonPayload, Callback cb) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("User-Agent", "ProSellerNotif/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                byte[] body = jsonPayload.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(body.length);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }

                int code = conn.getResponseCode();
                String resp;
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    resp = sb.toString();
                }
                if (cb != null) cb.onResult("HTTP " + code + " — " + resp);
            } catch (Exception e) {
                if (cb != null) cb.onResult("ERROR: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
