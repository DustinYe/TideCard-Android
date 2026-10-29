# Windows USB 工具缩放适配

## 1.1.4 诊断窗口验证

新增“连接诊断 / 华为”按钮与可滚动的诊断窗口。主窗口与诊断窗口各通过 14 组 DPI / 窄窗口布局模拟，共 1383 项文字高度、控件边界与滚动可达性断言；实际当前 DPI 为 240。诊断窗口按钮在窄窗口中自动换行，报告可滚动阅读。测试只绘制本工具自己的窗口，不改变 Windows 显示配置。新增诊断行为另通过 52 项断言，原有 43 项模拟激活检查保留。华为手机不在现场，未进行手机连接或激活实测。

## 1.0.1 修复

原 WinForms 窗口采用固定行高，缺少 96 DPI 设计基准和 .NET Framework 高 DPI 配置。高缩放下字体增大，标题、说明和按钮文字超过行高，截图中出现裁切。

现在明确以 96 DPI 设计，编译目标声明 .NET Framework 4.8，使用 `App.config` 开启 `PerMonitorV2`；去掉会覆盖配置的旧清单 `dpiAware` 设置。微软说明：[High DPI support in Windows Forms](https://learn.microsoft.com/en-us/dotnet/desktop/winforms/high-dpi-support-in-windows-forms?view=netframeworkdesktop-4.8)、[AutoScaleDimensions](https://learn.microsoft.com/en-us/dotnet/api/system.windows.forms.containercontrol.autoscaledimensions)。

文字行和按钮按内容计算高度，刷新按钮不再使用固定宽度，状态提示支持换行。外层滚动面板在小窗口中承载完整内容，显式同步滚动范围；日志框不抢占初始键盘焦点。显示器 DPI 改变后仍由 WinForms 负责缩放，应用只约束窗口不超出当前工作区。

## 验证

`Build.ps1` 对桌面 EXE 与检查程序使用相同的框架声明、清单和配置。`LayoutChecks.cs` 在真实当前 DPI 上启动窗口，再对 96 / 120 / 144 / 168 / 192 / 216 / 240 DPI 对应的字号与客户区尺寸做布局模拟，包含 740×650 和 520×400 的逻辑客户区、三种状态提示，检查文字所需高度、父控件边界及滚动到底部后的按钮可达性。没有改变 Windows 全局缩放设置。

本次真实窗口 DPI 为 **240（250%）**，另通过 14 组布局模拟、1100 项尺寸 / 裁切 / 可达性断言和原有 43 项 USB 桌面检查。模拟会同时调整控件几何尺寸和字体点数，以当前真实 DPI 补偿字体。它不是切换真实显示器的 DPI，不代表已经在七种 Windows 显示配置上逐一实测。运行记录和渲染图输出到忽略的 `desktop/usb-activator/build/`，不会打包测试程序或测试截图。

此外对实际新 EXE 做窗口截图检查，确认完整标题、全部三步说明和底部按钮显示。USB 流程模拟与随附 ADB 启动检查保留；没有手机连接，本次不涉及新的手机安装或改卡测试。Android APK、签名和激活协议保持不变。
