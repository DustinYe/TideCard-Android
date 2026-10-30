# 电脑工具检查更新与签名

USB 工具 **1.1.7** 起，底部提供“检查更新”。打开后检查本项目 GitHub 的电脑工具正式版，发现新版本时点“下载、更新并重启工具”。1.1.6 及更早版本没有此入口，需要先手动下载一次当前完整工具包。

## 更新行为

- 仅选择本仓库已公开、非预发布的 `usb-v主版本.次版本.修订号`，按数字比较版本，不将 Android `v2.x` 当作电脑工具版本。
- 下载该版本精确匹配的 Windows ZIP。仅访问 GitHub API、本项目发布下载路径及 GitHub 的官方附件 CDN，保留系统 HTTPS 证书验证。
- 要求 GitHub 附件提供 SHA-256，并核对精确大小、完整 ZIP 哈希、逐文件清单及 EXE 产品名 / 版本。清单缺失、校验失败、越界路径、重复文件和异常大小会停止更新。
- 下载可以取消。准备完成后，独立助手等待旧窗口退出，在原解压目录替换工具、配套 APK 和 ADB。内容相同的文件跳过替换，用户额外文件保留；写入失败尝试恢复原文件，原目录保留 `.tidecard-backup-*` 备份。不结束任何 ADB 服务。
- 当前目录不可写时提示将完整工具移至自己的下载 / 文档目录，不自动提权。系统拦截新版启动时，用户仍需根据来源与系统提示决定是否允许运行。
- 更新不会向手机执行任何命令；电脑中的配套 APK 更新不等于给手机安装。手机安装和激活仍需主界面的“安装 / 更新并激活”。

更新缓存位于 `%LOCALAPPDATA%\TideCardUsb\Updates`，包含下载包、校验后的文件和助手。完成更新且所有工具窗口关闭后，可手动清理旧缓存与不再需要的备份。不能把正在更新的缓存删除。

检查更新是用户主动触发的联网功能，不包含手机序列号、诊断内容或激活令牌。GitHub 会接收正常网络请求及电脑工具版本 User-Agent；激活与连接诊断仍在本机执行。

## Windows“发行者未知”

当前 EXE **没有 Authenticode 代码签名**；Android APK 具有自己的签名，两者互不替代。SmartScreen 的“无法识别的应用 / 发行者未知”截图不能单独证明检测到了恶意软件，也不能证明程序安全。

目前没有可用于公开发行的可信代码签名证书。本项目没有生成自签名证书让用户安装为受信任根，也不会关闭 Defender / SmartScreen 或删除下载来源标记。更新下载与新 EXE 保留 Internet 来源标记。

SHA-256 验证确保文件与通过 HTTPS 获得的 GitHub 发布记录一致；**它不是发行者身份的独立数字签名，不能防止仓库或发布账号本身被攻破。** 可信 Authenticode 签名需要经过验证的发行者证书或签名服务。签名后新文件仍可能由于信誉不足出现 SmartScreen 提示，不能承诺“一签就不拦截”。

来源：[Microsoft SmartScreen 与代码签名说明](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation)、[GitHub 附件 API 与 digest](https://docs.github.com/en/rest/releases/assets)。

## 验证范围

构建中的隔离检查覆盖正式版筛选、版本数字比较、附件来源、SHA-256、路径越界 / 重复文件拒绝、清单和版本不符、文件占用、写入失败恢复、额外文件保留，以及实际独立助手替换并启动只写测试标记的新版 EXE。另有 96–240 DPI 的更新弹窗布局检查。

可选联网检查使用正式发布服务，下载并验证真实工具包，但不安装、不调用 ADB：

```powershell
# 先执行 USB_ACTIVATION.md 中的 Build.ps1，再编译可选检查。
$sources = Get-ChildItem desktop\usb-activator\*.cs |
  Where-Object { $_.Name -notin @('Checks.cs','LayoutChecks.cs','ConnectionDiagnosticsChecks.cs','UpdateChecks.cs','MenuLifecycleChecks.cs') } |
  ForEach-Object FullName
$csc = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
& $csc /nologo /target:exe /main:TideCardUsb.NetworkUpdateChecks `
  /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.Core.dll /r:System.Management.dll `
  /r:System.Web.Extensions.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll `
  /out:desktop\usb-activator\build\NetworkUpdateChecks.exe `
  @sources desktop\usb-activator\build\BundleInfo.g.cs
Copy-Item desktop\usb-activator\App.config desktop\usb-activator\build\NetworkUpdateChecks.exe.config
& desktop\usb-activator\build\NetworkUpdateChecks.exe
```

OPPO 手机和出问题的电脑属于用户朋友，未在开发现场。这些主机检查不代表已验证该 OPPO 的连接或改卡能力，也未证明更新能消除不同电脑上的 SmartScreen 提示。
