using System;
using System.ComponentModel;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;

namespace TideCardUsb {
    internal sealed class AdbRunException : InvalidOperationException {
        internal readonly string Kind;
        internal AdbRunException(string kind,string message):base(message){Kind=kind;}
    }
    internal sealed class AdbRuntime {
        internal bool Exe,Api,UsbApi,Os64;
        internal string Machine="未知",WindowsVersion="未知";
        internal static string ReadMachine(string path) {
            try {using(BinaryReader reader=new BinaryReader(File.OpenRead(path))) {
                if(reader.BaseStream.Length<64||reader.ReadUInt16()!=0x5a4d)return "无效 PE";
                reader.BaseStream.Position=0x3c;int offset=reader.ReadInt32();
                if(offset<64||offset>1048576||offset>reader.BaseStream.Length-6)return "无效 PE";
                reader.BaseStream.Position=offset;if(reader.ReadUInt32()!=0x4550)return "无效 PE";
                ushort machine=reader.ReadUInt16();return machine==0x8664?"x64":machine==0x14c?"x86":machine==0xaa64?"ARM64":"未知";
            }}catch(Exception){return "未读取";}
        }
        internal static AdbRuntime Inspect(string path) {
            string directory=Path.GetDirectoryName(path);
            return new AdbRuntime {Exe=File.Exists(path),Api=File.Exists(Path.Combine(directory,"AdbWinApi.dll")),UsbApi=File.Exists(Path.Combine(directory,"AdbWinUsbApi.dll")),Os64=Environment.Is64BitOperatingSystem,Machine=ReadMachine(path),WindowsVersion=Environment.OSVersion.Version.ToString()};
        }
        internal string Describe() {
            return "Windows="+WindowsVersion+"；系统="+(Os64?"64 位":"32 位")+"；工具进程="+(Environment.Is64BitProcess?"64 位":"32 位")+"；ADB 架构="+Machine+Environment.NewLine+
                "随附文件：adb.exe="+(Exe?"存在":"缺失")+"；AdbWinApi.dll="+(Api?"存在":"缺失")+"；AdbWinUsbApi.dll="+(UsbApi?"存在":"缺失");
        }
        internal string Advice() {
            if(!Exe||!Api||!UsbApi)return "随附 ADB 文件不完整，请重新完整解压整个工具包，保留 adb 子文件夹及两个 DLL；不要只运行单独复制的 EXE。";
            if(!Os64&&Machine=="x64")return "当前是 32 位 Windows，随附 ADB 为 x64，两者不兼容。需要支持该 Windows 架构的官方 ADB 或改用兼容电脑；不要通过切换手机 USB 模式反复重试。";
            return "";
        }
    }
    /** Records safe error categories and numeric codes, never arbitrary process output or paths. */
    internal sealed class AdbProbe {
        internal AdbResult Result=new AdbResult(-1,"");
        internal bool Ran,Returned,Ok;
        internal string Issue="not-run",Advice="前一步未成功，未执行此项。",Stage;
        internal int? NativeCode;
        internal static AdbProbe Run(IAdb adb,string stage,string[] args,string expected,int timeout) {
            AdbProbe probe=new AdbProbe {Ran=true,Stage=stage};
            try {
                probe.Result=adb.Run(args,null,timeout);probe.Returned=true;
                probe.Ok=probe.Result.Code==0&&probe.Result.Output.Contains(expected);
                if(probe.Ok){probe.Issue="ok";probe.Advice="正常";}
                else probe.ClassifyExit();
            }catch(AdbRunException e){probe.Issue=e.Kind;probe.Advice=e.Kind=="missing-executable"?"找不到随附 adb.exe，请重新完整解压工具包。":e.Kind=="client-timeout"?"ADB 客户端等待超时；尚未得到有效结果，请检查本机服务状态。":"ADB 输出读取超时；尚未得到完整结果。";}
            catch(Win32Exception e){probe.NativeCode=e.NativeErrorCode;probe.Issue="process-start";probe.Advice=StartAdvice(e.NativeErrorCode);}
            catch(Exception e){probe.Issue="exception-"+e.GetType().Name;probe.Advice="ADB 调用发生异常，尚未完成检查；诊断仅记录异常类型，未复制含路径等信息的异常正文。";}
            return probe;
        }
        private static string StartAdvice(int code) {
            if(code==2||code==3)return "Windows 未找到启动文件或路径，请重新完整解压工具包。";
            if(code==5)return "Windows 拒绝启动 ADB；请检查文件权限或系统的具体拦截提示。";
            if(code==126)return "Windows 无法加载所需模块，请检查随附 DLL 是否完整。";
            if(code==193||code==216)return "Windows 拒绝此可执行文件格式；需核对 ADB / 系统架构、文件完整性及 Windows 版本。";
            if(code==1150)return "该可执行文件需要更新的 Windows 版本。";
            return "Windows 未能启动 ADB；请根据记录的 Win32 错误码检查运行环境。";
        }
        private void ClassifyExit() {
            uint code=unchecked((uint)Result.Code);string output=Result.Output??"";
            if(code==0xc0000135){Issue="missing-module";Advice="ADB 在 Windows 加载阶段退出：缺少所需 DLL / 运行模块。请先检查随附文件完整性。";}
            else if(code==0xc000007b){Issue="invalid-image";Advice="ADB 加载了无效或架构不匹配的可执行文件 / DLL；请使用同一完整官方工具包。";}
            else if(code==0xc0000142){Issue="initialization-failed";Advice="Windows 未能初始化 ADB 或其依赖模块。";}
            else if(Regex.IsMatch(output,@"server version[^\r\n]*(?:doesn't match|does not match)|server is out of date",RegexOptions.IgnoreCase)){Issue="server-version";Advice="ADB 报告客户端与服务版本不匹配；请检查其它调试工具的 ADB。本工具不会额外执行 kill-server。";}
            else if(Regex.IsMatch(output,@"protocol fault|failed to read response from server|cannot parse version string",RegexOptions.IgnoreCase)){Issue="server-protocol";Advice="本机 ADB 服务返回了无效协议响应；先检查本机服务和其它调试工具，不是手机配对码问题。";}
            else if(Regex.IsMatch(output,@"cannot connect to daemon|cannot connect to server|failed to start daemon|daemon (?:did not|didn't) ACK|failed to check server version",RegexOptions.IgnoreCase)){Issue="server-connect";Advice="ADB 客户端无法启动或连接本机服务。请检查本机 5037 端口、服务状态与系统具体提示；此时尚未完成手机设备检查。";}
            else {Issue=Result.Code==0?"unexpected-output":"client-exit";Advice=Result.Code==0?"ADB 返回成功退出码，但没有预期结果，未作为有效设备列表处理。":"ADB 非零退出，未得到预期结果。诊断已记录退出码；未知错误需进一步查看该电脑本地的原始命令输出。";}
        }
        internal string Describe() {
            StringBuilder text=new StringBuilder();text.Append(Stage??"ADB").Append("：").Append(Ok?"正常":"未完成").Append("；分类=").Append(Issue);
            if(Returned)text.Append("；退出码=").Append(Result.Code).Append(" (0x").Append(unchecked((uint)Result.Code).ToString("X8")).Append(')');
            if(NativeCode.HasValue)text.Append("；Win32=").Append(NativeCode.Value);
            return text.AppendLine().Append(Advice).ToString();
        }
    }
}
