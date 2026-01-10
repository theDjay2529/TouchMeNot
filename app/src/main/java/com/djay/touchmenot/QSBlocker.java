package com.djay.touchmenot;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Toast;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class QSBlocker implements IXposedHookLoadPackage {
    private static final String SYSTEMUI = "com.android.systemui";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEMUI.equals(lpparam.packageName)) return;

        Logger.hookSuccess("QSBlocker:init");
        hookQSTileImplClick(lpparam);
        hookInternetDialog(lpparam);
        hookFooterPowerButton(lpparam);
        hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.AirplaneModeTile", "handleClick");
        hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.BluetoothTile", "handleClickWithSatelliteCheck");
        hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.HotspotTile", "handleClick");

        // Additional discovery hooks for LineageOS/other ROM variants (non-breaking)
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.BluetoothTile", "handleClick");
            DiagnosticLogger.log("HOOK", "BluetoothTile#handleClick attempted (LineageOS/AOSP)");
        } catch (Throwable t) {
            DiagnosticLogger.log("HOOK_FAIL", "BluetoothTile#handleClick not found: " + t.getMessage());
        }
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.BluetoothTile", "handleSecondaryClick");
            DiagnosticLogger.log("HOOK", "BluetoothTile#handleSecondaryClick attempted");
        } catch (Throwable t) {
            DiagnosticLogger.log("HOOK_FAIL", "BluetoothTile#handleSecondaryClick not found: " + t.getMessage());
        }
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.WifiTile", "handleClick");
            DiagnosticLogger.log("HOOK", "WifiTile#handleClick attempted (LineageOS)");
        } catch (Throwable t) {
            DiagnosticLogger.log("HOOK_FAIL", "WifiTile#handleClick not found: " + t.getMessage());
        }
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.WifiTile", "handleSecondaryClick");
            DiagnosticLogger.log("HOOK", "WifiTile#handleSecondaryClick attempted");
        } catch (Throwable t) {
            DiagnosticLogger.log("HOOK_FAIL", "WifiTile#handleSecondaryClick not found: " + t.getMessage());
        }
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.CellularTile", "handleClick");
            DiagnosticLogger.log("HOOK", "CellularTile#handleClick attempted (LineageOS Mobile Data)");
        } catch (Throwable t) {
            DiagnosticLogger.log("HOOK_FAIL", "CellularTile#handleClick not found: " + t.getMessage());
        }
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.CellularTile", "handleSecondaryClick");
            DiagnosticLogger.log("HOOK", "CellularTile#handleSecondaryClick attempted");
        } catch (Throwable t) {
            DiagnosticLogger.log("HOOK_FAIL", "CellularTile#handleSecondaryClick not found: " + t.getMessage());
        }

        // Inspect available methods on target tiles to capture exact names in logs
        logClassMethods(lpparam, "com.android.systemui.qs.tiles.BluetoothTile");
        logClassMethods(lpparam, "com.android.systemui.qs.tiles.WifiTile");
        logClassMethods(lpparam, "com.android.systemui.qs.tiles.InternetTile");
        logClassMethods(lpparam, "com.android.systemui.qs.tiles.CellularTile");

        // Log touch interaction entrypoints to help trace which tile view is used
        hookQSTileViewTouchLogging(lpparam);
    }

    private void hookQSTileImplClick(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> qstileImpl = XposedHelpers.findClass("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader);
            for (Method m : qstileImpl.getDeclaredMethods()) {
                if (!"click".equals(m.getName())) continue;
                de.robv.android.xposed.XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object thisObj = param.thisObject;
                            if (thisObj == null) return;
                            Context ctx = getContextFromAny(thisObj);
                            if (ctx == null) return;
                            FeatureFlags.ensureInitialized(ctx);
                            if (!isKeyguardLocked(ctx)) return;
                            String instName = thisObj.getClass().getName();
                            DiagnosticLogger.log("QSTileImpl#click", "invoked by: " + instName);
                            if (instName.contains("InternetTile")) {
                                if (!FeatureFlags.blockInternet()) return;
                                rejectFeedback(ctx);
                                param.setResult(null);
                                Logger.blocked("QSTileImpl#click", "InternetTile_blocked");
                            }
                        } catch (Throwable t) {
                            Logger.error("QSTileImpl#click", t.getMessage());
                        }
                    }
                });
                Logger.hookSuccess("QSTileImpl#click hooked");
                return;
            }
            Logger.hookFail("QSTileImpl#click", "method_not_found");
        } catch (Throwable t) {
            Logger.hookFail("QSTileImpl", t.getMessage());
        }
    }

    private void hookInternetDialog(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClass("com.android.systemui.qs.tiles.dialog.InternetDialogControllerImpl", lpparam.classLoader);
            for (Method m : clazz.getDeclaredMethods()) {
                if (!"onUserClickedInternetDialog".equals(m.getName())) continue;
                de.robv.android.xposed.XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Context ctx = getContextFromAny(param.thisObject);
                            if (ctx != null) {
                                FeatureFlags.ensureInitialized(ctx);
                                if (isKeyguardLocked(ctx)) {
                                    if (!FeatureFlags.blockInternet()) return;
                                    rejectFeedback(ctx);
                                    param.setResult(null);
                                    Logger.blocked("InternetDialogControllerImpl#onUserClickedInternetDialog", "keyguard_locked");
                                }
                            }
                        } catch (Throwable t) {
                            Logger.error("InternetDialogControllerImpl#onUserClickedInternetDialog", t.getMessage());
                        }
                    }
                });
                Logger.hookSuccess("InternetDialogControllerImpl#onUserClickedInternetDialog hooked");
                return;
            }
            Logger.hookFail("InternetDialogControllerImpl#onUserClickedInternetDialog", "method_not_found");
        } catch (Throwable t) {
            Logger.hookFail("InternetDialogControllerImpl", t.getMessage());
        }
    }

    private void hookFooterPowerButton(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClass("com.android.systemui.globalactions.GlobalActionsDialogLite", lpparam.classLoader);
            for (Method m : clazz.getDeclaredMethods()) {
                if (!"showOrHideDialog".equals(m.getName())) continue;
                de.robv.android.xposed.XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Context ctx = getContextFromAny(param.thisObject);
                            if (ctx != null) {
                                FeatureFlags.ensureInitialized(ctx);
                                if (isKeyguardLocked(ctx)) {
                                    if (!FeatureFlags.blockFooterPowerMenu()) return;
                                    rejectFeedback(ctx);
                                    param.setResult(null);
                                    Logger.blocked("GlobalActionsDialogLite#showOrHideDialog", "keyguard_locked");
                                }
                            }
                        } catch (Throwable t) {
                            Logger.error("GlobalActionsDialogLite#showOrHideDialog", t.getMessage());
                        }
                    }
                });
                Logger.hookSuccess("GlobalActionsDialogLite#showOrHideDialog hooked");
                return;
            }
            Logger.hookFail("GlobalActionsDialogLite#showOrHideDialog", "method_not_found");
        } catch (Throwable t) {
            Logger.hookFail("GlobalActionsDialogLite", t.getMessage());
        }
    }

    private void hookSimpleTile(XC_LoadPackage.LoadPackageParam lpparam, String className, String methodName) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            Method target = null;
            for (Method m : clazz.getDeclaredMethods()) {
                if (methodName.equals(m.getName())) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                Logger.hookFail(className + "#" + methodName, "method_not_found");
                return;
            }
            de.robv.android.xposed.XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Context ctx = getContextFromAny(param.thisObject);
                        if (ctx == null) return;
                        FeatureFlags.ensureInitialized(ctx);
                        DiagnosticLogger.log("TileMethod", className + "#" + methodName + " invoked");
                        boolean shouldBlock;
                        if (className.endsWith("AirplaneModeTile")) {
                            shouldBlock = FeatureFlags.blockAirplane();
                        } else if (className.endsWith("BluetoothTile")) {
                            shouldBlock = FeatureFlags.blockBluetooth();
                        } else if (className.endsWith("HotspotTile")) {
                            shouldBlock = FeatureFlags.blockHotspot();
                        } else if (className.endsWith("WifiTile")) {
                            shouldBlock = FeatureFlags.blockInternet();
                        } else if (className.endsWith("CellularTile")) {
                            shouldBlock = FeatureFlags.blockInternet();
                        } else {
                            shouldBlock = true;
                        }
                        if (!shouldBlock) return;
                        if (ctx != null && isKeyguardLocked(ctx)) {
                            rejectFeedback(ctx);
                            param.setResult(null);
                            Logger.blocked(className + "#" + methodName, "keyguard_locked");
                        }
                    } catch (Throwable t) {
                        Logger.error(className + "#" + methodName, t.getMessage());
                    }
                }
            });
            Logger.hookSuccess(className + "#" + methodName + " hooked");
        } catch (Throwable t) {
            Logger.hookFail(className, t.getMessage());
        }
    }

    // Enumerate declared methods for a target class and log them for offline analysis
    private void logClassMethods(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            DiagnosticLogger.log("CLASS", "Inspecting: " + className);
            for (Method m : clazz.getDeclaredMethods()) {
                StringBuilder sb = new StringBuilder();
                sb.append(m.getName()).append("(");
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) {
                    sb.append(ps[i].getSimpleName());
                    if (i < ps.length - 1) sb.append(", ");
                }
                sb.append(")");
                DiagnosticLogger.log("METHOD", sb.toString());
            }
        } catch (Throwable t) {
            DiagnosticLogger.log("CLASS_FAIL", "Cannot inspect: " + className + " -> " + t.getMessage());
        }
    }

    // Log QSTileView touch entrypoints without altering behavior
    private void hookQSTileViewTouchLogging(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> tileView = XposedHelpers.findClass("com.android.systemui.plugins.qs.QSTileView", lpparam.classLoader);
            for (Method m : tileView.getDeclaredMethods()) {
                String name = m.getName();
                if ("onTouchEvent".equals(name) || "onInterceptTouchEvent".equals(name)) {
                    de.robv.android.xposed.XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                DiagnosticLogger.log("QSTileView", name + " called on: " + param.thisObject.getClass().getName());
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            }
            Logger.hookSuccess("QSTileView touch logging hooked");
        } catch (Throwable t) {
            Logger.hookFail("QSTileView touch logging", t.getMessage());
        }
    }

    private void rejectFeedback(Context ctx) {
        try {
            Toast.makeText(ctx, "Unlock to use", Toast.LENGTH_SHORT).show();
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
            Logger.error("rejectFeedback", t.getMessage());
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
