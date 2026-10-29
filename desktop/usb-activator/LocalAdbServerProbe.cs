using System;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;

namespace TideCardUsb {
    internal interface ILocalAdbServerProbe { LocalAdbServerResult Read(); }
    internal sealed class LocalAdbServerResult {
        internal string State="not-checked";internal int? Version,SocketCode;
        internal string Advice {get {
            if(State=="version-reply")return "本机 5037 能响应 ADB 版本查询，但设备查询仍未完成。拔下手机对照：若空列表正常返回，再检查接入后的 USB 枚举；若仍超时，可关闭其它调试工具，在任务管理器结束 adb.exe，再打开本工具（其它 ADB 连接会断开）。";
            if(State=="not-listening")return "检查时 IPv4 本机 127.0.0.1:5037 拒绝连接，服务未在此地址正常提供连接。请先检查电脑端服务启动结果；这不能归因于 ColorOS 的手机授权开关。";
            if(State=="invalid-reply")return "本机 5037 返回了非预期的 ADB 协议响应，可能有其它程序占用或服务异常。请核对本机端口和其它调试工具，尚未确认手机驱动是根因。";
            if(State=="response-timeout"||State=="connect-timeout")return "本机 5037 未在期限内完成 ADB 协议查询；可能为服务无响应、端口被其它程序占用或本机连接问题。请拔下手机重试诊断，对照是否仍超时。";
            if(State=="server-rejected")return "本机端口返回 ADB FAIL 状态，服务拒绝了版本查询；请检查本机 ADB 服务。";
            return "本机 ADB 协议检查未完成。请根据状态和 Socket 错误码继续检查；工具没有结束其它进程或重启服务。";
        }}
        internal string Describe(){return "本机服务协议检查（127.0.0.1:5037）："+State+(Version.HasValue?"；协议版本="+Version.Value:"")+(SocketCode.HasValue?"；Socket="+SocketCode.Value:"")+Environment.NewLine+Advice;}
    }
    /** Read-only smart-socket query, with one total deadline. No server startup, kill or device request. */
    internal sealed class LocalAdbServerProbe : ILocalAdbServerProbe {
        public LocalAdbServerResult Read(){return ReadPort(5037,2000);}
        private static int Remaining(Stopwatch watch,int timeout){int left=timeout-(int)watch.ElapsedMilliseconds;if(left<=0)throw new TimeoutException();return left;}
        private static byte[] ReadExact(NetworkStream stream,int count,Stopwatch watch,int timeout){
            byte[] data=new byte[count];int offset=0;while(offset<count){stream.ReadTimeout=Remaining(watch,timeout);int n=stream.Read(data,offset,count-offset);if(n==0)throw new EndOfStreamException();offset+=n;}return data;
        }
        internal static LocalAdbServerResult ReadPort(int port,int timeout) {
            LocalAdbServerResult result=new LocalAdbServerResult();bool connected=false;Stopwatch watch=Stopwatch.StartNew();
            try {using(TcpClient client=new TcpClient(AddressFamily.InterNetwork)) {
                var connection=client.ConnectAsync(IPAddress.Loopback,port);
                if(!connection.Wait(Remaining(watch,timeout))){result.State="connect-timeout";return result;}
                connected=true;using(NetworkStream stream=client.GetStream()) {
                    byte[] request=Encoding.ASCII.GetBytes("000Chost:version");stream.WriteTimeout=Remaining(watch,timeout);stream.Write(request,0,request.Length);
                    string status=Encoding.ASCII.GetString(ReadExact(stream,4,watch,timeout));
                    if(status=="FAIL"){result.State="server-rejected";return result;}
                    if(status!="OKAY"){result.State="invalid-reply";return result;}
                    int length;if(!int.TryParse(Encoding.ASCII.GetString(ReadExact(stream,4,watch,timeout)),NumberStyles.HexNumber,CultureInfo.InvariantCulture,out length)||length<1||length>8){result.State="invalid-reply";return result;}
                    int version;if(!int.TryParse(Encoding.ASCII.GetString(ReadExact(stream,length,watch,timeout)),NumberStyles.HexNumber,CultureInfo.InvariantCulture,out version)||version<1||version>65535){result.State="invalid-reply";return result;}
                    result.State="version-reply";result.Version=version;return result;
                }
            }}catch(AggregateException e){SocketException socket=e.GetBaseException() as SocketException;result.SocketCode=socket==null?(int?)null:socket.ErrorCode;result.State=socket!=null&&socket.SocketErrorCode==SocketError.ConnectionRefused?"not-listening":"connect-failed";}
            catch(TimeoutException){result.State=connected?"response-timeout":"connect-timeout";}
            catch(EndOfStreamException){result.State="peer-closed";}
            catch(IOException e){SocketException socket=e.InnerException as SocketException;result.SocketCode=socket==null?(int?)null:socket.ErrorCode;result.State=socket!=null&&(socket.SocketErrorCode==SocketError.TimedOut||socket.SocketErrorCode==SocketError.WouldBlock)?"response-timeout":"io-failed";}
            catch(SocketException e){result.SocketCode=e.ErrorCode;result.State=e.SocketErrorCode==SocketError.ConnectionRefused?"not-listening":"connect-failed";}
            catch(Exception){result.State="probe-failed";}
            return result;
        }
    }
}
