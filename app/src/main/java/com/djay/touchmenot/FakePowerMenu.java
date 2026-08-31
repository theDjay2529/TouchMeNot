package com.djay.touchmenot;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;

/**
 * Displays a fake power menu that looks authentic but does nothing.
 * Designed to confuse attackers while the device remains functional.
 */
public class FakePowerMenu {
    private static final int FAKE_SHUTDOWN_DURATION = 5000; // 4 seconds fake shutdown animation
    private static final int REQUIRED_TAPS = 20; // Number of rapid taps to disable BlackPage
    private static final long MAX_TAP_INTERVAL_MS = 500; // Max time between taps
    private static View overlayView = null;
    private static View menuOverlay = null;
    private static View backgroundOverlay = null;
    private static TextView shutdownTextView = null;
    private static WindowManager windowManager = null;
    private static boolean isInProtectedMode = false;
    private static boolean isBlackPageActive = false;
    private static int tapCount = 0;
    private static long lastTapTime = 0; // Timestamp del último toque
    private static android.app.Dialog inAppMenuDialog = null;
    private static android.app.Dialog inAppBlackDialog = null;
    private static Context appContext = null;
    private static android.content.BroadcastReceiver menuReceiver = null;
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static final long MENU_AUTO_DISMISS_MS = 12000; // Auto-dismiss the fake menu if left untouched
    private static final Runnable autoDismissRunnable = new Runnable() {
        @Override
        public void run() {
            Logger.blocked("FakePowerMenu", "menu_auto_dismiss_timeout");
            dismissMenu();
        }
    };

    /**
     * Shows a fake power menu dialog that mimics the system power menu.
     */
    public static void show(Context context) {
        if (context == null) return;

        try {
            if (windowManager == null) {
                windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            }

            // Remove existing menu if any
            dismissMenu();

            // Create semi-transparent background that dismisses menu on click
            if (!showBackgroundOverlay(context)) {
                showInAppMenu(context);
                return;
            }

            // Always use programmatic menu for consistency on Pixel stock ROMs
            View menuView = createProgrammaticMenu(context);

            // Create window params for showing over lock screen
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    getKeyguardWindowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(menuView, params);
            menuOverlay = menuView;
            registerMenuReceiver(context);
            scheduleMenuAutoDismiss();

            // Fade in animation
            menuView.setAlpha(0f);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(menuView, "alpha", 0f, 1f);
            fadeIn.setDuration(200);
            fadeIn.start();

            Logger.hookSuccess("FakePowerMenu displayed over lockscreen");

        } catch (Throwable t) {
            Logger.error("FakePowerMenu#show", t.getMessage());
            showInAppMenu(context);
        }
    }

    private static boolean showBackgroundOverlay(Context context) {
        try {
            if (backgroundOverlay != null) {
                return true;
            }

            View bg = new View(context);
            bg.setBackgroundColor(0x80000000); // Semi-transparent black
            bg.setOnClickListener(v -> dismissMenu());

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getKeyguardWindowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(bg, params);
            backgroundOverlay = bg;

            // Fade in
            bg.setAlpha(0f);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(bg, "alpha", 0f, 1f);
            fadeIn.setDuration(200);
            fadeIn.start();

            return true;
        } catch (Throwable t) {
            Logger.error("showBackgroundOverlay", t.getMessage());
            return false;
        }
    }

