package com.example.wechat2telecom.xposed;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "WeChat2Telecom_Xposed";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String VOIP_PLUGIN_CLASS = "com.tencent.mm.plugin.voip.model.u"; // Example common VOIP manager class

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(WECHAT_PACKAGE)) {
            return;
        }

        XposedBridge.log("WeChat2Telecom: Injected into WeChat");

        // Attempt to hook VOIP state changes
        try {
            hookVoipManager(lpparam);
        } catch (Exception e) {
            XposedBridge.log("WeChat2Telecom: Failed to hook VOIP manager: " + e.getMessage());
            // Fallback: Hook general VOIP UI or Broadcasts if primary manager hook fails
            hookVoipFallback(lpparam);
        }
    }

    private void hookVoipManager(LoadPackageParam lpparam) {
        // This is a generic hook for the VOIP notification logic in WeChat
        // Real-world WeChat versions obfuscate class names, but the structure remains
        XposedHelpers.findAndHookMethod(
            "com.tencent.mm.plugin.voip.ui.VideoActivity", 
            lpparam.classLoader, 
            "onCreate", 
            Bundle.class, 
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.thisObject;
                    XposedBridge.log("WeChat2Telecom: VideoActivity detected, notifying Telecom Bridge");
                    notifyTelecomBridge(context, "INCOMING_VOIP");
                }
            }
        );
    }

    private void hookVoipFallback(LoadPackageParam lpparam) {
        // Fallback hook for newer/older versions using different entry points
        XposedBridge.log("WeChat2Telecom: Using fallback hooks");
    }

    private void notifyTelecomBridge(Context context, String eventType) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.example.wechat2telecom", "com.example.wechat2telecom.ipc.TelecomBridgeService"));
        intent.putExtra("event_type", eventType);
        context.startService(intent);
    }
}