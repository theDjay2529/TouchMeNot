package com.djay.touchmenot;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event logger for debugging hook operations and blocked actions.
 * Writes timestamped logs to /sdcard/Download/touchmenot_recorder.log
 */
public class Logger {
    private static final String TAG = "TouchMeNot";
    private static final AtomicBoolean inited = new AtomicBoolean(false);
    private static volatile Writer writer = null;
    private static volatile String activePath = null;

    public static void initOnce() {
        if (inited.compareAndSet(false, true)) {
            String[] paths = new String[]{
                "/sdcard/Download/touchmenot_recorder.log",
                Environment.getExternalStorageDirectory() + "/Download/touchmenot_recorder.log",
                "/storage/emulated/0/Download/touchmenot_recorder.log",
                "/data/local/tmp/touchmenot_recorder.log",
                "/sdcard/Documents/touchmenot_recorder.log"
            };

            for (String path : paths) {
                try {
                    File f = new File(path);
                    File parent = f.getParentFile();
                    if (parent != null && !parent.exists()) {
                        boolean created = parent.mkdirs();
                        Log.d(TAG, "Created dir: " + parent + " = " + created);
                    }
                    try (FileOutputStream fos = new FileOutputStream(f, false)) {
                        String header = "=== Log Start: " + nowTs() + " ===\n";
                        fos.write(header.getBytes());
                        fos.flush();
                    }
                    writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true)));
                    activePath = path;
                    Log.i(TAG, "Logger initialized at: " + path);
                    return;
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to init logger at " + path + ": " + t.getMessage());
                }
            }
            Log.e(TAG, "Logger failed to initialize at any path");
            writer = null;
        }
    }

    private static String nowTs() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        } catch (Throwable t) {
            return Long.toString(System.currentTimeMillis());
        }
    }

    public static void log(String category, String message) {
        try {
            initOnce();
            String line = String.format(Locale.US, "%s | %s | %s", nowTs(), category, message);
            Log.d(TAG, "[" + category + "] " + message);
            Writer w = writer;
            if (w == null) {
                Log.w(TAG, "Writer is null, log not written to file");
                return;
            }
            synchronized (w) {
                w.write(line + "\n");
                w.flush();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Log write failed: " + t.getMessage());
            writer = null;
        }
    }

    public static void hookSuccess(String what) {
        log("HOOK", what);
    }

    public static void hookFail(String what, String reason) {
        log("HOOK_FAIL", what + " -> " + reason);
    }

    public static void blocked(String what, String reason) {
        log("BLOCK", what + " -> " + reason);
    }

    public static void info(String what) {
        log("INFO", what);
    }

    public static void error(String what, String reason) {
        log("ERROR", what + " -> " + reason);
    }
}
