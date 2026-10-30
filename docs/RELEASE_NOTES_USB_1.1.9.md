# 潮汐改卡 USB 工具 1.1.9

配套 APK 更新为同签名 **Android 2.4.2 / 版本码 13**，修复手机无线配对通知等待时没有输入入口的问题。USB 激活协议、设备诊断和电脑端更新流程保持不变。

完整解压 **TideCard-USB-1.1.9-Windows.zip** 后运行。已有 1.1.7 或更新版本可直接点“检查更新”下载此包。Android 2.4.2 的说明与手机端限制见[发布页](https://github.com/DustinYe/TideCard-Android/releases/tag/v2.4.2)。

Windows EXE 仍没有 Authenticode 签名，可能出现 SmartScreen 提示。朋友电脑上的 ADB `fdevent interrupt socketpair` 问题尚未实机解决，本工具不重置 Winsock，也不终止其它 ADB 服务。
