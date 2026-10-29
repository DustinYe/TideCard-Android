# 电脑 USB 激活

Windows 工具当前为 **1.1.6**，配套 Android APK 为 **2.4.1 正式版**。修复非华为品牌漏检，提供通用 USB 候选检测、OPPO / ColorOS 指引与本机服务协议检查，保留客户端自检和高 DPI 适配。受影响 OPPO / ColorOS 电脑及手机尚未现场验证，依据和步骤见 [OPPO 排查](OPPO_COLOROS_USB.md)。升级手机到本版后请使用匹配的电脑工具，旧版工具会阻止降级安装。技术说明与验证方式见 [桌面缩放适配](DESKTOP_UI.md)。

下载：[Android 2.4.1 发布页的 USB 1.1.6 附件](https://github.com/DustinYe/TideCard-Android/releases/download/v2.4.1/TideCard-USB-1.1.6-Windows.zip)，或查看 [USB 1.1.6 独立发布页](https://github.com/DustinYe/TideCard-Android/releases/tag/usb-v1.1.6)。两个位置提供相同文件；Android 应用内更新仍以 2.4.1 为最新版本。

适用于手机无线调试没有“使用配对码配对设备”、没有 IP，或希望通过数据线激活的场景。Windows 工具只负责安装应用、启动同一个内置 shell 后台，不自动选择地区、写入 CarrierConfig 或开启持续改卡。

## 使用

1. 在 Windows 10 / 11 上完整解压配套工具包，打开 `TideCard-USB.exe`。需要 .NET Framework 4.8，无需 Android Studio 或管理员权限。
2. 手机开启 USB 调试，数据线连接电脑；解锁后手动允许这台电脑的 USB 调试授权。无需开启无线调试。
3. 点“刷新设备”，明确选择手机；多台设备时不自动选择。点击“安装 / 更新并激活”，手机提示 USB 安装时手动允许。
4. 电脑与手机显示后台就绪后可拔线；在手机 USB 激活页面点“返回改卡界面”，选择地区与卡槽并开始。

重启手机、系统终止后台后可能需要重新连接电脑激活。后台就绪不等于系统允许改卡，实际写入 / 恢复仍需该 ROM 验证。目前只支持 Android 11 以上主用户（用户 0），不支持工作空间、系统分身或访客。

Android USB 调试需要系统开关和用户确认，不能用导入普通文件替代这一授权。没有设备时检查数据线、USB 接口和厂商驱动；某些系统还需要选择“传输文件”。参考 [Android 官方 ADB 文档](https://developer.android.com/tools/adb)。

OPPO / ColorOS 用户点“连接诊断”，从“OPPO / 厂商帮助”查看对应阶段的指引。检测不再只统计华为；若客户端正常而设备查询超时，先拔下手机对照，再核对本机 5037 服务。ColorOS 权限监控属于连接后的权限限制，不能用空列表或超时推断。华为专用的 HiSuite 入口独立列出，详见 [OPPO / 通用排查](OPPO_COLOROS_USB.md)。

## 安装与凭据

每次激活使用 `adb install -r` 覆盖安装工具随附且 SHA-256 匹配的 APK，保留应用数据。签名冲突或更高版本时停止，不自动卸载、清除数据或强制降级。已有改卡会话的恢复快照保持在应用私有存储中。卸载前应在原应用停止并恢复。

`BridgeProvider` 的 `usb_prepare` / `usb_status` 同原 attach 一样只接受 UID 2000。准备记录固定为版本、应用 UID、本次安装随机令牌、当前 APK 路径；由电脑严格解析，不接受手机返回的任意脚本。沿用已有 Bootstrap 的 shell UID、应用 UID、令牌、版本及 Binder 身份校验。新导出的 `UsbActivationActivity` 仅展示帮助和状态，打开页面不会启动服务或改卡。

电脑只操作所选序列号，网络和模拟器传输不列入选择。脚本中的路径用 POSIX 单引号转义；令牌仅在电脑内存处理，通过 ADB 标准输入传递，不进入 Windows 进程参数或界面日志。令牌仍按既有设计保存在手机应用私有偏好中。工具不联网、不保存诊断文件、不打开网络 ADB 端口、不重启设备、不清除数据或手动结束 ADB server。ADB 自身可能使用 / 启动本机的标准 ADB server。

Windows EXE 未做 Authenticode 代码签名，APK 保持原签名。完整压缩包提供逐文件 SHA-256 校验。随附 `adb.exe`、`AdbWinApi.dll`、`AdbWinUsbApi.dll` 为未修改的 Android SDK Platform-Tools 37.0.1 文件，完整声明随 `adb/NOTICE.txt` 提供。

连接诊断只读查询 Windows USB 候选节点与 ADB 状态，设备查询失败时另做有总超时的本机 ADB 协议检查，不自动保存或上传，复制内容不含序列号和 PnP 实例尾部。ADB 子进程固定本机 5037 端口，隔离 `ADB_SERVER_SOCKET`、`ANDROID_ADB_SERVER_PORT`、`ANDROID_ADB_SERVER_ADDRESS`、`ANDROID_SERIAL`、`ADB_TRACE`，并设置 `ADB_LIBUSB=0`；不更改全局环境或强制结束已有服务。原生 USB 后端仅对之后新启动的服务生效。

## 构建

先按仓库 Android 构建步骤生成签名 release APK，再运行：

```powershell
.\desktop\usb-activator\Build.ps1 -ApkPath .\app\build\outputs\apk\release\app-release.apk -SdkRoot $env:ANDROID_HOME
```

使用系统 .NET Framework C# 编译器，输出在 `desktop/usb-activator/build/package`。从实际 APK 读取包名、版本名和版本码并核对审核过的配套版本，生成固定 APK 哈希、编译 WinForms EXE 和回归检查，复制官方本机 SDK 的 ADB 文件及完整 NOTICE，不从其他下载源取程序。`Build.ps1` 会检查当前版本，升级版本时需同时审核协议和桌面约束。

## 初版（2.3.0-rc2）验证边界

通过桌面流程的设备选择、未授权、离线 / 断线、系统分身拒绝、覆盖安装失败、签名冲突、降级保护、后台确认 / 超时、错误版本、异常凭据 / 路径、日志与 APK 哈希检查；ADB 安装和手机回执均由模拟传输提供。实际随附 ADB 可在 Windows 启动并列出设备（本轮 0 台），桌面窗口已渲染检查。Java 主机协议与原配置恢复回归、APK / Android 测试包构建、lint 和原签名检查通过。

vivo 不在现场，**没有进行任何新手机安装、USB 激活、拔线、重启或地区写入 / 恢复实测**。不能将旧款小米的测试结果当作 vivo 兼容证据；2.3.0-rc1 的 K80 Ultra 兼容路径仍需该机验证。


2.3.0-rc3 已在连接的小米手机完成 USB 冷启动激活及新增地区 / 外观实机验证；最新范围见 [验证记录](VALIDATION.md)。
