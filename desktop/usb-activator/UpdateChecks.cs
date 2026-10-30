using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using System.Threading;

namespace TideCardUsb {
    internal static class UpdateChecks {
        private static int count;
        private static void Require(bool ok,string message){count++;if(!ok)throw new Exception("Update: "+message);}
        private static void Reject(Action action,string message){bool rejected=false;try{action();}catch(InvalidOperationException){rejected=true;}Require(rejected,message);}
        private static string version=DesktopInfo.Version;
        private static Dictionary<string,object> Release(string tag,bool draft,bool prerelease,string url,string digest) {
            return new Dictionary<string,object>{{"tag_name",tag},{"draft",draft},{"prerelease",prerelease},{"assets",new object[]{new Dictionary<string,object>{{"name","TideCard-USB-"+tag.Replace("usb-v","")+"-Windows.zip"},{"state","uploaded"},{"size",123},{"digest",digest},{"browser_download_url",url}}}}};
        }
        private static void Sums(string root) {
            string[] files=Directory.GetFiles(root,"*",SearchOption.AllDirectories).Where(p=>Path.GetFileName(p)!="SHA256SUMS.txt").OrderBy(p=>p).Select(p=>UsbUpdates.Hash(p)+"  "+p.Substring(root.Length+1).Replace('\\','/')).ToArray();File.WriteAllLines(Path.Combine(root,"SHA256SUMS.txt"),files,new UTF8Encoding(false));
        }
        private static void Bundle(string root,string exe) {
            Directory.CreateDirectory(Path.Combine(root,"adb"));File.Copy(exe,Path.Combine(root,"TideCard-USB.exe"));
            foreach(string name in new[]{"TideCard-USB.exe.config","LICENSE.txt","使用说明.txt","TideCard-2.4.1.apk","adb/adb.exe","adb/AdbWinApi.dll","adb/AdbWinUsbApi.dll","adb/NOTICE.txt","adb/source.properties"})File.WriteAllText(Path.Combine(root,name.Replace('/',Path.DirectorySeparatorChar)),"fixture "+name);Sums(root);
        }
        internal static void Run(string packageDirectory) {
            string tag="usb-v"+version,url=UsbUpdates.Repo+"/releases/download/"+tag+"/TideCard-USB-"+version+"-Windows.zip",digest="sha256:"+new string('a',64);
            Require(UsbUpdates.ParseRelease(Release(tag,false,false,url,digest)).Version==new Version(version),"formal USB release");
            Require(UsbUpdates.ParseRelease(Release("v2.4.1",false,false,url,digest))==null,"Android release excluded");
            Require(UsbUpdates.ParseRelease(Release(tag,true,false,url,digest))==null,"draft excluded");Require(UsbUpdates.ParseRelease(Release(tag,false,true,url,digest))==null,"prerelease excluded");Require(UsbUpdates.ParseRelease(Release("usb-v1.1.9-rc1",false,false,url,digest))==null,"RC tag excluded");
            Reject(()=>UsbUpdates.ParseRelease(Release(tag,false,false,url,"")),"missing digest rejected");Reject(()=>UsbUpdates.ParseRelease(Release(tag,false,false,url.Replace("DustinYe","other"),digest)),"foreign repository rejected");Reject(()=>UsbUpdates.ParseRelease(Release(tag,false,false,url.Replace("https:","http:"),digest)),"HTTP rejected");
            Require(UsbUpdates.ParseVersion("1.1.10")>UsbUpdates.ParseVersion("1.1.9"),"numeric versions");Reject(()=>UsbUpdates.ParseVersion("1.2"),"invalid version");
            string root=Path.Combine(UsbUpdates.CacheRoot,"check-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(root);
            try {
                string stage=Path.Combine(root,"stage"),exe=Path.Combine(packageDirectory,"TideCard-USB.exe");Bundle(stage,exe);UsbUpdates.ValidateBundle(stage,new Version(version));Require(true,"complete bundle");
                string originFile=Path.Combine(root,"origin.txt");File.WriteAllText(originFile,"download");string originHash=UsbUpdates.Hash(originFile);UsbUpdates.MarkInternet(originFile);Require(UsbUpdates.HasInternetOrigin(originFile),"Internet origin preserved");Require(UsbUpdates.Hash(originFile)==originHash,"origin stream preserves file hash");string originCopy=Path.Combine(root,"origin-copy.txt");File.Copy(originFile,originCopy);Require(UsbUpdates.HasInternetOrigin(originCopy),"copied origin stream preserved");
                foreach(string name in new[]{"../escape","/root","C:/escape","adb\\bad","adb/../escape","NUL.txt","COM1/x","bad.","adb/name:stream","adb//bad"})Reject(()=>UsbUpdates.SafeFile(stage,name),"unsafe path "+name);
                string zip=Path.Combine(root,"good.zip"),extracted=Path.Combine(root,"extracted");ZipFile.CreateFromDirectory(stage,zip);UsbUpdates.Extract(zip,extracted);UsbUpdates.ValidateBundle(extracted,new Version(version));Require(true,"archive extraction and manifest");
                UsbRelease download=new UsbRelease {Size=new FileInfo(zip).Length,Sha256=UsbUpdates.Hash(zip)};UsbUpdates.VerifyDownload(zip,download);Require(true,"download digest and size verified");string corruptZip=Path.Combine(root,"corrupt.zip");File.Copy(zip,corruptZip);using(FileStream changed=new FileStream(corruptZip,FileMode.Open,FileAccess.Write)){changed.Position=changed.Length-1;changed.WriteByte(123);}Reject(()=>UsbUpdates.VerifyDownload(corruptZip,download),"download hash mismatch");File.AppendAllText(corruptZip,"extra");Reject(()=>UsbUpdates.VerifyDownload(corruptZip,download),"download length mismatch");
                using(MemoryStream output=new MemoryStream()){CancellationTokenSource stop=new CancellationTokenSource();stop.Cancel();bool canceled=false;try{UsbUpdates.Transfer(url,output,1024,-1,stop.Token,null);}catch(OperationCanceledException){canceled=true;}Require(canceled&&output.Length==0,"cancel before network and write");stop.Dispose();Reject(()=>UsbUpdates.Transfer("http://github.com/DustinYe/TideCard-Android/releases/download/usb-v1.1.7/test.zip",output,1024,-1,CancellationToken.None,null),"insecure transfer rejected");}
                string wrapper=Path.Combine(root,"wrapper"),wrappedZip=Path.Combine(root,"wrapped.zip");Bundle(Path.Combine(wrapper,"TideCard-USB-"+version+"-Windows"),exe);ZipFile.CreateFromDirectory(wrapper,wrappedZip);string wrappedStage=Path.Combine(root,"wrapped-stage");UsbUpdates.ExtractBundle(wrappedZip,wrappedStage,new Version(version));UsbUpdates.ValidateBundle(wrappedStage,new Version(version));Require(true,"release wrapper normalized");
                string flatStage=Path.Combine(root,"flat-stage");UsbUpdates.ExtractBundle(zip,flatStage,new Version(version));UsbUpdates.ValidateBundle(flatStage,new Version(version));Require(true,"flat release normalized");Reject(()=>UsbUpdates.ExtractBundle(wrappedZip,Path.Combine(root,"wrong-version-stage"),new Version("9.9.9")),"foreign wrapper rejected");
                foreach(string bad in new[]{"../outside.txt","adb/../../outside.txt","C:/outside.txt","adb\\escape.txt"}) {string badZip=Path.Combine(root,"bad.zip");using(ZipArchive z=ZipFile.Open(badZip,ZipArchiveMode.Create)){using(StreamWriter w=new StreamWriter(z.CreateEntry(bad).Open()))w.Write("bad");}Reject(()=>UsbUpdates.Extract(badZip,Path.Combine(root,"bad-"+Guid.NewGuid().ToString("N"))),"zip traversal");File.Delete(badZip);}
                using(ZipArchive z=ZipFile.Open(Path.Combine(root,"duplicate.zip"),ZipArchiveMode.Create)){z.CreateEntry("file.txt");z.CreateEntry("FILE.txt");}Reject(()=>UsbUpdates.Extract(Path.Combine(root,"duplicate.zip"),Path.Combine(root,"duplicate")),"case duplicate");
                File.AppendAllText(Path.Combine(extracted,"LICENSE.txt"),"tampered");Reject(()=>UsbUpdates.ValidateBundle(extracted,new Version(version)),"manifest mismatch");
                File.WriteAllText(Path.Combine(stage,"extra.txt"),"extra");Reject(()=>UsbUpdates.ValidateBundle(stage,new Version(version)),"unlisted file");File.Delete(Path.Combine(stage,"extra.txt"));Reject(()=>UsbUpdates.ValidateBundle(stage,new Version("9.9.9")),"executable version mismatch");
                string target=Path.Combine(root,"target");Bundle(target,exe);File.WriteAllText(Path.Combine(target,"LICENSE.txt"),"previous licence");File.WriteAllText(Path.Combine(target,"personal.txt"),"preserve me");Sums(target);File.AppendAllText(Path.Combine(stage,"adb","AdbWinApi.dll"),"new runtime");Sums(stage);string original=UsbUpdates.Hash(Path.Combine(target,"TideCard-USB.exe")),manifest=UsbUpdates.Hash(Path.Combine(stage,"SHA256SUMS.txt"));
                Dictionary<string,string> before=Directory.GetFiles(target,"*",SearchOption.AllDirectories).ToDictionary(p=>p,p=>UsbUpdates.Hash(p));
                Reject(()=>UpdateInstaller.Apply(stage,target,original,manifest,new Version(version),n=>{if(n==1)throw new IOException("fixture interrupted");}),"rollback on write failure");foreach(var item in before)Require(UsbUpdates.Hash(item.Key)==item.Value,"rollback restored "+Path.GetFileName(item.Key));
                Reject(()=>UpdateInstaller.Apply(stage,target,new string('0',64),manifest,new Version(version),null),"changed original stops before replace");Reject(()=>UpdateInstaller.Apply(stage,target,original,new string('0',64),new Version(version),null),"changed staged manifest stops before replace");
                using(FileStream locked=new FileStream(Path.Combine(target,"adb","AdbWinApi.dll"),FileMode.Open,FileAccess.Read,FileShare.None))Reject(()=>UpdateInstaller.Apply(stage,target,original,manifest,new Version(version),null),"locked runtime stops update");foreach(var item in before)Require(UsbUpdates.Hash(item.Key)==item.Value,"locked rollback restored "+Path.GetFileName(item.Key));
                UpdateInstaller.Apply(stage,target,original,manifest,new Version(version),null);Require(File.ReadAllText(Path.Combine(target,"LICENSE.txt")).StartsWith("fixture"),"updated files applied");Require(File.ReadAllText(Path.Combine(target,"personal.txt"))=="preserve me","extra user files preserved");Require(Directory.GetDirectories(target,".tidecard-backup-*").Length>0,"backup retained");
                // Real helper handoff in an isolated cache folder. The new fixture executable
                // writes a marker and exits; it cannot invoke ADB or touch a phone.
                string fixtureSource=Path.Combine(root,"fixture.cs"),fixtureExe=Path.Combine(root,"fixture.exe"),marker=Path.Combine(root,"restarted.txt");
                File.WriteAllText(fixtureSource,"using System;using System.IO;using System.Reflection;[assembly:AssemblyFileVersion(\""+version+".0\")][assembly:AssemblyProduct(\"TideCard USB Activator\")]class Fixture{static void Main(string[] a){if(a.Length>0){System.Threading.Thread.Sleep(500);return;}File.WriteAllText(@\""+marker.Replace("\"","\"\"")+"\",\"restarted\");}}",Encoding.UTF8);
                using(Process compiler=Process.Start(new ProcessStartInfo(Path.Combine(System.Runtime.InteropServices.RuntimeEnvironment.GetRuntimeDirectory(),"csc.exe"),"/nologo /target:winexe /out:"+Adb.Quote(fixtureExe)+" "+Adb.Quote(fixtureSource)){UseShellExecute=false,CreateNoWindow=true})){compiler.WaitForExit();Require(compiler.ExitCode==0,"restart fixture compiled");}
                string handoff=Path.Combine(root,"handoff"),oldDir=Path.Combine(root,"old");Bundle(handoff,fixtureExe);Bundle(oldDir,exe);File.Copy(Path.Combine(packageDirectory,"TideCard-USB.exe.config"),Path.Combine(oldDir,"TideCard-USB.exe.config"),true);string work=Path.Combine(root,"work");Directory.CreateDirectory(work);Directory.Move(handoff,Path.Combine(work,"stage"));string helper=Path.Combine(work,"TideCard-USB-Updater.exe");File.Copy(exe,helper);File.Copy(Path.Combine(packageDirectory,"TideCard-USB.exe.config"),helper+".config");
                using(Process wait=Process.Start(new ProcessStartInfo(fixtureExe,"--wait"){UseShellExecute=false})) {
                    UpdateRequest request=new UpdateRequest {Work=work,Target=oldDir,Version=version,ProcessId=wait.Id,StartTicks=wait.StartTime.ToUniversalTime().Ticks,OriginalHash=UsbUpdates.Hash(exe),ManifestHash=UsbUpdates.Hash(Path.Combine(work,"stage","SHA256SUMS.txt"))};string requestPath=Path.Combine(work,"request.json");File.WriteAllText(requestPath,UsbUpdates.Json.Serialize(request));
                    using(Process updater=Process.Start(new ProcessStartInfo(helper,"--apply-update "+Adb.Quote(requestPath)){UseShellExecute=false})) {Require(updater.WaitForExit(15000),"helper terminated");Require(updater.ExitCode==0,"helper apply and launch succeeded");}
                }
                Stopwatch deadline=Stopwatch.StartNew();while(!File.Exists(marker)&&deadline.ElapsedMilliseconds<5000)Thread.Sleep(50);Require(File.Exists(marker),"updated executable restarted");Require(UsbUpdates.Hash(Path.Combine(oldDir,"TideCard-USB.exe"))==UsbUpdates.Hash(fixtureExe),"in-place executable replaced");
            }finally {
                // The absolute target is a unique child of our own update cache.
                string full=Path.GetFullPath(root),prefix=Path.GetFullPath(UsbUpdates.CacheRoot).TrimEnd('\\')+"\\";if(full.StartsWith(prefix,StringComparison.OrdinalIgnoreCase)){UsbUpdates.NoLinks(full);try{Directory.Delete(full,true);}catch(IOException){}}
            }
            Console.WriteLine("PASS "+count+" desktop update assertions: release filtering, SHA-256, archive paths, locked-file rollback, file preservation and real helper restart. Isolated fixtures; no phone operations.");
        }
    }
}
