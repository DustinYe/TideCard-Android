using System;
using System.Collections.Generic;
using System.Diagnostics;
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
                if(call=="devices -l")return Listing();
                if(call=="server-status")return new AdbResult(0,"usb_backend: LIBUSB\nexecutable_absolute_path: C:\\PRIVATE_PATH\\adb.exe\n");
                throw new InvalidOperationException("unexpected diagnostic command");
            }
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
            StubAdb stub=new StubAdb();d=ConnectionDiagnostics.Read(stub,new StubInventory());Require(stub.Calls.SequenceEqual(new[]{"devices -l","server-status"}),"diagnosis is read-only");Require(d.Report.Contains("实验性 LIBUSB"),"existing server backend disclosed");Require(!d.Report.Contains("PRIVATE_PATH"),"server executable path omitted");
            d=ConnectionDiagnostics.Read(new StubAdb {Fail=true},new StubInventory {Fail=true});Require(d.Summary.Contains("未正常返回"),"query failures handled");Require(!d.Report.Contains("PRIVATE_SERIAL"),"failure details omitted");
            Dictionary<string,string> before=ConnectionDiagnostics.IsolatedEnvironment.ToDictionary(name=>name,name=>Environment.GetEnvironmentVariable(name));
            ProcessStartInfo child=new ProcessStartInfo();foreach(string name in ConnectionDiagnostics.IsolatedEnvironment)child.EnvironmentVariables[name]="untrusted";
            Adb.ConfigureEnvironment(child);
            foreach(string name in ConnectionDiagnostics.IsolatedEnvironment)Require(name=="ADB_LIBUSB"?child.EnvironmentVariables[name]=="0":!child.EnvironmentVariables.ContainsKey(name),"child environment "+name);
            foreach(string name in ConnectionDiagnostics.IsolatedEnvironment)Require(Environment.GetEnvironmentVariable(name)==before[name],"global environment unchanged "+name);
            ProcessStartInfo info=new Adb(adbPath).CreateStartInfo(new[]{"devices","-l"},false);
            Require(info.Arguments=="-P 5037 \"devices\" \"-l\"","local standard server port");Require(!info.UseShellExecute&&info.CreateNoWindow,"no shell or visible helper");
            // Read-only host check: exercises actual WMI schema and bundled client; no phone is required.
            UsbSnapshot host=new WindowsUsbInventory().Read();Require(host.Available,"actual Windows USB inventory query: "+host.Failure);
            Console.WriteLine("Actual Windows Huawei USB query succeeded; present interface nodes: "+host.Nodes.Count+". This does not validate a Huawei phone.");
            Console.WriteLine("PASS "+count+" connection diagnostics assertions: interfaces, drivers, authorization, query failures, environment and redaction.");
        }
    }
}
