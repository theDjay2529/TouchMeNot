package com.djay.touchmenot;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class VolumeRingerBlocker implements IXposedHookLoadPackage {
    private static final String SYSTEMUI = "com.android.systemui";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEMUI.equals(lpparam.packageName)) return;

        Logger.hookSuccess("VolumeRingerBlocker:init");
        hookVolumeDialogControllerImpl(lpparam);
    }

    private void hookVolumeDialogControllerImpl(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClass("com.android.systemui.volume.VolumeDialogControllerImpl", lpparam.classLoader);
            
            // Hook setRingerMode method
            for (Method m : clazz.getDeclaredMethods()) {
                if (!"setRingerMode".equals(m.getName())) continue;
                
                de.robv.android.xposed.XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Context ctx = getContextFromAny(param.thisObject);
                            if (ctx == null) return;
                            
                            FeatureFlags.ensureInitialized(ctx);
                            if (!FeatureFlags.blockVolumeRingerMode()) return;
                            
                            if (isKeyguardLocked(ctx)) {
                                rejectFeedback(ctx);
                                param.setResult(null);
                                Logger.blocked("VolumeDialogControllerImpl#setRingerMode", "keyguard_locked");
                            }
                        } catch (Throwable t) {
                            Logger.error("VolumeDialogControllerImpl#setRingerMode", t.getMessage());
                        }
                    }
                });
                Logger.hookSuccess("VolumeDialogControllerImpl#setRingerMode hooked");
                return;
            }
            Logger.hookFail("VolumeDialogControllerImpl#setRingerMode", "method_not_found");
        } catch (Throwable t) {
            Logger.hookFail("VolumeDialogControllerImpl", t.getMessage());
        }
    }

    private void rejectFeedback(Context ctx) {
        try {
            Toast.makeText(ctx, "Unlock to change", Toast.LENGTH_SHORT).show();
            Vibrator vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null) {
                long[] pattern = new long[]{0, 40, 50, 40};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vib.vibrate(pattern, -1);
                }
            }
        } catch (Throwable t) {
            Logger.error("VolumeRingerBlocker:rejectFeedback", t.getMessage());
        }
    }

    private boolean isKeyguardLocked(Context ctx) {
        try {
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    private Context getContextFromAny(Object obj) {
        try {
            if (obj == null) return null;
            Object c1 = null;
            try {
                c1 = XposedHelpers.getObjectField(obj, "mContext");
            } catch (Throwable ignored) {
            }
            if (c1 instanceof Context) return (Context) c1;
            try {
                Object c2 = XposedHelpers.callMethod(obj, "getContext");
                if (c2 instanceof Context) return (Context) c2;
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
