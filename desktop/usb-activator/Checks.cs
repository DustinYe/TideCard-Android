using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text;
using System.Windows.Forms;

namespace TideCardUsb {
    internal sealed class FakeAdb : IAdb {
        internal string State="device",InstallFailure=null,Owner="0"; internal int Version=BundleInfo.VersionCode-1,Polls,ReadyAfter=2;
        internal bool Launched,AlreadyReady,WrongVersion,FailLaunch; internal List<string> Calls=new List<string>();
        internal string Record="Bundle[{record=TIDEUSB1|"+BundleInfo.VersionCode+"|10123|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA|"+Convert.ToBase64String(Encoding.UTF8.GetBytes("/data/app/example/base.apk"))+"}]";
        public AdbResult Run(string[] args,string input,int timeoutMs) {
            string call=string.Join(" ",args); Calls.Add(call);
            if(args[0]=="devices")return new AdbResult(0,"List of devices attached\nUSB123 "+State+" model:Vivo_Test transport_id:1\n192.0.2.1:5555 device model:Other\n");
            if(args.Length<3||args[0]!="-s"||args[1]!="USB123")throw new Exception("Command not pinned to selected device");
            if(call.Contains("getprop"))return new AdbResult(0,"36\n");
            if(call.Contains("get-current-user"))return new AdbResult(0,Owner+"\n");
            if(call.Contains("dumpsys package"))return new AdbResult(0,"versionCode="+Version+" minSdk=30\n");
            if(args[2]=="install") { Require(args.Length==5&&args[3]=="-r","only data-preserving install allowed"); return new AdbResult(InstallFailure==null?0:1,InstallFailure==null?"Success\n":InstallFailure); }
            if(call.Contains("am start")) { Require(call.Contains(".UsbActivationActivity"),"main UI could auto-resume SIM changes"); return new AdbResult(0,"Status: ok\n"); }
            if(call.Contains("usb_prepare"))return new AdbResult(0,Record);
            if(call.Contains("usb_status")) { Polls++; return new AdbResult(0,"Bundle[{record=TIDEUSBSTATUS1|"+(WrongVersion?BundleInfo.VersionCode-1:BundleInfo.VersionCode)+"|"+((AlreadyReady||Launched&&Polls>ReadyAfter)?"1":"0")+"}]"); }
            if(call.EndsWith("shell sh -s")) { Require(input!=null&&input.Contains(" app.tidecard.Bootstrap '")&&input.EndsWith("sleep 2\n"),"launch script missing"); Require(!call.Contains("AAAA"),"token leaked into process args"); Launched=true;return new AdbResult(FailLaunch?1:0,FailLaunch?"Permission denied":""); }
            throw new Exception("Unexpected command "+call);
        }
        private static void Require(bool ok,string text){if(!ok)throw new Exception(text);}
    }
    internal static class Checks {
        private static int count;
        private static void Require(bool ok,string text){count++;if(!ok)throw new Exception(text);}
        private static void Reject(Action a,string fragment){try{a();throw new Exception("Unexpected success: "+fragment);}catch(InvalidOperationException e){Require(e.Message.Contains(fragment),"Wrong failure: "+e.Message);}}
        private static Workflow Run(FakeAdb adb,List<string> logs){return new Workflow(adb,logs.Add,ms=>{});}
        [STAThread] private static int Main(string[] args) {
            try {
                string apk=Path.GetFullPath(args[0]);Workflow.VerifyPackage(apk);
                Require(Device.Parse("List of devices attached\nUSB123 unauthorized\n192.0.2.1:5555 device\nemulator-5554 device\nadb-test._adb-tls-connect._tcp device\nUSB456 offline\n").Count==2,"transport filtering");
                Require(Adb.Quote("C:\\some folder\\")=="\"C:\\some folder\\\\\"","Windows trailing slash quoting");
                Require(Adb.Quote("a\"b")=="\"a\\\"b\"","Windows quote escaping");
                List<string> logs=new List<string>();FakeAdb good=new FakeAdb();Run(good,logs).Activate("USB123",apk);
                Require(good.Launched&&good.Polls>=3,"success must wait for callback");
                Require(!string.Join("\n",logs).Contains("AAAAAAAA"),"activation secret in logs");
                FakeAdb ready=new FakeAdb{AlreadyReady=true};Run(ready,new List<string>()).Activate("USB123",apk);Require(!ready.Launched&&!ready.Calls.Any(c=>c.Contains("usb_prepare")),"duplicate backend launch");
                FakeAdb unauthorized=new FakeAdb{State="unauthorized"};Reject(()=>Run(unauthorized,logs).Activate("USB123",apk),"允许 USB 调试");Require(!unauthorized.Calls.Any(c=>c.Contains("install")),"unauthorized install attempted");
                FakeAdb offline=new FakeAdb{State="offline"};Reject(()=>Run(offline,logs).Activate("USB123",apk),"未就绪");
                FakeAdb missing=new FakeAdb();Reject(()=>Run(missing,logs).Activate("OTHER",apk),"已断开");
                FakeAdb user=new FakeAdb{Owner="10"};Reject(()=>Run(user,logs).Activate("USB123",apk),"主用户");Require(!user.Calls.Any(c=>c.Contains("install")),"secondary user install");
                FakeAdb newer=new FakeAdb{Version=99};Reject(()=>Run(newer,logs).Activate("USB123",apk),"更高版本");Require(!newer.Calls.Any(c=>c.Contains("install")),"downgrade attempted");
                foreach(string error in new[]{"INSTALL_FAILED_USER_RESTRICTED","INSTALL_FAILED_UPDATE_INCOMPATIBLE","INSTALL_FAILED_VERSION_DOWNGRADE"}) {
                    FakeAdb refused=new FakeAdb{InstallFailure=error};Reject(()=>Run(refused,logs).Activate("USB123",apk),error.Contains("USER_RESTRICTED")?"USB 安装":error.Contains("UPDATE")?"签名不同":"版本更高");Require(!refused.Launched,"launch after failed install");
                }
                FakeAdb timeout=new FakeAdb{ReadyAfter=99};Reject(()=>Run(timeout,logs).Activate("USB123",apk),"尚未确认激活成功");
                FakeAdb wrong=new FakeAdb{WrongVersion=true};Reject(()=>Run(wrong,logs).Activate("USB123",apk),"尚未确认激活成功");
                FakeAdb denied=new FakeAdb{FailLaunch=true};Reject(()=>Run(denied,logs).Activate("USB123",apk),"拒绝启动");
                Reject(()=>UsbRecord.Parse("Bundle[{record=TIDEUSB1|"+BundleInfo.VersionCode+"|10123|bad|L2RhdGE=}]"),"有效激活信息");
                Reject(()=>UsbRecord.Parse(good.Record.Replace("|"+BundleInfo.VersionCode+"|","|"+(BundleInfo.VersionCode-1)+"|")),"版本或用户身份不匹配");
                Reject(()=>UsbRecord.Parse(good.Record.Replace("|10123|","|110123|")),"版本或用户身份不匹配");
                string unsafePath="/data/app/evil\ncommand/base.apk";string bad=good.Record.Substring(0,good.Record.LastIndexOf('|')+1)+Convert.ToBase64String(Encoding.UTF8.GetBytes(unsafePath))+"}]";Reject(()=>UsbRecord.Parse(bad),"路径不受支持");
                Require(!UsbRecord.Ready("Bundle[{record=TIDEUSBSTATUS1|"+(BundleInfo.VersionCode-1)+"|1}]"),"stale version acknowledged");
                Require(!UsbRecord.Ready("Bundle[{record=TIDEUSBSTATUS1|"+BundleInfo.VersionCode+"|1x}]"),"malformed status acknowledged");
                foreach(string call in good.Calls)Require(!call.Contains("uninstall")&&!call.Contains("pm clear")&&!call.Contains("force-stop")&&!call.Contains("tcpip")&&!call.Contains("kill-server"),"unexpected device mutation");
                string corrupted=Path.GetTempFileName();try{File.WriteAllText(corrupted,"invalid APK");Reject(()=>Workflow.VerifyPackage(corrupted),"校验不一致");}finally{File.Delete(corrupted);}
                if(args.Length>2) { Adb real=new Adb(Path.GetFullPath(args[2]));AdbResult info=real.Run(new[]{"version"},null,15000);Require(info.Code==0&&info.Output.Contains("Android Debug Bridge version"),"bundled ADB failed to start");AdbResult connected=real.Run(new[]{"devices","-l"},null,15000);Require(connected.Code==0&&connected.Output.Contains("List of devices attached"),"bundled ADB list failed");Console.WriteLine("Real bundled ADB executable started; USB device count: "+Device.Parse(connected.Output).Count); }
                if(args.Length>1)LayoutChecks.Run(Path.GetDirectoryName(Path.GetFullPath(args[1])));
                Console.WriteLine("PASS "+count+" USB desktop assertions: authorization, selected device, install failure, downgrade, callback readiness, timeout, strict records, credentials and APK integrity. Mock transport; no physical device.");return 0;
            } catch(Exception e) {Console.Error.WriteLine(e.ToString());return 1;}
        }
    }
}
