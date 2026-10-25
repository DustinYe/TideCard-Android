/* SPDX-License-Identifier: Apache-2.0
 * TideCard adaptation, 2026: local-only Java implementation and application integration.
 * Protocol / pairing reference: RikkaApps/Shizuku v13.6.0 (RikkaApps contributors).
 * See NOTICE and THIRD_PARTY.md for source links and modification details.
 */
package app.tidecard.adb;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLSocket;

public final class AdbClient implements AutoCloseable {
    private static final int CNXN=0x4e584e43,STLS=0x534c5453,AUTH=0x48545541,OPEN=0x4e45504f,OKAY=0x59414b4f,WRTE=0x45545257,CLSE=0x45534c43;
    private Socket raw;private SSLSocket tls;private DataInputStream in;private DataOutputStream out;
    private record Message(int type,int a,int b,byte[] bytes){}
    private void write(int type,int a,int b,byte[] payload)throws IOException{
        if(payload==null)payload=new byte[0];int sum=0;for(byte v:payload)sum+=v&255;ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);h.putInt(type).putInt(a).putInt(b).putInt(payload.length).putInt(sum).putInt(type^-1);out.write(h.array());out.write(payload);out.flush();
    }
    private Message read()throws IOException{
        byte[] h=new byte[24];in.readFully(h);ByteBuffer b=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);int t=b.getInt(),a=b.getInt(),c=b.getInt(),n=b.getInt(),sum=b.getInt(),magic=b.getInt();
        if((t^-1)!=magic||n<0||n>1048576)throw new IOException("无效的本地 ADB 数据");byte[] bytes=new byte[n];in.readFully(bytes);int actual=0;for(byte v:bytes)actual+=v&255;if(sum!=0&&sum!=actual)throw new IOException("本地 ADB 数据校验失败");return new Message(t,a,c,bytes);
    }
    public void connect(int port,AdbIdentity identity)throws Exception{
        if(port<1||port>65535)throw new IllegalArgumentException("无效连接端口");raw=new Socket();raw.connect(new InetSocketAddress("127.0.0.1",port),5000);raw.setSoTimeout(12000);raw.setTcpNoDelay(true);in=new DataInputStream(raw.getInputStream());out=new DataOutputStream(raw.getOutputStream());
        write(CNXN,0x01000001,1048576,"host::\0".getBytes(StandardCharsets.UTF_8));Message m=read();
        if(m.type==STLS){write(STLS,0x01000000,0,null);tls=identity.tls(raw,port);in=new DataInputStream(tls.getInputStream());out=new DataOutputStream(tls.getOutputStream());m=read();}
        else if(m.type==AUTH){write(AUTH,2,0,identity.sign(m.bytes));m=read();if(m.type!=CNXN){write(AUTH,3,0,identity.publicAdb());m=read();}}
        if(m.type!=CNXN)throw new IOException("本机无线调试尚未授权，请先配对");
    }
    /** No user-supplied commands: caller only supplies the app's fixed bootstrap command. */
    public String launch(String command)throws Exception{
        write(OPEN,1,0,("shell:"+command+"\0").getBytes(StandardCharsets.UTF_8));Message m=read();if(m.type!=OKAY)throw new IOException("系统拒绝启动内置后台");ByteArrayOutputStream result=new ByteArrayOutputStream();
        while(true){m=read();if(m.type==WRTE){if(result.size()+m.bytes.length>65536)throw new IOException("启动输出过长");result.write(m.bytes);write(OKAY,1,m.a,null);}else if(m.type==CLSE){write(CLSE,1,m.a,null);break;}else throw new IOException("系统启动响应异常");}
        return result.toString("UTF-8");
    }
    public void close(){try{if(tls!=null)tls.close();}catch(Exception ignored){}try{if(raw!=null)raw.close();}catch(Exception ignored){}}
}
