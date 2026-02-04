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
            // QS Panel classes - to intercept tile clicks including DND
            Logger.info("VolumeRingerDiag: ===== STARTING QS PANEL HOOK ATTEMPTS =====");
            hookQSTileClick(lpparam, "com.android.systemui.qs.QSPanel");
            hookQSTileClick(lpparam, "com.android.systemui.qs.QSContainerImpl");
            hookQSTileClick(lpparam, "com.android.systemui.plugins.qs.QSTileView");
            hookQSTileClick(lpparam, "com.android.systemui.qs.tileimpl.QSTileViewImpl");
            hookQSTileClick(lpparam, "com.android.systemui.qs.tileimpl.QSTileImpl");
            
            // DND Tile classes - to find actual method being called
            Logger.info("VolumeRingerDiag: ===== STARTING DND TILE HOOK ATTEMPTS =====");
            hookDndTileClass(lpparam, "com.android.systemui.qs.tiles.DndTile");
            hookDndTileClass(lpparam, "com.android.systemui.qs.tiles.impl.dnd.domain.interactor.DndTileDataInteractor");
            hookDndTileClass(lpparam, "com.android.systemui.qs.tiles.impl.dnd.ui.viewmodel.DndTileViewModel");
            hookDndTileClass(lpparam, "com.android.systemui.qs.tiles.impl.dnd.ui.model.DndTileModel");
            hookDndTileClass(lpparam, "com.google.android.systemui.qs.tiles.DndTile");
            
            // Modern Compose volume implementations
            Logger.info("VolumeRingerDiag: ===== STARTING VOLUME HOOK ATTEMPTS =====");
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
                        
                        // Log volume-related AND DND-related AND menu-related clicks
                        String lowerResourceName = resourceName.toLowerCase();
                        if (lowerResourceName.contains("ringer") || 
                            lowerResourceName.contains("volume") ||
                            lowerResourceName.contains("dnd") ||
                            lowerResourceName.contains("disturb") ||
                            lowerResourceName.contains("menu") ||
                            lowerResourceName.contains("more") ||
                            lowerResourceName.contains("settings") ||
                            lowerResourceName.contains("expand") ||
                            lowerResourceName.contains("overflow") ||
                            lowerResourceName.contains("three_dot") ||
                            lowerResourceName.contains("dots")) {
                            Logger.info("VolumeRingerDiag: *** VIEW CLICKED *** ID: " + resourceName + 
                                       ", Class: " + view.getClass().getName());
                            
                            // Get content description if available
                            CharSequence desc = view.getContentDescription();
                            if (desc != null) {
                                Logger.info("VolumeRingerDiag:   ContentDescription: " + desc.toString());
                            }
                            
                            // Log parent view info
                            try {
                                Object parent = view.getParent();
                                if (parent != null) {
                                    Logger.info("VolumeRingerDiag:   Parent: " + parent.getClass().getName());
                                }
                            } catch (Throwable ignored) {}
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
               lower.contains("expand") ||
               lower.contains("show") ||
               lower.contains("open") ||
               lower.contains("menu") ||
               lower.contains("settings") ||
               lower.equals("onclick") ||
               lower.equals("ontoggle");
    }

    private void hookDndTileClass(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            Logger.info("VolumeRingerDiag: *** FOUND DND CLASS: " + className + " ***");
            
            // Log all methods in this class
            for (Method m : clazz.getDeclaredMethods()) {
                String methodSig = buildMethodSignature(m);
                Logger.info("VolumeRingerDiag: DND_METHOD: " + className + "#" + methodSig);
                
                // Hook ALL methods for DND tile to catch the actual click
                try {
                    de.robv.android.xposed.XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Logger.info("VolumeRingerDiag: *** DND TILE CALLED *** " + className + "#" + m.getName());
                            for (int i = 0; i < param.args.length; i++) {
                                Logger.info("VolumeRingerDiag:   DND arg[" + i + "] = " + param.args[i]);
                            }
                            
                            // Log stack trace to see who called it
                            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                            Logger.info("VolumeRingerDiag: DND Stack trace:");
                            for (int i = 0; i < Math.min(8, stack.length); i++) {
                                Logger.info("VolumeRingerDiag:   " + stack[i].toString());
                            }
                        }
                    });
                    Logger.info("VolumeRingerDiag: DND_HOOKED: " + className + "#" + m.getName());
                } catch (Throwable t) {
                    Logger.error("VolumeRingerDiag: Failed to hook DND " + className + "#" + m.getName(), t.getMessage());
                }
            }
        } catch (Throwable t) {
            Logger.info("VolumeRingerDiag: DND class not found: " + className);
        }
    }

    private void hookQSTileClick(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            Logger.info("VolumeRingerDiag: *** FOUND QS CLASS: " + className + " ***");
            
            // Hook click-related methods in QS classes
            String[] clickMethods = {"onClick", "click", "onTouch", "onTouchEvent", "onInterceptTouchEvent", 
                                     "handleClick", "performClick", "callOnClick"};
            
            for (String methodName : clickMethods) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().equals(methodName)) {
                        try {
                            de.robv.android.xposed.XposedBridge.hookMethod(m, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    Logger.info("VolumeRingerDiag: *** QS_CLICK *** " + className + "#" + methodName);
                                    for (int i = 0; i < param.args.length; i++) {
                                        Logger.info("VolumeRingerDiag:   QS arg[" + i + "] = " + param.args[i]);
                                        // Check if it's a tile and log its spec
                                        if (param.args[i] != null) {
                                            String argClass = param.args[i].getClass().getName();
                                            Logger.info("VolumeRingerDiag:   QS arg[" + i + "] class: " + argClass);
                                            
                                            // Try to get tile spec if available
                                            try {
                                                Object spec = XposedHelpers.callMethod(param.args[i], "getSpec");
                                                Logger.info("VolumeRingerDiag:   QS Tile Spec: " + spec);
                                            } catch (Throwable ignored) {}
                                        }
                                    }
                                    
                                    // Try to get spec from thisObject
                                    try {
                                        Object spec = XposedHelpers.callMethod(param.thisObject, "getSpec");
                                        Logger.info("VolumeRingerDiag:   QS This Tile Spec: " + spec);
                                    } catch (Throwable ignored) {}
                                }
                            });
                            Logger.info("VolumeRingerDiag: QS_HOOKED: " + className + "#" + methodName);
                        } catch (Throwable t) {
                            Logger.error("VolumeRingerDiag: Failed to hook QS " + className + "#" + methodName, t.getMessage());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Logger.info("VolumeRingerDiag: QS class not found: " + className);
        }
    }
}
