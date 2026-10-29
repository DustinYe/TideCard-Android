using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace TideCardUsb {
    internal sealed class AdbResult {
        internal int Code; internal string Output;
        internal AdbResult(int code, string output) { Code=code; Output=output; }
    }
    internal interface IAdb { AdbResult Run(string[] args, string input, int timeoutMs); }
    internal sealed class Adb : IAdb {
        private readonly string path;
        internal Adb(string path) { this.path=path; }
        internal static string Quote(string value) {
            // Windows CommandLineToArgvW escaping, including trailing backslashes.
            StringBuilder b=new StringBuilder("\""); int slashes=0;
            foreach(char c in value) {
                if(c=='\\') { slashes++; continue; }
                if(c=='"') { b.Append('\\',slashes*2+1); b.Append(c); }
                else { b.Append('\\',slashes); b.Append(c); }
                slashes=0;
            }
            b.Append('\\',slashes*2); b.Append('"'); return b.ToString();
        }
        internal static void ConfigureEnvironment(ProcessStartInfo info) {
            foreach(string name in ConnectionDiagnostics.IsolatedEnvironment)info.EnvironmentVariables.Remove(name);
            info.EnvironmentVariables["ADB_LIBUSB"]="0";
        }
        internal ProcessStartInfo CreateStartInfo(string[] args,bool input) {
            StringBuilder joined=new StringBuilder("-P 5037");foreach(string arg in args)joined.Append(' ').Append(Quote(arg));
            ProcessStartInfo info=new ProcessStartInfo(path,joined.ToString());
            info.UseShellExecute=false;info.CreateNoWindow=true;info.WorkingDirectory=Path.GetDirectoryName(path);
            info.RedirectStandardOutput=true;info.RedirectStandardError=true;info.RedirectStandardInput=input;
            info.StandardOutputEncoding=Encoding.UTF8;info.StandardErrorEncoding=Encoding.UTF8;
            ConfigureEnvironment(info);return info;
        }
        public AdbResult Run(string[] args,string input,int timeoutMs) {
            if(!File.Exists(path))throw new InvalidOperationException("找不到随附的 adb.exe，请先完整解压 ZIP 后运行。");
            ProcessStartInfo info=CreateStartInfo(args,input!=null);
            using(Process p=new Process()) {
                p.StartInfo=info; p.Start();
                Task<string> output=p.StandardOutput.ReadToEndAsync(), error=p.StandardError.ReadToEndAsync();
                if(input!=null) { p.StandardInput.Write(input); p.StandardInput.Close(); }
                if(!p.WaitForExit(timeoutMs)) { try { p.Kill(); } catch(InvalidOperationException) {} throw new InvalidOperationException("电脑操作超时。请检查手机上的解锁、USB 授权或安装提示后重试；没有清除应用数据。"); }
                if(!Task.WaitAll(new Task[]{output,error},3000))throw new InvalidOperationException("ADB 输出读取超时，请重新连接并重试；未清除应用数据。");
                return new AdbResult(p.ExitCode,output.Result+"\n"+error.Result);
            }
        }
    }
    internal sealed class Device {
        internal string Serial, State, Model;
        public override string ToString() { return Model+"  ·  "+Serial+"  ·  "+(State=="device"?"已授权":State=="unauthorized"?"待手机授权":State=="offline"?"离线":State); }
        internal static List<Device> Parse(string text) {
            List<Device> list=new List<Device>();
            foreach(string line in text.Replace("\r","").Split('\n')) {
                Match m=Regex.Match(line,@"^(\S+)\s+(device|unauthorized|offline|recovery|sideload)(?:\s|$)");
                if(!m.Success)continue; string serial=m.Groups[1].Value;
                // ADB lists TCP/mDNS/emulator transports alongside USB. This tool only selects USB-like serials.
                if(serial.Contains(":")||serial.StartsWith("emulator-",StringComparison.OrdinalIgnoreCase)||serial.IndexOf("_adb",StringComparison.OrdinalIgnoreCase)>=0)continue;
                Match model=Regex.Match(line,@"\bmodel:(\S+)");
                list.Add(new Device { Serial=serial, State=m.Groups[2].Value, Model=model.Success?model.Groups[1].Value.Replace('_',' '):"Android 手机" });
            }
            return list;
        }
    }
    internal sealed class UsbRecord {
        internal int Version, Owner; internal string Token, Apk;
        internal static UsbRecord Parse(string output) {
            MatchCollection matches=Regex.Matches(output,@"TIDEUSB1\|(\d+)\|(\d+)\|([A-Za-z0-9_-]{43})\|([A-Za-z0-9+/]+={0,2})(?=[\s}\],]|$)");
            if(matches.Count!=1)throw new InvalidOperationException("手机没有返回有效激活信息。请使用随附 APK，并保持手机解锁。");
            Match m=matches[0]; int version,owner;
            if(!int.TryParse(m.Groups[1].Value,out version)||!int.TryParse(m.Groups[2].Value,out owner)||version!=BundleInfo.VersionCode||owner<10000||owner>=100000)throw new InvalidOperationException("手机应用版本或用户身份不匹配，请使用手机主用户和配套版本。");
            string apk;
            try { apk=new UTF8Encoding(false,true).GetString(Convert.FromBase64String(m.Groups[4].Value)); }
            catch(Exception) { throw new InvalidOperationException("手机返回的 APK 路径无法识别。"); }
            if(apk.Length>4096||!apk.StartsWith("/data/app/",StringComparison.Ordinal)||!apk.EndsWith("/base.apk",StringComparison.Ordinal)||Regex.IsMatch(apk,@"[\x00-\x1f\x7f]"))throw new InvalidOperationException("手机返回的 APK 路径不受支持。");
            return new UsbRecord {Version=version,Owner=owner,Token=m.Groups[3].Value,Apk=apk};
        }
        internal string Script() {
            // Send credentials over stdin, never as a Windows command-line argument or UI log.
            string path="'"+Apk.Replace("'","'\"'\"'")+"'";
            return "CLASSPATH="+path+" /system/bin/nohup /system/bin/setsid /system/bin/app_process /system/bin --nice-name=app.tidecard:bridge app.tidecard.Bootstrap '"+Token+"' "+Owner+" >/data/local/tmp/tidecard-bridge.log 2>&1 </dev/null &\nsleep 2\n";
        }
        internal static bool Ready(string output) {
            MatchCollection matches=Regex.Matches(output,@"TIDEUSBSTATUS1\|(\d+)\|([01])(?=[\s}\],]|$)");
            return matches.Count==1&&matches[0].Groups[1].Value==BundleInfo.VersionCode.ToString()&&matches[0].Groups[2].Value=="1";
        }
    }
    internal sealed class Workflow {
        private readonly IAdb adb; private readonly Action<string> log; private readonly Action<int> delay;
        internal Workflow(IAdb adb,Action<string> log) : this(adb,log,Thread.Sleep) {}
        internal Workflow(IAdb adb,Action<string> log,Action<int> delay) { this.adb=adb; this.log=log; this.delay=delay; }
        internal List<Device> Devices() {
            AdbResult r=adb.Run(new string[]{"devices","-l"},null,15000);
            if(r.Code!=0||!r.Output.Contains("List of devices attached"))throw new InvalidOperationException("ADB 未能列出设备。请使用连接诊断检查 USB 接口、驱动和本机调试环境。");
            return Device.Parse(r.Output);
        }
        private AdbResult On(string serial,int timeout,string input,params string[] args) {
            List<string> all=new List<string> {"-s",serial}; all.AddRange(args); return adb.Run(all.ToArray(),input,timeout);
        }
        private AdbResult Shell(string serial,params string[] args) { List<string> all=new List<string>{"shell"}; all.AddRange(args); return On(serial,15000,null,all.ToArray()); }
        private AdbResult Provider(string serial,string method) { return Shell(serial,"content","call","--user","0","--uri","content://app.tidecard.bridge","--method",method); }
        internal static void VerifyPackage(string apk) {
            if(!File.Exists(apk))throw new InvalidOperationException("找不到配套 APK，请完整解压工具包后重试。");
            string hash; using(SHA256 sha=SHA256.Create())using(FileStream stream=File.OpenRead(apk))hash=BitConverter.ToString(sha.ComputeHash(stream)).Replace("-","").ToLowerInvariant();
            if(hash!=BundleInfo.ApkSha256)throw new InvalidOperationException("配套 APK 校验不一致，请重新获取完整工具包。");
        }
        internal void Activate(string serial,string apk) {
            Device selected=Devices().Find(d=>d.Serial==serial);
            if(selected==null)throw new InvalidOperationException("所选手机已断开。重新插线并点击刷新。");
            if(selected.State=="unauthorized")throw new InvalidOperationException("请解锁手机，在「允许 USB 调试」中允许这台电脑，再点击刷新。");
            if(selected.State!="device")throw new InvalidOperationException("手机当前未就绪。请重新插线、解锁并确认 USB 调试已开启。");
            AdbResult sdk=Shell(serial,"getprop","ro.build.version.sdk"); int api;
            if(sdk.Code!=0||!int.TryParse(sdk.Output.Trim(),out api)||api<30)throw new InvalidOperationException("需要 Android 11 或以上，且 USB 调试必须正常连接。");
            AdbResult user=Shell(serial,"am","get-current-user");
            if(user.Code!=0||user.Output.Trim()!="0")throw new InvalidOperationException("目前仅支持手机主用户。请退出访客、工作空间或系统分身后再试。");
            AdbResult installed=Shell(serial,"dumpsys","package","app.tidecard");
            Match version=Regex.Match(installed.Output,@"\bversionCode=(\d+)\b"); long code;
            if(version.Success&&long.TryParse(version.Groups[1].Value,out code)&&code>BundleInfo.VersionCode)throw new InvalidOperationException("手机装有更高版本，为保留数据不进行降级。请获取与手机版本配套的电脑工具。");
            VerifyPackage(apk); log("配套 APK 校验通过。正在安装 / 更新，保留原有数据…");
            log("如手机出现 USB 安装提示，请手动允许；保持手机解锁。");
            AdbResult install=On(serial,180000,null,"install","-r",apk);
            if(install.Code!=0||!Regex.IsMatch(install.Output,@"(?m)^Success\s*$")) {
                if(install.Output.Contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE"))throw new InvalidOperationException("手机上的应用签名不同，无法保留数据覆盖安装。没有卸载原应用；请先在原应用停止并恢复后再处理。");
                if(install.Output.Contains("INSTALL_FAILED_VERSION_DOWNGRADE"))throw new InvalidOperationException("手机版本更高，已停止降级安装。没有卸载或清除数据。");
                if(install.Output.Contains("USER_RESTRICTED")||install.Output.Contains("USER_CANCELED"))throw new InvalidOperationException("手机拒绝了 USB 安装。请在系统中允许本次安装，必要时开启开发者选项中的 USB 安装后重试。");
                throw new InvalidOperationException("安装未成功。请检查手机上的安装提示、存储空间和系统限制后重试；没有卸载或清除数据。");
            }
            log("应用已安装。正在打开手机上的 USB 激活页面…");
            AdbResult open=Shell(serial,"am","start","-W","-n","app.tidecard/.UsbActivationActivity");
            if(open.Code!=0||open.Output.Contains("Error:")||open.Output.Contains("Exception"))throw new InvalidOperationException("无法打开应用。请在手机上解锁并打开潮汐改卡后重试。");
            AdbResult initial=Provider(serial,"usb_status");
            if(initial.Code==0&&UsbRecord.Ready(initial.Output)) { log("内置后台已经就绪，无需重复启动。"); return; }
            AdbResult preparation=Provider(serial,"usb_prepare");
            if(preparation.Code!=0)throw new InvalidOperationException("无法读取本次安装的激活信息，请保持手机解锁并重试。");
            UsbRecord record=UsbRecord.Parse(preparation.Output);
            log("已读取本次安装信息，正在通过 USB 启动内置后台…");
            AdbResult launch=On(serial,15000,record.Script(),"shell","sh","-s");
            if(launch.Code!=0||launch.Output.Contains("Permission denied")||launch.Output.Contains("not found"))throw new InvalidOperationException("手机拒绝启动内置后台。请检查系统 USB 调试限制；无需继续尝试无线配对。");
            for(int i=0;i<20;i++) { delay(750); AdbResult state=Provider(serial,"usb_status"); if(state.Code==0&&UsbRecord.Ready(state.Output)) { log("手机已确认：内置后台就绪。"); return; } }
            throw new InvalidOperationException("后台未在规定时间内连接，尚未确认激活成功。请检查手机页面并重试；需要针对这台手机进一步诊断。");
        }
    }
}
