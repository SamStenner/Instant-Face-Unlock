package com.samstenner.instantunlock;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.service.notification.StatusBarNotification;
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
import static de.robv.android.xposed.XposedHelpers.getClassesAsArray;
import static de.robv.android.xposed.XposedHelpers.getMD5Sum;

public class UnlockManager implements IXposedHookLoadPackage {


    // Controllers for accessing methods
    private static Object contMediator;
    private static Object contStatusBar;
    private static Object contAmbientState;
    private static Object contNotifStack;

    // Record of preferences
    private static String unlockType;
    private static boolean allowMusic;
    private static boolean allowDynamic;
    private static boolean allowStatic;
    private static boolean revealSensitive;
    private static boolean vibration;
    private static int vibDuration;

    // References to all the classes that are hooked
    private String clsSystemUI = "com.android.systemui";
    private String clsKGMediator = "com.android.systemui.keyguard.KeyguardViewMediator";
    private String clsKGMonitor = "com.android.systemui.statusbar.policy.KeyguardMonitor";
    private String clsStatusBar = "com.android.systemui.statusbar.phone.PhoneStatusBar";
    private static String clsAmbientState = "com.android.systemui.statusbar.stack.AmbientState";
    private static String clsNotifRow = "com.android.systemui.statusbar.ExpandableNotificationRow";
    private static String clsMediaRow = "com.android.systemui.statusbar.MediaExpandableNotificationRow";
    private static String clsNotifStack = "com.android.systemui.statusbar.stack.AmbientState.NotificationStackScrollLayout";

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable { // Check every loaded package

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
                        allowMusic = prefs.getBoolean("music", false);
                        allowDynamic = prefs.getBoolean("dynamic", false);
                        allowStatic = prefs.getBoolean("static", false);
                        revealSensitive = prefs.getBoolean("sensitive", false);
                        vibration = prefs.getBoolean("vibrate", false);
                        vibDuration = prefs.getInt("vib_duration", 120);
                        int[] arrayDelays = new int[] {0, 1, 2, 3, 5, 10};
                        int delay = arrayDelays[prefs.getInt("delay", 0)];
                        // Check the exceptions aren't active
                        boolean canDismiss = canUnlock(unlockType);
                        if (canDismiss) {
                            // Alohomora
                            unlock(delay);
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
                contAmbientState = param.thisObject;
            }
        });

        // When notifaction stack, assign stack object
        findAndHookMethod(clsNotifStack, lpparam.classLoader, "initView", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                contNotifStack = param.thisObject;
                // TEMP
                contAmbientState = XposedHelpers.getObjectField(contNotifStack, "mAmbientState");
            }
        });

    }

    // Determines whether exceptions prevent unlock
    private static boolean canUnlock(String unlockType){
        // No exceptions, allow
        if (unlockType.equals("FORCE")) {
            return true;
        }
        // If media is currently playing and allowed, disallow dismissal
        if (XposedHelpers.getObjectField(contStatusBar, "mMediaMetadata") != null && allowMusic) {
            return false;
        }
        // Get all notifications as group
        ViewGroup group = (ViewGroup) XposedHelpers.getObjectField(contStatusBar, "mStackScroller");
        // If revealing, disallow dismissal
        if (group.getChildCount() > 0 && revealSensitive) {
            showSensitive(group);
            return false;
        }
        // Loop each status bar element
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                // If element is notification
                if (child.getClass().getName().equals(clsNotifRow)) {
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
    private void unlock(int seconds) {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int buildVersion = Build.VERSION.SDK_INT;
                // Changes in Oreo require version selection
                if (buildVersion >= Build.VERSION_CODES.O) {
                    // Informs mediator that user dismissed using swipe when they actually haven't
                    XposedHelpers.callMethod(contMediator, "handleKeyguardDone");
                } else {
                    // Same as before, strong authentication is specified as true
                    XposedHelpers.callMethod(contMediator, "handleKeyguardDone", true);
                }
                if (vibration) {
                    Vibrator vib = (Vibrator) AndroidAppHelper.currentApplication().getSystemService(Context.VIBRATOR_SERVICE);
                    if (vib.hasVibrator()) {
                        vib.vibrate(vibDuration);
                    }
                }
            }
            // If user wanted a delay, perform here
        }, seconds * 1000);
    }


    // Reveals sensitive notification data upon unlock
    private static void showSensitive(ViewGroup group){
        // Make sensitivity false
        XposedHelpers.callMethod(contAmbientState, "setHideSensitive", false);
        // Loop each notification
        for (int i = 0; i < group.getChildCount(); i++){
            View child = (View)(group.getChildAt(i));
            // Update the sensitivity for the notification
            XposedHelpers.callMethod(contNotifStack, "updateHideSensitiveForChild", child);
        }
        // Refresh the notification status to UI
        XposedHelpers.callMethod(contNotifStack, "requestChildrenUpdate");
    }


}

