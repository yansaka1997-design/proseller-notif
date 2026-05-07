package com.proseller.notiflistener;

import android.content.*;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // NotificationListenerService dimulai otomatis oleh Android
        // saat izin diberikan. Broadcast ini memastikan komponen tetap aktif.
        Log.d("ProSellerNotif", "Boot received — service akan aktif otomatis.");
    }
}
