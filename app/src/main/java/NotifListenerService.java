package com.proseller.notiflistener;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.*;

public class NotifListenerService extends NotificationListenerService {

    private static final String CHANNEL_ID = "proseller_fg";
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(MainActivity.PREF, MODE_PRIVATE);
        startForegroundNotif();
    }

    private void startForegroundNotif() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "ProSeller Notif Listener",
                NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Service aktif mendengarkan notif ewallet & bank");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
        }
        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ProSeller Notif — Aktif")
            .setContentText("Memantau notifikasi ewallet & bank...")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
        startForeground(1, notif);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String pkg = sbn.getPackageName();
        Set<String> monitored = prefs.getStringSet(MainActivity.KEY_APPS, new HashSet<>());

        if (!monitored.contains(pkg)) return;

        // Skip notif dari app itu sendiri yang bukan transaksi
        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text  = extras.getCharSequence(Notification.EXTRA_TEXT,  "") != null
                       ? extras.getCharSequence(Notification.EXTRA_TEXT).toString() : "";
        String bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT, "") != null
                       ? extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString() : "";

        if (!bigText.isEmpty()) text = bigText;
        if (title.isEmpty() && text.isEmpty()) return;

        // Filter noise: skip notif promosi/iklan umum yang tidak mengandung angka rupiah
        String combined = (title + " " + text).toLowerCase();
        boolean hasRupiah = combined.contains("rp") || combined.contains("idr")
            || combined.contains("bayar") || combined.contains("transfer")
            || combined.contains("masuk") || combined.contains("berhasil")
            || combined.contains("terima") || combined.contains("debit")
            || combined.contains("kredit") || combined.contains("tagihan")
            || combined.contains("top up") || combined.contains("saldo");

        if (!hasRupiah) return;

        String appName = getAppName(pkg);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(new Date(sbn.getPostTime()));
        String secret = prefs.getString(MainActivity.KEY_SECRET, "");
        String url    = prefs.getString(MainActivity.KEY_URL, "");

        if (url.isEmpty()) return;

        // Build JSON payload
        String payload = "{"
            + "\"app\":\"" + escJson(pkg) + "\","
            + "\"app_name\":\"" + escJson(appName) + "\","
            + "\"title\":\"" + escJson(title) + "\","
            + "\"text\":\"" + escJson(text) + "\","
            + "\"timestamp\":" + sbn.getPostTime() + ","
            + "\"datetime\":\"" + timestamp + "\","
            + "\"secret\":\"" + escJson(secret) + "\""
            + "}";

        String logLine = "[" + timestamp + "] " + appName + "\n"
            + "📌 " + title + "\n"
            + "💬 " + text;
        LogStore.add(logLine);

        // Broadcast to MainActivity jika terbuka
        sendBroadcast(new Intent("com.proseller.notiflistener.LOG_UPDATE"));

        WebhookSender.send(url, payload, result -> {});
    }

    private String getAppName(String pkg) {
        // Try to get from APP_MAP first
        if (MainActivity.APP_MAP.containsKey(pkg))
            return MainActivity.APP_MAP.get(pkg);
        try {
            return getPackageManager()
                .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))
                .toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        requestRebind(new ComponentName(this, NotifListenerService.class));
    }
}
