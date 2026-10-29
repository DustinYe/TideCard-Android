using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.ComponentModel;
using System.IO;
using System.Linq;

namespace TideCardUsb {
    internal static class ConnectionDiagnosticsChecks {
        private static int count;
        private static void Require(bool ok,string text){count++;if(!ok)throw new InvalidOperationException("USB diagnosis: "+text);}
        private static UsbNode Node(string name,int? problem=0) {return new UsbNode {DeviceId=@"USB\VID_12D1&PID_107E&MI_01\PRIVATE_SERIAL_987",Name=name,Manufacturer="HUAWEI",ProblemCode=problem};}
        private static UsbSnapshot Snapshot(params UsbNode[] nodes){return new UsbSnapshot {Available=true,Nodes=nodes.ToList()};}
        private static AdbResult Listing(string lines=""){return new AdbResult(0,"List of devices attached\n"+lines);}
        private static Diagnosis Analyze(UsbSnapshot snapshot,string lines=""){return ConnectionDiagnostics.Analyze(Listing(lines),snapshot);}
        private sealed class StubInventory : IUsbInventory {
            internal bool Fail;
            public UsbSnapshot Read(){if(Fail)throw new InvalidOperationException("PRIVATE_SERIAL_987");return Snapshot(Node("HUAWEI MTP"));}
        }
        private sealed class StubAdb : IAdb {
            internal readonly List<string> Calls=new List<string>();internal bool Fail;
            public AdbResult Run(string[] args,string input,int timeoutMs){
                Require(input==null,"diagnosis must not send commands over stdin");string call=string.Join(" ",args);Calls.Add(call);
                if(Fail)throw new InvalidOperationException("PRIVATE_SERIAL_987");
                if(call=="version")return new AdbResult(0,"Android Debug Bridge version 1.0.41\n");
                if(call=="devices -l")return Listing();
                if(call=="server-status")return new AdbResult(0,"usb_backend: LIBUSB\nexecutable_absolute_path: C:\\PRIVATE_PATH\\adb.exe\n");
                throw new InvalidOperationException("unexpected diagnostic command");
            }
        }
        private sealed class ProbeAdb : IAdb {
            internal AdbResult Version=new AdbResult(0,"Android Debug Bridge version 1.0.41\nPRIVATE_PATH_987");
            internal AdbResult Devices=Listing();internal Exception Failure;internal string FailureStage="version";internal List<string> Calls=new List<string>();
            public AdbResult Run(string[] args,string input,int timeoutMs) {
                string stage=args[0];Calls.Add(stage);Require(input==null,"probe must be read-only");
                if(Failure!=null&&stage==FailureStage)throw Failure;
                if(stage=="version")return Version;if(stage=="devices")return Devices;
                if(stage=="server-status")return new AdbResult(0,"usb_backend: LIBADBUSB\nexecutable_absolute_path: C:\\PRIVATE_PATH_987");
                throw new InvalidOperationException("unexpected command");
            }
        }
        private static void ProbeChecks(string adbPath) {
            foreach(int code in new[]{2,3,5,126,193,216,1150,999}) {
                ProbeAdb fake=new ProbeAdb {Failure=new Win32Exception(code,"PRIVATE_SERIAL_987 C:\\PRIVATE_PATH_987")};
                Diagnosis report=ConnectionDiagnostics.Read(fake,new StubInventory());
                Require(report.Report.Contains("Win32="+code),"native error code "+code);Require(!report.Report.Contains("PRIVATE_"),"native exception redaction");
                Require(fake.Calls.SequenceEqual(new[]{"version"}),"no server calls after process startup failure");
            }
            foreach(string issue in new[]{"missing-executable","client-timeout","output-timeout"}) {
                Diagnosis report=ConnectionDiagnostics.Read(new ProbeAdb {Failure=new AdbRunException(issue,"PRIVATE_PATH_987")},new StubInventory());
                Require(report.Report.Contains("分类="+issue),"typed failure "+issue);Require(!report.Report.Contains("PRIVATE_"),"typed exception redaction");
            }
            uint[] exits={0xc0000135,0xc000007b,0xc0000142};string[] issues={"missing-module","invalid-image","initialization-failed"};
            for(int i=0;i<exits.Length;i++) {
                Diagnosis report=ConnectionDiagnostics.Read(new ProbeAdb {Version=new AdbResult(unchecked((int)exits[i]),"PRIVATE_PATH_987")},new StubInventory());
                Require(report.Report.Contains("分类="+issues[i]),"loader error category");Require(report.Report.Contains("0x"+exits[i].ToString("X8")),"hex loader status");Require(!report.Report.Contains("PRIVATE_"),"loader output redaction");
            }
            string[] errors={"adb server version (39) doesn't match this client (41)","error: protocol fault (couldn't read status)","cannot connect to daemon at tcp:5037","unknown failure"};
            string[] signatures={"server-version","server-protocol","server-connect","client-exit"};
            for(int i=0;i<errors.Length;i++) {
                ProbeAdb fake=new ProbeAdb {Devices=new AdbResult(1,errors[i]+"\nPRIVATE_SERIAL_987 C:\\PRIVATE_PATH_987 TOKEN_987")};Diagnosis report=ConnectionDiagnostics.Read(fake,new StubInventory());
                Require(report.Report.Contains("分类="+signatures[i]),"server failure category");Require(report.Report.Contains("退出码=1 (0x00000001)"),"nonzero exit retained");
                Require(!report.Report.Contains("PRIVATE_")&&!report.Report.Contains("TOKEN_987"),"server output redaction");Require(fake.Calls.SequenceEqual(new[]{"version","devices"}),"failed list skips status query");
            }
            Diagnosis malformed=ConnectionDiagnostics.Read(new ProbeAdb {Devices=new AdbResult(0,"PRIVATE_SERIAL_987 device\n")},new StubInventory());
            Require(malformed.Report.Contains("unexpected-output"),"zero exit does not imply valid list");Require(malformed.Report.Contains("上述计数不能"),"invalid list counts qualified");
            Require(WindowsUsbInventory.IncludePresence(null,0),"missing Present retained as candidate");Require(WindowsUsbInventory.IncludePresence(null,null),"unknown provider state retained");
            Require(!WindowsUsbInventory.IncludePresence(false,0),"explicitly absent node excluded");Require(!WindowsUsbInventory.IncludePresence(null,45),"disconnected error excluded");
            Require(WindowsUsbInventory.IncludePresence(true,28),"present unbound node retained");
            UsbSnapshot unknown=Snapshot(Node("HUAWEI MTP"));unknown.PresenceUnknown=1;Require(Analyze(unknown).Report.Contains("Present 状态未提供"),"unknown presence disclosed");
            AdbRuntime mismatch=new AdbRuntime {Exe=true,Api=true,UsbApi=true,Os64=false,Machine="x64",WindowsVersion="10.0"};
            Require(mismatch.Advice().Contains("32 位 Windows"),"runtime architecture mismatch");ProbeAdb blocked=new ProbeAdb();Diagnosis blockedReport=ConnectionDiagnostics.Read(blocked,new StubInventory(),mismatch);Require(blocked.Calls.Count==0&&blockedReport.Report.Contains("runtime-incompatible"),"incompatible runtime does not launch client");mismatch.Os64=true;Require(mismatch.Advice()=="","matching runtime not rejected");
            string directory=Path.Combine(Path.GetTempPath(),"TideCard-Diagnostics-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(directory);string missing=Path.Combine(directory,"adb.exe");
            try {
                AdbRuntime runtime=AdbRuntime.Inspect(missing);Require(!runtime.Exe&&!runtime.Api&&!runtime.UsbApi,"missing package files");Require(runtime.Advice().Contains("不完整"),"incomplete package advice");
                Diagnosis report=ConnectionDiagnostics.Read(new Adb(missing),new StubInventory(),runtime);Require(report.Report.Contains("package-incomplete")&&report.Report.Contains("adb.exe=缺失"),"actual incomplete package previously swallowed");Require(!report.Report.Contains(directory),"runtime path omitted");
                blocked=new ProbeAdb();ConnectionDiagnostics.Read(blocked,new StubInventory(),runtime);Require(blocked.Calls.Count==0,"incomplete package does not launch client");Require(AdbProbe.Run(new Adb(missing),"ADB 客户端自检",new[]{"version"},"Android Debug Bridge version",1000).Issue=="missing-executable","actual missing executable classified");
                byte[] pe=new byte[128];pe[0]=0x4d;pe[1]=0x5a;pe[0x3c]=0x40;pe[64]=0x50;pe[65]=0x45;pe[68]=0x64;pe[69]=0x86;File.WriteAllBytes(missing,pe);Require(AdbRuntime.ReadMachine(missing)=="x64","PE machine detection");
                File.WriteAllText(missing,"invalid executable");Require(AdbRuntime.ReadMachine(missing)=="无效 PE","corrupt PE rejected");
            }finally{if(File.Exists(missing))File.Delete(missing);Directory.Delete(directory);}
            AdbRuntime bundled=AdbRuntime.Inspect(adbPath);Require(bundled.Exe&&bundled.Api&&bundled.UsbApi,"actual bundled ADB files");Require(bundled.Machine=="x86"||bundled.Machine=="x64"||bundled.Machine=="ARM64","official bundled ADB machine readable");Console.WriteLine("Actual bundled ADB PE architecture: "+bundled.Machine);
            Diagnosis host=ConnectionDiagnostics.Read(new Adb(adbPath),new WindowsUsbInventory(),bundled);Require(host.Report.Contains("ADB 客户端自检：正常")&&host.Report.Contains("ADB 设备查询：正常"),"actual client and server diagnostics");
        }
        internal static void Run(string adbPath) {
            UsbNode mtp=Node("HUAWEI MTP");Require(mtp.Kind==UsbKind.Portable,"MTP classification");
            Diagnosis d=Analyze(Snapshot(mtp));Require(d.Summary.Contains("没有枚举出 ADB"),"MTP is not ADB");Require(d.Report.Contains("文件传输接口"),"MTP explanation");
            Require(Analyze(Snapshot(Node("HUAWEI HDB Interface"))).Report.Contains("仅有 HDB"),"HDB distinct from ADB");
            Require(Analyze(Snapshot(Node("HUAWEI HDC Interface"))).Summary.Contains("HDC 接口"),"HDC distinct from ADB");
            Require(Analyze(Snapshot(Node("HUAWEI_ADB_Interface"))).Summary.Contains("列表仍为空"),"underscore ADB label");
            foreach(int code in new[]{28,10,31}){d=Analyze(Snapshot(Node("Android ADB Interface",code)));Require(d.Summary.Contains("驱动异常"),"driver error "+code);Require(d.Report.Contains("Windows错误码="+code),"driver error preserved");}
            d=Analyze(Snapshot(Node("Android ADB Interface",null)));Require(!d.Summary.Contains("驱动异常"),"unknown error code is not driver failure");Require(d.Report.Contains("Windows错误码=未知"),"unknown code preserved");
            UsbNode compatible=Node("Unknown device",28);compatible.CompatibleIds=new[]{@"USB\Class_FF&SubClass_42&Prot_01"};Require(compatible.Kind==UsbKind.Adb,"unbound compatible ADB interface");
            compatible.CompatibleIds=new[]{@"USB\Class_FF&SubClass_42&Prot_03"};Require(compatible.Kind!=UsbKind.Adb,"non-ADB USB protocol");
            UsbSnapshot failed=new UsbSnapshot {Failure="ManagementException"};d=Analyze(failed);Require(d.Summary.Contains("未能获取"),"WMI unavailable is not absent hardware");
            Require(Analyze(Snapshot()).Summary.Contains("未查询到"),"no Huawei nodes");
            Require(Analyze(Snapshot(Node("MTP")),"PRIVATE_SERIAL_987 unauthorized\n").Summary.Contains("等待 USB 调试授权"),"authorization priority");
            Require(Analyze(failed,"PRIVATE_SERIAL_987 offline\n").Summary.Contains("离线"),"offline priority");
            Require(Analyze(failed,"PRIVATE_SERIAL_987 device model:Huawei\n").Summary.Contains("已识别 USB"),"ready priority");
            Require(Analyze(Snapshot(),"192.0.2.1:5555 device\nadb-test._adb-tls-connect._tcp device\nemulator-5554 device\n").Summary.Contains("未查询到"),"wireless and emulator are not USB");
            Require(ConnectionDiagnostics.Analyze(new AdbResult(1,"List of devices attached\nPRIVATE_SERIAL_987 device\n"),Snapshot()).Summary.Contains("未正常返回"),"nonzero list status");
            Require(ConnectionDiagnostics.Analyze(new AdbResult(0,"PRIVATE_SERIAL_987 device\n"),Snapshot()).Summary.Contains("未正常返回"),"list header required");
            UsbNode secret=Node("PRIVATE_NAME_987");secret.Service="unsafe\\PRIVATE_PATH";d=ConnectionDiagnostics.Analyze(Listing("PRIVATE_SERIAL_987 unauthorized\n"),Snapshot(secret),"PRIVATE_BACKEND:bad");
            Require(!d.Report.Contains("PRIVATE_"),"report must not copy identifying data or untrusted backend");Require(d.Report.Contains("VID_12D1")&&d.Report.Contains("MI_01"),"hardware interface identifiers retained");
            Require(d.Report.Contains("驱动服务=未提供"),"service validation");
            failed.Failure="Private:SECRET";Require(!Analyze(failed).Report.Contains("SECRET"),"exception details sanitized");
            StubAdb stub=new StubAdb();d=ConnectionDiagnostics.Read(stub,new StubInventory());Require(stub.Calls.SequenceEqual(new[]{"version","devices -l","server-status"}),"diagnosis is read-only");Require(d.Report.Contains("实验性 LIBUSB"),"existing server backend disclosed");Require(!d.Report.Contains("PRIVATE_PATH"),"server executable path omitted");
            StubAdb broken=new StubAdb {Fail=true};d=ConnectionDiagnostics.Read(broken,new StubInventory {Fail=true});Require(d.Summary.Contains("客户端自检未通过"),"query failures handled");Require(broken.Calls.SequenceEqual(new[]{"version"}),"failed client skips server operations");Require(!d.Report.Contains("PRIVATE_SERIAL"),"failure details omitted");
            Dictionary<string,string> before=ConnectionDiagnostics.IsolatedEnvironment.ToDictionary(name=>name,name=>Environment.GetEnvironmentVariable(name));
            ProcessStartInfo child=new ProcessStartInfo();foreach(string name in ConnectionDiagnostics.IsolatedEnvironment)child.EnvironmentVariables[name]="untrusted";
            Adb.ConfigureEnvironment(child);
            foreach(string name in ConnectionDiagnostics.IsolatedEnvironment)Require(name=="ADB_LIBUSB"?child.EnvironmentVariables[name]=="0":!child.EnvironmentVariables.ContainsKey(name),"child environment "+name);
            foreach(string name in ConnectionDiagnostics.IsolatedEnvironment)Require(Environment.GetEnvironmentVariable(name)==before[name],"global environment unchanged "+name);
            ProcessStartInfo info=new Adb(adbPath).CreateStartInfo(new[]{"devices","-l"},false);
            Require(info.Arguments=="-P 5037 \"devices\" \"-l\"","local standard server port");Require(!info.UseShellExecute&&info.CreateNoWindow,"no shell or visible helper");
            // Read-only host check: exercises actual WMI schema and bundled client; no phone is required.
            UsbSnapshot host=new WindowsUsbInventory().Read();Require(host.Available,"actual Windows USB inventory query: "+host.Failure);
            ProbeChecks(adbPath);
            Console.WriteLine("Actual Windows Huawei USB query succeeded; candidate interface nodes: "+host.Nodes.Count+". This does not validate a Huawei phone.");
            Console.WriteLine("PASS "+count+" connection diagnostics assertions: interfaces, drivers, authorization, query failures, environment and redaction.");
        }
    }
}
