package com.djay.touchmenot;

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
    private static final String LOG_PATH = "/sdcard/Download/touchmenot_recorder.log";
    private static final AtomicBoolean inited = new AtomicBoolean(false);
    private static volatile Writer writer = null;

    public static void initOnce() {
        if (inited.compareAndSet(false, true)) {
            try {
                File f = new File(LOG_PATH);
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(f, false)) {
                    String header = "=== Log Start: " + nowTs() + " ===\n";
                    fos.write(header.getBytes());
                    fos.flush();
                }
                writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true)));
            } catch (Throwable t) {
                writer = null;
            }
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
        // Always emit to logcat FIRST. The hooks run in system_server / SystemUI,
        // where the /sdcard file writer cannot be opened (no FUSE view), so `writer`
        // stays null there. The previous code returned on null BEFORE Log.d, which
        // left those processes with no diagnostics at all. logcat works everywhere:
        //   adb logcat -s TouchMeNot:*
        String line = String.format(Locale.US, "%s | %s | %s", nowTs(), category, message);
        try {
            Log.d("TouchMeNot", line);
        } catch (Throwable ignored) {
        }
        // Best-effort file log (works from the app process; no-op in system_server).
        try {
            initOnce();
            Writer w = writer;
            if (w == null) return;
            synchronized (w) {
                w.write(line);
                w.write("\n");
                w.flush();
            }
        } catch (Throwable t) {
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
