package com.cameron.phoneaccess;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.UserManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.ZoneId;

/** An event-driven monitor; no timer, network access, or wake lock is needed. */
public final class UnlockService extends Service {
    public static final String HISTORY_CHANGED = "com.cameron.phoneaccess.HISTORY_CHANGED";
    public static volatile boolean running = false;
    public static volatile String lastError = null;

    private static final String TAG = "PhoneAccessHistory";
    private static final String CHANNEL = "unlock_monitoring";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFERENCES = "monitoring_state";
    private static final String PENDING = "pending_unlocks";
    private boolean receiverRegistered;
    private boolean nextUnlockAllowed = true;

    private static SharedPreferences state(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /** Called from the visible activity. Opening the app always enables logging. */
    public static void start(Context context) {
        try {
            if (!state(context).edit().putBoolean("monitoring", true).commit()) {
                throw new IllegalStateException("Could not save monitoring setting");
            }
            launch(context);
        } catch (RuntimeException error) {
            reportError("Could not start monitoring", error);
        }
    }

    static void startIfEnabled(Context context) {
        try {
            if (state(context).getBoolean("monitoring", false)) launch(context);
        } catch (RuntimeException error) {
            reportError("Could not resume monitoring after restart", error);
        }
    }

    private static void launch(Context context) {
        if (!running) {
            context.startForegroundService(new Intent(context, UnlockService.class));
        }
    }

    private static void reportError(String description, Exception error) {
        String detail = error.getMessage();
        lastError = description + (detail == null ? "." : ": " + detail);
        Log.e(TAG, description, error);
    }

    private final BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            try {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    nextUnlockAllowed = true;
                } else if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                    // Some devices can send USER_PRESENT more than once for a
                    // single wake/unlock. Count only the first successful save
                    // in each screen cycle. A fresh service permits its first
                    // event so startup while locked does not require a prior
                    // SCREEN_OFF broadcast to have been observed.
                    if (!nextUnlockAllowed) return;
                    long timestamp = System.currentTimeMillis();
                    String zoneId = ZoneId.systemDefault().getId();
                    if (isCredentialStorageAvailable()) {
                        flushPending();
                        HistoryStore.get(UnlockService.this).record(timestamp, zoneId);
                        nextUnlockAllowed = false;
                        lastError = null;
                        notifyHistoryChanged();
                    } else {
                        // This rare race preserves the real event without opening
                        // credential-encrypted storage before it is available.
                        bufferEvent(timestamp, zoneId);
                        nextUnlockAllowed = false;
                    }
                } else if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                    // Storage becoming available is not an additional unlock.
                    flushPending();
                }
            } catch (Exception error) {
                reportError("Could not save unlock history", error);
                notifyHistoryChanged();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Unlock monitoring", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps phone unlock history recording in the background");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);

            Intent open = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Phone Access History")
                    .setContentText("Recording phone unlocks on this device")
                    .setContentIntent(pending)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_USER_UNLOCKED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(unlockReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(unlockReceiver, filter);
            }
            receiverRegistered = true;
            running = true;
            lastError = null;
            notifyHistoryChanged();
        } catch (RuntimeException error) {
            reportError("Could not initialize monitoring", error);
            running = false;
            notifyHistoryChanged();
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!receiverRegistered) return START_NOT_STICKY;
        try {
            flushPending();
        } catch (Exception error) {
            reportError("Could not restore buffered history", error);
            notifyHistoryChanged();
        }
        // The system may recreate a killed service with a null intent. Nothing
        // about service creation or restart is recorded as a phone unlock.
        return START_STICKY;
    }

    private boolean isCredentialStorageAvailable() {
        UserManager manager = getSystemService(UserManager.class);
        return manager != null && manager.isUserUnlocked();
    }

    private void bufferEvent(long timestamp, String zoneId) throws JSONException {
        SharedPreferences preferences = state(this);
        JSONArray events = new JSONArray(preferences.getString(PENDING, "[]"));
        JSONObject event = new JSONObject();
        event.put("timestamp", timestamp);
        event.put("zone", zoneId);
        events.put(event);
        if (!preferences.edit().putString(PENDING, events.toString()).commit()) {
            throw new IllegalStateException("Could not buffer unlock before storage became available");
        }
    }

    private void flushPending() throws JSONException {
        if (!isCredentialStorageAvailable()) return;
        SharedPreferences preferences = state(this);
        JSONArray events = new JSONArray(preferences.getString(PENDING, "[]"));
        if (events.length() == 0) return;
        HistoryStore database = HistoryStore.get(this);
        for (int index = 0; index < events.length(); index++) {
            JSONObject event = events.getJSONObject(index);
            database.record(event.getLong("timestamp"), event.getString("zone"));
        }
        if (!preferences.edit().remove(PENDING).commit()) {
            throw new IllegalStateException("Could not finish restoring buffered history");
        }
        lastError = null;
        notifyHistoryChanged();
    }

    private void notifyHistoryChanged() {
        try {
            sendBroadcast(new Intent(HISTORY_CHANGED).setPackage(getPackageName()));
        } catch (RuntimeException error) {
            // A UI refresh failure must never stop recording or interrupt the
            // service's lifecycle cleanup. The activity also refreshes on resume.
            Log.w(TAG, "Could not notify history screen", error);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (receiverRegistered) {
            unregisterReceiver(unlockReceiver);
            receiverRegistered = false;
        }
        notifyHistoryChanged();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
