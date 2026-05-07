package com.proseller.notiflistener;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private EditText etUrl, etSecret;
    private TextView tvStatus, tvLog;
    private Switch swGopay, swOvo, swDana, swShopee, swLinkaja;
    private Switch swBca, swBri, swMandiri, swBni, swBsi;
    private Switch swCustApp;
    private EditText etCustomApps;
    private ScrollView svLog;
    private SharedPreferences prefs;

    static final String PREF = "proseller_notif";
    static final String KEY_URL = "webhook_url";
    static final String KEY_SECRET = "secret";
    static final String KEY_APPS = "monitored_apps";

    // Package names
    static final Map<String, String> APP_MAP = new LinkedHashMap<>();
    static {
        APP_MAP.put("com.gojek.app", "GoPay");
        APP_MAP.put("ovo.id", "OVO");
        APP_MAP.put("com.dana.mobileapp", "Dana");
        APP_MAP.put("com.shopee.id", "ShopeePay");
        APP_MAP.put("com.telkom.tcash", "LinkAja");
        APP_MAP.put("com.bca", "BCA Mobile / myBCA");
        APP_MAP.put("com.bri.brimobile", "BRImo");
        APP_MAP.put("com.bni.mobilebanking", "BNI Mobile");
        APP_MAP.put("com.bsm.mobile", "BSI Mobile");
        APP_MAP.put("id.go.bri.brimo", "BRImo (alt)");
        APP_MAP.put("com.mandiri.smartphone", "Livin by Mandiri");
        APP_MAP.put("com.seabank.app", "SeaBank");
        APP_MAP.put("id.co.akulaku.android", "Akulaku");
        APP_MAP.put("com.jenius.android", "Jenius");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREF, MODE_PRIVATE);

        etUrl     = findViewById(R.id.etUrl);
        etSecret  = findViewById(R.id.etSecret);
        tvStatus  = findViewById(R.id.tvStatus);
        tvLog     = findViewById(R.id.tvLog);
        svLog     = findViewById(R.id.svLog);
        swGopay   = findViewById(R.id.swGopay);
        swOvo     = findViewById(R.id.swOvo);
        swDana    = findViewById(R.id.swDana);
        swShopee  = findViewById(R.id.swShopee);
        swLinkaja = findViewById(R.id.swLinkaja);
        swBca     = findViewById(R.id.swBca);
        swBri     = findViewById(R.id.swBri);
        swMandiri = findViewById(R.id.swMandiri);
        swBni     = findViewById(R.id.swBni);
        swBsi     = findViewById(R.id.swBsi);
        swCustApp = findViewById(R.id.swCustom);
        etCustomApps = findViewById(R.id.etCustomApps);

        loadPrefs();

        findViewById(R.id.btnSave).setOnClickListener(v -> saveAndRestart());
        findViewById(R.id.btnGrant).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        findViewById(R.id.btnTest).setOnClickListener(v -> testWebhook());
        findViewById(R.id.btnClearLog).setOnClickListener(v -> {
            tvLog.setText("Log kosong.");
            LogStore.clear();
        });

        swCustApp.setOnCheckedChangeListener((b, c) ->
            etCustomApps.setVisibility(c ? View.VISIBLE : View.GONE));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        refreshLog();
    }

    private void updateStatus() {
        boolean granted = isNotifAccessGranted();
        boolean running = isServiceRunning();
        if (granted && running) {
            tvStatus.setText("✅ Aktif — Mendengarkan notifikasi");
            tvStatus.setBackgroundColor(0xFF1B5E20);
        } else if (granted) {
            tvStatus.setText("⚠️ Izin OK, Service belum jalan — Klik SIMPAN");
            tvStatus.setBackgroundColor(0xFFE65100);
        } else {
            tvStatus.setText("❌ Belum ada izin — Klik IZINKAN AKSES NOTIFIKASI");
            tvStatus.setBackgroundColor(0xFFB71C1C);
        }
    }

    private boolean isNotifAccessGranted() {
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private boolean isServiceRunning() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(50)) {
            if (NotifListenerService.class.getName().equals(s.service.getClassName()))
                return true;
        }
        return false;
    }

    private void loadPrefs() {
        etUrl.setText(prefs.getString(KEY_URL, ""));
        etSecret.setText(prefs.getString(KEY_SECRET, ""));

        Set<String> saved = prefs.getStringSet(KEY_APPS, new HashSet<>());
        swGopay.setChecked(saved.contains("com.gojek.app"));
        swOvo.setChecked(saved.contains("ovo.id"));
        swDana.setChecked(saved.contains("com.dana.mobileapp"));
        swShopee.setChecked(saved.contains("com.shopee.id"));
        swLinkaja.setChecked(saved.contains("com.telkom.tcash"));
        swBca.setChecked(saved.contains("com.bca"));
        swBri.setChecked(saved.contains("com.bri.brimobile") || saved.contains("id.go.bri.brimo"));
        swMandiri.setChecked(saved.contains("com.mandiri.smartphone"));
        swBni.setChecked(saved.contains("com.bni.mobilebanking"));
        swBsi.setChecked(saved.contains("com.bsm.mobile"));

        // custom
        StringBuilder custom = new StringBuilder();
        for (String pkg : saved) {
            if (!APP_MAP.containsKey(pkg)) {
                if (custom.length() > 0) custom.append("\n");
                custom.append(pkg);
            }
        }
        if (custom.length() > 0) {
            swCustApp.setChecked(true);
            etCustomApps.setVisibility(View.VISIBLE);
            etCustomApps.setText(custom.toString());
        }
    }

    private Set<String> buildSelectedApps() {
        Set<String> set = new HashSet<>();
        if (swGopay.isChecked())   set.add("com.gojek.app");
        if (swOvo.isChecked())     set.add("ovo.id");
        if (swDana.isChecked())    set.add("com.dana.mobileapp");
        if (swShopee.isChecked())  set.add("com.shopee.id");
        if (swLinkaja.isChecked()) set.add("com.telkom.tcash");
        if (swBca.isChecked())     set.add("com.bca");
        if (swBri.isChecked())     { set.add("com.bri.brimobile"); set.add("id.go.bri.brimo"); }
        if (swMandiri.isChecked()) set.add("com.mandiri.smartphone");
        if (swBni.isChecked())     set.add("com.bni.mobilebanking");
        if (swBsi.isChecked())     set.add("com.bsm.mobile");
        if (swCustApp.isChecked()) {
            String raw = etCustomApps.getText().toString().trim();
            for (String line : raw.split("[\\n,;]+")) {
                String pkg = line.trim();
                if (!pkg.isEmpty()) set.add(pkg);
            }
        }
        return set;
    }

    private void saveAndRestart() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "URL webhook tidak boleh kosong!", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString(KEY_URL, url);
        ed.putString(KEY_SECRET, etSecret.getText().toString().trim());
        ed.putStringSet(KEY_APPS, buildSelectedApps());
        ed.apply();

        // Restart service
        ComponentName cn = new ComponentName(this, NotifListenerService.class);
        getPackageManager().setComponentEnabledSetting(cn,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
        getPackageManager().setComponentEnabledSetting(cn,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);

        Toast.makeText(this, "✅ Disimpan! Service direstart.", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void testWebhook() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Isi URL dulu!", Toast.LENGTH_SHORT).show();
            return;
        }
        String secret = etSecret.getText().toString().trim();
        String payload = "{\"app\":\"test\",\"app_name\":\"ProSeller Test\","
            + "\"title\":\"Test Notifikasi\","
            + "\"text\":\"Ini test dari ProSeller Notif App\","
            + "\"timestamp\":" + System.currentTimeMillis() + ","
            + "\"secret\":\"" + secret + "\"}";
        WebhookSender.send(url, payload, result -> runOnUiThread(() -> {
            Toast.makeText(this, "Test: " + result, Toast.LENGTH_LONG).show();
            refreshLog();
        }));
    }

    public void refreshLog() {
        List<String> logs = LogStore.getLogs();
        if (logs.isEmpty()) {
            tvLog.setText("Belum ada notifikasi tertangkap.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = logs.size() - 1; i >= 0; i--) {
                sb.append(logs.get(i)).append("\n─────────────\n");
            }
            tvLog.setText(sb.toString());
        }
        svLog.post(() -> svLog.fullScroll(View.FOCUS_UP));
    }
}
