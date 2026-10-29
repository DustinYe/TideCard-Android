using System;
using System.Collections.Generic;
using System.Linq;
using System.Management;
using System.Text;
using System.Text.RegularExpressions;

namespace TideCardUsb {
    internal enum UsbKind { Other, Portable, Hdb, Hdc, Adb }
    internal enum UsbVendor { Unknown, Huawei, OppoFamily }
    internal sealed class UsbNode {
        internal string DeviceId="",Name="",Manufacturer="",Service="",PnpClass="";
        internal string[] CompatibleIds=new string[0],HardwareIds=new string[0];
        internal int? ProblemCode;
        private string Ids {get{return DeviceId+"\n"+string.Join("\n",HardwareIds);}}
        private bool Vid(string id){return Regex.IsMatch(Ids,"VID_"+id+@"(?:$|[^0-9a-f])",RegexOptions.IgnoreCase);}
        internal bool UsbSource {get{return DeviceId.StartsWith("USB\\",StringComparison.OrdinalIgnoreCase)||Regex.IsMatch(DeviceId,@"USB[#\\]VID_",RegexOptions.IgnoreCase)||HardwareIds.Any(id=>id.StartsWith("USB\\",StringComparison.OrdinalIgnoreCase));}}
        internal UsbVendor Vendor {get {
            string label=Manufacturer+" "+Name;
            // Vendor IDs and names are hints, never proof of a phone model or ROM.
            if(Vid("22D9")||Vid("2A70")||Regex.IsMatch(label,@"\b(?:OPPO|OnePlus|realme|OPlus)\b",RegexOptions.IgnoreCase))return UsbVendor.OppoFamily;
            if(Vid("12D1")||label.IndexOf("HUAWEI",StringComparison.OrdinalIgnoreCase)>=0||label.Contains("华为"))return UsbVendor.Huawei;
            return UsbVendor.Unknown;
        }}
        internal bool Candidate {get{return UsbSource&&(Vendor!=UsbVendor.Unknown||Kind!=UsbKind.Other||ProblemCode.HasValue&&ProblemCode.Value!=0);}}
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
            string hardware=string.Join(" / ",fields.Select(pattern=>Regex.Match(Ids,pattern,RegexOptions.IgnoreCase).Value.ToUpperInvariant()).Where(value=>value.Length>0));
            string service=Regex.IsMatch(Service,@"^[A-Za-z0-9_.-]{1,60}$")?Service:"未提供";
            string vendor=Vendor==UsbVendor.OppoFamily?"OPPO/OnePlus/realme 线索":Vendor==UsbVendor.Huawei?"Huawei 线索":"厂商未确认";
            return (hardware.Length==0?"USB 候选接口":hardware)+" · "+vendor+" · "+Kind+" · 驱动服务="+service+" · Windows错误码="+(ProblemCode.HasValue?ProblemCode.Value.ToString():"未知");
        }
    }
    internal sealed class UsbSnapshot {
        internal bool Available;internal string Failure="";internal int PresenceUnknown,ScannedUsbNodes;internal List<UsbNode> Nodes=new List<UsbNode>();
    }
    internal interface IUsbInventory { UsbSnapshot Read(); }
    internal sealed class WindowsUsbInventory : IUsbInventory {
        private static string Text(ManagementBaseObject item,string key) {return Convert.ToString(item[key])??"";}
        internal static bool IncludePresence(bool? present,int? problem) {return present!=false&&problem!=45;}
        private static bool? Present(ManagementBaseObject item) {try{object value=item["Present"];return value==null?(bool?)null:Convert.ToBoolean(value);}catch(ManagementException){return null;}}
        public UsbSnapshot Read() {
            UsbSnapshot result=new UsbSnapshot();
            try {
                EnumerationOptions options=new EnumerationOptions {Timeout=TimeSpan.FromSeconds(6),ReturnImmediately=true,Rewindable=false};
                // Some providers omit Present. Do not silently discard every candidate with a WQL Present=TRUE filter.
                string query="SELECT * FROM Win32_PnPEntity WHERE DeviceID LIKE 'USB%' OR PNPClass='WPD' OR Name LIKE '%ADB%' OR Name LIKE '%HDB%' OR Name LIKE '%HDC%'";
                using(ManagementObjectSearcher search=new ManagementObjectSearcher("root\\CIMV2",query,options))
                using(ManagementObjectCollection rows=search.Get())foreach(ManagementObject row in rows)using(row) {
                    UsbNode node=new UsbNode {DeviceId=Text(row,"DeviceID"),Name=Text(row,"Name"),Manufacturer=Text(row,"Manufacturer"),Service=Text(row,"Service"),PnpClass=Text(row,"PNPClass"),CompatibleIds=row["CompatibleID"] as string[]??new string[0],HardwareIds=row["HardwareID"] as string[]??new string[0]};
                    object code=row["ConfigManagerErrorCode"];if(code!=null)node.ProblemCode=Convert.ToInt32(code);
                    bool? present=Present(row);
                    if(node.UsbSource&&IncludePresence(present,node.ProblemCode))result.ScannedUsbNodes++;
                    if(node.Candidate&&IncludePresence(present,node.ProblemCode)){result.Nodes.Add(node);if(!present.HasValue)result.PresenceUnknown++;}
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
        internal static Diagnosis Read(IAdb adb,IUsbInventory inventory,AdbRuntime runtime=null,ILocalAdbServerProbe serverProbe=null) {
            string runtimeAdvice=runtime==null?"":runtime.Advice();
            AdbProbe version=runtimeAdvice.Length>0?new AdbProbe {Stage="ADB 客户端自检",Issue=(!runtime.Exe||!runtime.Api||!runtime.UsbApi)?"package-incomplete":"runtime-incompatible",Advice=runtimeAdvice}:AdbProbe.Run(adb,"ADB 客户端自检",new[]{"version"},"Android Debug Bridge version",8000);
            AdbProbe listing=version.Ok?AdbProbe.Run(adb,"ADB 设备查询",new[]{"devices","-l"},"List of devices attached",15000):new AdbProbe {Stage="ADB 设备查询"};
            UsbSnapshot snapshot;try{snapshot=inventory.Read();}catch(Exception e){snapshot=new UsbSnapshot {Failure=e.GetType().Name};}
            string backend="";if(listing.Ok)try{AdbResult status=adb.Run(new[]{"server-status"},null,4000);if(status.Code==0)backend=Regex.Match(status.Output,@"(?m)^usb_backend:\s*([A-Z_]+)\s*$").Groups[1].Value;}catch(Exception){}
            LocalAdbServerResult local=null;if(version.Ok&&!listing.Ok&&serverProbe!=null)try{local=serverProbe.Read();}catch(Exception){local=new LocalAdbServerResult {State="probe-failed"};}
            return Analyze(listing.Result,snapshot,backend,version,listing,runtime,local);
        }
        internal static Diagnosis Analyze(AdbResult listing,UsbSnapshot snapshot,string backend="",AdbProbe version=null,AdbProbe query=null,AdbRuntime runtime=null,LocalAdbServerResult local=null) {
            bool adbOk=listing.Code==0&&listing.Output.Contains("List of devices attached");List<Device> devices=Device.Parse(listing.Output);
            int ready=devices.Count(d=>d.State=="device"),unauthorized=devices.Count(d=>d.State=="unauthorized"),offline=devices.Count(d=>d.State=="offline");
            List<UsbNode> nodes=snapshot.Nodes.Where(n=>n.Candidate).ToList(),adbNodes=nodes.Where(n=>n.Kind==UsbKind.Adb).ToList();
            bool oppo=nodes.Any(n=>n.Vendor==UsbVendor.OppoFamily),huawei=nodes.Any(n=>n.Vendor==UsbVendor.Huawei);
            string driverAdvice=oppo&&huawei?"检测到多种品牌线索，请先核对目标接口，再从厂商帮助选择对应品牌；不要把不同设备的接口混在一起。":oppo?"若目标手机为 OPPO，请查看“OPPO / 厂商帮助”中的官方连接排查，按实际接口和机型检查驱动；只更新 MTP 文件传输驱动不代表 ADB 已就绪。":huawei?"若目标手机为华为，可从厂商帮助进入官方手机助手，按实际系统安装 USB 驱动；HDB 不等于 ADB。":"请按目标手机的实际品牌，通过厂商官网或 Android 官方 OEM 驱动目录核对驱动。";
            string summary,advice;
            if(adbOk&&ready>0){summary="ADB 已识别 USB 手机，可以返回选择设备。";advice="如果连接了多台手机，请明确选择目标后激活。此结果仅确认连接，不代表系统允许改卡。";}
            else if(adbOk&&unauthorized>0){summary="已发现手机，正在等待 USB 调试授权。";advice="请保持手机解锁，在“允许 USB 调试”的 RSA 授权弹窗中确认自己的电脑，再点刷新。没有弹窗时先重新插线，核对文件传输模式与 USB 调试开关。";}
            else if(adbOk&&offline>0){summary="ADB 已发现手机，但连接处于离线状态。";advice="解锁手机，重新插线并刷新；检查其他正在使用手机的调试工具。保留本应用数据。";}
            else if(version!=null&&!version.Ok){summary="ADB 客户端自检未通过，尚未进入手机连接检查。";advice=runtime!=null&&runtime.Advice().Length>0?runtime.Advice():version.Advice;}
            else if(!adbOk){summary="ADB 未正常返回设备列表。";advice=local!=null?local.Advice:query!=null?query.Advice:"请先检查电脑上的 ADB 客户端与本机服务。未得到有效列表，不能将此状态解释成手机未授权或没有设备。";}
            else if(!snapshot.Available){summary="未发现 ADB 手机；Windows USB 接口信息未能获取。";advice="请打开设备管理器检查手机的 ADB 接口和驱动状态。无法读取接口信息不能当作“没有插手机”的证据。";}
            else if(adbNodes.Any(n=>n.ProblemCode.HasValue&&n.ProblemCode.Value!=0)){summary="发现 ADB 候选接口，但 Windows 报告驱动异常。";advice="打开设备管理器核对目标接口及错误码；28 通常表示未安装驱动，10/31 表示设备或驱动未正常启动。"+driverAdvice;}
            else if(adbNodes.Count>0){summary="Windows 查询到 ADB 候选接口，但 ADB 列表仍为空。";advice="保持解锁并确认 USB 调试 RSA 授权；切换传输文件 / 传输照片后重新插线、刷新。检查驱动及其它调试工具的服务版本。"+driverAdvice;}
            else if(nodes.Any(n=>n.Kind==UsbKind.Hdc)){summary="发现 HDC 接口，尚未发现本工具需要的 ADB 接口。";advice="按实际手机系统确认 Android APK / ADB 支持，HDC 不能直接替代 Android ADB 激活流程。";}
            else if(nodes.Count>0){summary="查询到 USB 候选接口，尚未枚举出 ADB 接口。";advice="候选项可能属于其它设备，请在设备管理器核对目标手机。资源管理器能打开存储只证明文件传输可用；保持 USB 调试开启、使用传输文件模式并重新连接。"+driverAdvice+" 仅有 HDB 接口也不等于 ADB 已就绪。";}
            else {summary="未发现 ADB 手机，也未查询到匹配的 USB 候选接口。";advice="检查数据线、USB 接口与手机连接模式，再核对目标手机品牌和设备管理器。此结果不表示手机系统不支持 ADB。";}
            StringBuilder text=new StringBuilder();text.AppendLine("潮汐改卡 USB 连接诊断 "+DesktopInfo.Version).AppendLine("配套 APK："+BundleInfo.VersionName).AppendLine().AppendLine(summary).AppendLine().AppendLine(advice).AppendLine();
            if(runtime!=null)text.AppendLine(runtime.Describe()).AppendLine();
            if(version!=null)text.AppendLine(version.Describe()).AppendLine();
            if(query!=null)text.AppendLine(query.Describe()).AppendLine();
            if(local!=null)text.AppendLine(local.Describe()).AppendLine();
            text.AppendLine("ADB 列表有效："+adbOk+"；已授权="+ready+"，待授权="+unauthorized+"，离线="+offline);
            if(!adbOk)text.AppendLine("列表未成功读取；上述计数不能用于判断手机是否连接或已授权。");
            text.AppendLine("当前 ADB 服务 USB 后端："+(Regex.IsMatch(backend??"",@"^[A-Z_]{1,32}$")?backend:"未获取"));
            if(backend=="LIBUSB")text.AppendLine("当前服务使用 Windows 上的实验性 LIBUSB 后端。若仍无设备，请检查其他调试工具启动的服务配置；原生后端设置需在服务下次启动时生效。本工具不会强制结束服务。");
            text.AppendLine("Windows USB 查询可用："+snapshot.Available+"；扫描 USB 节点="+snapshot.ScannedUsbNodes+"；候选接口数="+nodes.Count+"；ADB 接口数="+adbNodes.Count);
            text.AppendLine("品牌线索接口数：OPPO/OnePlus/realme="+nodes.Count(n=>n.Vendor==UsbVendor.OppoFamily)+"；Huawei="+nodes.Count(n=>n.Vendor==UsbVendor.Huawei)+"；未确认="+nodes.Count(n=>n.Vendor==UsbVendor.Unknown));
            if(snapshot.PresenceUnknown>0)text.AppendLine("其中 Present 状态未提供的候选接口数="+snapshot.PresenceUnknown+"；无法确认这些接口当前是否在线，已排除明确断开的节点。");
            if(!snapshot.Available)text.AppendLine("接口查询状态："+(Regex.IsMatch(snapshot.Failure??"",@"^[A-Za-z0-9.]{1,80}$")?snapshot.Failure:"未提供"));
            foreach(UsbNode node in nodes)text.AppendLine("• "+node.SafeDescription());
            if(oppo&&adbOk&&ready>0)text.AppendLine("OPPO / ColorOS：连接已建立；若后续 shell 命令明确被拒绝，可查看厂商帮助中的权限监控说明。USB 品牌线索和连接成功都不能证明该机允许后台启动或 SIM 改卡。");
            text.AppendLine().AppendLine("检测不限定厂商：USB ADB 兼容类、文件传输及异常 USB 项均作为候选。VID / 名称仅作品牌线索，可能包含其它设备，不能据此确认机型、ROM 或对应某一条 ADB 连接。");
            text.AppendLine("ADB 子进程固定连接本机 5037 端口，隔离远端连接变量并为新启动的服务指定 Windows 原生 USB 后端；已运行的服务保持现状，全局环境变量不变。");
            text.AppendLine("诊断仅在本机显示；复制内容不含手机序列号、PnP 实例尾部、ADB 密钥或激活令牌。");
            return new Diagnosis {Summary=summary,Report=text.ToString()};
        }
    }
}
