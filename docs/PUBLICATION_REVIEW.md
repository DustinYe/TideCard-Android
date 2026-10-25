# 发布前查重与许可核对

检查日期：2026-10-25。目标仓库：`DustinYe/TideCard-Android`。这是基于当前公开资料、当前源码和已知组件的工程核对记录，不是法律意见或不侵权保证。

## GitHub 查重结果

已通过 GitHub API 检查当前账号所有自有仓库，未发现同名或同类仓库。

| 查询 | 本次结果 | 说明 |
|---|---:|---|
| `TideCard in:name` | 1 | `Tidecard/tide-referral`，金融推广页面，与 Android SIM 工具无关 |
| `"潮汐改卡" in:name,description,readme` | 0 | 未检索到相同中文名称的项目 |
| `"TideCard" android in:readme` | 2 | 冲浪预报 / 河流水文项目，不是 SIM 改卡应用 |

检索会受索引、搜索范围、时点及权限影响。没有“搜不到就必定不侵权”的结论。英文 TideCard 存在其他用途，未完成任何地区的商标注册检索。

## 已发现的相关项目

| 项目 | 功能相近处 | 仓库显示许可 | 本项目的处理 |
|---|---|---|---|
| [Ackites/Nrfr](https://github.com/Ackites/Nrfr) | 免 Root 覆盖系统 SIM 国家码 / 运营商名 | Apache-2.0 | 方案参考，明确致谢并随包保留许可；单独 Java 后台及恢复流程 |
| [lmh-codes/Nrfr](https://github.com/lmh-codes/Nrfr) | 双卡覆盖、还原、单 APK | Apache-2.0 | 查重发现，未从此分支新增复制代码 |
| [eigenlux-ai/roamer](https://github.com/eigenlux-ai/roamer) | 国家码、运营商名及恢复 | MIT | 查重发现，未从该项目新增复制代码 |
| [JHR-deve/Nrfr-Android17](https://github.com/JHR-deve/Nrfr-Android17) | Nrfr 的 Android 17 适配 | Apache-2.0，含 MIT 声明 | 查重发现，未合入适配代码，未宣称支持 Android 17 |
| [Ritel-T/SamsungRegionOverride](https://github.com/Ritel-T/SamsungRegionOverride) | 地区覆盖、短期会话及恢复 | MIT | 查重发现，未从该项目新增复制代码 |
| [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) | 无线配对、ADB 启动及特权进程 | Apache-2.0 | 实际使用 / Java 改写参考；补齐二进制来源、修改标记和传递依赖许可 |

## 来源审查和修正

- 保留直接使用的 Shizuku v13.6.0 原生配对库，核验官方 APK 哈希，并记录每个 ABI 的库文件哈希。
- 检查该版本上游 Gradle / CMake 依赖，补入精确 BoringSSL 20250114 的 ISC、OpenSSL、SSLeay、MIT 声明，以及 LLVM libc++ 的 Apache-2.0 with LLVM Exceptions / 历史许可文本。
- 保留 Conscrypt 2.8.0 和 BoringSSL 许可副本。
- 对 Java ADB / 配对 / 身份 / 发现适配文件添加来源与修改标记，并在 `NOTICE` 中说明使用范围。
- 添加根目录 `LICENSE`、`NOTICE`、`THIRD_PARTY.md`，APK 也打包可阅读副本，增加“使用说明 → 开源许可”入口。
- 保留 Gradle wrapper 的原版权头部。没有打包 Surfboard 的代码、图标、截图或品牌素材。
- 发布区不包含签名私钥、签名密码、ADB 身份、激活令牌、个人设备序列号、本机 SDK 路径、调试转储或用户截图。

本项目自身代码采用 Apache-2.0，第三方部分分别保留原条款。相似功能本身不足以判定版权侵权；实际表达、源代码、素材、授权条款和可能的商标 / 专利仍需分别评估。

## 可核对的依据

- [Apache License 2.0 第 4 节：再分发条件](https://www.apache.org/licenses/LICENSE-2.0)
- [GitHub：为仓库选择和附带许可证](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)
- [U.S. Copyright Office：版权保护的范围](https://www.copyright.gov/help/faq/faq-protect.html)（仅作为“方法与表达有区别”的一般性解释，不能替代适用司法辖区的法律分析）
- 上游逐项链接与完整许可位置见 [THIRD_PARTY.md](../THIRD_PARTY.md)。

本次检查不涵盖未公开源码、所有代码历史、全球商标或专利检索，也不能判断未知第三方权利。若收到具体权利主张，应根据具体文件及来源重新核对。
