package com.example.wechat2telecom.ipc;

/**
 * 微信 VoIP 与系统 Telecom 框架之间的核心 IPC 接口。
 * 定义了 Xposed 注入层（微信进程）与系统 Bridge 层（应用进程）之间的双向通信。
 */
interface IWeChatVoipBridge {
    /**
     * 当微信收到来电时，由 Xposed 钩子调用，通知系统 Bridge。
     * @param callerName 来电者昵称或显示名
     * @param callId 微信内部的通话唯一标识
     */
    void onIncomingCall(String callerName, String callId);

    /**
     * 当通话在微信端结束或挂断时调用。
     * @param callId 通话唯一标识
     */
    void onCallDisconnected(String callId);

    /**
     * 系统 Telecom 界面点击“接听”后，由 Bridge 回调至微信进行接听。
     * @param callId 通话唯一标识
     */
    void answerCall(String callId);

    /**
     * 系统 Telecom 界面点击“挂断”后，由 Bridge 回调至微信进行操作。
     * @param callId 通话唯一标识
     */
    void hangupCall(String callId);
}
