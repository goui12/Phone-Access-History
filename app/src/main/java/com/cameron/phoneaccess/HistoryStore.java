package com.cameron.phoneaccess;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Small, private, credential-encrypted history database. */
public final class HistoryStore extends SQLiteOpenHelper {
    private static HistoryStore instance;

    public static final class Event {
        public final long timestamp;
        public final String zoneId;

        public Event(long timestamp, String zoneId) {
            this.timestamp = timestamp;
            this.zoneId = zoneId;
        }
    }

    public static final class Day {
        public final String date;
        public final int count;

        public Day(String date, int count) {
            this.date = date;
            this.count = count;
        }
    }

    public static synchronized HistoryStore get(Context context) {
        if (instance == null) {
            // The Application uses the default credential-encrypted context.
            // Only the service's settings/buffer explicitly use a separate
            // device-protected context; this database never uses that context.
            Context application = context.getApplicationContext();
            if (application.isDeviceProtectedStorage()) {
                throw new IllegalStateException(
                        "History requires credential-encrypted application storage");
            }
            instance = new HistoryStore(application);
        }
        return instance;
    }

    private HistoryStore(Context context) {
        super(context, "phone_access_history.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE unlocks ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "timestamp INTEGER NOT NULL, "
                + "day TEXT NOT NULL, "
                + "zone TEXT NOT NULL, "
                + "UNIQUE(timestamp, zone))");
        db.execSQL("CREATE INDEX unlocks_day_time ON unlocks(day, timestamp DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 has no migrations. Never discard a user's history here.
        throw new IllegalStateException("Unsupported history database upgrade");
    }

    public void record(long timestamp, String zoneId) {
        String day = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of(zoneId))
                .toLocalDate().toString();
        // Replaying an event buffered during Direct Boot must be idempotent if
        // the process died between committing the row and clearing the buffer.
        // executeInsert propagates actual database failures to the caller; an
        // ignored duplicate is the only expected no-row result.
        try (SQLiteStatement statement = getWritableDatabase().compileStatement(
                "INSERT OR IGNORE INTO unlocks(timestamp, day, zone) VALUES (?, ?, ?)")) {
            statement.bindLong(1, timestamp);
            statement.bindString(2, day);
            statement.bindString(3, zoneId);
            statement.executeInsert();
        }
    }

    public List<Event> eventsForDay(String date) {
        List<Event> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("unlocks",
                new String[]{"timestamp", "zone"}, "day = ?", new String[]{date},
                null, null, "timestamp DESC, id DESC")) {
            while (cursor.moveToNext()) {
                result.add(new Event(cursor.getLong(0), cursor.getString(1)));
            }
        }
        return result;
    }

    public List<Day> days() {
        List<Day> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT day, COUNT(*) FROM unlocks GROUP BY day ORDER BY day DESC", null)) {
            while (cursor.moveToNext()) {
                result.add(new Day(cursor.getString(0), cursor.getInt(1)));
            }
        }
        return result;
    }

    public int countForDay(String day) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM unlocks WHERE day = ?", new String[]{day})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }
}
