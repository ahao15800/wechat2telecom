package com.example.wechat2telecom.xposed;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.example.wechat2telecom.ipc.IWeChatVoipBridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Xposed 注入入口类。
 * 核心逻辑：
 * 1. 注入微信进程，定位 VoIP 相关业务逻辑。
 * 2. 建立与本应用（TelecomBridgeService）的 AIDL IPC 连接。
 * 3. 拦截微信来电事件并透传至系统 Telecom 框架。
 */
public class XposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "W2T_Xposed";
    private static final String TARGET_PACKAGE = "com.tencent.mm";
    
    // 远程 AIDL 接口实例，用于向系统 Bridge 发送事件
    private IWeChatVoipBridge bridgeService;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }

        XposedBridge.log("WeChat2Telecom: 成功注入微信进程 - 开始执行逻辑");

        // 第一步：在微信上下文准备好后，绑定远程 Bridge 服务
        hookApplicationContext(lpparam);

        // 第二步：挂钩 VoIP 核心管理器 (此处使用微信常见的业务模式作为示例)
        // 注意：微信的代码高度混淆，生产环境需要动态扫描或针对特定版本匹配类名
        setupVoipHooks(lpparam);
    }

    private void hookApplicationContext(final LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            "android.app.Application", 
            lpparam.classLoader, 
            "onCreate", 
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.thisObject;
                    bindBridgeService(context);
                }
            }
        );
    }

    private void bindBridgeService(Context context) {
        Intent intent = new Intent();
        // 绑定本应用提供的 TelecomBridgeService
        intent.setComponent(new ComponentName("com.example.wechat2telecom", "com.example.wechat2telecom.ipc.TelecomBridgeService"));
        
        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                bridgeService = IWeChatVoipBridge.Stub.asInterface(service);
                XposedBridge.log("WeChat2Telecom: 已建立与 Telecom Bridge 的 AIDL 连接");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                bridgeService = null;
                XposedBridge.log("WeChat2Telecom: 与 Telecom Bridge 的连接已断开");
            }
        }, Context.BIND_AUTO_CREATE);
    }

    private void setupVoipHooks(LoadPackageParam lpparam) {
        // 示例：挂钩微信 VoIP UI 的显示逻辑。
        // 生产级实现通常会挂钩更底层的网络回调类 (如 com.tencent.mm.plugin.voip.model.n)
        // 此处以 VideoActivity 入口模拟“收到来电”的触发点。
        try {
            XposedHelpers.findAndHookMethod(
                "com.tencent.mm.plugin.voip.ui.VideoActivity", 
                lpparam.classLoader, 
                "onCreate", 
                android.os.Bundle.class, 
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("WeChat2Telecom: 检测到微信视频/音频通话界面弹出");
                        
                        // 获取来电者信息 (实际需根据微信对象字段反射获取)
                        String callerName = "微信好友"; 
                        String callId = String.valueOf(System.currentTimeMillis());

                        if (bridgeService != null) {
                            try {
                                // 通过 AIDL 将事件通知给系统层
                                bridgeService.onIncomingCall(callerName, callId);
                                XposedBridge.log("WeChat2Telecom: 已成功透传至 Telecom 框架");
                            } catch (RemoteException e) {
                                XposedBridge.log("WeChat2Telecom: IPC 调用失败: " + e.getMessage());
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("WeChat2Telecom: 无法挂钩特定类 (版本差异)，错误: " + t.getMessage());
        }
    }
}
