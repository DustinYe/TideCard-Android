# 潮汐改卡 USB 工具 1.1.6

修复旧版只统计华为 USB 接口，导致 OPPO 等品牌的 Windows 接口被漏检的问题；补充客户端正常、设备查询超时的本机服务检查。

- 通用 USB / ADB / MTP 候选检测，增加 OPPO / OnePlus / realme 品牌线索，并说明多设备和未知接口的不确定性。
- 通用“连接诊断”和“OPPO / 厂商帮助”，按实际品牌提供官方入口；HiSuite 明确标为华为专用。
- 设备查询失败时，使用有总超时的本机 ADB 只读协议检查，区分端口拒绝、无响应和异常回复。
- 已连接后的 shell 拒绝按实际阶段报告，避免误判 Android 版本、主用户或 USB 连接状态。ColorOS 权限受限说明只用于相应阶段。
- 保留原签名 Android **2.4.1** APK、安装版本保护、高 DPI 适配和脱敏复制。

## 使用

完整解压 **TideCard-USB-1.1.6-Windows.zip**，保留 EXE 旁的 `adb` 文件夹。打开“连接诊断”，确认报告顶部显示 **1.1.6**。

若客户端正常而设备查询超时，请先拔下手机做一次对照；拔下后仍超时，应优先恢复电脑端 ADB 服务。如果通过任务管理器结束 `adb.exe`，其它 ADB 调试连接会断开。客户端 / 服务问题尚未排除前，不将故障归因于 ColorOS 权限监控，也不把文件传输驱动当作 ADB 驱动。

公开信息来源、分阶段操作与限制见 [OPPO / ColorOS 指引](https://github.com/DustinYe/TideCard-Android/blob/usb-v1.1.6/docs/OPPO_COLOROS_USB.md)。本版同时附在 [Android 2.4.1 发布页](https://github.com/DustinYe/TideCard-Android/releases/tag/v2.4.1)。

**受影响 OPPO / ColorOS 电脑和手机不在现场，尚未确认其连接根因或实机修复效果。** Windows EXE 暂无 Authenticode 签名，发布附件提供 SHA-256 校验。

主机验证通过 214 项诊断断言、62 项模拟流程断言和 28 组 DPI / 窄窗口检查（1393 项断言）。实际 ADB 客户端、设备查询与本机服务版本响应正常，当前没有连接手机。
