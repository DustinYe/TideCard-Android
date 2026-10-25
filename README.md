# 潮汐改卡 / TideCard Android

[下载安装包](https://github.com/DustinYe/TideCard-Android/releases/latest) · [开源许可与来源](THIRD_PARTY.md) · [发布前查重记录](docs/PUBLICATION_REVIEW.md)

当前版本 **2.2.0**。Android 11 及以上；已在原厂 HyperOS 3 / Android 16 实测应用内通知开关、超级岛选项及本机配对激活流程。请在 [Releases 下载 TideCard-2.2.0.apk](https://github.com/DustinYe/TideCard-Android/releases/tag/v2.2.0) 并按系统提示安装。升级内容见 [CHANGELOG](CHANGELOG.md)。此项目与 Shizuku、Nrfr、Surfboard 均无官方隶属关系。

无需 Root 的 Android SIM 地区配置工具，内含本机无线 ADB 配对和专用后台。无需另装 Shizuku，不占用 VPN 通道，不修改实体 SIM 卡。

## 开源许可

本项目自身代码采用 [Apache-2.0](LICENSE)。第三方组件保留各自许可，详见 [NOTICE](NOTICE) 和 [THIRD_PARTY.md](THIRD_PARTY.md)。应用内可通过“使用说明 → 开源许可”阅读。

## 首次激活

1. 连接 Wi-Fi，在系统开发者选项中开启“无线调试”。
2. 打开潮汐改卡 →“激活 / 配对”→“开始配对”，允许通知。
3. 打开系统无线调试中的“使用配对码配对设备”，保持窗口打开。支持实况通知的系统会显示配对岛；可展开岛卡片，点击“打开配对输入”。若系统设置页面隐藏岛，直接下拉通知栏点击同名按钮。
4. 通知转为普通通知后，展开它，点击“输入六位码”，使用系统通知输入并发送。配对成功显示约两秒勾选反馈，然后自动结束实况通知。
5. 应用通过本机无线调试启动内置后台。返回主界面，看到“内置后台 · 已激活”即可使用。
6. 重启手机后，开启无线调试并点击“启动内置后台”；系统未清除配对记录时可复用配对身份。

Android 的实况通知明确不展示 RemoteInput 回复输入；此手机的无线调试设置还会隐藏第三方悬浮窗。因此本版不会在岛内直接绘制输入框，而是在用户点击后暂时取消通知提升，由 SystemUI 显示输入。没有申请悬浮窗或无障碍权限。系统允许时会尝试展开通知栏，该接口不可用时手动下拉即可。岛的外观、出现位置和图标过渡由系统决定，不能保证不同 ROM 有相同动画。日常改卡通知不请求实况展示。

## 通知与超级岛设置

主页右上角“通知设置”和配对向导中的“通知与超级岛设置”均可进入。

- **持续改卡通知**：内置后台已激活时，可在应用内直接隐藏或重新显示自己的持续改卡通知。只调整本应用 `region_session` 通知类别，不改变其他应用、配对通知或系统通知总权限。关闭时改卡和定时检查继续运行，需回到主页停止并恢复。开关显示系统实际状态，选择由系统保存；重新开启时保留原通知级别和声音、振动等其他属性。
- **配对时显示超级岛**：独立保存的应用开关。关闭后配对通知不请求实况展示，直接提供“输入六位码”；成功反馈同样使用普通通知。开启后仅在系统支持并允许时申请实况展示，输入及成功后清理规则保持不变。正在运行的配对通知也会响应选项变化。
- 内置后台尚未激活或系统不支持直接调整时，可使用页面中的系统通知设置入口。应用不会停止持续检查来隐藏通知，也不会申请通知监听器、无障碍或悬浮窗权限。Android 的“活动应用”列表可能仍显示前台服务。

首次系统调试授权需要用户手动完成。不能自动开启开发者选项、无线调试或绕过锁屏。ROM 兼容性以实机结果为准。通知未找到本机端口时，可用“手动输入端口 / 配对码”；手动配对需要系统配对窗口同时保持打开，例如分屏。电脑 USB 激活是备用方式，应用会复制本次安装专用的激活命令，请仅在自己的电脑使用。

内置后台启动后通过手机内部 Binder 通信工作。在本次测试的 HyperOS 设备上，关闭无线调试后仍可继续改卡；重启或后台被结束后需重新激活。其他 ROM 行为可能不同。

## 改卡与停止

- 目标地区：日本、美国、新加坡、英国、德国、法国、加拿大、澳大利亚、韩国、中国台湾。
- 建议改卡目标区域与 VPN 区域相同。该应用本身不是 VPN。
- 卡槽可选卡 1、卡 2、双卡，推荐改双卡。单卡模式只处理所选卡，双卡模式处理实际存在的卡槽。
- 右下角播放键启动，运行时显示停止图标及动画。应用每 30 秒检查配置，发现偏离才重新应用。
- 点击停止键或前台通知中的停止按钮，恢复原配置并核对。
- 运行中需先停止才能更换目标。拔卡、换卡、清除应用数据或卸载前应先停止恢复。

这改变的是系统运营商国家码和显示配置，不承诺改变所有 SIM 标识，也不保证任何第三方应用必定可用。应用被系统强制结束时，临时配置可能继续存在；重新打开、激活后台后可停止恢复。

## 配置与恢复

使用 `ICarrierConfigLoader.overrideConfig(..., persistent=false)`。开始前读取卡槽及原配置，并将恢复快照提交到应用私有存储；发现其他工具已有临时或持久覆盖时拒绝开始。停止时先恢复真实原国家码，再清除本次覆盖，处理部分系统清除配置后仍缓存旧国家码的行为。恢复失败会保留快照及待恢复状态，重新连接后可重试，不能把失败当作已停止。

## 本机通信与权限

INTERNET 权限用于访问 `127.0.0.1` 的系统无线调试端口；本机服务发现通过 Android NSD 查找端口，只接受当前手机的网卡地址。代码没有远程 API、广告或统计上报。无需位置、电话、短信、联系人或存储权限。通知权限用于配对码输入及运行状态；POST_PROMOTED_NOTIFICATIONS 用于用户主动开始配对时的短时实况状态，EXPAND_STATUS_BAR 用于用户点击配对入口后尝试展开系统通知栏。

ADB RSA 身份存放于应用私有目录，使用 AndroidKeyStore 的 AES-GCM 密钥加密；配对码不持久保存。应用备份关闭。卸载或清除数据后需重新配对。内置后台只暴露改卡所需的固定操作，不向其他应用提供任意命令执行接口。后台通过 shell UID、应用 UID、安装专用随机令牌、版本号及 Binder 调用方身份校验连接。

## Android Studio 构建

Java 17 源码、Android SDK 36、AGP 8.13.0、Gradle 8.14.3，最低 Android 11。建议 Android Studio 使用 JDK 21。打开此目录同步后构建：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

签名从项目外 `../tidecard-signing/key.properties` 读取，`storeFile` 相对项目根目录。仓库不提供发布私钥。开发者可在项目旁创建自己的 tidecard-signing 目录，放入密钥和配置。无此文件时可使用 Android Studio 的 Generate Signed Bundle / APK。覆盖升级必须保留相同签名。

```properties
storeFile=../tidecard-signing/release.jks
storePassword=your-password
keyAlias=tidecard
keyPassword=your-password
```

不要公开签名密钥、密码或激活令牌。公开仓库不包含本机 SDK 路径、Gradle 缓存及签名秘密。

## 测试

`app/src/androidTest` 是独立测试 APK，不会编入正式 APK。测试实际系统读回的卡槽国家码，覆盖单卡范围、双卡、不同地区、停止恢复、偏离自动纠正、恢复失败保留快照和通知停止。另有 `-e uiOnly true` 地区选择测试、`-e wirelessOnly true` 已配对后的独立无线激活测试。

`-e notificationOnly true` 只检查配对通知状态、普通通知输入、过期操作拒绝、成功反馈、取消、超时和晚到回调清理，并核对原改卡会话及备份保持不变。成功 UI 注入仅验证界面，不代替真实密码学配对测试。该模式会保存几张临时手机截图到应用自己的 externalFilesDir，用于本机视觉检查，不会上传。

`-e settingsOnly true` 实际点击通知设置开关，检查显示/隐藏通知时持续服务仍在运行、通知类别其他属性保留、界面重新创建后选择持久化、超级岛关闭后直接输入以及运行中通知和成功反馈遵守选项。测试恢复原通知选择，不修改目标地区或恢复快照。部分 HyperOS 会拦截测试进程自行显示界面，测试提示等待主界面时，需由 ADB 前台启动 MainActivity。

```powershell
.\gradlew.bat :app:assembleRelease :app:assembleReleaseAndroidTest
adb install -r app\build\outputs\apk\release\app-release.apk
adb install -r -t app\build\outputs\apk\androidTest\release\app-release-androidTest.apk
adb shell am instrument -w -r app.tidecard.test/app.tidecard.test.SmokeRunner
```

部分 ROM 限制后台启动测试界面，需要在测试开始后另用 ADB 启动 `app.tidecard/.MainActivity`。测试会临时修改 SIM 地区，结束时恢复。无线启动测试要求先完成配对，并先停止现有专用后台以证明启动路径。

## 第三方组件

Conscrypt 2.8.0（Apache 2.0），以及官方 Shizuku 13.6.0 发布包中的 ADB SPAKE2 配对本机库（相关 Apache 2.0、BoringSSL/第三方许可证随应用 assets 交付）。四种 ABI 的预编译库来源、原始 APK 哈希及说明见 `app/src/main/assets/THIRD_PARTY_NOTICES.txt`，JNI 包装对应源码见 `third_party/shizuku`。这些库用于本机 ADB 配对，未打包或要求安装外部 Shizuku 管理器。

参考源码：https://github.com/RikkaApps/Shizuku
系统接口：https://developer.android.com/reference/android/telephony/CarrierConfigManager
