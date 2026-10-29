**潮汐改卡 2.4.1 正式版**：修复部分系统在后台激活后读取 SIM 卡槽信息时的兼容性问题。

**Windows 配套工具更新：推荐下载本页附件中的 [TideCard-USB-1.1.5-Windows.zip](https://github.com/DustinYe/TideCard-Android/releases/download/v2.4.1/TideCard-USB-1.1.5-Windows.zip)。** 1.1.5 随附相同的原签名 Android 2.4.1 APK，包含华为 / 鸿蒙连接诊断和 ADB 环境隔离，并补充客户端自检、错误码和文件缺失检查，华为实机效果仍待验证。旧版 1.1.3 / 1.1.4 作为历史附件保留。

- 修复 `Integer.intValue()` 空指针：系统返回空订阅编号时按“无有效订阅”处理。
- 兼容旧系统返回的 `int[]` 订阅编号格式；空数组和无效编号不会中断另一张卡的读取。
- 保留卡槽与订阅编号身份校验。多个不同有效编号或未知返回格式会停止操作，避免误选另一张 SIM。
- 增加对应简体中文、繁體中文和英语提示。Android 版本码为 12，沿用原签名与应用数据。
- 原始配套 Windows USB 工具为 **1.1.3**；电脑激活现在推荐使用后续更新的 **1.1.5**，随附同版 APK。

**本版已完成主机回归和构建检查，截图报错手机尚未实测。** 用户提供了最新版报错截图，但机型、系统版本及卡槽状态未知。此次修复针对源码中确认存在、且能复现同类异常的缺陷；正式发布不表示已经确认该手机能完成改卡，也不改变系统自身的写入权限限制。

验证包括：22 项 JVM 检查复现旧版空值拆箱及数组类型转换错误，并覆盖整数 / 数组返回、空值、无效编号、单卡 / 双卡选择、卡槽隔离、重复编号和歧义拒绝。Release / Android 测试包构建、lint 与原签名校验通过。Windows 工具的模拟流程与 DPI / 窄窗口布局检查范围见 `docs/VALIDATION.md`。本轮没有执行新的 SIM 写入。

**下载验证方法**

1. 下载 `TideCard-2.4.1.apk`。如已有改卡会话，先停止并恢复，再直接覆盖安装。如果同类错误导致无法恢复，请保留数据覆盖安装修复版，重新激活后优先执行停止恢复；不要卸载或清除应用数据。
2. 升级后重新启动内置后台，通常可复用已有配对身份。电脑激活请使用本页的 `TideCard-USB-1.1.5-Windows.zip`。
3. 先确认主界面能显示实际 SIM 国家码，再按原需求选择卡槽和地区测试。如果仍报错，请反馈手机型号、Android / 系统版本、已插入与启用的 SIM 数量，以及新错误截图。

2.4.0 用户也可在“设置 → 检查更新”中获取此版，下载校验后由系统安装器确认安装。

附件包含签名 APK、对应源码、Windows 工具、SHA-256 校验文件及签名摘要。Android 2.4.1 与原始配套 1.1.3 源码以 `v2.4.1` 标签为准；新增的 USB 1.1.5 源码以 `usb-v1.1.5` 标签及 `TideCard-USB-1.1.5-source.zip` 为准。

`SHA256SUMS.txt` 保留原始附件的校验值；新增 1.1.5 工具包和源码包请使用 **`SHA256SUMS-USB-1.1.5.txt`** 校验。1.1.5 的具体调整和主机验证范围见 [独立发布说明](https://github.com/DustinYe/TideCard-Android/releases/tag/usb-v1.1.5)。

**English**

TideCard 2.4.1 fixes null subscription-ID unboxing and supports legacy array results after backend activation. Empty slots are excluded, while ambiguous or unsupported responses stop the operation instead of guessing another SIM. The recommended matching Windows USB activator is now 1.1.5, also available on this release page; the original 1.1.3 remains as a historical asset. Both bundle the same signed Android 2.4.1 APK. Use SHA256SUMS-USB-1.1.5.txt to verify the added tool and source ZIPs. Host regression and build checks passed; the reporter's unidentified phone and the Huawei connection still need device validation. Restore an existing session before installing over the current app, reactivate the backend, then verify SIM readback and report the device/OS details if an error remains.