    private static void showInAppMenu(Context context) {
        try {
            if (!(context instanceof android.app.Activity)) {
                return;
            }
            dismissInAppDialogs();

            android.app.Dialog dialog = new android.app.Dialog(context, android.R.style.Theme_Material_Light_Dialog_NoActionBar);
            View menuView = createProgrammaticMenu(context);
            dialog.setContentView(menuView);
            dialog.setCanceledOnTouchOutside(true);
            dialog.setCancelable(true);

            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
                window.setDimAmount(0.6f);
            }

            inAppMenuDialog = dialog;
            dialog.show();
        } catch (Throwable t) {
            Logger.error("showInAppMenu", t.getMessage());
        }
    }

    private static void dismissInAppDialogs() {
        try {
            if (inAppMenuDialog != null && inAppMenuDialog.isShowing()) {
                inAppMenuDialog.dismiss();
            }
            inAppMenuDialog = null;
            if (inAppBlackDialog != null && inAppBlackDialog.isShowing()) {
                inAppBlackDialog.dismiss();
            }
            inAppBlackDialog = null;
        } catch (Throwable t) {
            Logger.error("dismissInAppDialogs", t.getMessage());
        }
    }

    /**
     * Dismisses the fake menu
     */
    private static void dismissMenu() {
        try {
            cancelMenuAutoDismiss();
            unregisterMenuReceiver();
            // NOTE: never gate removal on isAttachedToWindow(). A view added via
            // WindowManager.addView() is registered with WMS synchronously, but
            // isAttachedToWindow() only becomes true after the first traversal
            // (~1 frame later). Removing during that gap is valid; skipping it here
            // is exactly what left orphan lockscreen windows that never closed.
            if (menuOverlay != null && windowManager != null) {
                try {
                    windowManager.removeView(menuOverlay);
                } catch (Throwable ignored) {
                }
            }
            menuOverlay = null;
            if (backgroundOverlay != null && windowManager != null) {
                try {
                    windowManager.removeView(backgroundOverlay);
                } catch (Throwable ignored) {
                }
            }
            backgroundOverlay = null;
        } catch (Throwable t) {
            Logger.error("dismissMenu", t.getMessage());
        }
    }

    /**
     * Schedules a safety timeout so the fake menu can never linger indefinitely
     * (e.g. if the user walks away without touching it).
     */
    private static void scheduleMenuAutoDismiss() {
        try {
            uiHandler.removeCallbacks(autoDismissRunnable);
            uiHandler.postDelayed(autoDismissRunnable, MENU_AUTO_DISMISS_MS);
        } catch (Throwable ignored) {
        }
    }

    private static void cancelMenuAutoDismiss() {
        try {
            uiHandler.removeCallbacks(autoDismissRunnable);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Registers a receiver that tears the fake menu down as soon as the device is
     * unlocked or the screen turns off. Without this, unlocking with a fingerprint
     * while the fake menu is up leaves it floating over the launcher. The receiver
     * is only used for the menu card; the protected-mode black screen is intentionally
     * persistent and is dismissed separately via the 20-tap gesture.
     */
    private static void registerMenuReceiver(Context context) {
        try {
            if (menuReceiver != null) return;
            Context base = context.getApplicationContext();
            if (base == null) base = context;
            appContext = base;
            menuReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context c, android.content.Intent intent) {
                    Logger.blocked("FakePowerMenu", "menu_dismissed_by_" +
                            (intent != null ? intent.getAction() : "null"));
                    dismissMenu();
                }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction(android.content.Intent.ACTION_SCREEN_OFF);
            filter.addAction(android.content.Intent.ACTION_USER_PRESENT);
            base.registerReceiver(menuReceiver, filter);
        } catch (Throwable t) {
            Logger.error("registerMenuReceiver", t.getMessage());
            menuReceiver = null;
        }
    }

    private static void unregisterMenuReceiver() {
        try {
            if (menuReceiver != null && appContext != null) {
                appContext.unregisterReceiver(menuReceiver);
            }
        } catch (Throwable ignored) {
        } finally {
            menuReceiver = null;
        }
    }

    /**
     * Creates menu programmatically if XML loading fails
     */
    private static View createProgrammaticMenu(Context context) {
        Context moduleCtx = getModuleContext(context);

        // Contenedor principal (tarjeta estilo Pixel)
        LinearLayout container = new LinearLayout(moduleCtx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 0, 0, 0);

        int width = dpToPx(moduleCtx, 320);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT);
        container.setLayoutParams(lp);

        // Fondo con esquinas redondeadas estilo Pixel
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xF2171717); // negro Pixel-ish
        bg.setCornerRadius(dpToPx(moduleCtx, 28));
        container.setBackground(bg);
        container.setClipToOutline(true);

