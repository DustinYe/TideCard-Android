# 第三方组件与来源

本项目自身代码采用 Apache-2.0，第三方部分分别保留原许可。完整声明在根目录 `NOTICE` 和 APK 的 `app/src/main/assets`，应用内可通过“使用说明 → 开源许可”阅读。

| 组件 | 版本 / 来源 | 实际用途 | 许可 |
|---|---|---|---|
| Shizuku ADB pairing | 官方 v13.6.0 发布包，4 ABI `libadb.so` | SPAKE2 配对原生计算 | Apache-2.0 |
| Shizuku ADB / identity flow | v13.6.0 的 AdbClient、AdbPairingClient、AdbKey、AdbMdns | Java 改写 / 适配的参考 | Apache-2.0 |
| BoringSSL（配对库静态依赖） | `io.github.vvb2060.ndk:boringssl:20250114`，源提交 `571a7432a19592c620fa316abde47770dad4f82b` | libadb.so 加密实现 | ISC / OpenSSL / SSLeay / MIT，完整原许可随包 |
| LLVM libc++ | 上游依赖 `org.lsposed.libcxx:libcxx:27.0.12077973` | 原生 C++ 运行库 | Apache-2.0 WITH LLVM-exception 及历史声明 |
| Conscrypt | `org.conscrypt:conscrypt-android:2.8.0` | 本机 ADB TLS 1.3 | Apache-2.0 及附带声明 |
| Nrfr | [Ackites/Nrfr](https://github.com/Ackites/Nrfr) | CarrierConfig API 方案参考 | Apache-2.0 |
| Nrfr 兼容分支 | [a5533348/Nrfr](https://github.com/a5533348/Nrfr/tree/6c05f224dc802d0df952db93a6d2852a91c9edb0) | 无重启 instrumentation 与临时测试授权机制参考；本项目使用独立回执和固定字段实现 | Apache-2.0 |
| Gradle wrapper | 8.14.3，保留脚本原始头部 | 构建工具 | Apache-2.0 |

## 直接使用和改写的边界

- `app/src/main/jniLibs/*/libadb.so` 来自官方 Shizuku 发布 APK，未修改二进制。原 APK SHA-256 为 `6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f`，下载地址为 https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk 。每个原生文件的哈希记录在 `third_party/native-libraries.json`。
- `third_party/shizuku` 保存对应 JNI、辅助源码和上游 CMake 配置作为来源参考；应用默认直接打包已验证的预编译库。重新构建原生库需按照上游项目配置 Android NDK、Prefab 及上述精确依赖，不能把这个目录当作完整独立的上游构建树。
- `adb/AdbClient.java`、`adb/PairingClient.java`、`adb/AdbIdentity.java`、`LocalDiscovery.java` 以及 `moe/shizuku/manager/adb/PairingContext.java` 是本项目的 Java 适配 / 改写，已在文件头标记来源。主要改动：仅限本机端点、超时与数据长度检查、加密身份保存、通知配对、固定启动命令、独立后台鉴权。没有把这些协议流程宣称为完全原创。
- SIM 后台为本项目单独编写的 Java 实现；参考了 Nrfr 对 CarrierConfig 国家码和运营商名的使用，增加非持久覆盖、原配置快照、卡槽范围、持续保持与恢复逻辑。
- Roamer、Nrfr 的其他分支及 SamsungRegionOverride 是查重时发现的相关项目，本次没有从这些项目新增复制代码。
- Surfboard 仅是最初交互描述的参考，没有使用其源代码、图标、名称、商标或图片素材。播放 / 停止动画是本项目独立 Canvas 实现；本项目与其没有隶属关系。

## 上游核对入口

- Shizuku 许可：https://github.com/RikkaApps/Shizuku/blob/v13.6.0/LICENSE
- Shizuku 配对源码：https://github.com/RikkaApps/Shizuku/blob/v13.6.0/manager/src/main/jni/adb_pairing.cpp
- Shizuku 依赖版本：https://github.com/RikkaApps/Shizuku/blob/v13.6.0/manager/build.gradle
- BoringSSL 包装及精确源码：https://github.com/vvb2060/BoringSSL_Android/tree/20250114
- LLVM 许可：https://github.com/LSPosed/libcxx/blob/master/LICENSE.TXT
- Conscrypt：https://github.com/google/conscrypt
- Nrfr 许可：https://github.com/Ackites/Nrfr/blob/master/LICENSE

`NOTICE` 是来源说明，不代表上述项目认可或担保本项目。GitHub 查重与许可核对的范围和限制见 `docs/PUBLICATION_REVIEW.md`。
