package com.samstenner.instantunlock;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class UnlockManager implements IXposedHookLoadPackage {


        // Controllers for accessing methods
        private Object contMediator;
        private Object contStatusBar;
        private Object contAmbientState;
        private Object contNotifStack;

        // Record of preferences
        private String unlockType;
        private boolean fastSpeed;
        private boolean allowMusic;
        private boolean allowDynamic;
        private boolean allowStatic;
        private boolean revealSensitive;
        private boolean vibration;
        private int vibDuration;
        private int unlockDelay;

        // References to all the classes that are hooked
        private String clsSystemUI = "com.android.systemui";
        private String clsKGMediator = clsSystemUI + ".keyguard.KeyguardViewMediator";
        private String clsKGMonitor = clsSystemUI + ".statusbar.policy.KeyguardMonitor";
        private String clsStatusBar = clsSystemUI + ".statusbar.phone.PhoneStatusBar";
        private String clsAmbientState = clsSystemUI + ".statusbar.stack.AmbientState";
        private String clsNotifStack = clsSystemUI + ".statusbar.stack.NotificationStackScrollLayout";
        private String clsNotifRow = clsSystemUI + ".statusbar.ExpandableNotificationRow";
        private String clsMediaRow = clsSystemUI + ".statusbar.MediaExpandableNotificationRow";

    // To do list:
    // Fix L/M crash

    // Checks every loaded package
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        // Only continue if loaded package in SystemUI
        if (!lpparam.packageName.equals(clsSystemUI)) {
            return;
        }

        // Correction for Oreo classes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            clsKGMonitor = "com.android.systemui.statusbar.policy.KeyguardMonitorImpl";
            clsStatusBar = "com.android.systemui.statusbar.phone.StatusBar";
        }

        // When keyguard mediator loads, assign mediator object
        findAndHookMethod(clsKGMediator, lpparam.classLoader, "setupLocked", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                contMediator = param.thisObject;
            }
        });

        // When keyguard has changed status, attempt to unlock
        findAndHookMethod(clsKGMonitor, lpparam.classLoader, "notifyKeyguardChanged", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                try {
                    // Check if qualified to unlock and read preferences
                    XSharedPreferences prefs = new XSharedPreferences("com.samstenner.instantunlock", "instant_unlock_settings");
                    boolean enabled = prefs.getBoolean("enabled", true);
                    boolean controlled = contMediator != null ? true : false;
                    boolean visible = (boolean) XposedHelpers.callMethod(param.thisObject, "isShowing");
                    boolean unlocked = XposedHelpers.getBooleanField(param.thisObject, "mCanSkipBouncer");
                    // If able to be unlocked
                    if (enabled && unlocked && controlled && visible) {
                        // Retrieve preferences
                        unlockType = prefs.getString("mode", "FORCE");
                        fastSpeed = prefs.getBoolean("fast", true);
                        allowMusic = prefs.getBoolean("music", false);
                        allowDynamic = prefs.getBoolean("dynamic", false);
                        allowStatic = prefs.getBoolean("static", false);
                        revealSensitive = prefs.getBoolean("sensitive", false);
                        vibration = prefs.getBoolean("vibrate", false);
                        vibDuration = prefs.getInt("vib_duration", 120);
                        int[] arrayDelays = new int[] {0, 1, 2, 3, 5, 10};
                        unlockDelay = arrayDelays[prefs.getInt("delay", 0)];
                        // Check the exceptions aren't active
                        boolean canDismiss = canUnlock(unlockType);
                        if (canDismiss) {
                            // Alohomora
                            unlock();
                        }
                    }
                } catch (Throwable t){
                    XposedBridge.log(t);
                }
            }
        });

        // When status bar loads, assign status bar object
        findAndHookMethod(clsStatusBar, lpparam.classLoader,"start", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                if (contStatusBar == null) {
                    contStatusBar = param.thisObject;
                }
            }
        });

        // When first animation has finished, cancel second animation
        findAndHookMethod(clsKGMediator, lpparam.classLoader, "startKeyguardExitAnimation", long.class, long.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // Set duration to 0ms
                param.args[1] = 0;
            }
        });


        // When status bar loads, assign status bar object
        findAndHookMethod(clsAmbientState, lpparam.classLoader, "isHideSensitive", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (contAmbientState == null) {
                    contAmbientState = param.thisObject;
                }
            }
        });

        // When notification stack loads, assign stack object
        findAndHookMethod(clsNotifStack, lpparam.classLoader, "initView", Context.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                contNotifStack = param.thisObject;
            }
        });

    }

    // Determines whether exceptions prevent unlock
    private boolean canUnlock(String unlockType){
        // No exceptions, allow
        if (unlockType.equals("FORCE")) {
            return true;
        }
        // If media currently playing and allowed, disallow dismissal
        if (XposedHelpers.getObjectField(contStatusBar, "mMediaMetadata") != null && allowMusic) {
            return false;
        }
        // Get all notifications as group
        ViewGroup group = (ViewGroup) XposedHelpers.getObjectField(contStatusBar, "mStackScroller");
        // Loop each status bar element
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                // If element is notification
                if (child.getClass().getName().equals(clsNotifRow)) {
                    // If revealing, disallow dismissal
                    if (revealSensitive) {
                        showSensitive(group);
                        return false;
                    }
                    boolean isClearable = (boolean) XposedHelpers.callMethod(child, "isClearable");
                    // Disallow keyguard dismisal if allowing dismissable notifications
                    if (isClearable && allowDynamic) {
                        return false;
                    }
                    // Same but for non-dismissable notifications
                    else if (!isClearable && allowStatic) {
                        return false;
                    }
                // some custom ROMs use media row, check if using, disallow if intended
                } else if (child.getClass().getName().equals(clsMediaRow) && allowMusic) {
                    return false;
                }
            }
        }
        // If status bar 'forever holds its peace', then allow dismissal
        return true;
    }

    // Unlocks the device
    private void unlock() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int buildVersion = Build.VERSION.SDK_INT;
                // If fast enabled, unlock without animation
                if (fastSpeed) {
                    // Changes in Oreo require version selection
                    if (buildVersion >= Build.VERSION_CODES.O) {
                        // Informs mediator that user dismissed using swipe when they actually haven't
                        XposedHelpers.callMethod(contMediator, "handleKeyguardDone");
                    } else {
                        // Same as before, but strong authentication is specified as true
                        XposedHelpers.callMethod(contMediator, "handleKeyguardDone", true);
                    }
                }
                // Otherwise unlock with animation
                else {
                    // For some reason, SDK 24 doesn't need an argument
                    if (buildVersion == Build.VERSION_CODES.N) {
                        XposedHelpers.callMethod(contMediator, "dismiss");
                    } else if (buildVersion >= Build.VERSION_CODES.O) {
                        XposedHelpers.callMethod(contMediator, "dismiss", (Object)null);
                    } else {
                        XposedHelpers.callMethod(contMediator, "dismiss", false);
                    }
                }
                // Vibrates if intended
                if (vibration) {
                    Vibrator vib = (Vibrator) AndroidAppHelper.currentApplication().getSystemService(Context.VIBRATOR_SERVICE);
                    if (vib.hasVibrator()) {
                        vib.vibrate(vibDuration);
                    }
                }
            }
            // If user wanted a delay, perform here
        }, unlockDelay * 1000);
    }

    // Reveals sensitive notification data upon unlock
    private void showSensitive(ViewGroup group){
        // Make sensitivity false
        XposedHelpers.callMethod(contAmbientState, "setHideSensitive", false);
        // Loop each notification
        for (int i = 0; i < group.getChildCount(); i++){
            View child = group.getChildAt(i);
            // Update the sensitivity for the notification
            XposedHelpers.callMethod(contNotifStack, "onViewAddedInternal", child);
            // Refresh the notification status to UI
            XposedHelpers.callMethod(contNotifStack, "requestChildrenUpdate");
        }
    }


}

