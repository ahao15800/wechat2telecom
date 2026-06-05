package com.example.wechat2telecom.ipc;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统的核心桥接服务。
 * 它同时扮演两个角色：
 * 1. 作为 AIDL 服务端，接收来自微信（Xposed 注入进程）的通话指令。
 * 2. 继承 ConnectionService，实现 Android 系统原生的 Telecom 框架接口，使微信来电像普通电话一样显示。
 */
public class TelecomBridgeService extends ConnectionService {
    private static final String TAG = "W2T_BridgeService";
    private static final String ACCOUNT_ID = "WeChat_Bridge_Account";
    
    private TelecomManager telecomManager;
    private PhoneAccountHandle phoneAccountHandle;
    
    // 追踪当前活动的通话，Key 为 callId
    private final Map<String, WeChatConnection> activeConnections = new HashMap<>();

    /**
     * 实现 AIDL 接口，供 Xposed 层调用。
     */
    private final IWeChatVoipBridge.Stub mBinder = new IWeChatVoipBridge.Stub() {
        @Override
        public void onIncomingCall(String callerName, String callId) throws RemoteException {
            Log.i(TAG, "收到微信来电请求: " + callerName);
            reportIncomingCallToSystem(callerName, callId);
        }

        @Override
        public void onCallDisconnected(String callId) throws RemoteException {
            WeChatConnection conn = activeConnections.get(callId);
            if (conn != null) {
                conn.setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));
                conn.destroy();
                activeConnections.remove(callId);
            }
        }

        @Override
        public void answerCall(String callId) throws RemoteException {
            // 这里通常是从系统 UI 触发的，但也可以通过 IPC 强制执行
        }

        @Override
        public void hangupCall(String callId) throws RemoteException {
            // 同上
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        registerPhoneAccount();
    }

    /**
     * 注册系统的 PhoneAccount，这是让应用能够“模拟通话”的前提。
     */
    private void registerPhoneAccount() {
        ComponentName componentName = new ComponentName(this, TelecomBridgeService.class);
        phoneAccountHandle = new PhoneAccountHandle(componentName, ACCOUNT_ID);

        PhoneAccount account = PhoneAccount.builder(phoneAccountHandle, "WeChat VoIP")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED) // 自管理通话，不占用基带
                .setShortDescription("WeChat Incoming Bridge")
                .build();

        telecomManager.registerPhoneAccount(account);
        Log.i(TAG, "PhoneAccount 已成功注册至系统 Telecom 框架");
    }

    /**
     * 向系统报告一个新的来电。
     */
    private void reportIncomingCallToSystem(String callerName, String callId) {
        Bundle extras = new Bundle();
        extras.putString("caller_name", callerName);
        extras.putString("call_id", callId);
        
        // 伪造一个 URI
        Uri uri = Uri.fromParts("tel", "wechat-" + callId, null);
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri);

        try {
            telecomManager.addNewIncomingCall(phoneAccountHandle, extras);
            Log.d(TAG, "已调用 addNewIncomingCall 通知系统");
        } catch (SecurityException e) {
            Log.e(TAG, "权限不足，无法报告来电: " + e.getMessage());
        }
    }

    /**
     * ConnectionService 核心回调：当系统确认可以开始处理通话时触发。
     */
    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Bundle extras = request.getExtras();
        String callerName = extras.getString("caller_name");
        String callId = extras.getString("call_id");

        WeChatConnection connection = new WeChatConnection(callId);
        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED);
        connection.setInitializing();
        
        activeConnections.put(callId, connection);
        
        connection.setActive();
        return connection;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // 如果是系统 Telecom 绑定，返回 super (ConnectionService)
        if (ACTION_CONNECTION_SERVICE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        // 如果是 Xposed 注入层绑定，返回 AIDL Stub
        return mBinder;
    }

    /**
     * 内部通话类，处理系统通话状态的变更（接听/挂断）。
     */
    private class WeChatConnection extends Connection {
        private final String callId;

        WeChatConnection(String callId) {
            this.callId = callId;
        }

        @Override
        public void onAnswer() {
            super.onAnswer();
            Log.i(TAG, "用户点击了系统接听按钮: " + callId);
            // TODO: 这里应通过广播或另一个 AIDL 回调通知微信进程执行“接听”代码
        }

        @Override
        public void onDisconnect() {
            super.onDisconnect();
            Log.i(TAG, "用户点击了系统挂断按钮: " + callId);
            setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            destroy();
            activeConnections.remove(callId);
        }
    }
}
