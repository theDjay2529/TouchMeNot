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
        } catch (Throwable ignored) {}
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.BluetoothTile", "handleSecondaryClick");
        } catch (Throwable ignored) {}
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.WifiTile", "handleClick");
        } catch (Throwable ignored) {}
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.WifiTile", "handleSecondaryClick");
        } catch (Throwable ignored) {}
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.CellularTile", "handleClick");
        } catch (Throwable ignored) {}
        try {
            hookSimpleTile(lpparam, "com.android.systemui.qs.tiles.CellularTile", "handleSecondaryClick");
        } catch (Throwable ignored) {}
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
        String[] globalActionMethods = new String[]{
            "showOrHideDialog",
            "showDialog",
            "show",
            "showGlobalActions",
            "showGlobalActionsDialog",
            "openGlobalActions",
            "openPowerMenu",
            "showPowerMenu"
        };

        String[] footerMethods = new String[]{
            "onPowerMenuClicked",
            "onPowerMenuButtonClicked",
            "onPowerButtonClicked",
            "onPowerMenuClick",
            "onPowerClick",
            "showPowerMenu",
            "showGlobalActions",
            "openPowerMenu"
        };

        tryHookFooterClass(lpparam, "com.android.systemui.globalactions.GlobalActionsDialogLite", globalActionMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.globalactions.GlobalActionsDialog", globalActionMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.globalactions.GlobalActionsDialogLite$GlobalActionsDialogLite", globalActionMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.statusbar.phone.PhoneStatusBar", globalActionMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.qs.footer.ui.viewmodel.FooterActionsViewModel", footerMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.qs.footer.ui.viewmodel.FooterActionsViewModelImpl", footerMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.qs.footer.domain.interactor.FooterActionsInteractor", footerMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.qs.footer.domain.interactor.FooterActionsInteractorImpl", footerMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.qs.footer.ui.binder.FooterActionsViewBinder", footerMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.qs.footer.ui.binder.FooterActionsViewBinder$Companion", footerMethods);
    }

    private void tryHookFooterClass(XC_LoadPackage.LoadPackageParam lpparam, String className, String[] methodNames) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            boolean hookedAny = false;
            for (Method m : clazz.getDeclaredMethods()) {
                for (String target : methodNames) {
                    if (!target.equals(m.getName())) continue;
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
                                        Logger.blocked(className + "#" + m.getName(), "keyguard_locked");
                                    }
                                }
                            } catch (Throwable t) {
                                Logger.error(className + "#" + m.getName(), t.getMessage());
                            }
                        }
                    });
                    hookedAny = true;
                    Logger.hookSuccess(className + "#" + m.getName() + " hooked");
                }
            }
            if (!hookedAny) {
                Logger.hookFail(className, "no_target_methods");
            }
        } catch (Throwable t) {
            Logger.hookFail(className, t.getMessage());
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
