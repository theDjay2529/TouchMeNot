package com.djay.touchmenot;

import android.content.Context;
import android.view.View;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class VolumeRingerDiagnostic implements IXposedHookLoadPackage {
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String ANDROID = "android";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEMUI.equals(lpparam.packageName) && !ANDROID.equals(lpparam.packageName)) return;

        Logger.info("VolumeRingerDiagnostic:init for " + lpparam.packageName);

        if (SYSTEMUI.equals(lpparam.packageName)) {
            // Modern Compose implementations
            hookVolumeClass(lpparam, "com.android.systemui.volume.ui.viewmodel.RingerModeViewModel");
            hookVolumeClass(lpparam, "com.android.systemui.volume.ui.viewmodel.VolumePanelViewModel");
            hookVolumeClass(lpparam, "com.android.systemui.volume.ui.viewmodel.VolumePanelViewModelImpl");
            hookVolumeClass(lpparam, "com.android.systemui.volume.domain.interactor.RingerModeInteractor");
            hookVolumeClass(lpparam, "com.android.systemui.volume.ui.binder.RingerModeIconBinder");
            hookVolumeClass(lpparam, "com.android.systemui.volume.VolumePanel");
            hookVolumeClass(lpparam, "com.android.systemui.volume.VolumePanelCompose");
            hookVolumeClass(lpparam, "com.android.systemui.volume.RingerModeIconButton");
            hookVolumeClass(lpparam, "com.android.systemui.volume.RingerModeButton");
            hookVolumeClass(lpparam, "com.android.systemui.volume.VolumeDialogComponent");
            hookVolumeClass(lpparam, "com.android.systemui.volume.VolumeDialogComponentImpl");

            // Legacy View-based implementations
            hookVolumeClass(lpparam, "com.android.systemui.volume.VolumeDialogImpl");
            hookVolumeClass(lpparam, "com.android.systemui.volume.VolumeDialog");
            hookVolumeClass(lpparam, "com.android.systemui.volume.VolumeUI");
            hookVolumeClass(lpparam, "com.android.systemui.volume.VolumeDialogControllerImpl");
            hookVolumeClass(lpparam, "com.android.systemui.volume.VolumeDialogController");

            // Pixel-specific
            hookVolumeClass(lpparam, "com.google.android.systemui.volume.VolumeDialogImpl");
            hookVolumeClass(lpparam, "com.google.android.systemui.volume.PixelVolumeDialogImpl");

            // View.OnClickListener discovery
            hookViewOnClick(lpparam);
        }

        if (ANDROID.equals(lpparam.packageName)) {
            // System-level AudioManager hooks
            hookAudioManager(lpparam);
        }
    }

    private void hookVolumeClass(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            Logger.info("VolumeRingerDiag: Found class " + className);
            
            // Log all methods in this class
            for (Method m : clazz.getDeclaredMethods()) {
                String methodSig = buildMethodSignature(m);
                Logger.info("VolumeRingerDiag: " + className + " has method: " + methodSig);
                
                // Hook methods that might be click handlers
                if (isLikelyClickHandler(m.getName())) {
                    try {
                        de.robv.android.xposed.XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Logger.info("VolumeRingerDiag: *** CALLED *** " + className + "#" + m.getName());
                                for (int i = 0; i < param.args.length; i++) {
                                    Logger.info("VolumeRingerDiag:   arg[" + i + "] = " + param.args[i]);
                                }
                            }
                        });
                        Logger.info("VolumeRingerDiag: Hooked " + className + "#" + m.getName());
                    } catch (Throwable t) {
                        Logger.error("VolumeRingerDiag: Failed to hook " + className + "#" + m.getName(), t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            Logger.info("VolumeRingerDiag: Class not found: " + className);
        }
    }

    private void hookViewOnClick(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> viewClass = XposedHelpers.findClass("android.view.View", lpparam.classLoader);
            Method performClick = viewClass.getDeclaredMethod("performClick");
            
            de.robv.android.xposed.XposedBridge.hookMethod(performClick, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        View view = (View) param.thisObject;
                        String resourceName = "";
                        try {
                            if (view.getId() != View.NO_ID) {
                                resourceName = view.getResources().getResourceEntryName(view.getId());
                            }
                        } catch (Throwable ignored) {}
                        
                        if (resourceName.toLowerCase().contains("ringer") || 
                            resourceName.toLowerCase().contains("volume")) {
                            Logger.info("VolumeRingerDiag: View clicked - ID: " + resourceName + 
                                       ", Class: " + view.getClass().getName());
                        }
                    } catch (Throwable ignored) {}
                }
            });
            Logger.info("VolumeRingerDiag: Hooked View.performClick for resource discovery");
        } catch (Throwable t) {
            Logger.error("VolumeRingerDiag: Failed to hook View.performClick", t.getMessage());
        }
    }

    private void hookAudioManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> audioManager = XposedHelpers.findClass("android.media.AudioManager", lpparam.classLoader);
            
            String[] methods = {"setRingerMode", "setRingerModeInternal", "setRingerModeExternal"};
            for (String methodName : methods) {
                for (Method m : audioManager.getDeclaredMethods()) {
                    if (m.getName().equals(methodName)) {
                        de.robv.android.xposed.XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Logger.info("VolumeRingerDiag: *** AudioManager." + methodName + " called ***");
                                for (int i = 0; i < param.args.length; i++) {
                                    Logger.info("VolumeRingerDiag:   arg[" + i + "] = " + param.args[i]);
                                }
                                
                                // Log stack trace to see caller
                                StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                                Logger.info("VolumeRingerDiag: Stack trace:");
                                for (int i = 0; i < Math.min(10, stack.length); i++) {
                                    Logger.info("VolumeRingerDiag:   " + stack[i].toString());
                                }
                            }
                        });
                        Logger.info("VolumeRingerDiag: Hooked AudioManager." + methodName);
                    }
                }
            }
        } catch (Throwable t) {
            Logger.error("VolumeRingerDiag: Failed to hook AudioManager", t.getMessage());
        }
    }

    private String buildMethodSignature(Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getName()).append("(");
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            sb.append(params[i].getSimpleName());
            if (i < params.length - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    private boolean isLikelyClickHandler(String methodName) {
        String lower = methodName.toLowerCase();
        return lower.contains("click") || 
               lower.contains("ringer") || 
               lower.contains("toggle") ||
               lower.contains("select") ||
               lower.contains("change") ||
               lower.contains("set") && lower.contains("mode") ||
               lower.equals("onclick") ||
               lower.equals("ontoggle");
    }
}
