package com.djay.touchmenot;

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
 * Lightweight diagnostic logger to capture class and method calls during runtime.
 * Writes to /sdcard/Download/touchmenot_classes.log so it doesn't mix with the main recorder.
 */
public final class DiagnosticLogger {
    private static final String LOG_PATH = "/sdcard/Download/touchmenot_classes.log";
    private static final AtomicBoolean inited = new AtomicBoolean(false);
    private static volatile Writer writer = null;

    private DiagnosticLogger() {}

    private static void initOnce() {
        if (inited.compareAndSet(false, true)) {
            try {
                File f = new File(LOG_PATH);
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(f, false)) {
                    String header = "=== Class/Method Trace Start: " + nowTs() + " ===\n";
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
        try {
            initOnce();
            Writer w = writer;
            if (w == null) return;
            String line = String.format(Locale.US, "%s | %s | %s\n", nowTs(), category, message);
            synchronized (w) {
                w.write(line);
                w.flush();
            }
        } catch (Throwable t) {
            writer = null;
        }
    }
}
