package com.samstenner.instantunlock;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Vibrator;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class UnlockManager implements IXposedHookLoadPackage {

    // Controllers for accessing methods
    private Object contMediator;
    private Object contStatusBar;
    private Object contAmbientState;
    private Object contNotifStack;
    private Object contMonitor;

    // Record of preferences
    private boolean enabled;
    private String unlockType;
    private boolean fastSpeed;
    private boolean allowMusic;
    private boolean allowDynamic;
    private boolean allowStatic;
    private boolean revealSensitive;
    private boolean onlyDelayNotifs;
    private boolean vibration;
    private int vibDuration;
    private boolean unlocking;

    // References to all the classes that are hooked
    private String pakSystemUI = "com.android.systemui";
    private String pakKeyguard = "com.android.keyguard";
    private String clsKGUpdate = pakKeyguard + ".KeyguardUpdateMonitor";
    private String clsKGMediator = pakSystemUI + ".keyguard.KeyguardViewMediator";
    private String clsKGMonitor = pakSystemUI + ".statusbar.policy.KeyguardMonitor";
    private String clsStatusBar = pakSystemUI + ".statusbar.phone.PhoneStatusBar";
    private String clsAmbientState = pakSystemUI + ".statusbar.stack.AmbientState";
    private String clsNotifStack = pakSystemUI + ".statusbar.stack.NotificationStackScrollLayout";
    private String clsNotifRow = pakSystemUI + ".statusbar.ExpandableNotificationRow";
    private String clsMediaRow = pakSystemUI + ".statusbar.MediaExpandableNotificationRow";

    // Miscellaneous variables
    private int unlockDelay;
    private int buildVersion;
    private boolean unlocked = false;


    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {

        // Only continue if loaded package in SystemUI
        if (!lpparam.packageName.equals(pakSystemUI) && !lpparam.packageName.equals(pakKeyguard)) {
            return;
        }
        XposedBridge.log("Successfully Accessed Packages");

        buildVersion = Build.VERSION.SDK_INT;
        XposedBridge.log(String.valueOf(buildVersion));
        // Correction for Oreo classes
        if (buildVersion >= Build.VERSION_CODES.O) {
            XposedBridge.log("Corrected for Oreo");
            clsKGMonitor = pakSystemUI + ".statusbar.policy.KeyguardMonitorImpl";
            clsStatusBar = pakSystemUI + ".statusbar.phone.StatusBar";
        }

        // When keyguard mediator loads, assign mediator object
        findAndHookMethod(clsKGMediator, lpparam.classLoader, "setupLocked", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                XposedBridge.log("Hooked Mediator");
                contMediator = param.thisObject;
            }
        });

        // Keyguard updates were given own function in Marshmallow
        if (buildVersion >= Build.VERSION_CODES.M) {
            // When keyguard has changed status, attempt to unlock
            findAndHookMethod(clsKGMonitor, lpparam.classLoader, "notifyKeyguardChanged", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    contMonitor = param.thisObject;
                    XposedBridge.log("Hooked Monitor");
                    handleKGChange();
                }
            });

        }
        // If lower than Marshmallow, manually check for keyguard updates
        else if (buildVersion < Build.VERSION_CODES.M) {
            // When keyguard has changed status, attempt to unlock on Lollipop
            findAndHookMethod(clsKGMonitor, lpparam.classLoader, "notifyKeyguardState", boolean.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    contMonitor = param.thisObject;
                    boolean showing = (boolean) param.args[0];
                    boolean secure = (boolean) param.args[1];
                    boolean mShowing = XposedHelpers.getBooleanField(contMonitor, "mShowing");
                    boolean mSecure = XposedHelpers.getBooleanField(contMonitor, "mSecure");
                    if (mShowing == showing && mSecure == secure) handleKGChange();
                }
            });
        }


        // Check when trust has changed
        findAndHookMethod(clsKGUpdate, lpparam.classLoader, "getUserHasTrust", int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) {
                XposedBridge.log("Unlockable: " + unlocked);

                // This is a dirty hack that (sort of) enables functionality on Android P. This isn't as
                // good as doing it the usual way, but until a better method is figured out
                // then it will have to do!
                unlocked = (boolean) param.getResult();
                if (buildVersion > Build.VERSION_CODES.P) {
                    if (unlocked && !unlocking) {
                        unlocking = true;
                        handleKGChange();
                    }
                    if (unlocking && !unlocked) unlocking = false;
                }
            }
        });


        // When status bar loads, assign status bar object
        findAndHookMethod(clsStatusBar, lpparam.classLoader,"start", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) {
                if (contStatusBar == null) {
                    XposedBridge.log("Hooked Status Bar");
                    contStatusBar = param.thisObject;
                }
            }
        });

        // When ambient state loads, assign ambient state object
        findAndHookMethod(clsAmbientState, lpparam.classLoader, "isHideSensitive", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (contAmbientState == null) {
                    XposedBridge.log("Hooked Ambient State");
                    contAmbientState = param.thisObject;
                }
            }
        });

        // When notification stack loads, assign stack object
        findAndHookMethod(clsNotifStack, lpparam.classLoader, "initView", Context.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log("Hooked Notification Stack");
                contNotifStack = param.thisObject;
            }
        });

        // When first animation has finished, cancel second animation
        findAndHookMethod(clsKGMediator, lpparam.classLoader, "startKeyguardExitAnimation", long.class, long.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log("Hooked Exiting Animation");
                if (fastSpeed) {
                    // Set duration and start to 0ms
                    param.args[0] = 0;
                    param.args[1] = 0;
                }
            }
        });
    }

    //
    private void handleKGChange() {
        XposedBridge.log("Hooked Keyguard Changed");
        try {
            readPrefs();
            boolean controlled = contMediator != null;
            boolean visible = contMonitor == null || (boolean) XposedHelpers.callMethod(contMonitor, "isShowing");
            XposedBridge.log(
                    "Enabled: " + enabled + "\n" +
                            "Controlled: " + controlled + "\n" +
                            "Visible: " + visible + "\n" +
                            "Unlocked: " + unlocked);
            // If able to be unlocked

            if (enabled && unlocked && controlled && visible) {
                // Check the exceptions aren't active
                boolean canDismiss = canUnlock(unlockType);
                if (canDismiss) {
                    // Alohomora
                    unlock();
                } else {
                    XposedBridge.log("Preferences Blocked Unlock");
                }
            } else if (unlocked && visible && unlocked && revealSensitive && !enabled) {
                canUnlock(ValueHolder.REVEAL);
            }
            else {
                XposedBridge.log("Not Eligible For Unlock");
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    // Determines whether exceptions prevent unlock
    private boolean canUnlock(String unlockType){
        XposedBridge.log("Checking Unlock Eligibility");
        // No exceptions, allow
        if (unlockType.equals(ValueHolder.FORCE)) return true;
        // If media currently playing and allowed, disallow dismissal
        if (contStatusBar != null && XposedHelpers.getObjectField(contStatusBar, "mMediaMetadata") != null && allowMusic) return false;
        // Get all notifications as group
        ViewGroup group = (ViewGroup) XposedHelpers.getObjectField(contStatusBar, "mStackScroller");
        // Loop each status bar element
        int notifCounter = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                // If element is notification
                notifCounter++;
                if (child.getClass().getName().equals(clsNotifRow)) {
                    // If revealing, disallow dismissal
                    if (revealSensitive) showSensitive(group);
                    boolean isClearable = (boolean) XposedHelpers.callMethod(child, "isClearable");
                    // Disallow keyguard dismisal if allowing dismissable notifications
                    if (isClearable && allowDynamic) return false;
                    // Same but for non-dismissable notifications
                    else if (!isClearable && allowStatic) return false;
                // some custom ROMs use media row, check if using, disallow if intended
                } else if (child.getClass().getName().equals(clsMediaRow) && allowMusic) return false;
            }
        }
        if (notifCounter == 0 && onlyDelayNotifs) unlockDelay = 0;
        // If status bar is groovy, then allow dismissal
        return true;
    }

    // Unlocks the device
    private void unlock() {
        XposedBridge.log("Attempting Unlock");
        final Handler handler = new Handler();
        final String handle = "handleKeyguardDone";
        final String dismiss = "dismiss";
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // If fast enabled, unlock without animation
                if (fastSpeed) {
                    if (buildVersion >= Build.VERSION_CODES.O) {
                        // Informs mediator that user dismissed using swipe when they actually haven't
                        XposedHelpers.callMethod(contMediator, handle);
                    } else if (buildVersion >= Build.VERSION_CODES.N ) {
                        // Same as before, but strong authentication is specified as true
                        XposedHelpers.callMethod(contMediator, handle, true);
                    } else {
                        XposedHelpers.callMethod(contMediator, handle, true, false);
                    }
                }
                // Otherwise unlock with animation
                else {
                    if (buildVersion >= Build.VERSION_CODES.P)
                        XposedHelpers.callMethod(contMediator, dismiss, (Object)null, null);
                    else if (buildVersion >= Build.VERSION_CODES.O)
                        XposedHelpers.callMethod(contMediator, dismiss, (Object)null);
                    else if (buildVersion == Build.VERSION_CODES.N_MR1)
                        XposedHelpers.callMethod(contMediator, dismiss, false);
                    else
                        XposedHelpers.callMethod(contMediator, dismiss);
                }
                // Vibrates if intended
                if (vibration) {
                    XposedBridge.log("Vibrating");
                    Vibrator vib = (Vibrator) AndroidAppHelper.currentApplication().getSystemService(Context.VIBRATOR_SERVICE);
                    if (vib.hasVibrator()) vib.vibrate(vibDuration);
                }
                XposedBridge.log("Unlocked Successfully");
            }
            // If user wanted a delay, perform here
        }, unlockDelay * 1000);
    }

    // Reveals sensitive notification data upon unlock
    private void showSensitive(ViewGroup group){
        XposedBridge.log("Showing Sensitive Notifications");
        // Make sensitivity false
        XposedHelpers.callMethod(contAmbientState, "setHideSensitive", false);
        // Loop each notification
        if (buildVersion < Build.VERSION_CODES.M) {
            XposedHelpers.callMethod(contNotifStack, "setHideSensitive", false, true);
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (buildVersion < Build.VERSION_CODES.M) {
                XposedHelpers.callMethod(contNotifStack, "generateAddAnimation", child, false);
                XposedHelpers.callMethod(contNotifStack, "updateAnimationState", child);
            } else {
                // Update the sensitivity for the notification
                XposedHelpers.callMethod(contNotifStack, "onViewAddedInternal", child);
            }
            // Refresh the notification status on UI
            XposedHelpers.callMethod(contNotifStack, "requestChildrenUpdate");
        }
    }

    private void readPrefs(){
        XposedBridge.log("Retrieving Preferences");
        // Retrieve preferences
        XSharedPreferences prefs = new XSharedPreferences("com.samstenner.instantunlock", "instant_unlock_settings");
        enabled = prefs.getBoolean("enabled", true);
        revealSensitive = prefs.getBoolean("sensitive", false);
        unlockType = prefs.getString("mode", ValueHolder.FORCE);
        fastSpeed = prefs.getBoolean("fast", true);
        allowMusic = prefs.getBoolean("music", false);
        allowDynamic = prefs.getBoolean("dynamic", false);
        allowStatic = prefs.getBoolean("static", false);
        onlyDelayNotifs = prefs.getBoolean("delay_notifs", false);
        vibration = prefs.getBoolean("vibrate", false);
        vibDuration = prefs.getInt("vib_duration", 120);
        int[] arrayDelays = new int[]{0, 1, 2, 3, 5, 10};
        unlockDelay = arrayDelays[prefs.getInt("delay", 0)];
    }

    public static class ValueHolder {

        public static String FORCE = "FORCE";
        public static String REVEAL = "REVEAL";

    }

}

