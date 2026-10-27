# 新系统 shell 拒绝写入的兼容测试

报告设备：Redmi K80 Ultra，HyperOS `3.0.307.0.WONCNXM.C11`。截图报错为 `overrideConfig cannot be invoked by shell`。本轮未连接到该设备，不能确认实际 Android API、补丁日期或兼容结果。

## 参考案例与实现边界

- 原版 [Nrfr issue #138](https://github.com/Ackites/Nrfr/issues/138) 有 HyperOS 3 同文报错；[#121](https://github.com/Ackites/Nrfr/issues/121) 指向修复分支。
- 参考代码为 [a5533348/Nrfr 的 PrivilegedCarrierConfigRunner](https://github.com/a5533348/Nrfr/blob/6c05f224dc802d0df952db93a6d2852a91c9edb0/app/src/main/java/com/github/nrfr/manager/PrivilegedCarrierConfigRunner.kt)，Apache-2.0。参考其 `INSTR_FLAG_NO_RESTART` 和测试授权思路，未引入其 APK、Shizuku 依赖或 HiddenApiBypass 库。
- AOSP [ActivityManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/am/ActivityManagerService.java) 的无重启 instrumentation 路径，以及 [AccessCheckDelegateHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/am/AccessCheckDelegateHelper.java) 的 shell 测试授权约束，是实现依据；OEM 运行时是否一致仍待验证。

## TideCard 的改动

1. 保留原直接写入路径；仅在明确遇到 shell 身份拒绝后启用兼容路径。
2. 已授权的专用 shell 后台启动目标为 TideCard 自身的无重启 instrumentation。没有这个标志时不改用会重启界面的普通模式。
3. 每次请求绑定应用 UID、原 PID 和随机 nonce。授权与完成回执不经过会被原同步调用占用的业务锁；重复、过期和不同进程回执被拒绝。
4. 临时委派只包含 `MODIFY_PHONE_STATE`。应用端使用公开 Parcel/Binder 传输，不在普通应用 UID 中反射隐藏接口；服务和事务编号由后台从该设备的接口解析。
5. 只允许既定 CarrierConfig 描述符和三个受支持字段，或用于恢复的 null bundle；所有写入仍为非持久模式。收到写入和收尾回执后才报告成功；超时保留备份。
6. 失败后的原配置回读确认与部分写入记录决定是否可以清理快照。未知结果不会被当作零修改；恢复阶段只有原值写入成功的卡才进入清除覆盖步骤。

没有增加清单权限，但兼容操作会短暂将 shell 的上述测试权限委派给应用。代码包含 finally 清理、完成清理和超时清理；这些实际运行行为尚未在此设备验证。

## 验证状态

- 已通过 Java 主机检查：回执 UID/PID/nonce、重复和过期拒绝、部分/未知写入保留备份；原配置解析与未知字段保护回归。
- 已构建 Release 和 Android 测试 APK，lint 通过；沿用原发布签名。
- 未安装到 K80 Ultra，未运行新的设备写入、权限撤回、无重启 UI 或停止恢复测试。现有测试 APK仅构建，不能据此宣称实机通过。
- 稳定版保持 2.2.1；本版本只作为 prerelease 分发。覆盖安装后若后台显示待激活，需要通过应用原有向导重新启动内置后台。

该设备需验证：开始后实际国家码是否改变；界面是否保持运行；停止后是否回到原国家码；再次开始/停止是否正常。失败时应保留完整错误提示，不能只以“后台已激活”作为成功证据。
