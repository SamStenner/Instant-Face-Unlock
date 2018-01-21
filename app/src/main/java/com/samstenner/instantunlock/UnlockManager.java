package com.samstenner.instantunlock;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.security.spec.ECField;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class UnlockManager implements IXposedHookLoadPackage {

    private static Object controller;
    private static Object statusBar;
    private static String unlockType;
    private static boolean allowMusic;
    private static boolean allowDynamic;
    private static boolean allowStatic;
    private static boolean vibration;
    private static int vibDuration;

    private String clsSystemUI = "com.android.systemui";
    private String clsKGMediator = "com.android.systemui.keyguard.KeyguardViewMediator";
    private String clsKGMonitor = "com.android.systemui.statusbar.policy.KeyguardMonitor";
    private String clsStatusBar = "com.android.systemui.statusbar.phone.PhoneStatusBar";
    private static String clsNotifRow = "com.android.systemui.statusbar.ExpandableNotificationRow";
    private static String clsMediaRow = "com.android.systemui.statusbar.MediaExpandableNotificationRow";

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        if (!lpparam.packageName.equals(clsSystemUI))
            return;

        if (Build.VERSION.SDK_INT >= 26) {
            clsKGMonitor = "com.android.systemui.statusbar.policy.KeyguardMonitorImpl";
            clsStatusBar = "com.android.systemui.statusbar.phone.StatusBar";
        }

        // region Mediator
        findAndHookMethod(clsKGMediator, lpparam.classLoader, "setupLocked", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                controller = param.thisObject;
            }
        });
        // endregion


        // region Monitor
        findAndHookMethod(clsKGMonitor, lpparam.classLoader, "notifyKeyguardChanged", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                XSharedPreferences prefs = new XSharedPreferences("com.samstenner.instantunlock", "instant_unlock_settings");
                try {
                    boolean controlled = controller != null ? true : false;
                    boolean enabled = prefs.getBoolean("enabled", true);
                    boolean visible = (boolean) XposedHelpers.callMethod(param.thisObject, "isShowing");
                    boolean unlocked = XposedHelpers.getBooleanField(param.thisObject, "mCanSkipBouncer");
                    if (enabled && unlocked && controlled && visible) {
                        unlockType = prefs.getString("mode", "FORCE");
                        allowMusic = prefs.getBoolean("music", false);
                        allowDynamic = prefs.getBoolean("dynamic", false);
                        allowStatic = prefs.getBoolean("static", false);
                        vibration = prefs.getBoolean("vibrate", false);
                        vibDuration = prefs.getInt("vib_duration", 120);
                        boolean canDismiss = canUnlock(unlockType);
                        int[] arrayDelays = new int[] {0, 1, 2, 3, 5, 10};
                        int delay = arrayDelays[prefs.getInt("delay", 0)];
                        if (canDismiss) {
                            unlock(delay);
                        }
                    }
                } catch (Throwable t){
                    XposedBridge.log(t);
                }
            }
        });
        // endregion

        // region Media
        XposedHelpers.findAndHookMethod(clsStatusBar, lpparam.classLoader,
                "start", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        if (statusBar == null) {
                            statusBar = param.thisObject;
                        }
                    }
                });
        // endregion

    }



    private static boolean canUnlock(String unlockType){
        if (unlockType.equals("FORCE"))
            return true;
        ViewGroup group = (ViewGroup) XposedHelpers.getObjectField(statusBar, "mStackScroller");
        int children = group.getChildCount();
        for (int i = 0; i < children; i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                if (child.getClass().getName().equals(clsNotifRow)) {
                    boolean isClearable = (boolean) XposedHelpers.callMethod(child, "isClearable");
                    if (isClearable && allowDynamic) {
                        return false;
                    } else if (!isClearable && allowStatic) {
                        return false;
                    }
                } else if (child.getClass().getName().equals(clsMediaRow) &&
                        allowMusic) {
                    return false;
                }
            }
        }
        return true;
    }

    private void unlock(int seconds) {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int buildVersion = Build.VERSION.SDK_INT;
                XposedBridge.log(Integer.toString(buildVersion));
                if (buildVersion == 24) {
                    XposedHelpers.callMethod(controller, "dismiss");
                } else if (buildVersion >= 26) {
                    XposedHelpers.callMethod(controller, "dismiss", (Object)null);
                    // Alternative:
                    // XposedHelpers.callMethod(statusBar, "executeRunnableDismissingKeyguard", true, false, true);
                } else {
                    XposedHelpers.callMethod(controller, "dismiss", false);
                }
                if (vibration) {
                    Vibrator vib = (Vibrator) AndroidAppHelper.currentApplication().getSystemService(Context.VIBRATOR_SERVICE);
                    if (vib.hasVibrator()) {
                        vib.vibrate(vibDuration);
                    }
                }
            }
        }, seconds * 1000);
    }

}

