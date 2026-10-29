# OPPO / ColorOS：USB 连接与 ADB 分阶段排查

## 本次反馈与证据边界

用户将本轮故障机从华为更正为 OPPO / ColorOS；具体型号和 ColorOS / Android 版本尚未提供。此前按华为排查的结果不能直接套用。

提供的 1.1.5 报告显示 Windows 10.0.19045（64 位），工具进程 64 位，ADB x86；随附 EXE 和两个 DLL 存在，`adb version` 正常，但设备查询在 15 秒内未完成（`client-timeout`）。用户在“拔下 OPPO 再诊断”的对照问题中返回了同样的超时报告。以上是用户提供的日志与反馈，未在这台电脑现场验证。

这表明应优先检查本机 ADB 服务 / 端口与设备枚举阻塞，不能把超时当作“已经查到 0 台手机”，也不能断言某个 ColorOS 权限开关是根因。旧工具仅查询华为 PnP 节点，其“华为接口数=0”对 OPPO 不具备判断价值。

## 公开信息检索（2026-10-29）

- [OPPO 官方 Windows 连接排查](https://support.oppo.com/it/answer/?aid=neu10634)：包含线缆 / USB 模式、文件传输、USB 调试及 Windows 驱动检查。该说明支持先使用“传输文件”，没有提供“所有 OPPO 必须切换 MIDI”的结论。OPPO 帮助页可能需浏览器启用 JavaScript。
- [Shizuku 官方使用手册](https://shizuku.rikka.app/zh-hans/guide/setup/#通过无线调试启动-通过连接电脑启动-adb-权限受限)：在“ADB 权限受限”一节给出 ColorOS（OPPO / OnePlus）权限监控说明。这针对连接后的权限限制，与客户端无法运行、端口异常、RSA 未授权和设备列表为空是不同阶段。该页标注更新时间较早，具体选项名称需按当前 ROM 核对。
- [scrcpy 官方 FAQ](https://github.com/Genymobile/scrcpy/blob/master/FAQ.md)：区分设备未发现、未授权，以及多套 ADB 版本冲突。不能仅由品牌推断所有问题来自同一根因。
- [Android 官方 OEM 驱动说明](https://developer.android.com/studio/run/oem-usb)：Windows 应按实际设备选择厂商驱动；Google USB 驱动有其设备适用范围。文件传输的 MTP 接口不能代替 ADB 接口检查。
- [公开 USB 规则的维护者源码](https://github.com/M0Rf30/android-udev-rules/blob/main/51-android.rules)：记录 OPPO 相关 VID 22d9 和 OnePlus VID 2a70，并包含跨品牌设备例子。新版仅把 VID / 名称当作品牌线索，不能由它确认机型或 ROM。
- [AOSP ADB 服务协议](https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/services.md)：`host:version` 查询本机服务协议版本，`host:devices-l` 请求设备列表。这是 1.1.6 增加本机服务只读协议检查的依据。

## 1.1.6 改进

1. Windows 检测不再限定华为，覆盖通用 USB ADB 兼容接口、USB 文件传输和异常 USB 候选，保留 OPPO / OnePlus / realme 与华为的品牌线索。正常鼠标、网络 WPD 等非目标项排除；候选项可能来自其它设备，不强行对应到某条 ADB 连接。
2. “连接诊断”与厂商帮助改为通用入口。OPPO 官方排查、ColorOS 已连接后的权限说明、Android OEM 驱动目录和华为专用 HiSuite 分开呈现。
3. 客户端自检通过但设备查询失败时，额外对 `127.0.0.1:5037` 发出一次有总超时的只读 `host:version` 查询，区分正常版本回复、拒绝连接、无响应、异常协议和连接关闭。不自动启动、停止服务或结束其它进程。
4. 已连接后 shell 命令被拒绝时，报告实际失败阶段，避免将 Android 版本读取失败误报为版本过低，或将当前用户查询失败误报为处于系统分身。继续保留数据覆盖安装、版本保护与凭据脱敏。

## 按结果处理

- **客户端自检失败**：先查看文件状态、Win32 错误码和退出码。应用界面可打开不等于随附 ADB 运行正常。
- **设备查询超时**：拔下手机再诊断。如果仍超时，可关闭使用 ADB 的手机助手、模拟器、Android Studio 等程序，在任务管理器结束 `adb.exe` 后重新打开工具；这会断开其它 ADB 调试连接。若诊断显示端口返回非 ADB 协议，应先核对 5037 的占用程序，不将其它程序当作 ADB 结束。
- **客户端与列表正常，但无目标手机**：确认 USB 调试、文件传输模式、数据线和设备管理器中的目标 ADB 接口。按 OPPO 官方指引与实际硬件 ID 检查驱动。
- **unauthorized**：解锁手机，确认是自己的电脑后允许 USB 调试 RSA 授权，再刷新。
- **已连接、具体 shell 操作被拒绝**：这时才查看 ColorOS 权限受限说明。选项名称和开关方向需按该机系统核对；空列表或查询超时不能直接推出应该修改权限监控。
- **后台已激活，但改卡失败**：需要该机的应用错误、系统版本及 SIM 状态；USB 连接恢复不等于系统允许 CarrierConfig 写入。

使用 [1.1.6 工具](https://github.com/DustinYe/TideCard-Android/releases/download/v2.4.1/TideCard-USB-1.1.6-Windows.zip) 完整解压后重新诊断，确认报告顶部显示 1.1.6。随附 APK 仍为原签名 2.4.1。具体主机测试见 [验证记录](VALIDATION.md)；受影响 OPPO 实机效果待用户验证。
