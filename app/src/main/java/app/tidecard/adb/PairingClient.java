/* SPDX-License-Identifier: Apache-2.0
 * TideCard adaptation, 2026: local-only Java implementation and application integration.
 * Protocol / pairing reference: RikkaApps/Shizuku v13.6.0 (RikkaApps contributors).
 * See NOTICE and THIRD_PARTY.md for source links and modification details.
 */
package app.tidecard.adb;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLSocket;
import org.conscrypt.Conscrypt;
import moe.shizuku.manager.adb.PairingContext;

/** Android wireless ADB pairing, restricted to this phone's loopback endpoint. */
public final class PairingClient {
    private static void packet(DataOutputStream out,int type,byte[] payload)throws IOException{out.writeByte(1);out.writeByte(type);out.writeInt(payload.length);out.write(payload);out.flush();}
    private static byte[] read(DataInputStream in,int type)throws IOException{
        if(in.readUnsignedByte()!=1||in.readUnsignedByte()!=type)throw new IOException("系统返回了不支持的配对协议");int n=in.readInt();if(n<1||n>16384)throw new IOException("无效配对数据");byte[] b=new byte[n];in.readFully(b);return b;
    }
    public static void pair(int port,String code,AdbIdentity identity)throws Exception{
        if(port<1||port>65535||!code.matches("[0-9]{6}"))throw new IllegalArgumentException("请输入正确的六位配对码");
        Socket raw=new Socket();raw.connect(new InetSocketAddress("127.0.0.1",port),6000);raw.setSoTimeout(15000);raw.setTcpNoDelay(true);
        try(raw;SSLSocket tls=identity.tls(raw,port)){
            byte[] material=Conscrypt.exportKeyingMaterial(tls,"adb-label\0",null,64),codeBytes=code.getBytes(StandardCharsets.UTF_8),password=new byte[codeBytes.length+material.length];
            System.arraycopy(codeBytes,0,password,0,codeBytes.length);System.arraycopy(material,0,password,codeBytes.length,material.length);
            try(PairingContext pairing=new PairingContext(password)){
                java.util.Arrays.fill(password,(byte)0);
                DataInputStream in=new DataInputStream(tls.getInputStream());DataOutputStream out=new DataOutputStream(tls.getOutputStream());
                packet(out,0,pairing.message());if(!pairing.initialize(read(in,0)))throw new IOException("配对码错误或已失效，请重新打开系统配对窗口");
                byte[] peer=new byte[8192],key=identity.publicAdb();System.arraycopy(key,0,peer,1,Math.min(key.length,8191));
                byte[] encrypted=pairing.encrypt(peer);if(encrypted==null)throw new IOException("无法加密配对信息");packet(out,1,encrypted);
                byte[] result=pairing.decrypt(read(in,1));if(result==null||result.length!=8192)throw new IOException("配对码错误或已失效");
            }
        }
    }
}
