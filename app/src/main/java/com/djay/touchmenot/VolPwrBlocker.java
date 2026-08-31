package com.djay.touchmenot;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class VolPwrBlocker implements IXposedHookLoadPackage {
    private static final String ANDROID_PKG = "android";

    private static boolean isPowerKeyHeld = false;
    private static boolean isVolumeUpKeyHeld = false;
    private static boolean isVolumeDownKeyHeld = false;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!ANDROID_PKG.equals(lpparam.packageName)) return;
        Logger.hookSuccess("VolPwrBlocker:init");
        hookPowerMenu(lpparam);
        hookKeyCombination(lpparam);
        hookPowerLongPress(lpparam);
    }

    private void hookPowerMenu(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pwm = lpparam.classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            XposedHelpers.findAndHookMethod(pwm, "showGlobalActions", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Context ctx = getContextFromObject(param.thisObject);
                        if (ctx == null) return;
                        FeatureFlags.ensureInitialized(ctx);
                        if (!FeatureFlags.blockPowerControls()) return;
                        KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
                        if (km != null && km.isKeyguardLocked()) {
                            param.setResult(null);
                            // Show fake power menu if enabled, otherwise just block
                            if (FeatureFlags.showFakePowerMenu()) {
                                showFakePowerMenu(ctx);
                                Logger.blocked("PhoneWindowManager#showGlobalActions", "keyguard_locked_fake_menu_shown");
                            } else {
                                Logger.blocked("PhoneWindowManager#showGlobalActions", "keyguard_locked_blocked");
                            }
                        }
                    } catch (Throwable t) {
                        Logger.error("PhoneWindowManager#showGlobalActions", t.getMessage());
                    }
                }
            });
            Logger.hookSuccess("PhoneWindowManager#showGlobalActions hooked");
        } catch (Throwable t) {
            Logger.hookFail("PhoneWindowManager#showGlobalActions", t.getMessage());
        }
    }

    /**
     * Hooks all methods related to power long-press to block vibration in protected mode
     */
    private void hookPowerLongPress(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> pwm;
        try {
            pwm = lpparam.classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
        } catch (Throwable t) {
            Logger.hookFail("hookPowerLongPress", "Class not found: " + t.getMessage());
            return;
        }

        // Hook the long-press / power-press entrypoints that trigger global actions.
        // These are hooked BY NAME (every overload) because their signatures differ
        // across Android versions: e.g. Android 17 uses powerLongPress(long) and
        // powerPress(long,int,int), while older ROMs had powerLongPress() with no args.
        // findAndHookMethod without arg types only matches a zero-arg method, so it
        // silently missed these on modern Android.
        final XC_MethodHook blockInProtected = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (FakePowerMenu.isInProtectedMode()) {
                    param.setResult(null);
                    Logger.blocked("PhoneWindowManager#" + param.method.getName(), "blocked_in_protected_mode");
                }
            }
        };
        String[] longPressMethods = {
            "powerLongPress", "powerPress", "powerVeryLongPress",
            "globalActionsDialogLongPress", "showGlobalActionsInternal"
        };
        for (String methodName : longPressMethods) {
            int n = hookAllByName(pwm, methodName, blockInProtected);
            if (n > 0) Logger.hookSuccess("PhoneWindowManager#" + methodName + " hooked (" + n + ")");
        }

        // Block the power-menu haptic while in protected mode. Android 17 renamed the
        // old performHapticFeedbackLw(...) to performHapticFeedback(int, String), so we
        // hook both names by method name and coerce the return to match its type.
        final XC_MethodHook blockHaptic = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!FakePowerMenu.isInProtectedMode()) return;
                Class<?> ret = ((Method) param.method).getReturnType();
                if (ret == boolean.class || ret == Boolean.class) {
                    param.setResult(false);
                } else {
                    param.setResult(null);
                }
            }
        };
        int haptic = hookAllByName(pwm, "performHapticFeedback", blockHaptic)
                + hookAllByName(pwm, "performHapticFeedbackLw", blockHaptic);
        if (haptic > 0) {
            Logger.hookSuccess("PhoneWindowManager haptic hooked (" + haptic + ")");
        } else {
            Logger.hookFail("performHapticFeedback", "Could not hook - vibration may still occur");
        }

        // Hook the Vibrator service directly to block vibration in protected mode
        String[] vibratorClasses = {
            "com.android.server.VibratorService",
            "com.android.server.vibrator.VibratorManagerService",
        };
        for (String className : vibratorClasses) {
            try {
                Class<?> cls = lpparam.classLoader.loadClass(className);
                for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                    if (m.getName().equals("vibrate")) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (FakePowerMenu.isInProtectedMode()) {
                                    param.setResult(null);
                                    Logger.blocked(className + "#vibrate", "blocked_in_protected_mode");
                                }
                            }
                        });
                        Logger.hookSuccess(className + "#vibrate hooked");
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void hookKeyCombination(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pwm = lpparam.classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            // Android 17 dropped the boolean parameter: interceptKeyBeforeQueueing is now
            // (KeyEvent, int) instead of (KeyEvent, int, boolean). Hook every overload
            // whose first arg is a KeyEvent so the signature change can't unhook us. The
            // body only reads args[0] and the return type stays int, so it is signature-agnostic.
            final XC_MethodHook interceptHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (param.args == null || param.args.length == 0) return;
                        if (!(param.args[0] instanceof KeyEvent)) return;
                        KeyEvent event = (KeyEvent) param.args[0];
                        if (event == null) return;

                        int keyCode = event.getKeyCode();
                        Context ctx = getContextFromObject(param.thisObject);
                        if (ctx != null) FeatureFlags.ensureInitialized(ctx);

                        // Track key states
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            if (keyCode == KeyEvent.KEYCODE_POWER) {
                                isPowerKeyHeld = true;
                            }
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                isVolumeUpKeyHeld = true;
                            }
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                                isVolumeDownKeyHeld = true;
                            }

                            // PWR + VOL_UP: Show fake menu when locked
                            if (isPowerKeyHeld && isVolumeUpKeyHeld && !isVolumeDownKeyHeld) {
                                param.setResult(0);

                                // If BlackPage is active, do nothing (need 10 taps to disable)
                                if (FakePowerMenu.isBlackPageActive()) {
                                    Logger.blocked("Power+VolUp", "blackpage_active_need_10_taps");
                                    return;
                                }

                                if (FeatureFlags.blockPowerControls() && isLocked(param.thisObject)) {
                                    Logger.blocked("Power+VolUp", "combination_blocked_while_locked");
                                    if (ctx != null && FeatureFlags.showFakePowerMenu()) {
                                        showFakePowerMenu(ctx);
                                    }
                                }
                                return;
                            }
                        } else if (event.getAction() == KeyEvent.ACTION_UP) {
                            if (keyCode == KeyEvent.KEYCODE_POWER) {
                                isPowerKeyHeld = false;
                            }
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                isVolumeUpKeyHeld = false;
                            }
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                                isVolumeDownKeyHeld = false;
                            }
                        }

                        // PROTECTED MODE: Block power button COMPLETELY (highest priority)
                        // This blocks ALL power button events, even screen wake
                        if (FakePowerMenu.isInProtectedMode() && keyCode == KeyEvent.KEYCODE_POWER) {
                            param.setResult(0);
                            Logger.blocked("PowerButton", "blocked_in_protected_mode");
                            return;
                        }

                        // Only apply other blocks if feature is enabled
                        if (!FeatureFlags.blockPowerControls()) return;

                        // Normal lockscreen behavior: block combinations
                        if (isLocked(param.thisObject)) {
                            if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP && isPowerKeyHeld) ||
                                (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && isPowerKeyHeld) ||
                                (keyCode == KeyEvent.KEYCODE_POWER && (isVolumeUpKeyHeld || isVolumeDownKeyHeld))) {
                                param.setResult(0);
                                Logger.blocked("Power+Vol", "partial_combination_blocked");
                                return;
                            }
                        }
                    } catch (Throwable t) {
                        Logger.error("interceptKeyBeforeQueueing", t.getMessage());
                    }
                }
            };
            int hooked = 0;
            for (Method m : pwm.getDeclaredMethods()) {
                if (!"interceptKeyBeforeQueueing".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length >= 1 && p[0] == KeyEvent.class) {
                    try {
                        XposedBridge.hookMethod(m, interceptHook);
                        hooked++;
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (hooked > 0) {
                Logger.hookSuccess("PhoneWindowManager#interceptKeyBeforeQueueing hooked (" + hooked + ")");
            } else {
                Logger.hookFail("PhoneWindowManager#interceptKeyBeforeQueueing", "method_not_found");
            }
        } catch (Throwable t) {
            Logger.hookFail("PhoneWindowManager#interceptKeyBeforeQueueing", t.getMessage());
        }
    }

    /**
     * Hooks every declared method with the given name, regardless of signature.
     * Returns how many overloads were successfully hooked.
     */
    private int hookAllByName(Class<?> clazz, String name, XC_MethodHook callback) {
        int count = 0;
        try {
            for (Method m : clazz.getDeclaredMethods()) {
                if (!name.equals(m.getName())) continue;
                try {
                    XposedBridge.hookMethod(m, callback);
                    count++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return count;
    }

    private boolean isLocked(Object obj) {
        try {
            Context ctx = getContextFromObject(obj);
            if (ctx == null) return false;
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            Logger.error("isLocked", t.getMessage());
            return false;
        }
    }

    private Context getContextFromObject(Object obj) {
        if (obj == null) return null;
        try {
            String[] fields = new String[]{"mContext", "context", "mContextImpl"};
            for (String f : fields) {
                try {
                    Field field = obj.getClass().getDeclaredField(f);
                    field.setAccessible(true);
                    Object v = field.get(obj);
                    if (v instanceof Context) return (Context) v;
                } catch (Throwable ignored) {
                }
            }
            try {
                Method gm = obj.getClass().getMethod("getContext");
                Object r = gm.invoke(obj);
                if (r instanceof Context) return (Context) r;
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Logger.error("getContextFromObject", t.getMessage());
        }
        return null;
    }

    private void showFakePowerMenu(Context ctx) {
        if (ctx == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                FakePowerMenu.show(ctx);
            } catch (Throwable t) {
                Logger.error("showFakePowerMenu", t.getMessage());
                // Fallback to original feedback if fake menu fails
                dispatchFeedback(ctx, "Unlock to use");
            }
        });
    }

    private void dispatchFeedback(Context ctx, String message) {
        if (ctx == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                String text = message != null && !message.isEmpty() ? message : "Action blocked — unlock to access";
                Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show();
                Vibrator vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (vib == null) return;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        VibrationEffect first = VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE);
                        VibrationEffect second = VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE);
                        vib.vibrate(first);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                vib.vibrate(second);
                            } catch (Throwable ignored) {
                            }
                        }, 120);
                    } else {
                        long[] pattern = new long[]{0, 45, 120, 45};
                        vib.vibrate(pattern, -1);
                    }
                } catch (Throwable vibErr) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vib.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vib.vibrate(60);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                Logger.error("dispatchFeedback", t.getMessage());
            }
        });
    }
}
