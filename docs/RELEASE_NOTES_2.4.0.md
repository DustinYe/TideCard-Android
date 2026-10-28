**潮汐改卡 2.4.0 正式版**：新增多语言和应用内更新。

- 设置中可选简体中文、繁體中文、English 或跟随系统；覆盖主页、地区圆盘、设置、配对 / USB 指引、通知及常见后台错误。地区名称随语言切换，搜索兼容简体、繁体、英语和 ISO 地区码。
- 点击设置中的“检查更新”即可查询 GitHub 最新正式版；发现新版本后可下载 APK，核对大小、SHA-256、包名、版本和签名，再打开系统安装界面。仅由用户主动触发，不定时联网，不包含广告或统计上报。
- Android 版本码为 11，沿用原签名。保留地区、卡槽、配对身份和恢复备份。
- 配套 Windows USB 激活工具更新为 **1.1.2**，随附相同的 2.4.0 APK；保留高 DPI 布局和原激活流程。

**下载和升级**

1. 手机下载 `TideCard-2.4.0.apk`。先停止改卡并恢复原配置，再直接覆盖安装；不要先卸载。
2. 升级后可能需要重新启动内置后台，已有配对身份保留。没有无线配对码 / IP 时，可使用 `TideCard-USB-1.1.2-Windows.zip`，完整解压后运行其中 EXE。
3. 后续应用内更新首次需手动允许本应用安装未知应用，最终安装仍需系统确认。网络无法访问 GitHub 时，可重试或手动访问发布页。

**验证范围**

在小米 2304FPN6DC / HyperOS 3 / Android 16 上验证三种语言与跟随系统、地区圆盘 / 搜索、偏好恢复，以及真实 GitHub 版本查询和 APK 下载校验。通过较高版本签名测试包、错误签名、错误版本和旧版拒绝检查；更新专用 content URI 可读，安装权限说明弹窗已截图确认。测试未授予未知应用安装权限或确认最终安装；完整线上升级还需将来较新版本发布后验证。

Release / Android 测试包构建、lint（0 errors / 45 warnings）及签名检查通过。Windows 工具通过 43 项模拟流程断言和 14 组 DPI / 窄窗口布局检查；真实 ADB 可启动并列出 USB 设备。本轮未进行新的 SIM 写入，也未重做 USB 激活；vivo 与报错的 Redmi K80 Ultra 的 ROM 写入兼容性仍待各自实机确认。详情见 `docs/VALIDATION.md`。

附件包括签名 APK、对应源码、Windows 工具、SHA-256 校验文件及签名摘要。源码以 `v2.4.0` 标签为准。

**English**

TideCard 2.4.0 adds Simplified Chinese, Traditional Chinese, English and system-language selection, plus user-initiated GitHub update checks, verified APK downloads and a system-installer handoff. The matching Windows USB activator is 1.1.2. Stop and restore the SIM session before upgrading, then install over the existing app to preserve its data. Android still requires permission to install unknown apps and confirmation in the system installer. Xiaomi testing does not establish vivo or Redmi K80 Ultra compatibility.
