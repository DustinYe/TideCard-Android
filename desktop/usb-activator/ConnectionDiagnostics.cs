using System;
using System.Collections.Generic;
using System.Linq;
using System.Management;
using System.Text;
using System.Text.RegularExpressions;

namespace TideCardUsb {
    internal enum UsbKind { Other, Portable, Hdb, Hdc, Adb }
    internal sealed class UsbNode {
        internal string DeviceId="",Name="",Manufacturer="",Service="",PnpClass="";
        internal string[] CompatibleIds=new string[0];
        internal int? ProblemCode;
        internal bool Huawei { get {return DeviceId.IndexOf("VID_12D1",StringComparison.OrdinalIgnoreCase)>=0||Manufacturer.IndexOf("HUAWEI",StringComparison.OrdinalIgnoreCase)>=0||Manufacturer.Contains("华为")||Name.IndexOf("HUAWEI",StringComparison.OrdinalIgnoreCase)>=0;} }
        internal UsbKind Kind { get {
            if(Regex.IsMatch(Name,@"(?:^|[^a-z0-9])adb(?:$|[^a-z0-9])",RegexOptions.IgnoreCase)||CompatibleIds.Any(id=>Regex.IsMatch(id,@"Class_FF&SubClass_42&Prot_01(?:$|&)",RegexOptions.IgnoreCase)))return UsbKind.Adb;
            if(Regex.IsMatch(Name,@"(?:^|[^a-z0-9])hdc(?:$|[^a-z0-9])",RegexOptions.IgnoreCase))return UsbKind.Hdc;
            if(Regex.IsMatch(Name,@"(?:^|[^a-z0-9])hdb(?:$|[^a-z0-9])",RegexOptions.IgnoreCase))return UsbKind.Hdb;
            if(PnpClass.Equals("WPD",StringComparison.OrdinalIgnoreCase)||Name.IndexOf("MTP",StringComparison.OrdinalIgnoreCase)>=0||Service.IndexOf("Wpd",StringComparison.OrdinalIgnoreCase)>=0)return UsbKind.Portable;
            return UsbKind.Other;
        } }
        internal string SafeDescription() {
            // Never copy a PnP instance suffix: it can contain the phone's unique serial number.
            string[] fields={"VID_[0-9A-F]{4}","PID_[0-9A-F]{4}","MI_[0-9A-F]{2}"};
            string hardware=string.Join(" / ",fields.Select(pattern=>Regex.Match(DeviceId,pattern,RegexOptions.IgnoreCase).Value.ToUpperInvariant()).Where(value=>value.Length>0));
            string service=Regex.IsMatch(Service,@"^[A-Za-z0-9_.-]{1,60}$")?Service:"未提供";
            return (hardware.Length==0?"华为接口":hardware)+" · "+Kind+" · 驱动服务="+service+" · Windows错误码="+(ProblemCode.HasValue?ProblemCode.Value.ToString():"未知");
        }
    }
    internal sealed class UsbSnapshot {
        internal bool Available;internal string Failure="";internal List<UsbNode> Nodes=new List<UsbNode>();
    }
    internal interface IUsbInventory { UsbSnapshot Read(); }
    internal sealed class WindowsUsbInventory : IUsbInventory {
        private static string Text(ManagementBaseObject item,string key) {return Convert.ToString(item[key])??"";}
        public UsbSnapshot Read() {
            UsbSnapshot result=new UsbSnapshot();
            try {
                EnumerationOptions options=new EnumerationOptions {Timeout=TimeSpan.FromSeconds(6),ReturnImmediately=true,Rewindable=false};
                string query="SELECT Name,DeviceID,Manufacturer,Service,PNPClass,ConfigManagerErrorCode,CompatibleID FROM Win32_PnPEntity WHERE Present=TRUE AND (DeviceID LIKE '%VID_12D1%' OR Manufacturer LIKE '%HUAWEI%' OR Manufacturer LIKE '%华为%' OR Name LIKE '%HUAWEI%')";
                using(ManagementObjectSearcher search=new ManagementObjectSearcher("root\\CIMV2",query,options))
                using(ManagementObjectCollection rows=search.Get())foreach(ManagementObject row in rows)using(row) {
                    UsbNode node=new UsbNode {DeviceId=Text(row,"DeviceID"),Name=Text(row,"Name"),Manufacturer=Text(row,"Manufacturer"),Service=Text(row,"Service"),PnpClass=Text(row,"PNPClass"),CompatibleIds=row["CompatibleID"] as string[]??new string[0]};
                    object code=row["ConfigManagerErrorCode"];if(code!=null)node.ProblemCode=Convert.ToInt32(code);
                    if(node.Huawei)result.Nodes.Add(node);
                }
                result.Available=true;
            }catch(Exception e){result.Failure=e.GetType().Name;}
            return result;
        }
    }
    internal sealed class Diagnosis {
        internal string Summary,Report;
    }
    internal static class ConnectionDiagnostics {
        internal static readonly string[] IsolatedEnvironment={"ADB_SERVER_SOCKET","ANDROID_ADB_SERVER_PORT","ANDROID_ADB_SERVER_ADDRESS","ANDROID_SERIAL","ADB_LIBUSB","ADB_TRACE"};
        internal static Diagnosis Read(IAdb adb,IUsbInventory inventory) {
            AdbResult listing;try{listing=adb.Run(new[]{"devices","-l"},null,15000);}catch(Exception){listing=new AdbResult(-1,"");}
            UsbSnapshot snapshot;try{snapshot=inventory.Read();}catch(Exception e){snapshot=new UsbSnapshot {Failure=e.GetType().Name};}
            string backend="";try{AdbResult status=adb.Run(new[]{"server-status"},null,4000);if(status.Code==0)backend=Regex.Match(status.Output,@"(?m)^usb_backend:\s*([A-Z_]+)\s*$").Groups[1].Value;}catch(Exception){}
            return Analyze(listing,snapshot,backend);
        }
        internal static Diagnosis Analyze(AdbResult listing,UsbSnapshot snapshot,string backend="") {
            bool adbOk=listing.Code==0&&listing.Output.Contains("List of devices attached");List<Device> devices=Device.Parse(listing.Output);
            int ready=devices.Count(d=>d.State=="device"),unauthorized=devices.Count(d=>d.State=="unauthorized"),offline=devices.Count(d=>d.State=="offline");
            List<UsbNode> nodes=snapshot.Nodes.Where(n=>n.Huawei).ToList(),adbNodes=nodes.Where(n=>n.Kind==UsbKind.Adb).ToList();
            string summary,advice;
            if(adbOk&&ready>0){summary="ADB 已识别 USB 手机，可以返回选择设备。";advice="如果连接了多台手机，请明确选择目标后激活。此结果仅确认连接，不代表系统允许改卡。";}
            else if(adbOk&&unauthorized>0){summary="已发现手机，正在等待 USB 调试授权。";advice="请保持手机解锁，在“允许 USB 调试”的 RSA 授权弹窗中确认自己的电脑，再点刷新。华为手机助手的 HDB 授权与这里的 ADB 授权应分别检查。";}
            else if(adbOk&&offline>0){summary="ADB 已发现手机，但连接处于离线状态。";advice="解锁手机，重新插线并刷新；检查其他正在使用手机的调试工具。保留本应用数据。";}
            else if(!adbOk){summary="ADB 未正常返回设备列表。";advice="请确认完整解压工具包、随附 ADB 可以运行，并检查电脑上的调试环境。工具已为子进程固定本机标准端口，未重启其他 ADB 会话。";}
            else if(!snapshot.Available){summary="未发现 ADB 手机；Windows USB 接口信息未能获取。";advice="请打开设备管理器检查手机的 ADB 接口和驱动状态。无法读取接口信息不能当作“没有插手机”的证据。";}
            else if(adbNodes.Any(n=>n.ProblemCode.HasValue&&n.ProblemCode.Value!=0)){summary="发现华为 ADB 接口，但 Windows 报告驱动异常。";advice="打开设备管理器查看错误码；28 通常表示未安装驱动，10/31 表示设备或驱动未正常启动。请从华为官网获取手机助手，在其中选择 HarmonyOS 4 及以下，并使用安装 USB 驱动入口，完成后重新连接。";}
            else if(adbNodes.Count>0){summary="Windows 已枚举华为 ADB 接口，但 ADB 列表仍为空。";advice="保持解锁并确认 USB 调试的 RSA 授权；可切换“传输文件 / 传输照片”后重新连接。检查驱动状态及其他调试工具的占用。该现象不能单凭截图确定为鸿蒙系统缺陷。";}
            else if(nodes.Any(n=>n.Kind==UsbKind.Hdc)){summary="发现华为 HDC 接口，尚未发现本工具需要的 ADB 接口。";advice="请确认实际系统和 Android APK / ADB 支持。HDC 不能直接替代本工具的 Android ADB 激活流程；HarmonyOS 4.2 不应直接套用面向纯鸿蒙的 HDC 教程。";}
            else if(nodes.Count>0){summary="Windows 发现华为 USB 设备，但没有枚举出 ADB 接口。";advice="资源管理器能打开手机存储，只表示文件传输接口可用。保持 USB 调试和“仅充电模式下允许 ADB 调试”开启，尝试“传输文件 / 传输照片”并重新插线。若仍无 ADB 接口，请通过华为官方手机助手安装 USB 驱动。仅有 HDB 接口也不等于 ADB 已就绪。";}
            else {summary="未发现 ADB 手机，也未查询到当前华为 USB 节点。";advice="检查数据线、USB 接口与手机连接模式；也可换一台电脑交叉验证。此结果不表示该系统不支持 ADB。";}
            StringBuilder text=new StringBuilder();text.AppendLine("潮汐改卡 USB 连接诊断 "+DesktopInfo.Version).AppendLine("配套 APK："+BundleInfo.VersionName).AppendLine().AppendLine(summary).AppendLine().AppendLine(advice).AppendLine();
            text.AppendLine("ADB 列表有效："+adbOk+"；已授权="+ready+"，待授权="+unauthorized+"，离线="+offline);
            text.AppendLine("当前 ADB 服务 USB 后端："+(Regex.IsMatch(backend??"",@"^[A-Z_]{1,32}$")?backend:"未获取"));
            if(backend=="LIBUSB")text.AppendLine("当前服务使用 Windows 上的实验性 LIBUSB 后端。若仍无设备，请检查其他调试工具启动的服务配置；原生后端设置需在服务下次启动时生效。本工具不会强制结束服务。");
            text.AppendLine("华为 USB 枚举可用："+snapshot.Available+"；接口数="+nodes.Count+"；ADB 接口数="+adbNodes.Count);
            if(!snapshot.Available)text.AppendLine("接口查询状态："+(Regex.IsMatch(snapshot.Failure??"",@"^[A-Za-z0-9.]{1,80}$")?snapshot.Failure:"未提供"));
            foreach(UsbNode node in nodes)text.AppendLine("• "+node.SafeDescription());
            text.AppendLine().AppendLine("ADB 子进程固定连接本机 5037 端口，隔离远端连接变量并为新启动的服务指定 Windows 原生 USB 后端；已运行的服务保持现状，全局环境变量不变。多台华为设备的 PnP 信息可能同时出现，不能仅凭这些节点对应到某台手机。");
            text.AppendLine("诊断仅在本机显示；复制内容不含手机序列号、PnP 实例尾部、ADB 密钥或激活令牌。");
            return new Diagnosis {Summary=summary,Report=text.ToString()};
        }
    }
}
