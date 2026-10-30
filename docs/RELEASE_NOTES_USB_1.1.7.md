# 潮汐改卡 USB 工具 1.1.7

新增电脑端“检查更新”和“下载、更新并重启工具”，首次请完整解压 **TideCard-USB-1.1.7-Windows.zip**。

- 主动检查本仓库的电脑工具正式版，独立于 Android 应用版本；发现新版本后可下载完整工具包并在原目录更新。
- 核对 GitHub 附件 SHA-256、大小、包内文件清单和 EXE 版本，拒绝异常路径和损坏包。下载可取消，文件替换失败时尝试回退，保留额外用户文件和本地备份。
- 内容相同的 ADB 文件跳过替换，不停止其它 ADB 连接；更新过程不对手机安装、激活或改卡。
- 保留 1.1.6 的通用 USB / OPPO 品牌线索、本机 ADB 服务协议检查和分阶段排查。
- 随附相同原签名 **Android 2.4.1** APK。

Windows EXE **仍未做 Authenticode 签名**，可能显示 SmartScreen“发行者未知”。哈希校验不等于发行者签名；下载与 EXE 保留 Internet 来源标记，不关闭系统保护。详情见[更新与签名](https://github.com/DustinYe/TideCard-Android/blob/usb-v1.1.7/docs/DESKTOP_UPDATES.md)。

主机验证包括更新文件校验、损坏 / 异常包拒绝、文件占用与恢复、独立助手实际替换并重启测试 EXE，以及主界面 / 诊断 / 更新弹窗的 DPI 与窄窗口检查。OPPO 手机和故障电脑不在现场，连接根因与实机效果仍待朋友反馈验证。

旧版没有自动更新入口，需要先手动下载一次 1.1.7。此版本也附于 [Android 2.4.1 发布页](https://github.com/DustinYe/TideCard-Android/releases/tag/v2.4.1)，电脑端与 Android 应用的版本号分别维护。
