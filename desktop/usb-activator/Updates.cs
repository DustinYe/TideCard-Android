using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;

namespace TideCardUsb {
    internal sealed class UsbRelease {
        internal Version Version; internal string Tag,Url,Sha256; internal long Size;
    }
    internal static class UsbUpdates {
        internal const string Repo="https://github.com/DustinYe/TideCard-Android";
        internal const string Api="https://api.github.com/repos/DustinYe/TideCard-Android/releases";
        internal static string CacheRoot {get{return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"TideCardUsb","Updates");}}
        internal static readonly JavaScriptSerializer Json=new JavaScriptSerializer {MaxJsonLength=8*1024*1024};
        internal static Version ParseVersion(string value) {
            if(value==null||!Regex.IsMatch(value,@"^\d{1,4}\.\d{1,4}\.\d{1,4}$"))throw new InvalidOperationException("版本格式无效。");
            return new Version(value);
        }
        internal static UsbRelease ParseRelease(IDictionary<string,object> item) {
            string tag=Convert.ToString(Get(item,"tag_name"));
            if(Get(item,"draft") as bool? != false||Get(item,"prerelease") as bool? != false||!tag.StartsWith("usb-v",StringComparison.Ordinal))return null;
            Version version;try {version=ParseVersion(tag.Substring(5));}catch(InvalidOperationException){return null;}
            IEnumerable assets=Get(item,"assets") as IEnumerable;if(assets==null)return null;
            UsbRelease result=null;string name="TideCard-USB-"+version+"-Windows.zip";
            foreach(object raw in assets) {
                IDictionary<string,object> asset=raw as IDictionary<string,object>;if(asset==null||Convert.ToString(Get(asset,"name"))!=name)continue;
                string digest=Convert.ToString(Get(asset,"digest")),url=Convert.ToString(Get(asset,"browser_download_url"));long size;
                if(result!=null||Convert.ToString(Get(asset,"state"))!="uploaded"||!Regex.IsMatch(digest,@"^sha256:[0-9a-fA-F]{64}$")||!long.TryParse(Convert.ToString(Get(asset,"size")),out size)||size<1||size>128*1024*1024)throw new InvalidOperationException("正式版附件缺少有效 SHA-256 或大小信息，请到发布页核对。");
                if(url!=Repo+"/releases/download/"+tag+"/"+name)throw new InvalidOperationException("更新附件地址不属于本项目的正式发布。");
                result=new UsbRelease {Version=version,Tag=tag,Url=url,Sha256=digest.Substring(7).ToLowerInvariant(),Size=size};
            }
            return result;
        }
        private static object Get(IDictionary<string,object> item,string key){object value;return item.TryGetValue(key,out value)?value:null;}
        internal static UsbRelease FindLatest(CancellationToken cancel) {
            UsbRelease latest=null;
            for(int page=1;page<=5;page++) {
                string data;using(MemoryStream output=new MemoryStream()){Transfer(Api+"?per_page=100&page="+page,output,8*1024*1024,-1,cancel,null);data=Encoding.UTF8.GetString(output.ToArray());}
                object[] releases=Json.DeserializeObject(data) as object[];if(releases==null)throw new InvalidOperationException("GitHub 返回的版本列表无效。");
                foreach(object raw in releases) {IDictionary<string,object> item=raw as IDictionary<string,object>;if(item==null)continue;UsbRelease next=ParseRelease(item);if(next!=null&&(latest==null||next.Version>latest.Version))latest=next;}
                if(releases.Length<100)return latest;
            }
            throw new InvalidOperationException("发布列表过长，尚未完成检查。请到 GitHub 发布页核对。");
        }
        private static bool AllowedDownload(Uri uri) {
            return uri.Scheme=="https"&&uri.IsDefaultPort&&string.IsNullOrEmpty(uri.UserInfo)&&(uri.Host=="github.com"&&uri.AbsolutePath.StartsWith("/DustinYe/TideCard-Android/releases/download/",StringComparison.Ordinal)||uri.Host=="release-assets.githubusercontent.com");
        }
        internal static void Transfer(string url,Stream output,long limit,long expected,CancellationToken cancel,Action<int> progress) {
            ServicePointManager.SecurityProtocol|=SecurityProtocolType.Tls12;Uri uri=new Uri(url);Stopwatch elapsed=Stopwatch.StartNew();bool api=url.StartsWith(Api+"?",StringComparison.Ordinal);
            for(int redirects=0;redirects<5;redirects++) {
                if(api?uri.Host!="api.github.com"||uri.Scheme!="https"||uri.AbsolutePath!="/repos/DustinYe/TideCard-Android/releases":!AllowedDownload(uri))throw new InvalidOperationException("更新请求被重定向到了不受支持的地址。");
                cancel.ThrowIfCancellationRequested();HttpWebRequest request=(HttpWebRequest)WebRequest.Create(uri);request.AllowAutoRedirect=false;request.Timeout=20000;request.ReadWriteTimeout=20000;request.UserAgent="TideCard-USB/"+DesktopInfo.Version;request.Accept=api?"application/vnd.github+json":"application/octet-stream";
                using(cancel.Register(request.Abort)) {
                    try {using(HttpWebResponse response=(HttpWebResponse)request.GetResponse()) {
                        if((int)response.StatusCode>=300&&(int)response.StatusCode<400){string location=response.Headers["Location"];if(string.IsNullOrEmpty(location)||api)throw new InvalidOperationException("更新服务重定向无效。");uri=new Uri(uri,location);continue;}
                        if(response.StatusCode!=HttpStatusCode.OK||response.ContentLength>limit)throw new InvalidOperationException("更新服务返回异常或文件过大。");
                        using(Stream input=response.GetResponseStream()) {byte[] buffer=new byte[65536];long total=0;int read;
                            while((read=input.Read(buffer,0,buffer.Length))>0) {cancel.ThrowIfCancellationRequested();total+=read;if(total>limit||elapsed.ElapsedMilliseconds>300000)throw new InvalidOperationException("更新下载超出大小或时间限制，请重试。");output.Write(buffer,0,read);if(progress!=null&&expected>0)progress((int)Math.Min(100,total*100/expected));}
                            if(expected>=0&&total!=expected)throw new InvalidOperationException("下载文件不完整，尚未修改当前工具。");
                        }
                        return;
                    }}catch(WebException){cancel.ThrowIfCancellationRequested();throw new InvalidOperationException("无法连接 GitHub 更新服务。请检查网络，稍后重试；当前工具未改动。");}
                }
            }
            throw new InvalidOperationException("更新下载重定向次数过多。");
        }
        internal static string Hash(string file) {using(SHA256 sha=SHA256.Create())using(FileStream stream=File.OpenRead(file))return BitConverter.ToString(sha.ComputeHash(stream)).Replace("-","").ToLowerInvariant();}
        internal static string SafeFile(string root,string name) {
            if(string.IsNullOrEmpty(name)||name.Contains("\\")||name.Contains(":"))throw new InvalidOperationException("更新包路径无效。");
            foreach(string part in name.Split('/'))if(part.Length==0||part=="."||part==".."||part.EndsWith(".")||part.EndsWith(" ")||part.IndexOfAny(Path.GetInvalidFileNameChars())>=0||Regex.IsMatch(part,@"^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\.|$)",RegexOptions.IgnoreCase))throw new InvalidOperationException("更新包路径无效。");
            string path=Path.GetFullPath(Path.Combine(root,name.Replace('/',Path.DirectorySeparatorChar)));string prefix=Path.GetFullPath(root).TrimEnd('\\')+"\\";
            if(!path.StartsWith(prefix,StringComparison.OrdinalIgnoreCase))throw new InvalidOperationException("更新包试图写入目录之外。");
            NoLinks(path);return path;
        }
        internal static void NoLinks(string path) {for(string current=Path.GetFullPath(path);!string.IsNullOrEmpty(current);current=Path.GetDirectoryName(current))if((File.Exists(current)||Directory.Exists(current))&&(File.GetAttributes(current)&FileAttributes.ReparsePoint)!=0)throw new InvalidOperationException("更新目录包含链接，请将工具移动到普通文件夹后重试。");}
        internal static Dictionary<string,string> Manifest(string root) {
            string file=Path.Combine(root,"SHA256SUMS.txt");NoLinks(file);if(!File.Exists(file)||new FileInfo(file).Length>32768)throw new InvalidOperationException("更新包缺少文件清单。");
            Dictionary<string,string> files=new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
            foreach(string line in File.ReadAllLines(file,Encoding.UTF8)) {if(line.Length==0)continue;Match m=Regex.Match(line,@"^([0-9a-fA-F]{64})  (.+)$");if(!m.Success)throw new InvalidOperationException("更新文件清单无效。");string name=m.Groups[2].Value;SafeFile(root,name);if(name.Equals("SHA256SUMS.txt",StringComparison.OrdinalIgnoreCase)||files.ContainsKey(name)||files.Count>=64)throw new InvalidOperationException("更新文件清单重复或过大。");files.Add(name,m.Groups[1].Value.ToLowerInvariant());}
            return files;
        }
        internal static void ValidateBundle(string root,Version version) {
            Dictionary<string,string> files=Manifest(root);
            foreach(string name in new[]{"TideCard-USB.exe","TideCard-USB.exe.config","LICENSE.txt","使用说明.txt","adb/adb.exe","adb/AdbWinApi.dll","adb/AdbWinUsbApi.dll","adb/NOTICE.txt","adb/source.properties"})if(!files.ContainsKey(name))throw new InvalidOperationException("更新包缺少必要文件："+name);
            foreach(KeyValuePair<string,string> item in files) {string path=SafeFile(root,item.Key);if(!File.Exists(path)||Hash(path)!=item.Value)throw new InvalidOperationException("更新包文件校验失败，当前工具未改动。");}
            foreach(string file in Directory.GetFiles(root,"*",SearchOption.AllDirectories)) {string relative=file.Substring(root.TrimEnd('\\').Length+1).Replace('\\','/');if(relative!="SHA256SUMS.txt"&&!files.ContainsKey(relative))throw new InvalidOperationException("更新包存在清单外文件。");}
            if(!files.Keys.AnyApk())throw new InvalidOperationException("更新包缺少配套 APK。");
            FileVersionInfo info=FileVersionInfo.GetVersionInfo(Path.Combine(root,"TideCard-USB.exe"));if(info.FileMajorPart!=version.Major||info.FileMinorPart!=version.Minor||info.FileBuildPart!=version.Build||info.FilePrivatePart!=0||info.ProductName!="TideCard USB Activator")throw new InvalidOperationException("更新程序身份或版本与发布不符。");
        }
        private static bool AnyApk(this IEnumerable<string> names) {int count=0;foreach(string name in names)if(Regex.IsMatch(name,@"^TideCard-\d+\.\d+\.\d+(?:-rc\d+)?\.apk$"))count++;return count==1;}
        internal static void Extract(string zip,string root) {
            Directory.CreateDirectory(root);NoLinks(root);HashSet<string> seen=new HashSet<string>(StringComparer.OrdinalIgnoreCase);long total=0;
            using(ZipArchive archive=ZipFile.OpenRead(zip)) {if(archive.Entries.Count>64)throw new InvalidOperationException("更新包文件过多。");foreach(ZipArchiveEntry entry in archive.Entries) {
                string name=entry.FullName;if(name.EndsWith("/")) {SafeFile(root,name.TrimEnd('/'));continue;}string path=SafeFile(root,name);
                if(!seen.Add(name)||entry.Length>128*1024*1024||(total+=entry.Length)>256*1024*1024||((entry.ExternalAttributes>>16)&0xF000)==0xA000)throw new InvalidOperationException("更新包包含重复、链接或超大文件。");
                Directory.CreateDirectory(Path.GetDirectoryName(path));using(Stream input=entry.Open())using(FileStream output=new FileStream(path,FileMode.CreateNew,FileAccess.Write)){byte[] buffer=new byte[65536];long copied=0;int read;while((read=input.Read(buffer,0,buffer.Length))>0){copied+=read;if(copied>entry.Length||copied>128*1024*1024)throw new InvalidOperationException("更新包解压大小异常。");output.Write(buffer,0,read);}if(copied!=entry.Length)throw new InvalidOperationException("更新包文件不完整。");}
            }}
        }
        internal static void ExtractBundle(string zip,string stage,Version version) {
            string unpack=stage+".unpack";Extract(zip,unpack);string bundle=unpack;
            if(!File.Exists(Path.Combine(unpack,"SHA256SUMS.txt"))) {
                string[] folders=Directory.GetDirectories(unpack);if(Directory.GetFiles(unpack).Length!=0||folders.Length!=1||Path.GetFileName(folders[0])!="TideCard-USB-"+version+"-Windows")throw new InvalidOperationException("更新包的顶层目录无效。");
                bundle=folders[0];
            }
            Directory.Move(bundle,stage);
        }
        internal static string Prepare(UsbRelease release,CancellationToken cancel,Action<int> progress) {
            string work=Path.Combine(CacheRoot,Guid.NewGuid().ToString("N"));Directory.CreateDirectory(work);NoLinks(work);string zip=Path.Combine(work,"package.zip"),stage=Path.Combine(work,"stage");
            using(FileStream output=new FileStream(zip,FileMode.CreateNew,FileAccess.Write))Transfer(release.Url,output,128*1024*1024,release.Size,cancel,progress);
            cancel.ThrowIfCancellationRequested();VerifyDownload(zip,release);
            MarkInternet(zip);ExtractBundle(zip,stage,release.Version);ValidateBundle(stage,release.Version);
            foreach(string exe in Directory.GetFiles(stage,"*.exe",SearchOption.AllDirectories))MarkInternet(exe);
            cancel.ThrowIfCancellationRequested();return work;
        }
        internal static void VerifyDownload(string zip,UsbRelease release) {if(new FileInfo(zip).Length!=release.Size||Hash(zip)!=release.Sha256)throw new InvalidOperationException("下载的更新包大小或 SHA-256 不符，已停止更新。");}
        // Preserve Windows' downloaded-file origin. Hash validation is not publisher signing.
        [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]
        private static extern Microsoft.Win32.SafeHandles.SafeFileHandle CreateFileW(string name,uint access,uint share,IntPtr security,uint disposition,uint flags,IntPtr template);
        internal static FileStream OriginStream(string path,bool write) {
            // .NET Framework rejects ADS syntax in path-based FileStream constructors.
            Microsoft.Win32.SafeHandles.SafeFileHandle handle=CreateFileW(Path.GetFullPath(path)+":Zone.Identifier",write?0x40000000u:0x80000000u,1,IntPtr.Zero,write?2u:3u,0x80,IntPtr.Zero);
            if(handle.IsInvalid){int code=Marshal.GetLastWin32Error();handle.Dispose();throw new IOException("无法保留下载来源标记。",new System.ComponentModel.Win32Exception(code));}
            return new FileStream(handle,write?FileAccess.Write:FileAccess.Read);
        }
        internal static void MarkInternet(string path) {using(StreamWriter writer=new StreamWriter(OriginStream(path,true),new UTF8Encoding(false)))writer.Write("[ZoneTransfer]\r\nZoneId=3\r\n");}
        internal static bool HasInternetOrigin(string path){using(StreamReader reader=new StreamReader(OriginStream(path,false)))return reader.ReadToEnd().Contains("ZoneId=3");}
    }
    internal sealed class UpdateRequest {
        public string Target,Work,Version,OriginalHash,ManifestHash;public int ProcessId;public long StartTicks;
    }
    internal static class UpdateInstaller {
        internal static void Writable(string target) {
            UsbUpdates.NoLinks(target);string probe=Path.Combine(target,".tidecard-write-"+Guid.NewGuid().ToString("N"));try {using(FileStream file=new FileStream(probe,FileMode.CreateNew,FileAccess.Write))file.WriteByte(0);}finally{if(File.Exists(probe))File.Delete(probe);}
        }
        internal static Process Start(string work,string target) {
            target=Path.GetFullPath(target);Writable(target);string original=Path.Combine(target,"TideCard-USB.exe");Process self=Process.GetCurrentProcess();
            UpdateRequest request=new UpdateRequest {Work=work,Target=target,Version=FileVersionInfo.GetVersionInfo(Path.Combine(work,"stage","TideCard-USB.exe")).FileVersion,ProcessId=self.Id,StartTicks=self.StartTime.ToUniversalTime().Ticks,OriginalHash=UsbUpdates.Hash(original),ManifestHash=UsbUpdates.Hash(Path.Combine(work,"stage","SHA256SUMS.txt"))};
            // FileVersion has four components, release tags have three.
            request.Version=new Version(request.Version).ToString(3);string path=Path.Combine(work,"request.json");File.WriteAllText(path,UsbUpdates.Json.Serialize(request),new UTF8Encoding(false));
            string helper=Path.Combine(work,"TideCard-USB-Updater.exe");File.Copy(original,helper,false);File.Copy(original+".config",helper+".config",false);
            return Process.Start(new ProcessStartInfo(helper,"--apply-update "+Adb.Quote(path)){UseShellExecute=false,WorkingDirectory=work});
        }
        internal static void Apply(string stage,string target,string originalHash,string manifestHash,Version version,Action<int> afterWrite) {
            UsbUpdates.NoLinks(target);UsbUpdates.NoLinks(stage);if(!Directory.Exists(target)||Path.GetPathRoot(target).TrimEnd('\\').Equals(target.TrimEnd('\\'),StringComparison.OrdinalIgnoreCase))throw new InvalidOperationException("更新目标目录无效。");
            if(UsbUpdates.Hash(Path.Combine(target,"TideCard-USB.exe"))!=originalHash||UsbUpdates.Hash(Path.Combine(stage,"SHA256SUMS.txt"))!=manifestHash)throw new InvalidOperationException("准备后文件发生改变，请重新检查更新。");
            UsbUpdates.ValidateBundle(stage,version);Writable(target);Dictionary<string,string> files=UsbUpdates.Manifest(stage);List<string> names=new List<string>(files.Keys);names.Sort(StringComparer.OrdinalIgnoreCase);names.Add("SHA256SUMS.txt");
            string backup=Path.Combine(target,".tidecard-backup-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(backup);List<string> changed=new List<string>();
            try {foreach(string name in names) {
                string source=UsbUpdates.SafeFile(stage,name),dest=UsbUpdates.SafeFile(target,name),old=UsbUpdates.SafeFile(backup,name);if(File.Exists(dest)&&UsbUpdates.Hash(source)==UsbUpdates.Hash(dest))continue;Directory.CreateDirectory(Path.GetDirectoryName(dest));Directory.CreateDirectory(Path.GetDirectoryName(old));
                string temp=Path.Combine(Path.GetDirectoryName(dest),".tidecard-new-"+Guid.NewGuid().ToString("N"));File.Copy(source,temp,false);
                try {if(File.Exists(dest))File.Replace(temp,dest,old);else File.Move(temp,dest);changed.Add(name);}finally{if(File.Exists(temp))File.Delete(temp);}
                if(afterWrite!=null)afterWrite(changed.Count);
            }}catch(Exception error) {
                bool restored=true;for(int i=changed.Count-1;i>=0;i--) {string dest=UsbUpdates.SafeFile(target,changed[i]),old=UsbUpdates.SafeFile(backup,changed[i]);try {if(File.Exists(old)) {if(File.Exists(dest))File.Replace(old,dest,null);else File.Move(old,dest);}else if(File.Exists(dest))File.Delete(dest);}catch{restored=false;}}
                throw new InvalidOperationException(restored?"更新失败，已恢复更新前的文件。请关闭正在使用这些文件的程序后重试。":"更新中断，部分文件无法自动恢复。备份保存在："+backup,error);
            }
            // Keep a local backup; do not kill a running ADB server to remove locked files.
        }
        internal static int RunHelper(string requestPath) {
            try {
                string path=Path.GetFullPath(requestPath),cache=Path.GetFullPath(UsbUpdates.CacheRoot).TrimEnd('\\')+"\\";UsbUpdates.NoLinks(path);
                if(!path.StartsWith(cache,StringComparison.OrdinalIgnoreCase)||Path.GetFileName(path)!="request.json"||new FileInfo(path).Length>16384)throw new InvalidOperationException("更新请求无效。");
                UpdateRequest request=UsbUpdates.Json.Deserialize<UpdateRequest>(File.ReadAllText(path));if(request==null||Path.GetFullPath(request.Work)!=Path.GetDirectoryName(path)||UsbUpdates.Hash(Process.GetCurrentProcess().MainModule.FileName)!=request.OriginalHash)throw new InvalidOperationException("更新助手身份校验失败。");
                try {using(Process old=Process.GetProcessById(request.ProcessId)) {if(old.StartTime.ToUniversalTime().Ticks==request.StartTicks&&!old.WaitForExit(60000))throw new InvalidOperationException("原窗口尚未关闭，更新已取消。");}}catch(ArgumentException){}
                Apply(Path.Combine(request.Work,"stage"),Path.GetFullPath(request.Target),request.OriginalHash,request.ManifestHash,UsbUpdates.ParseVersion(request.Version),null);
                try {Process.Start(new ProcessStartInfo(Path.Combine(request.Target,"TideCard-USB.exe")){UseShellExecute=true,WorkingDirectory=request.Target});}
                catch(Exception){System.Windows.Forms.MessageBox.Show("文件已更新，但 Windows 未能打开新版。请从原目录打开 TideCard-USB.exe，留意系统的签名或安全提示。\r\n"+request.Target,"潮汐改卡 · 更新后启动",System.Windows.Forms.MessageBoxButtons.OK,System.Windows.Forms.MessageBoxIcon.Information);return 2;}
                return 0;
            }catch(Exception e){System.Windows.Forms.MessageBox.Show(e.Message,"潮汐改卡 · 更新未完成",System.Windows.Forms.MessageBoxButtons.OK,System.Windows.Forms.MessageBoxIcon.Warning);return 1;}
        }
    }
}
