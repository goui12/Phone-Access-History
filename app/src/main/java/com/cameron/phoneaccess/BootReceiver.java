package com.cameron.phoneaccess;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reconnects an already-enabled monitor after boot or an app upgrade. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            // LOCKED_BOOT_COMPLETED registers before first post-reboot unlock.
            // Normal BOOT_COMPLETED alone can arrive too late for that event.
            // Force Stop and manufacturer restrictions can still prevent boot
            // delivery; opening the app explicitly restarts the monitor.
            UnlockService.startIfEnabled(context);
        }
    }
}