//        // HEADER
//        LinearLayout header = new LinearLayout(moduleCtx);
//        header.setOrientation(LinearLayout.VERTICAL);
//        header.setPadding(dpToPx(moduleCtx, 24), dpToPx(moduleCtx, 20),
//                dpToPx(moduleCtx, 24), dpToPx(moduleCtx, 12));
//
//        TextView title = new TextView(moduleCtx);
//        title.setText(moduleCtx.getString(R.string.fake_power_menu_title));
//        title.setTextColor(0xFFFFFFFF);
//        title.setTextSize(17);
//        title.setTypeface(null, Typeface.BOLD);
//        header.addView(title);
//
//        TextView subtitle = new TextView(moduleCtx);
//        subtitle.setText(moduleCtx.getString(R.string.fake_power_menu_subtitle));
//        subtitle.setTextColor(0xB3FFFFFF);
//        subtitle.setTextSize(13);
//        subtitle.setPadding(0, dpToPx(moduleCtx, 2), 0, 0);
//        header.addView(subtitle);
//
//        container.addView(header);

        // Espaciado estilo Pixel (NO divisores)
        container.addView(createSpacer(moduleCtx, 8));

        // Items
        container.addView(createMenuItem(moduleCtx,
                android.R.drawable.ic_lock_power_off,
                moduleCtx.getString(R.string.fake_power_menu_power_off),
                0xFFFFFFFF,
                v -> {
                    dismissMenu();
                    simulateShutdown(context);
                }
        ));

        container.addView(createMenuItem(moduleCtx,
                android.R.drawable.ic_popup_sync,
                moduleCtx.getString(R.string.fake_power_menu_restart),
                0xFFFFFFFF,
                v -> {
                    dismissMenu();
                    simulateRestart(context);
                }
        ));

        container.addView(createMenuItem(moduleCtx,
                android.R.drawable.ic_menu_call,
                moduleCtx.getString(R.string.fake_power_menu_emergency),
                0xFFFF6B6B,
                v -> {
                    dismissMenu();
                    Toast.makeText(context,
                            moduleCtx.getString(R.string.fake_power_menu_emergency_toast),
                            Toast.LENGTH_SHORT).show();
                }
        ));

        container.addView(createSpacer(moduleCtx, 12));

        return container;
    }

    /**
     * Creates a divider line
     */
    private static View createDivider(Context context, boolean indented) {
        View divider = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(context, 1)
        );
        if (indented) {
            params.setMargins(dpToPx(context, 16), 0, dpToPx(context, 16), 0);
        }
        divider.setLayoutParams(params);
        divider.setBackgroundColor(0x22FFFFFF);
        return divider;
    }

    private static View createSpacer(Context context, int dp){
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(context,dp)
        ));
        return v;
    }

    /**
     * Creates a single menu item programmatically
     */
    private static LinearLayout createMenuItem(
            Context context,
            int iconRes,
            String text,
            int textColor,
            View.OnClickListener listener) {

        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(context, 60)
                );
        item.setLayoutParams(params);

        int paddingH = dpToPx(context, 24);
        item.setPadding(paddingH, 0, paddingH, 0);

        item.setClickable(true);
        item.setFocusable(true);

        // Ripple estilo Pixel
        android.util.TypedValue outValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground,
                outValue,
                true
        );
        item.setBackgroundResource(outValue.resourceId);

        // ICONO (más grande)
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(textColor);

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dpToPx(context, 30), dpToPx(context, 30));
        iconParams.rightMargin = dpToPx(context, 24);
        icon.setLayoutParams(iconParams);

        item.addView(icon);

        // TEXTO (más grande)
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextColor(textColor);
        textView.setTextSize(18);
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        item.addView(textView);

        item.setOnClickListener(listener);

        return item;
    }

    private static Context getModuleContext(Context context) {
        try {
            return context.createPackageContext("com.djay.touchmenot", Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable t) {
            Logger.error("getModuleContext", t.getMessage());
            return context;
        }
    }

    /**
     * Simulates a fake shutdown animation with realistic effects
     */
    private static void simulateShutdown(Context context) {
        Logger.blocked("FakePowerMenu", "fake_shutdown_initiated");
        try {
            // Show the black overlay FIRST. Only commit to protected mode (and the
            // irreversible silent/connectivity changes) if the overlay actually
            // attached — otherwise the user would be locked into protected mode with
            // no black screen to tap, and the only exit (20 taps) would be gone.
            Context moduleCtx = getModuleContext(context);
            boolean shown = showShutdownAnimation(context, moduleCtx.getString(R.string.fake_shutdown_text));
            if (!shown) {
                Logger.error("simulateShutdown", "overlay_failed_aborting_protected_mode");
                return;
            }

            // Activate protected mode
            isInProtectedMode = true;
            isBlackPageActive = true;
            Logger.hookSuccess("Protected mode ACTIVATED - Power button disabled");

            // Vibrate like real shutdown
            vibrateShutdown(context);

            // Set to silent mode PERMANENTLY
            setToSilentModePermanent(context);

            // Enable WiFi, Mobile Data and Bluetooth for tracking
            enableConnectivity(context);

            // After animation time, remove only the TEXT (keep black screen)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                hideTextKeepBlackScreen(); // Only hide the text, keep black screen forever
            }, FAKE_SHUTDOWN_DURATION);

        } catch (Throwable t) {
            Logger.error("simulateShutdown", t.getMessage());
        }
    }

    /**
     * Simulates a fake restart animation
     */
    private static void simulateRestart(Context context) {
        Logger.blocked("FakePowerMenu", "fake_restart_initiated");
        try {
            // Show restart animation with text "Reiniciando..." FIRST, then gate
            // protected mode on it (see simulateShutdown for the rationale).
            Context moduleCtx = getModuleContext(context);
            boolean shown = showShutdownAnimation(context, moduleCtx.getString(R.string.fake_restart_text));
            if (!shown) {
                Logger.error("simulateRestart", "overlay_failed_aborting_protected_mode");
                return;
            }

            // Activate protected mode
            isInProtectedMode = true;
            isBlackPageActive = true;
            Logger.hookSuccess("Protected mode ACTIVATED - Power button disabled");

            // Vibrate like real restart
            vibrateShutdown(context);

            // Set to silent mode PERMANENTLY
            setToSilentModePermanent(context);

            // Enable WiFi, Mobile Data and Bluetooth for tracking
            enableConnectivity(context);

            // After animation time, remove only the TEXT (keep black screen)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                hideTextKeepBlackScreen(); // Only hide the text, keep black screen forever
                Logger.hookSuccess("BlackPage ACTIVATED - Only PWR+VOL+VOL- can disable");
            }, FAKE_SHUTDOWN_DURATION);

        } catch (Throwable t) {
            Logger.error("simulateRestart", t.getMessage());
        }
    }

    /**
     * Vibrates with a realistic shutdown pattern
     */
    private static void vibrateShutdown(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                // Long vibration like real shutdown
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    VibrationEffect effect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE);
                    vibrator.vibrate(effect);
                } else {
                    vibrator.vibrate(500);
                }
            }
        } catch (Throwable t) {
            Logger.error("vibrateShutdown", t.getMessage());
        }
    }

    /**
     * Sets device to silent mode PERMANENTLY (does not restore)
     */
    private static void setToSilentModePermanent(Context context) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                // Set to silent WITHOUT saving previous mode
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                Logger.hookSuccess("Device set to PERMANENT silent mode");
            }
        } catch (Throwable t) {
            Logger.error("setToSilentModePermanent", t.getMessage());
        }
    }

    /**
     * Enables WiFi, Mobile Data and Bluetooth if they are off.
     * This ensures the device stays trackable via Find My Device.
     */
    private static void enableConnectivity(Context context) {
        enableWifi(context);
        enableMobileData(context);
        enableBluetooth();
    }

    /**
     * Enables WiFi if it's off
     */
    private static void enableWifi(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && !wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
                Logger.hookSuccess("WiFi ENABLED for tracking");
            }
        } catch (Throwable t) {
            Logger.error("enableWifi", t.getMessage());
        }
    }

    /**
     * Enables Mobile Data if it's off using TelephonyManager reflection
     */
    private static void enableMobileData(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                // Try setDataEnabled (API 26+)
                try {
                    Method setDataEnabled = tm.getClass().getDeclaredMethod("setDataEnabled", boolean.class);
                    setDataEnabled.setAccessible(true);
                    setDataEnabled.invoke(tm, true);
                    Logger.hookSuccess("Mobile Data ENABLED via TelephonyManager");
                    return;
                } catch (Throwable ignored) {
                }

                // Fallback: try via ConnectivityManager
                try {
                    android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                            context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (cm != null) {
                        Method setMobileDataEnabled = cm.getClass().getDeclaredMethod("setMobileDataEnabled", boolean.class);
                        setMobileDataEnabled.setAccessible(true);
                        setMobileDataEnabled.invoke(cm, true);
                        Logger.hookSuccess("Mobile Data ENABLED via ConnectivityManager");
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Logger.error("enableMobileData", t.getMessage());
        }
    }

    /**
     * Enables Bluetooth if it's off
     */
    @android.annotation.SuppressLint("MissingPermission")
    private static void enableBluetooth() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
                Logger.hookSuccess("Bluetooth ENABLED for tracking");
            }
        } catch (Throwable t) {
            Logger.error("enableBluetooth", t.getMessage());
        }
    }

    /**
     * Checks if device is in protected mode
     */
    public static boolean isInProtectedMode() {
        return isInProtectedMode;
    }

    /**
     * Checks if BlackPage is active
     */
    public static boolean isBlackPageActive() {
        return isBlackPageActive;
    }

    /**
     * Deactivates protected mode and removes BlackPage (called by 20 taps)
     */
    public static void deactivateProtectedMode() {
        isInProtectedMode = false;
        isBlackPageActive = false;
        tapCount = 0; // Reset tap counter
        removeBlackScreen(); // Remove the entire black screen overlay
        Logger.hookSuccess("Protected mode DEACTIVATED - Power button enabled, BlackPage removed");
    }

    /**
     * Test-only: shows the shutdown animation without activating protected mode,
     * silent mode, or connectivity changes. Call deactivateProtectedMode() first
     * if a previous overlay is still showing.
     */
    public static void testShutdownAnimation(Context context) {
        if (context == null) return;
        if (context instanceof android.app.Activity) {
            testShutdownAnimationInApp(context);
            return;
        }
        try {
            if (windowManager == null) {
                windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            }
            isBlackPageActive = true;
            Context moduleCtx = getModuleContext(context);
            showShutdownAnimation(context, moduleCtx.getString(R.string.fake_shutdown_text));
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                hideTextKeepBlackScreen();
            }, FAKE_SHUTDOWN_DURATION);
        } catch (Throwable t) {
            Logger.error("testShutdownAnimation", t.getMessage());
        }
    }

    public static void testShutdownAnimationInApp(Context context) {
        try {
            if (!(context instanceof android.app.Activity)) return;
            dismissInAppDialogs();

            android.app.Dialog dialog = new android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            android.widget.FrameLayout root = new android.widget.FrameLayout(context);
            root.setBackgroundColor(Color.BLACK);
            root.setClickable(true);
            root.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (tapCount > 0 && (now - lastTapTime) > MAX_TAP_INTERVAL_MS) {
                    tapCount = 0;
                }
                lastTapTime = now;
                tapCount++;
                if (tapCount >= REQUIRED_TAPS) {
                    deactivateProtectedMode();
                    dismissInAppDialogs();
                    Context moduleCtx = getModuleContext(context);
                    Toast.makeText(moduleCtx,
                            moduleCtx.getString(R.string.fake_protected_mode_off_toast),
                            Toast.LENGTH_SHORT).show();
                }
            });

            TextView textView = new TextView(context);
            textView.setText(getModuleContext(context).getString(R.string.fake_shutdown_text));
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(20);
            textView.setGravity(Gravity.CENTER);

            android.widget.FrameLayout.LayoutParams textParams =
                    new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER
                    );
            root.addView(textView, textParams);

            dialog.setContentView(root);
            inAppBlackDialog = dialog;
            dialog.show();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                hideTextKeepBlackScreen();
                textView.setVisibility(View.GONE);
            }, FAKE_SHUTDOWN_DURATION);
        } catch (Throwable t) {
            Logger.error("testShutdownAnimationInApp", t.getMessage());
        }
    }

    /**
     * Shows shutdown animation with text
     */
    private static boolean showShutdownAnimation(Context context, String message) {
        try {
            // Remove existing overlay if any
            removeBlackScreen();

            // Reset tap counter
            tapCount = 0;

            // Root layout
            android.widget.FrameLayout root = new android.widget.FrameLayout(context);
            root.setBackgroundColor(Color.BLACK);

            // Tap listener on root to count rapid taps
            root.setClickable(true);
            root.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (tapCount > 0 && (now - lastTapTime) > MAX_TAP_INTERVAL_MS) {
                    tapCount = 0;
                }
                lastTapTime = now;
                tapCount++;
                if (tapCount >= REQUIRED_TAPS) {
                    deactivateProtectedMode();
                    Context moduleCtx = getModuleContext(context);
                    Toast.makeText(moduleCtx,
                            moduleCtx.getString(R.string.fake_protected_mode_off_toast),
                            Toast.LENGTH_SHORT).show();
                }
            });

            // TextView centrado REAL
            TextView textView = new TextView(context);
            textView.setText(message);
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(20);
            textView.setGravity(Gravity.CENTER);
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setIncludeFontPadding(false);

            android.widget.FrameLayout.LayoutParams textParams =
                    new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER
                    );

            textView.setLayoutParams(textParams);
            root.addView(textView);

            shutdownTextView = textView;

            // Window params (SIN hacks de tamaños gigantes ni offsets negativos)
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.OPAQUE
            );

            params.gravity = Gravity.TOP | Gravity.START;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

            windowManager.addView(root, params);
            overlayView = root;

            // Fade in animation
            root.setAlpha(0f);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(root, "alpha", 0f, 1f);
            fadeIn.setDuration(500);
            fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());
            fadeIn.start();

            return true;
        } catch (Throwable t) {
            Logger.error("showShutdownAnimation", t.getMessage());
            return false;
        }
    }

    /**
     * Hides only the text "Apagando..." but keeps the black screen forever
     */
    private static void hideTextKeepBlackScreen() {
        try {
            if (shutdownTextView != null) {
                // Fade out only the text with animation
                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(shutdownTextView, "alpha", 1f, 0f);
                fadeOut.setDuration(1000);
                fadeOut.setInterpolator(new AccelerateDecelerateInterpolator());
                fadeOut.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        try {
                            if (shutdownTextView != null) {
                                shutdownTextView.setVisibility(View.GONE);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
                fadeOut.start();
                Logger.hookSuccess("Text hidden, black screen remains FOREVER");
            }
            // overlayView (black screen) stays forever - NOT removed
        } catch (Throwable t) {
            Logger.error("hideTextKeepBlackScreen", t.getMessage());
        }
    }

    /**
     * Removes the BlackPage completely (called only by 10 taps)
     */
    private static void removeBlackScreen() {
        try {

            if (overlayView != null && windowManager != null) {
                // Fade out animation
                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(overlayView, "alpha", 1f, 0f);
                fadeOut.setDuration(500);
                fadeOut.setInterpolator(new AccelerateDecelerateInterpolator());

                final View viewToRemove = overlayView;
                fadeOut.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        try {
                            if (windowManager != null) {
                                windowManager.removeView(viewToRemove);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
                fadeOut.start();

                overlayView = null;
                shutdownTextView = null;
            }
        } catch (Throwable t) {
            Logger.error("removeBlackScreen", t.getMessage());
        }
    }

    /**
     * Gets appropriate window type for showing over lock screen
     */
    private static int getKeyguardWindowType() {
        // TYPE_KEYGUARD_DIALOG works on lock screen across all Android versions
        // This is the same type used by system dialogs on the lock screen
        return WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;
    }

    /**
     * Converts dp to pixels
     */
    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
