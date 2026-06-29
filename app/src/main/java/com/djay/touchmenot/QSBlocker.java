package com.djay.touchmenot;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
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

        // 1. Hook QSTileImpl to intercept clicks on all tiles extending it
        hookQSTileImplClick(lpparam);

        // 2. Hook Internet Dialog controller
        hookInternetDialog(lpparam);

        // 3. Hook Footer Power menu
        hookFooterPowerButton(lpparam);

        // 4. Hook specific tile classes directly to catch overloads or custom behaviors
        hookClickMethods(lpparam, "com.android.systemui.qs.tiles.InternetTile", "internet");
        hookClickMethods(lpparam, "com.android.systemui.qs.tiles.InternetTileNewImpl", "internet");
        hookClickMethods(lpparam, "com.android.systemui.qs.tiles.CellularTile", "internet");
        hookClickMethods(lpparam, "com.android.systemui.qs.tiles.MobileDataTile", "internet");
        hookClickMethods(lpparam, "com.android.systemui.qs.tiles.WifiTile", "internet");
        hookClickMethods(lpparam, "com.android.systemui.qs.tiles.BluetoothTile", "bluetooth");
        hookClickMethods(lpparam, "com.android.systemui.qs.tiles.AirplaneModeTile", "airplane");
        hookClickMethods(lpparam, "com.android.systemui.qs.tiles.HotspotTile", "hotspot");

        // 5. Inspect available methods on target tiles to capture exact names in logs
        logClassMethods(lpparam, "com.android.systemui.qs.tiles.BluetoothTile");
        logClassMethods(lpparam, "com.android.systemui.qs.tiles.WifiTile");
        logClassMethods(lpparam, "com.android.systemui.qs.tiles.InternetTile");
        logClassMethods(lpparam, "com.android.systemui.qs.tiles.CellularTile");
        logClassMethods(lpparam, "com.android.systemui.qs.tiles.MobileDataTile");

        // 6. Log touch interaction entrypoints to help trace which tile view is used
        hookQSTileViewTouchLogging(lpparam);

        // 7. Hook modern QS3 pipeline interactors (Android 17 / modern SystemUI)
        hookUserActionInteractor(lpparam, "com.android.systemui.qs.tiles.impl.cellular.domain.interactor.CellularTileUserActionInteractor", "internet");
        hookUserActionInteractor(lpparam, "com.android.systemui.qs.tiles.impl.internet.domain.interactor.InternetTileUserActionInteractor", "internet");
        hookUserActionInteractor(lpparam, "com.android.systemui.qs.tiles.impl.bluetooth.domain.interactor.BluetoothTileUserActionInteractor", "bluetooth");
        hookUserActionInteractor(lpparam, "com.android.systemui.qs.tiles.impl.airplane.domain.interactor.AirplaneModeTileUserActionInteractor", "airplane");
        hookUserActionInteractor(lpparam, "com.android.systemui.qs.tiles.impl.hotspot.domain.interactor.HotspotTileUserActionInteractor", "hotspot");
    }

    private void hookQSTileImplClick(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> qstileImpl = XposedHelpers.findClass("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader);
            boolean hookedAny = false;
            for (Method m : qstileImpl.getDeclaredMethods()) {
                String name = m.getName();
                if ("click".equals(name) || "handleClick".equals(name) || "handleSecondaryClick".equals(name) || "handleLongClick".equals(name)) {
                    de.robv.android.xposed.XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object thisObj = param.thisObject;
                                if (thisObj == null) return;
                                Context ctx = getContextFromAny(thisObj);
                                if (ctx == null) return;
                                FeatureFlags.ensureInitialized(ctx);
                                
                                String instName = thisObj.getClass().getName();
                                String tileKey = null;
                                if (instName.contains("InternetTile") || instName.contains("CellularTile") || instName.contains("MobileDataTile") || instName.contains("WifiTile")) {
                                    tileKey = "internet";
                                } else if (instName.contains("BluetoothTile")) {
                                    tileKey = "bluetooth";
                                } else if (instName.contains("AirplaneModeTile")) {
                                    tileKey = "airplane";
                                } else if (instName.contains("HotspotTile")) {
                                    tileKey = "hotspot";
                                }
                                
                                if (tileKey == null) return;
                                
                                boolean shouldBlock = false;
                                if ("internet".equals(tileKey)) {
                                    shouldBlock = FeatureFlags.blockInternet();
                                } else if ("bluetooth".equals(tileKey)) {
                                    shouldBlock = FeatureFlags.blockBluetooth();
                                } else if ("airplane".equals(tileKey)) {
                                    shouldBlock = FeatureFlags.blockAirplane();
                                } else if ("hotspot".equals(tileKey)) {
                                    shouldBlock = FeatureFlags.blockHotspot();
                                }
                                
                                if (!shouldBlock) return;
                                if (isKeyguardLocked(ctx)) {
                                    rejectFeedback(ctx);
                                    param.setResult(null);
                                    Logger.blocked("QSTileImpl#" + m.getName() + " (" + instName + ")", "keyguard_locked");
                                }
                            } catch (Throwable t) {
                                Logger.error("QSTileImpl#" + m.getName(), t.getMessage());
                            }
                        }
                    });
                    hookedAny = true;
                    Logger.hookSuccess("QSTileImpl#" + name + " hooked");
                }
            }
            if (!hookedAny) {
                Logger.hookFail("QSTileImpl", "no_methods_found");
            }
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

        hookGlobalActionsClass(lpparam, "com.android.systemui.globalactions.GlobalActionsDialogLite");
        hookGlobalActionsClass(lpparam, "com.android.systemui.globalactions.GlobalActionsDialogLite$ActionsDialogLite");
        tryHookFooterClass(lpparam, "com.android.systemui.globalactions.GlobalActionsDialogLite", globalActionMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.globalactions.GlobalActionsDialogLite$ActionsDialogLite", globalActionMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.globalactions.GlobalActionsDialogLite$GlobalActionsDialogLite", globalActionMethods);
        tryHookFooterClass(lpparam, "com.android.systemui.globalactions.GlobalActionsDialog", globalActionMethods);
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
            logClassMethods(lpparam, className);
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

    private void hookGlobalActionsClass(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            logClassMethods(lpparam, className);
            Method target = null;
            for (Method m : clazz.getDeclaredMethods()) {
                String name = m.getName();
                if ("showOrHideDialog".equals(name) || "showDialog".equals(name) || "toggleDialog".equals(name)) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                for (Method m : clazz.getDeclaredMethods()) {
                    String name = m.getName();
                    if ((name.startsWith("show") || name.startsWith("toggle")) && m.getReturnType() == void.class) {
                        target = m;
                        break;
                    }
                }
            }
            if (target == null) {
                Logger.hookFail(className, "method_not_found");
                return;
            }
            final String methodName = target.getName();
            de.robv.android.xposed.XposedBridge.hookMethod(target, new XC_MethodHook() {
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
                                Logger.blocked(className + "#" + methodName, "keyguard_locked");
                            }
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

    private void hookClickMethods(XC_LoadPackage.LoadPackageParam lpparam, String className, String tileKey) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            boolean hookedAny = false;
            for (Method m : clazz.getDeclaredMethods()) {
                String name = m.getName();
                if ("click".equals(name) || "handleClick".equals(name) || "handleSecondaryClick".equals(name) || "handleLongClick".equals(name) || "handleClickWithSatelliteCheck".equals(name)) {
                    de.robv.android.xposed.XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Context ctx = getContextFromAny(param.thisObject);
                                if (ctx == null) return;
                                FeatureFlags.ensureInitialized(ctx);
                                
                                boolean shouldBlock = false;
                                if ("internet".equals(tileKey)) {
                                    shouldBlock = FeatureFlags.blockInternet();
                                } else if ("bluetooth".equals(tileKey)) {
                                    shouldBlock = FeatureFlags.blockBluetooth();
                                } else if ("airplane".equals(tileKey)) {
                                    shouldBlock = FeatureFlags.blockAirplane();
                                } else if ("hotspot".equals(tileKey)) {
                                    shouldBlock = FeatureFlags.blockHotspot();
                                }
                                
                                if (!shouldBlock) return;
                                if (isKeyguardLocked(ctx)) {
                                    rejectFeedback(ctx);
                                    param.setResult(null);
                                    Logger.blocked(className + "#" + m.getName(), "keyguard_locked");
                                }
                            } catch (Throwable t) {
                                Logger.error(className + "#" + m.getName(), t.getMessage());
                            }
                        }
                    });
                    hookedAny = true;
                    Logger.hookSuccess(className + "#" + name + " hooked");
                }
            }
            if (!hookedAny) {
                Logger.hookFail(className, "no_click_methods_found");
            }
        } catch (Throwable t) {
            Logger.hookFail(className, t.getMessage());
        }
    }

    private void hookUserActionInteractor(XC_LoadPackage.LoadPackageParam lpparam, String className, String tileKey) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            Method target = null;
            for (Method m : clazz.getDeclaredMethods()) {
                if ("handleInput".equals(m.getName())) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                Logger.hookFail(className + "#handleInput", "method_not_found");
                return;
            }
            de.robv.android.xposed.XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Context ctx = getContextFromAny(param.thisObject);
                        if (ctx == null) return;
                        FeatureFlags.ensureInitialized(ctx);
                        DiagnosticLogger.log("UserActionInteractor", className + "#handleInput invoked");

                        boolean shouldBlock = false;
                        if ("internet".equals(tileKey)) {
                            shouldBlock = FeatureFlags.blockInternet();
                        } else if ("bluetooth".equals(tileKey)) {
                            shouldBlock = FeatureFlags.blockBluetooth();
                        } else if ("airplane".equals(tileKey)) {
                            shouldBlock = FeatureFlags.blockAirplane();
                        } else if ("hotspot".equals(tileKey)) {
                            shouldBlock = FeatureFlags.blockHotspot();
                        }

                        if (!shouldBlock) return;
                        if (isKeyguardLocked(ctx)) {
                            rejectFeedback(ctx);
                            param.setResult(null);
                            Logger.blocked(className + "#handleInput", "keyguard_locked");
                        }
                    } catch (Throwable t) {
                        Logger.error(className + "#handleInput", t.getMessage());
                    }
                }
            });
            Logger.hookSuccess(className + "#handleInput hooked");
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
            if (ctx != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        Toast.makeText(ctx, "Unlock to use", Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) {
                    }
                });
            }
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
            if (km == null) return false;
            return km.isKeyguardLocked() || km.isDeviceLocked() || km.inKeyguardRestrictedInputMode();
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
            try {
                Class<?> activityThread = Class.forName("android.app.ActivityThread");
                java.lang.reflect.Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
                currentApplication.setAccessible(true);
                Object app = currentApplication.invoke(null);
                if (app instanceof Context) return (Context) app;
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
