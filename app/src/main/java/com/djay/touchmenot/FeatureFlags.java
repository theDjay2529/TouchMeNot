package com.djay.touchmenot;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-process feature flags cache backed by PreferencesProvider.
 * Maintains in-memory cache with live updates via ContentObserver.
 */
public final class FeatureFlags {
    private static final String AUTH = "com.djay.touchmenot.preferences";
    private static final Uri FLAGS_URI = Uri.parse("content://" + AUTH + "/flags");

    private static volatile boolean blockAirplane = true;
    private static volatile boolean blockHotspot = true;
    private static volatile boolean blockInternet = true;
    private static volatile boolean blockBluetooth = true;
    private static volatile boolean blockPowerControls = true;
    private static volatile boolean showFakePowerMenu = true;

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static volatile ContentResolver resolver = null;

    private FeatureFlags() {
    }

    public static void ensureInitialized(Context ctx) {
        if (ctx == null) return;
        if (initialized.compareAndSet(false, true)) {
            try {
                resolver = ctx.getApplicationContext().getContentResolver();
                reload();
                resolver.registerContentObserver(FLAGS_URI, true, new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        reload();
                    }

                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        reload();
                    }
                });
            } catch (Throwable t) {
                // Ignore initialization errors, use defaults
            }
        }
    }

    public static void reload() {
        final ContentResolver r = resolver;
        if (r == null) return;
        Cursor c = null;
        try {
            c = r.query(FLAGS_URI, null, null, null, null);
            if (c == null) return;
            int keyIdx = c.getColumnIndex("key");
            int valIdx = c.getColumnIndex("value");
            while (c.moveToNext()) {
                String key = keyIdx >= 0 ? c.getString(keyIdx) : null;
                int iv = valIdx >= 0 ? c.getInt(valIdx) : 1;
                boolean v = iv != 0;
                if (key == null) continue;
                switch (key) {
                    case "tmn_block_airplane":
                        blockAirplane = v;
                        break;
                    case "tmn_block_hotspot":
                        blockHotspot = v;
                        break;
                    case "tmn_block_internet":
                        blockInternet = v;
                        break;
                    case "tmn_block_bluetooth":
                        blockBluetooth = v;
                        break;
                    case "tmn_block_power_controls":
                        blockPowerControls = v;
                        break;
                    case "tmn_show_fake_power_menu":
                        showFakePowerMenu = v;
                        break;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                if (c != null) c.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean blockAirplane() {
        return blockAirplane;
    }

    public static boolean blockHotspot() {
        return blockHotspot;
    }

    public static boolean blockInternet() {
        return blockInternet;
    }

    public static boolean blockBluetooth() {
        return blockBluetooth;
    }

    public static boolean blockFooterPowerMenu() {
        return blockPowerControls;
    }

    public static boolean blockPowerControls() {
        return blockPowerControls;
    }

    public static boolean showFakePowerMenu() {
        return showFakePowerMenu;
    }
}
