# WeChat2Telecom (微信原生通话桥接器)

本项目是一个基于 LSPosed 框架的混合式 Xposed 模块，旨在将微信的 VoIP 通话（语音/视频聊天）无缝桥接至安卓系统的 **Telecom Framework**。通过本项目，微信通话可以像运营商电话一样在锁屏界面显示、在系统通话记录中留存，并支持通过系统蓝牙、车机系统进行接听和挂断。

---

## 1. 项目架构 (Project Architecture)

本项目采用 **双进程协作架构**，通过 AIDL 建立跨进程通信机制：

1.  **WeChat Process (Hook 层)**:
    *   宿主：`com.tencent.mm`
    *   职责：利用 Xposed 拦截微信内部的 `VoipMgr` 事件。
    *   通信：作为客户端，通过 `IWeChatVoipBridge` AIDL 接口绑定至主程序服务。
2.  **Telecom Bridge Process (系统交互层)**:
    *   宿主：`com.example.wechat2telecom`
    *   职责：实现 `ConnectionService`。
    *   通信：作为服务端，接收 Hook 层的指令，调用 `TelecomManager` 向系统注册 `PhoneAccount` 并触发原生通话界面。

**流程图**:
`微信通话触发` -> `Xposed Hook 拦截` -> `AIDL 发送事件` -> `Bridge Service` -> `TelecomManager (System API)` -> `原生通话 UI`

---

## 2. 核心实现逻辑

### 2.1 Telecom 桥接
项目核心类 `TelecomBridgeService` 继承自 `ConnectionService`。
*   **PhoneAccount 注册**: 在服务启动时自动注册 `CAPABILITY_SELF_MANAGED` 类型的电话账户，确保系统识别本应用为通话提供方。
*   **Incoming Call 注入**: 调用 `addNewIncomingCall` 将微信来电通知给系统，唤起全屏通话界面。

### 2.2 跨进程通信 (IPC)
使用 `IWeChatVoipBridge.aidl` 接口：
*   `onIncomingCall`: 当微信检测到来电时，通知主进程。
*   `answerCall / hangupCall`: 当用户在系统界面点击接听/挂断时，通过回调或广播同步给微信进程。

---

## 3. 逆向指南：如何查找混淆类名

微信代码经过深度混淆，类名（如 `u`, `ab`, `c`）会随版本更新而变化。推荐以下工具和方法：

### 工具
*   **Jadx-GUI**: 静态分析 APK 源码。
*   **MT 管理器**: 在手机端快速查看类名和方法签名。

### 查找步骤
1.  **定位通话入口**: 搜索字符串 `"voip"` 或 `"VideoActivity"`。
2.  **追踪通知逻辑**: 查找调用 `NotificationManager.notify()` 的地方，通常向上回溯几层即可找到负责处理通话状态变化的 `Manager` 类。
3.  **动态调试**: 使用 `XposedBridge.log` 打印特定包名（如 `com.tencent.mm.plugin.voip.model`）下的所有方法执行，观察通话时触发的函数。

---

## 4. 编译与安装

1.  **克隆代码**:
    ```bash
    git clone https://github.com/ahao15800/wechat2telecom.git
    ```
2.  **编译环境**: 使用 Android Studio Flamingo (2022.2.1) 或更高版本。
3.  **构建 APK**: 运行 `./gradlew assembleDebug`。
4.  **模块激活**:
    *   安装 APK。
    *   在 LSPosed 管理器中启用模块。
    *   **作用域勾选**: 必须勾选“系统框架”和“微信”。
    *   重启设备（或强杀微信进程）。

---

## 5. 绕过 Android 14+ / HyperOS "受限设置"

在较新版本的安卓系统（尤其是 HyperOS/MIUI）中，通过外部安装的 APK 可能会被标记为“受限设置”，导致无法开启“辅助功能”或某些关键权限。

### 解决方法
1.  **进入应用信息**: 打开手机“设置” -> “应用管理” -> “WeChat2Telecom”。
2.  **开启允许受限设置**: 点击右上角的三个点（更多选项），选择 **“允许受限设置”** (Allow Restricted Settings)。
3.  **验证权限**: 之后即可正常开启“修改系统设置”、“显示在其他应用上”等权限。

---

## 许可证
本项目仅供学习和技术研究使用，严禁用于任何违法用途。请遵守微信用户协议。
